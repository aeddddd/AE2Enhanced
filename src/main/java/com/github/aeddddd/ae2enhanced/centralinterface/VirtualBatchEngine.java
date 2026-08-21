package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import appeng.me.GridAccessException;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketVirtualCraftingParticles;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 虚拟批量合成引擎.
 *
 * <p>安装虚拟并行卡后，对支持 {@link HandlerCapabilities#VIRTUAL_BATCH} 的目标
 * 直接从 AE2 存储体系扣除资源并返回产物，不占用物理设备。</p>
 *
 * <p>资源核算策略（双池模型）：
 * <ul>
 *   <li>物品：AE2 CPU 在 submitJob 时已把整单材料从网络预提到 CPU 内部缓存
 *       （MECraftingInventory），调用 pushPattern 前又提取 1 份到 InventoryCrafting。
 *       因此虚拟批量从 <b>CPU 内部缓存</b>核算并提取额外的 {@code parallel - 1} 份；
 *       不能从网络核算——网络已被任务预留掏空，从网络核算并行数会恒退化为 1。</li>
 *   <li>非物品（流体、能量、Mana、Starlight、气体、源质等）：CPU 不会预提取，
 *       因此从网络提取完整 {@code parallel} 份。</li>
 * </ul>
 * 催化剂/非消耗项由具体 handler 的 {@link IVirtualBatchCraftingHandler#getVirtualCost}
 * 决定是否排除。</p>
 */
public class VirtualBatchEngine {

    private final DualityCentralInterface owner;

    // 虚拟合成粒子效果：目标位置 + 剩余 tick（仅本引擎使用，从 Duality 内聚至此）
    private final List<VirtualParticleTarget> activeParticleTargets = new ArrayList<>();

    public VirtualBatchEngine(DualityCentralInterface owner) {
        this.owner = owner;
    }

    /**
     * 是否仍有未播完的虚拟合成粒子（供 Duality 决定 tick 速率）。
     */
    boolean hasActiveParticles() {
        return !this.activeParticleTargets.isEmpty();
    }

    static class VirtualParticleTarget {
        final BlockPos pos;
        final int particleType;
        final int color;
        int remainingTicks;

        VirtualParticleTarget(BlockPos pos, int particleType, int color, int duration) {
            this.pos = pos;
            this.particleType = particleType;
            this.color = color;
            this.remainingTicks = duration;
        }
    }

    /**
     * 尝试对指定目标执行一次虚拟批量合成。
     *
     * @param grid        网格连接 seam
     * @param patternDetails 配方详情
     * @param originalTable 原始合成台（已包含 CPU 提取的第一份物品）
     * @param target      目标绑定
     * @param handler     批量虚拟合成 handler
     * @param maxParallel 卡片 tier 提供的最大并行数（Long.MAX_VALUE 表示无上限）
     * @return 实际完成的并行数，0 表示失败
     */
    public long execute(IGridConnection grid,
                        ICraftingPatternDetails patternDetails,
                        InventoryCrafting originalTable,
                        TargetBinding target,
                        IVirtualBatchCraftingHandler handler,
                        long maxParallel) {
        World world = owner.host.getTileEntity().getWorld();
        if (world.provider.getDimension() != target.dimension) {
            logFail(world, target, "dimension mismatch");
            return 0;
        }

        if (owner.isOnGlobalVirtualCooldown()) {
            logFail(world, target, "on global virtual cooldown");
            return 0;
        }
        if (owner.isOnVirtualCooldown(target)) {
            logFail(world, target, "on target virtual cooldown");
            return 0;
        }
        TargetSession session = owner.getOrCreateSession(target);
        if (!session.isIdle()) {
            logFail(world, target, "session not idle (" + session.getState() + ")");
            return 0;
        }

        // handler 反射可能抛出异常/Error（如 NoClassDefFoundError），必须隔离
        boolean validTarget;
        try {
            validTarget = handler.isValidTarget(world, target.pos);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[AE2E] Virtual isValidTarget threw for {} at {}: {}",
                    target.blockId, target.pos, t.toString());
            validTarget = false;
        }
        if (!validTarget) {
            session.setUnavailable();
            logFail(world, target, "invalid target");
            return 0;
        }

        if (!TargetOwnershipTracker.instance().tryAcquire(target, owner)) {
            logFail(world, target, "ownership already held");
            return 0;
        }

        boolean ownershipAcquired = true;
        try {
            InventoryCrafting virtualTable = owner.copyInventoryCrafting(originalTable);
            IAEItemStack[] outputs = patternDetails.getOutputs();

            if (!handler.canCraftVirtually(world, target.pos, virtualTable, outputs, patternDetails)) {
                logFail(world, target, "canCraftVirtually returned false");
                return 0;
            }

            IStorageGrid storage;
            IEnergySource energy;
            try {
                storage = grid.storage();
                energy = grid.energy();
            } catch (GridAccessException e) {
                logFail(world, target, "grid access exception: " + e.getMessage());
                return 0;
            }

            // CPU 内部物品缓存：任务提交时整单材料已预提到此处，虚拟并行的额外物品
            // 必须从该缓存核算/提取。可能为 null（非 CPU 直推场景），此时回退到网络核算。
            IMEInventory<IAEItemStack> itemSource = owner.getVirtualItemSource();

            long actualParallel = computeActualParallel(storage, energy, handler, world, target,
                    virtualTable, outputs, maxParallel, patternDetails, itemSource);
            if (actualParallel <= 0) {
                logFail(world, target, "computeActualParallel returned 0");
                return 0;
            }

            IActionSource source = NetworkAccess.machineSource(owner.host);
            List<IAEStack> netCosts = getNetCosts(handler, world, target, virtualTable,
                    outputs, actualParallel, patternDetails);
            if (netCosts == null) {
                logFail(world, target, "getNetCosts returned null for parallel=" + actualParallel);
                return 0;
            }

            if (!VirtualCostExtractor.simulateExtract(storage, netCosts, source, itemSource)) {
                logFail(world, target, "simulateExtract failed for parallel=" + actualParallel
                        + ", netCosts=[" + describeCosts(netCosts) + "]");
                return 0;
            }

            double energyCost = AE2EnhancedConfig.centralInterface.virtualParallelEnergyCost * (double) actualParallel;
            if (!VirtualCostExtractor.simulateExtractEnergy(energy, energyCost)) {
                logFail(world, target, "simulateExtractEnergy failed (need " + energyCost + " AE)");
                return 0;
            }

            List<IAEStack> extracted = VirtualCostExtractor.extractAll(storage, netCosts, source, itemSource);
            if (extracted == null) {
                logFail(world, target, "extractAll failed, netCosts=[" + describeCosts(netCosts) + "]");
                return 0;
            }

            if (!VirtualCostExtractor.extractEnergy(energy, energyCost, source)) {
                VirtualCostExtractor.rollbackExtracted(storage, extracted, source, itemSource);
                logFail(world, target, "extractEnergy failed after resources extracted");
                return 0;
            }

            List<ItemStack> products = handler.virtualCraftBatch(world, target.pos, virtualTable,
                    outputs, actualParallel, source, patternDetails);
            if (products == null || products.isEmpty()) {
                // handler 未能产出：回滚已提取资源，避免材料被吞。
                // 已扣除的 AE 能量无法经 IEnergySource 返还，属可接受的微小损耗。
                VirtualCostExtractor.rollbackExtracted(storage, extracted, source, itemSource);
                logFail(world, target, "virtualCraftBatch returned no products");
                return 0;
            }

            products = mergeProducts(products);
            owner.pendingProducts.addAll(products);

            List<EnumParticleTypes> particleTypes = handler.getVirtualCraftingParticles(world, target.pos);
            int particleType = particleTypes.isEmpty()
                    ? EnumParticleTypes.PORTAL.getParticleID()
                    : particleTypes.get(world.rand.nextInt(particleTypes.size())).getParticleID();
            addParticleTarget(target.pos, particleType);

            if (actualParallel > 1 || !handler.skipCooldownOnSingleBatch()) {
                owner.virtualCooldowns.put(target, AE2EnhancedConfig.centralInterface.virtualCooldownTargetTicks);
            } else {
                // 部分 handler（如 Extended Crafting 工作台）在单份处理时希望跳过冷却，
                // 使 CPU 能立即继续调度，等效于无并行时的物理发配行为。
            }
            owner.tryWakeTickDevice();
            return actualParallel;
        } catch (Throwable t) {
            // 必须捕获 Throwable：第三方 handler 的 canCraftVirtually/virtualCraftBatch/getVirtualCost
            // 可能抛出 NoClassDefFoundError/NoSuchMethodError 等 Error，逃逸至 AE2 CPU tick 会崩服
            AE2Enhanced.LOGGER.warn("[AE2E] Virtual batch dispatch error for {} at {}: {}",
                    target.blockId, target.pos, t.toString());
            return 0;
        } finally {
            if (ownershipAcquired) {
                TargetOwnershipTracker.instance().release(target, owner);
            }
        }
    }

    /**
     * 处理虚拟产物的网络注入、粒子包发送、冷却递减。
     *
     * @return 是否在本 tick 中实际做了工作
     */
    public boolean tick(IGridConnection grid) {
        boolean didWork = false;
        World world = owner.host.getTileEntity().getWorld();

        if (!owner.pendingProducts.isEmpty()) {
            List<ItemStack> toInject = owner.pendingProducts.drainAll();
            if (owner.injectItemsToNetwork(grid, world, toInject)) {
                didWork = true;
            } else {
                owner.stashItemsToStorage(world, toInject);
            }
        }

        if (sendParticlePackets(world)) {
            didWork = true;
        }

        return didWork;
    }

    /**
     * 计算当前资源可支撑的最大虚拟并行数。
     *
     * <p>直接按“单份成本 × 可用量”计算，不再使用 binary search，
     * 因此对 Long.MAX_VALUE 级别的并行卡也没有性能问题。</p>
     *
     * <p>物品可用量取自 CPU 内部缓存（任务已预留整单材料），并额外加 1
     * （CPU 已预提取 1 份到 InventoryCrafting）；非物品可用量取自网络，
     * 不加 1，因为 CPU 不会预提取流体/能量/气体等。</p>
     *
     * @param itemSource CPU 内部物品缓存；为 null 时物品回退到网络核算
     */
    long computeActualParallel(IStorageGrid storage,
                                       IEnergySource energy,
                                       IVirtualBatchCraftingHandler handler,
                                       World world,
                                       TargetBinding target,
                                       InventoryCrafting virtualTable,
                                       IAEItemStack[] outputs,
                                       long maxParallel,
                                       ICraftingPatternDetails details,
                                       IMEInventory<IAEItemStack> itemSource) {
        IActionSource source = NetworkAccess.machineSource(owner.host);
        List<IAEStack> perCopy = handler.getVirtualCost(world, target.pos, virtualTable, outputs, 1, details);
        if (perCopy == null || perCopy.isEmpty()) {
            long actual = maxParallel;
            double perOp = AE2EnhancedConfig.centralInterface.virtualParallelEnergyCost;
            if (perOp > 0) {
                double availableEnergy = VirtualCostExtractor.queryAvailableEnergy(energy);
                long energySupported = (long) (availableEnergy / perOp);
                if (energySupported <= 0) {
                    logFail(world, target, "computeActualParallel returned 0: no resource cost, energy insufficient availableEnergy="
                            + availableEnergy + " AE, perOp=" + perOp + " AE");
                    return 0;
                }
                actual = Math.min(actual, energySupported);
            }
            return actual > 0 ? actual : 0;
        }

        List<IAEStack> mergedCosts = mergeItemCosts(perCopy);

        long actual = maxParallel;
        for (IAEStack cost : mergedCosts) {
            if (cost == null || cost.getStackSize() <= 0) continue;
            long perCopySize = cost.getStackSize();
            long supported;
            long available;
            if (cost instanceof IAEItemStack) {
                // 物品：从 CPU 内部缓存核算（任务已预留整单材料）；
                // CPU 已预提取 1 份到 table，缓存只需支撑 parallel - 1 份
                available = VirtualCostExtractor.queryAvailable(storage, cost, source, itemSource);
                supported = (available / perCopySize) + 1;
            } else {
                // 非物品资源 CPU 不会预提取，网络需提供完整 parallel 份
                available = VirtualCostExtractor.queryAvailable(storage, cost, source, null);
                supported = available / perCopySize;
            }
            if (supported <= 0) {
                logFail(world, target, "computeActualParallel returned 0: resource " + describeCost(cost)
                        + " perCopy=" + perCopySize + " available=" + available
                        + " | costs=" + describeCosts(mergedCosts));
                return 0;
            }
            if (supported >= Long.MAX_VALUE) supported = Long.MAX_VALUE;
            actual = Math.min(actual, supported);
            if (actual <= 0) {
                logFail(world, target, "computeActualParallel returned 0: clamped to 0 by " + describeCost(cost)
                        + " supported=" + supported);
                return 0;
            }
        }

        double perOp = AE2EnhancedConfig.centralInterface.virtualParallelEnergyCost;
        if (perOp > 0) {
            double availableEnergy = VirtualCostExtractor.queryAvailableEnergy(energy);
            long energySupported = (long) (availableEnergy / perOp);
            if (energySupported <= 0) {
                logFail(world, target, "computeActualParallel returned 0: energy insufficient availableEnergy="
                        + availableEnergy + " AE, perOp=" + perOp + " AE");
                return 0;
            }
            actual = Math.min(actual, energySupported);
        }
        return actual > 0 ? actual : 0;
    }

    /**
     * 资源描述（用于失败日志）：物品显示名称+数量，其他类型显示类名+数量。
     */
    private static String describeCost(IAEStack stack) {
        if (stack instanceof IAEItemStack) {
            ItemStack is = ((IAEItemStack) stack).createItemStack();
            return is.isEmpty() ? String.valueOf(stack) : is.getDisplayName() + " x" + stack.getStackSize();
        }
        return stack.getClass().getSimpleName() + " x" + stack.getStackSize() + " [" + stack + "]";
    }

    private static String describeCosts(List<IAEStack> costs) {
        StringBuilder sb = new StringBuilder();
        for (IAEStack c : costs) {
            if (c == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(describeCost(c));
        }
        return sb.toString();
    }

    /**
     * 获取需要额外提取的资源清单。
     *
     * <p>策略：以 handler 返回的 {@code parallel} 份总成本为权威值。
     * 物品成本按物品身份对齐后减去 CPU 已预提取到 table 的第一份（其余份数由
     * 调用方从 CPU 内部缓存提取）；非物品成本不减（CPU 不预提取，由网络全额提供）。</p>
     *
     * <p>物品对齐按 {@link ItemCostKey} 聚合而非 List 下标，避免依赖 handler
     * 在 count=1 与 count=parallel 两次调用中返回相同的条目顺序。</p>
     */
    List<IAEStack> getNetCosts(IVirtualBatchCraftingHandler handler,
                                       World world,
                                       TargetBinding target,
                                       InventoryCrafting virtualTable,
                                       IAEItemStack[] outputs,
                                       long parallel,
                                       ICraftingPatternDetails details) {
        if (parallel <= 0) {
            return Collections.emptyList();
        }

        List<IAEStack> fullCosts = handler.getVirtualCost(world, target.pos, virtualTable, outputs, parallel, details);
        if (fullCosts == null || fullCosts.isEmpty()) {
            return Collections.emptyList();
        }
        List<IAEStack> perCopy = handler.getVirtualCost(world, target.pos, virtualTable, outputs, 1, details);

        // 单份物品成本按物品类型聚合，用于扣除 CPU 已预提取的第一份
        Map<ItemCostKey, Long> perCopyByItem = new HashMap<>();
        if (perCopy != null) {
            for (IAEStack cost : mergeItemCosts(perCopy)) {
                if (cost instanceof IAEItemStack && cost.getStackSize() > 0) {
                    perCopyByItem.merge(new ItemCostKey(((IAEItemStack) cost).createItemStack()),
                            cost.getStackSize(), Long::sum);
                }
            }
        }

        List<IAEStack> net = new ArrayList<>();
        for (IAEStack full : mergeItemCosts(fullCosts)) {
            if (full == null || full.getStackSize() <= 0) continue;
            long extra;
            if (full instanceof IAEItemStack) {
                // 物品：CPU 已预提取 1 份，只需额外提取剩余份数
                Long perCopySize = perCopyByItem.get(new ItemCostKey(((IAEItemStack) full).createItemStack()));
                long firstCopy = perCopySize != null ? perCopySize : (full.getStackSize() / parallel);
                extra = full.getStackSize() - firstCopy;
            } else {
                // 非物品：CPU 未预提取，需全额提取
                extra = full.getStackSize();
            }
            if (extra > 0) {
                IAEStack extraStack = full.copy();
                extraStack.setStackSize(extra);
                net.add(extraStack);
            }
        }
        return net;
    }

    /**
     * 合并产物列表中可堆叠的相同物品。
     */
    List<ItemStack> mergeProducts(List<ItemStack> products) {
        if (products == null || products.size() <= 1) {
            return products;
        }
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack incoming : products) {
            if (incoming.isEmpty()) continue;
            boolean found = false;
            for (ItemStack existing : merged) {
                if (ItemStack.areItemsEqual(existing, incoming) && ItemStack.areItemStackTagsEqual(existing, incoming)) {
                    int canAdd = Math.min(incoming.getCount(), existing.getMaxStackSize() - existing.getCount());
                    existing.grow(canAdd);
                    if (canAdd < incoming.getCount()) {
                        ItemStack leftover = incoming.copy();
                        leftover.setCount(incoming.getCount() - canAdd);
                        merged.add(leftover);
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(incoming.copy());
            }
        }
        return merged;
    }

    /**
     * 记录虚拟合成失败原因。默认只在 debug 日志开启时输出，避免刷屏。
     */
    private void logFail(World world, TargetBinding target, String reason) {
        if (AE2EnhancedConfig.centralInterface.debugVirtualBatch && !world.isRemote) {
            AE2Enhanced.LOGGER.debug("[AE2E-VirtBatch] fail at {}: {}", target.pos, reason);
        }
    }

    private void addParticleTarget(BlockPos pos, int particleType) {
        int color = 0xFFFFFFFF;
        if (particleType == EnumParticleTypes.PORTAL.getParticleID()) color = 0xFFAA55FF;
        else if (particleType == EnumParticleTypes.ENCHANTMENT_TABLE.getParticleID()) color = 0xFF55AAFF;
        else if (particleType == EnumParticleTypes.SPELL_WITCH.getParticleID()) color = 0xFFFF55FF;
        else if (particleType == EnumParticleTypes.END_ROD.getParticleID()) color = 0xFFFFFFFF;

        int maxTargets = AE2EnhancedConfig.centralInterface.virtualParticleMaxTargets;
        if (this.activeParticleTargets.size() >= maxTargets) {
            this.activeParticleTargets.remove(0);
        }
        this.activeParticleTargets.add(new VirtualParticleTarget(
                pos, particleType, color, AE2EnhancedConfig.centralInterface.virtualParticleDurationTicks));
    }

    private boolean sendParticlePackets(World world) {
        if (this.activeParticleTargets.isEmpty()) {
            return false;
        }
        if (world == null || world.isRemote) {
            return false;
        }

        int countPerTick = AE2EnhancedConfig.centralInterface.virtualParticleCountPerTick;
        int renderDistance = AE2EnhancedConfig.centralInterface.virtualParticleRenderDistance;
        int renderDistanceSq = renderDistance * renderDistance;

        List<PacketVirtualCraftingParticles.ParticleTarget> packetTargets = new ArrayList<>();
        Iterator<VirtualParticleTarget> it = this.activeParticleTargets.iterator();
        while (it.hasNext()) {
            VirtualParticleTarget target = it.next();
            target.remainingTicks--;
            if (target.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            packetTargets.add(new PacketVirtualCraftingParticles.ParticleTarget(
                    target.pos, target.particleType, countPerTick, target.color));
        }

        if (packetTargets.isEmpty()) {
            return false;
        }

        PacketVirtualCraftingParticles packet = new PacketVirtualCraftingParticles(packetTargets);
        BlockPos interfacePos = owner.host.getTileEntity().getPos();
        for (net.minecraft.entity.player.EntityPlayerMP player : world.getPlayers(net.minecraft.entity.player.EntityPlayerMP.class,
                p -> p.getDistanceSq(interfacePos) <= renderDistanceSq)) {
            AE2Enhanced.network.sendTo(packet, player);
        }
        return true;
    }

    /**
     * 将 IAEStack 列表中的 IAEItemStack 按物品类型合并，
     * 避免同种物品分散在多个 crafting slot 时被重复计算并行数。
     */
    static List<IAEStack> mergeItemCosts(List<IAEStack> costs) {
        if (costs == null || costs.isEmpty()) {
            return Collections.emptyList();
        }
        Map<ItemCostKey, Long> itemSums = new HashMap<>();
        List<IAEStack> others = new ArrayList<>();
        for (IAEStack cost : costs) {
            if (cost == null || cost.getStackSize() <= 0) continue;
            if (cost instanceof IAEItemStack) {
                ItemCostKey key = new ItemCostKey(((IAEItemStack) cost).createItemStack());
                itemSums.merge(key, cost.getStackSize(), Long::sum);
            } else {
                others.add(cost);
            }
        }
        List<IAEStack> merged = new ArrayList<>(others);
        for (Map.Entry<ItemCostKey, Long> entry : itemSums.entrySet()) {
            IAEItemStack stack = AEItemStack.fromItemStack(entry.getKey().stack.copy());
            if (stack != null) {
                stack.setStackSize(entry.getValue());
                merged.add(stack);
            }
        }
        return merged;
    }

    private static final class ItemCostKey {
        private final ItemStack stack;

        ItemCostKey(ItemStack stack) {
            this.stack = stack.copy();
            this.stack.setCount(1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemCostKey)) return false;
            ItemStack other = ((ItemCostKey) o).stack;
            return ItemStack.areItemsEqual(this.stack, other)
                    && ItemStack.areItemStackTagsEqual(this.stack, other);
        }

        @Override
        public int hashCode() {
            net.minecraft.util.ResourceLocation regName = this.stack.getItem().getRegistryName();
            int result = regName != null ? regName.hashCode() : System.identityHashCode(this.stack.getItem());
            result = 31 * result + this.stack.getMetadata();
            if (this.stack.hasTagCompound()) {
                result = 31 * result + this.stack.getTagCompound().hashCode();
            }
            return result;
        }
    }

}
