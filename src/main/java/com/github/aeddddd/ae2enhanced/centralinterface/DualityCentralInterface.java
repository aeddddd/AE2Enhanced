package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.AEApi;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternSubDetails;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketVirtualCraftingParticles;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidCompat;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 中枢 ME 接口的核心逻辑类,复刻 AE2 {@link appeng.helpers.DualityInterface} 的结构.
 *
 * P1 阶段实现：
 * - 样板存储与 CraftingProvider 注册
 * - 远程目标绑定管理
 * - 单份材料发配(默认 fallback)：推送材料到目标 IItemHandler,tick 时收集产物
 */
public class DualityCentralInterface implements appeng.util.inv.IAEAppEngInventory {

    public static final int NUMBER_OF_PATTERN_SLOTS = 36;
    public static final int NUMBER_OF_CONFIG_SLOTS = 9;
    public static final int NUMBER_OF_STORAGE_SLOTS = 9;
    public static final int NUMBER_OF_UPGRADE_SLOTS = 4;

    final ICentralInterfaceHost host;
    private final ConfigManager cm;

    private final AppEngInternalAEInventory config;
    private final AppEngInternalInventory patterns;
    private final AppEngInternalInventory storage;

    private Set<ICraftingPatternDetails> craftingList = null;
    private int priority = 0;

    // 远程绑定
    private final List<TargetBinding> bindings = new ArrayList<>();
    private String boundBlockId = null;
    // 每个绑定目标对应一个 TargetSession,集中管理 PUSHING/PROCESSING/COLLECTING/IDLE/UNAVAILABLE
    final Map<TargetBinding, TargetSession> sessions = new HashMap<>();
    // 虚拟合成产物暂存队列(等待 waitingFor 注册后再注入网络)
    final List<ItemStack> pendingVirtualProducts = new ArrayList<>();

    // 虚拟合成粒子效果：目标位置 + 剩余 tick
    final List<VirtualParticleTarget> activeParticleTargets = new ArrayList<>();

    // 虚拟合成冷却：每个目标成功执行一批后进入冷却
    final Map<TargetBinding, Integer> virtualCooldowns = new HashMap<>();

    // 全局虚拟批间冷却：每个 pushPattern 调用最多触发一次虚拟批处理，成功/失败后均进入冷却
    int globalVirtualCooldown = 0;

    // 上一次虚拟批量合成的实际并行数，供 CraftingCPUCluster Mixin 修正任务计数
    private long lastVirtualBatchSize = 0;

    // Mixin 传入的下一次虚拟批量上限（通常为 CPU 任务剩余数），0 表示未设置
    private long nextVirtualBatchLimit = 0;

    // Mixin 传入的 CPU 内部物品缓存（MECraftingInventory），虚拟批量应从此处提取额外份数，
    // 而不是从网络提取，因为 AE2 CPU 已经把整单材料预提到 CPU 缓存中。
    private appeng.api.storage.IMEInventory<appeng.api.storage.data.IAEItemStack> virtualItemSource = null;

    private final PhysicalDispatcher physicalDispatcher;
    private final VirtualBatchEngine virtualBatchEngine;

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

    // 流体 IO 尝试顺序：null（内部 tank）优先，然后按 UP/DOWN/NORTH/SOUTH/WEST/EAST 逐个尝试
    private static final List<EnumFacing> FLUID_FACE_ORDER;
    static {
        List<EnumFacing> list = new ArrayList<>();
        list.add(null);
        list.add(EnumFacing.UP);
        list.add(EnumFacing.DOWN);
        list.add(EnumFacing.NORTH);
        list.add(EnumFacing.SOUTH);
        list.add(EnumFacing.WEST);
        list.add(EnumFacing.EAST);
        FLUID_FACE_ORDER = Collections.unmodifiableList(list);
    }

    public DualityCentralInterface(ICentralInterfaceHost host) {
        this.host = host;
        this.physicalDispatcher = new PhysicalDispatcher(this);
        this.virtualBatchEngine = new VirtualBatchEngine(this);
        this.cm = new ConfigManager(new IConfigManagerHost() {
            @Override
            public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
                DualityCentralInterface.this.host.saveChanges();
            }
        });
        this.cm.registerSetting(Settings.BLOCK, YesNo.NO);
        this.cm.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);
        this.cm.registerSetting(Settings.UNLOCK, LockCraftingMode.NONE);

        this.bindings.clear();
        this.sessions.clear();

        this.config = new AppEngInternalAEInventory(this, NUMBER_OF_CONFIG_SLOTS, 512);
        this.patterns = new AppEngInternalInventory(this, NUMBER_OF_PATTERN_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem;
            }
        };
        this.storage = new AppEngInternalInventory(this, NUMBER_OF_STORAGE_SLOTS, 512) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return true;
            }
        };
    }

    // ---- Inventory Access ----

    public AppEngInternalAEInventory getConfig() { return this.config; }
    public AppEngInternalInventory getPatterns() { return this.patterns; }
    public AppEngInternalInventory getStorage() { return this.storage; }

    public IItemHandler getInventoryByName(String name) {
        if ("config".equals(name)) return this.config;
        if ("patterns".equals(name)) return this.patterns;
        if ("storage".equals(name)) return this.storage;
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.util.EnumFacing facing) {
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) this.storage;
        }
        return null;
    }

    public void dropExcessPatterns() {
        IItemHandler patterns = this.getPatterns();
        java.util.ArrayList<net.minecraft.item.ItemStack> dropList = new java.util.ArrayList<>();
        int allowedSlots = 9 + getInstalledUpgrades(Upgrades.PATTERN_EXPANSION) * 9;
        for (int invSlot = allowedSlots; invSlot < patterns.getSlots(); ++invSlot) {
            net.minecraft.item.ItemStack is = patterns.getStackInSlot(invSlot);
            if (!is.isEmpty()) {
                dropList.add(patterns.extractItem(invSlot, Integer.MAX_VALUE, false));
            }
        }
        if (dropList.size() > 0) {
            net.minecraft.world.World world = this.host.getTileEntity().getWorld();
            net.minecraft.util.math.BlockPos pos = this.host.getTileEntity().getPos();
            for (net.minecraft.item.ItemStack stack : dropList) {
                net.minecraft.entity.item.EntityItem entityItem = new net.minecraft.entity.item.EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                world.spawnEntity(entityItem);
            }
        }
    }

    public int getInstalledUpgrades(Upgrades upgrade) {
        return ((appeng.api.implementations.IUpgradeableHost) this.host).getInstalledUpgrades(upgrade);
    }

    public LockCraftingMode getCraftingLockedReason() {
        // 当前未实现红石状态检测和 unlockEvent 追踪,默认返回 NONE
        return LockCraftingMode.NONE;
    }

    // ---- Crafting Provider ----

    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        AENetworkProxy proxy = this.host.getProxy();
        if (proxy.isActive() && this.craftingList != null && !this.bindings.isEmpty()) {
            for (ICraftingPatternDetails details : this.craftingList) {
                details.setPriority(this.priority);
                // 每个 binding 注册一次,使 AE2 网络能并行调度多个相同配方
                for (int i = 0; i < this.bindings.size(); i++) {
                    craftingTracker.addCraftingOption(this.host, details);
                }
            }
        }
    }

    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        this.lastVirtualBatchSize = 0;
        long pendingLimit = this.nextVirtualBatchLimit;
        this.nextVirtualBatchLimit = 0;
        AENetworkProxy proxy = this.host.getProxy();
        if (!proxy.isActive() || this.craftingList == null
                || !this.craftingList.contains(patternDetails)) {
            return false;
        }

        long baseVirtualParallel = getVirtualParallel();
        if (pendingLimit > 0) {
            baseVirtualParallel = Math.min(baseVirtualParallel, pendingLimit);
        }
        boolean globalVirtualCooling = isOnGlobalVirtualCooldown();
        List<TargetBinding> candidates = findIdleTargets();
        boolean attemptedVirtual = false;

        for (TargetBinding target : candidates) {
            IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
            if (handler == null) {
                continue;
            }

            long handlerDefaultParallel = 1;
            if (handler instanceof IVirtualBatchCraftingHandler) {
                handlerDefaultParallel = ((IVirtualBatchCraftingHandler) handler).getDefaultParallel();
            }
            long virtualParallel = Math.max(baseVirtualParallel, handlerDefaultParallel);
            if (pendingLimit > 0) {
                virtualParallel = Math.min(virtualParallel, pendingLimit);
            }
            boolean canUseVirtual = handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)
                    && handler instanceof IVirtualBatchCraftingHandler;

            AE2Enhanced.LOGGER.info("[AE2E-Diag] pushPattern target={} handler={} baseParallel={} pendingLimit={} virtualParallel={} globalCooldown={}",
                    target.pos, handler.getClass().getSimpleName(), baseVirtualParallel, pendingLimit, virtualParallel, globalVirtualCooling);

            // 优先尝试虚拟批量合成（handler 支持虚拟批量即可，virtualParallel 仅限制最大并行数）
            // 注意：纯虚拟 handler（如 Extended Crafting 工作台）即使 virtualParallel=1 也必须走此路径，
            // 否则订单剩余 1 份时会因无物理能力而直接失败。
            if (canUseVirtual
                    && !globalVirtualCooling) {
                attemptedVirtual = true;
                IVirtualBatchCraftingHandler vh = (IVirtualBatchCraftingHandler) handler;
                long actualParallel = this.virtualBatchEngine.execute(proxy, patternDetails, table, target, vh, virtualParallel);
                if (actualParallel > 0) {
                    this.lastVirtualBatchSize = actualParallel;
                    this.globalVirtualCooldown = AE2EnhancedConfig.centralInterface.virtualCooldownGlobalTicks;
                    tryWakeTickDevice();
                    return true;
                }

                // 虚拟合成失败时，若该目标同时支持物理发配，则回退到同目标物理发配
                if (handler.hasCapability(HandlerCapabilities.PHYSICAL)) {
                    if (this.physicalDispatcher.dispatch(proxy, patternDetails, table, target, handler)) {
                        this.lastVirtualBatchSize = 1;
                        return true;
                    }
                }
                continue;
            }

            // 否则尝试物理发配
            if (handler.hasCapability(HandlerCapabilities.PHYSICAL)) {
                if (this.physicalDispatcher.dispatch(proxy, patternDetails, table, target, handler)) {
                    this.lastVirtualBatchSize = 1;
                    return true;
                }
            }
        }

        // 尝试了虚拟批量但未成功，进入全局冷却防止 CPU 同 tick 反复重试
        if (attemptedVirtual) {
            this.globalVirtualCooldown = AE2EnhancedConfig.centralInterface.virtualCooldownGlobalTicks;
            tryWakeTickDevice();
        }
        return false;
    }

    /**
     * 返回上一次 pushPattern 中虚拟批量合成的实际并行数。
     * 供 MixinCraftingCPUCluster 修正 AE2 CPU 的任务计数。
     */
    public long getLastVirtualBatchSize() {
        return this.lastVirtualBatchSize;
    }

    /**
     * 设置下一次虚拟批量合成的上限，防止实际并行数超过 CPU 任务剩余数。
     * 由 MixinCraftingCPUCluster 在调用 pushPattern 前设置。
     */
    public void setNextVirtualBatchLimit(long limit) {
        this.nextVirtualBatchLimit = Math.max(0, limit);
    }

    /**
     * 设置虚拟批量合成时额外物品份数的来源。
     * 传入 CPU 的 MECraftingInventory，使虚拟批量从 CPU 缓存提取，而非网络。
     */
    public void setVirtualItemSource(appeng.api.storage.IMEInventory<appeng.api.storage.data.IAEItemStack> source) {
        this.virtualItemSource = source;
    }

    public appeng.api.storage.IMEInventory<appeng.api.storage.data.IAEItemStack> getVirtualItemSource() {
        return this.virtualItemSource;
    }

    /**
     * 从 Central Interface 升级槽中读取虚拟并行卡，返回最高 tier 对应的并行数。
     * 未安装卡时返回 1。
     */
    long getVirtualParallel() {
        long maxParallel = 1;
        IItemHandler upgrades = null;
        try {
            upgrades = ((appeng.api.implementations.IUpgradeableHost) this.host).getInventoryByName("upgrades");
        } catch (Exception ignored) {
            // host 不是 IUpgradeableHost 或不支持 upgrades 栏位
        }
        if (upgrades == null) {
            return maxParallel;
        }
        boolean foundCard = false;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemVirtualParallelCard) {
                foundCard = true;
                maxParallel = Math.max(maxParallel, ItemVirtualParallelCard.getParallel(stack));
            }
        }

        return maxParallel;
    }

    /**
     * 返回所有当前处于 IDLE 状态且不在虚拟冷却中的绑定目标.
     */
    private List<TargetBinding> findIdleTargets() {
        List<TargetBinding> result = new ArrayList<>();
        for (TargetBinding binding : this.bindings) {
            TargetSession session = this.sessions.get(binding);
            if (session == null || session.isIdle()) {
                if (!isOnVirtualCooldown(binding)) {
                    result.add(binding);
                }
            }
        }
        return result;
    }

    boolean isOnVirtualCooldown(TargetBinding binding) {
        Integer cooldown = this.virtualCooldowns.get(binding);
        return cooldown != null && cooldown > 0;
    }

    boolean isOnGlobalVirtualCooldown() {
        return this.globalVirtualCooldown > 0;
    }

    /**
     * 递减所有虚拟合成冷却，返回是否有冷却刚好结束。
     */
    boolean decrementVirtualCooldowns() {
        boolean expired = false;

        if (this.globalVirtualCooldown > 0) {
            this.globalVirtualCooldown--;
            if (this.globalVirtualCooldown <= 0) {
                expired = true;
            }
        }

        Iterator<Map.Entry<TargetBinding, Integer>> it = this.virtualCooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TargetBinding, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            entry.setValue(remaining);
            if (remaining <= 0) {
                it.remove();
                expired = true;
            }
        }
        return expired;
    }

    TargetSession getOrCreateSession(TargetBinding binding) {
        return this.sessions.computeIfAbsent(binding, b -> new TargetSession(b, this));
    }

    /**
     * 将物品列表注入 AE 网络,溢出部分先进入 storage slots,再溢出则掉落.
     * 流体假物品走物品通道,由 ae2fc 的 FakeMonitor 体系接管(若 ae2fc 未安装,
     * 则由本 mod 的 MixinNetworkMonitorFluid 转注入流体通道).
     */
    boolean injectItemsToNetwork(AENetworkProxy proxy, World world, List<ItemStack> items) {
        try {
            IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            for (ItemStack product : items) {
                if (product.isEmpty()) continue;
                IAEItemStack toInsert = AEItemStack.fromItemStack(product);
                IAEItemStack remaining = Platform.poweredInsert(
                        proxy.getEnergy(),
                        proxy.getStorage().getInventory(channel),
                        toInsert,
                        new appeng.me.helpers.MachineSource(this.host));
                if (remaining != null && remaining.getStackSize() > 0) {
                    ItemStack leftover = remaining.createItemStack();
                    stashItemToStorage(world, leftover);
                }
            }
            return true;
        } catch (appeng.me.GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] CentralInterface failed to inject items to network", e);
            return false;
        }
    }

    /**
     * 将单个物品暂存到 storage slots,溢出则掉落.
     */
    void stashItemToStorage(World world, ItemStack item) {
        if (item.isEmpty()) return;
        ItemStack leftover = item.copy();
        for (int s = 0; s < this.storage.getSlots() && !leftover.isEmpty(); s++) {
            leftover = this.storage.insertItem(s, leftover, false);
        }
        if (!leftover.isEmpty()) {
            BlockPos pos = this.host.getTileEntity().getPos();
            net.minecraft.entity.item.EntityItem entityItem = new net.minecraft.entity.item.EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, leftover);
            world.spawnEntity(entityItem);
        }
    }

    /**
     * 将物品暂存到 storage slots,溢出则掉落.
     */
    void stashItemsToStorage(World world, List<ItemStack> items) {
        for (ItemStack item : items) {
            stashItemToStorage(world, item);
        }
    }

    public boolean isBusy() {
        if (this.bindings.isEmpty()) {
            return false;
        }
        // 虚拟全局冷却期间，CPU 应认为本接口忙碌，避免同一 tick 内反复触发多批
        if (isOnGlobalVirtualCooldown()) {
            return true;
        }
        for (TargetBinding binding : this.bindings) {
            TargetSession session = this.sessions.get(binding);
            // UNAVAILABLE 目标不算作“忙碌”，否则目标方块短暂卸载/替换后会永久卡住 CPU
            if (session == null || session.isIdle() || session.isUnavailable()) {
                if (!isOnVirtualCooldown(binding)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 对 UNAVAILABLE 目标定期进行有效性检查，若重新有效则恢复为 IDLE.
     * 防止目标方块短暂卸载/替换后该并行槽位永久冻结。
     */
    private void recoverUnavailableTargets() {
        World world = this.host.getTileEntity().getWorld();
        for (TargetBinding binding : this.bindings) {
            TargetSession session = this.sessions.get(binding);
            if (session == null || !session.isUnavailable()) {
                continue;
            }
            if (world.provider.getDimension() != binding.dimension) continue;
            if (!world.isBlockLoaded(binding.pos)) continue;

            IRemoteHandler handler = HandlerRegistry.findHandler(binding.blockId);
            if (handler == null) continue;

            if (handler.isValidTarget(world, binding.pos)) {
                session.recoverFromUnavailable();
            }
        }
    }

    // ---- Ticking ----

    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 5, !hasWorkToDo(), true);
    }

    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        AENetworkProxy proxy = this.host.getProxy();
        if (!proxy.isActive()) {
            return TickRateModulation.SLEEP;
        }

        // 递减虚拟合成冷却
        boolean cooldownExpired = decrementVirtualCooldowns();

        // 先尝试恢复 UNAVAILABLE 目标：如果目标重新有效，则恢复为 IDLE
        recoverUnavailableTargets();

        boolean didWork = false;
        World world = this.host.getTileEntity().getWorld();
        int timeoutTicks = com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.centralInterface.processingTimeoutTicks;

        // 物理 session 处理（PROCESSING / COLLECTING）
        if (this.physicalDispatcher.tick(proxy, world, timeoutTicks)) {
            didWork = true;
        }

        // 虚拟产物注入 + 粒子包
        if (this.virtualBatchEngine.tick(proxy)) {
            didWork = true;
        }

        // 将 storage slots 中的物品推入网络(如果有空间)
        pushStorageToNetwork(proxy);

        return (hasWorkToDo() || !this.activeParticleTargets.isEmpty())
                ? (didWork || cooldownExpired ? TickRateModulation.URGENT : TickRateModulation.SLOWER)
                : TickRateModulation.SLEEP;
    }

    private void pushStorageToNetwork(AENetworkProxy proxy) {
        try {
            IItemStorageChannel channel =
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            for (int s = 0; s < this.storage.getSlots(); s++) {
                ItemStack stack = this.storage.getStackInSlot(s);
                if (stack.isEmpty()) continue;
                IAEItemStack notInserted = Platform.poweredInsert(
                        proxy.getEnergy(),
                        proxy.getStorage().getInventory(channel),
                        AEItemStack.fromItemStack(stack),
                        new appeng.me.helpers.MachineSource(this.host));
                if (notInserted == null || notInserted.getStackSize() == 0) {
                    this.storage.extractItem(s, stack.getCount(), false);
                } else {
                    int inserted = (int) (stack.getCount() - notInserted.getStackSize());
                    if (inserted > 0) {
                        this.storage.extractItem(s, inserted, false);
                    }
                }
            }
        } catch (appeng.me.GridAccessException e) {
            // 网络未连接,保持 storage 中
        }
    }

    /**
     * 向附近玩家发送虚拟合成粒子包。
     */
    // ---- 流体支持 ----

    /**
     * 将 table 中的流体假物品推送到目标的 IFluidHandler.
     * CPU 已事先将物品提取到 table 中,此处只做转换与推送,不再从网络提取.
     * 推送成功后,将对应槽位从 table 中清空,防止后续 handler.pushMaterials 再次处理.
     *
     * <p>按 {@link #FLUID_FACE_ORDER} 依次尝试各方向面,第一个能完整接受流体的面即被使用,
     * 避免向多个 face 重复推送或污染输出槽.</p>
     */
    boolean pushFluidInputs(World world, BlockPos pos, InventoryCrafting table, List<FluidStack> pushedFluids) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return true;

        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 只处理流体假物品（AE2E / ae2fc 的 FluidDrop / FluidPacket）。
            // 真实的流体容器（如水桶）应作为普通物品推送给机器，由机器自行处理空容器返还；
            // 否则这里会把容器直接抽干并清空槽位，导致空容器丢失。
            if (!Ae2fcFluidCompat.isAnyFluidFakeItem(stack)) continue;

            FluidStack fluid = Ae2fcFluidCompat.getFluidStack(stack);
            if (fluid == null) {
                // 兼容 ae2fc 的 ItemFluidPacket(旧版 fallback)
                String itemClass = stack.getItem().getClass().getName();
                if ("com.glodblock.github.common.item.ItemFluidPacket".equals(itemClass)) {
                    fluid = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.unpackAe2fcFluid(stack);
                }
            }
            if (fluid == null) continue;

            boolean pushed = false;
            for (EnumFacing face : FLUID_FACE_ORDER) {
                if (tryFillFluidHandler(te, face, fluid, pushedFluids)) {
                    pushed = true;
                    break;
                }
            }

            if (!pushed) {
                return false;
            }

            // 从 table 中移除已推送的流体,避免 handler.pushMaterials 再次尝试插入 ItemFluidDrop
            table.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        return true;
    }

    private boolean tryFillFluidHandler(TileEntity te, EnumFacing face, FluidStack fluid, List<FluidStack> pushedFluids) {
        if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) return false;
        IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        if (fh == null) return false;
        int filled = fh.fill(fluid, false);
        if (filled >= fluid.amount) {
            fh.fill(fluid, true);
            pushedFluids.add(fluid.copy());
            return true;
        }
        return false;
    }

    /**
     * 回退已推送到目标 IFluidHandler 的流体.
     * 按 {@link #FLUID_FACE_ORDER} 依次尝试抽取,按精确 FluidStack 匹配回退.
     */
    void revertPushedFluids(World world, BlockPos pos, List<FluidStack> fluids) {
        if (fluids.isEmpty()) return;
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return;
        for (FluidStack fluid : fluids) {
            for (EnumFacing face : FLUID_FACE_ORDER) {
                if (tryDrainFluidHandler(te, face, fluid)) break;
            }
        }
    }

    private boolean tryDrainFluidHandler(TileEntity te, EnumFacing face, FluidStack fluid) {
        if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) return false;
        IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        if (fh == null) return false;
        FluidStack drained = fh.drain(fluid, false);
        if (drained != null && drained.amount > 0) {
            // 只回退实际存在的量,避免过度抽取
            FluidStack toDrain = fluid.copy();
            toDrain.amount = Math.min(fluid.amount, drained.amount);
            fh.drain(toDrain, true);
            return true;
        }
        return false;
    }

    /**
     * 从目标的 IFluidHandler 收集流体产物.
     *
     * <p>第一阶段按 {@link #FLUID_FACE_ORDER} 收集与预期产物匹配的流体；
     * 第二阶段作为兜底，抽取各 face 上剩余的非输入流体，避免 TE 等机器的产物
     * 因数量/NBT 不完全匹配而残留在 tank 中。</p>
     */
    List<ItemStack> collectFluidProducts(World world, BlockPos pos, TargetSession session) {
        List<ItemStack> fluids = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return fluids;

        IAEItemStack[] expectedOutputs = session != null ? session.getExpectedOutputs() : null;
        List<FluidStack> inputFluids = session != null ? session.getInputFluids() : Collections.emptyList();

        // 阶段 1：按预期产物精确收集
        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null || expected.getStackSize() <= 0) continue;
                ItemStack stack = expected.createItemStack();
                FluidStack expectedFluid = extractFluidFromItemStack(stack);
                if (expectedFluid == null) continue;

                for (EnumFacing face : FLUID_FACE_ORDER) {
                    if (tryCollectExpectedFluid(te, face, expectedFluid, fluids)) {
                        break;
                    }
                }
            }
        }

        // 阶段 2：兜底收集剩余非输入流体（主要面向 TE 流体产物机器）
        for (EnumFacing face : FLUID_FACE_ORDER) {
            if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) continue;
            IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
            if (fh == null) continue;
            collectRemainingFluid(fh, inputFluids, fluids);
        }

        return fluids;
    }

    private FluidStack extractFluidFromItemStack(ItemStack stack) {
        if (!Ae2fcFluidCompat.isAnyFluidFakeItem(stack)) {
            return null;
        }
        FluidStack fluid = Ae2fcFluidCompat.getFluidStack(stack);
        if (fluid != null) {
            return fluid;
        }
        String itemClass = stack.getItem().getClass().getName();
        if ("com.glodblock.github.common.item.ItemFluidPacket".equals(itemClass)) {
            return com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.unpackAe2fcFluid(stack);
        }
        return null;
    }

    private boolean tryCollectExpectedFluid(TileEntity te, EnumFacing face, FluidStack expectedFluid, List<ItemStack> fluids) {
        if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) return false;
        IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        if (fh == null) return false;
        FluidStack drained = fh.drain(expectedFluid, false);
        if (drained != null && drained.amount >= expectedFluid.amount) {
            fh.drain(expectedFluid, true);
            fluids.add(Ae2fcFluidCompat.createFluidDrop(expectedFluid));
            return true;
        }
        return false;
    }

    /**
     * 循环抽取指定 IFluidHandler 中的剩余流体，跳过本批次推送的输入流体。
     */
    private void collectRemainingFluid(IFluidHandler fh, List<FluidStack> inputFluids, List<ItemStack> fluids) {
        while (true) {
            FluidStack drained = fh.drain(Integer.MAX_VALUE, false);
            if (drained == null || drained.amount <= 0) break;
            if (isInputFluid(drained, inputFluids)) break;
            FluidStack actual = fh.drain(drained, true);
            if (actual == null || actual.amount <= 0) break;
            ItemStack drop = Ae2fcFluidCompat.createFluidDrop(actual);
            if (!drop.isEmpty()) {
                fluids.add(drop);
            }
        }
    }

    private boolean isInputFluid(FluidStack fluid, List<FluidStack> inputFluids) {
        for (FluidStack input : inputFluids) {
            if (input != null && input.isFluidEqual(fluid)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWorkToDo() {
        if (isOnGlobalVirtualCooldown()) {
            return true;
        }
        if (!this.pendingVirtualProducts.isEmpty()) {
            return true;
        }
        for (TargetSession session : this.sessions.values()) {
            if (!session.isIdle() && !session.isUnavailable()) {
                return true;
            }
        }
        // 没有任何合成任务时，storage slots 中的残留物品也应被推回网络。
        for (int s = 0; s < this.storage.getSlots(); s++) {
            if (!this.storage.getStackInSlot(s).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---- Inventory Callbacks ----

    @Override
    public void saveChanges() {
        this.host.saveChanges();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.patterns) {
            updateCraftingList();
            this.host.saveChanges();
        } else if (inv == this.config) {
            this.host.saveChanges();
        } else if (inv == this.storage) {
            this.host.saveChanges();
            // storage 进入新物品时唤醒 tick，避免设备 sleeping 导致物品滞留。
            tryWakeTickDevice();
        }
    }

    public boolean canInsert(ItemStack stack) {
        return true;
    }

    // ---- Crafting List Management ----

    public void initialize() {
        updateCraftingList();
        // 加载完成后,若存在持久化的处理状态,唤醒 tick 以继续收集
        if (hasWorkToDo()) {
            tryWakeTickDevice();
        }
    }

    void tryWakeTickDevice() {
        try {
            AENetworkProxy proxy = this.host.getProxy();
            if (proxy.isActive()) {
                appeng.api.networking.ticking.ITickManager tm = proxy.getTick();
                if (tm != null) {
                    tm.wakeDevice(this.host.getProxy().getNode());
                }
            }
        } catch (appeng.me.GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to wake tick device for CentralInterface", e);
        }
    }

    private void updateCraftingList() {
        AENetworkProxy proxy = this.host.getProxy();

        boolean removed = false;
        boolean newPattern = false;

        if (this.craftingList == null) {
            this.craftingList = new HashSet<>();
        }

        boolean[] accountedFor = new boolean[this.patterns.getSlots()];

        Iterator<ICraftingPatternDetails> i = this.craftingList.iterator();
        while (i.hasNext()) {
            ICraftingPatternDetails details = i.next();
            boolean found = false;
            for (int x = 0; x < accountedFor.length; x++) {
                ItemStack stackInSlot = this.patterns.getStackInSlot(x);
                if (details.getPattern() == stackInSlot) {
                    found = true;
                    accountedFor[x] = true;
                    break;
                }
                // SmartPatternSubDetails: match by parent ItemSmartPattern
                if (details instanceof SmartPatternSubDetails) {
                    ItemStack parent = ((SmartPatternSubDetails) details).getPattern();
                    if (parent == stackInSlot) {
                        found = true;
                        accountedFor[x] = true;
                        break;
                    }
                }
            }
            if (!found) {
                removed = true;
                i.remove();
            }
        }

        for (int x = 0; x < accountedFor.length; x++) {
            if (!accountedFor[x]) {
                newPattern = true;
                addToCraftingList(this.patterns.getStackInSlot(x));
            }
        }

        // 网络就绪后才发送事件；若未就绪，craftingList 仍已完成重建，
        // 等网络恢复后 CraftingGridCache 的 provideCrafting 扫描即可发现配方。
        if (proxy.isReady() && (newPattern || removed)) {
            try {
                proxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this.host, proxy.getNode()));
            } catch (GridAccessException e) {
                // ignore
            }
        }
    }

    private void addToCraftingList(ItemStack stack) {
        if (stack.isEmpty()) return;
        // 智能样板展开
        if (stack.getItem() instanceof ItemSmartPattern) {
            World world = this.host.getTileEntity().getWorld();
            List<SmartPatternSubDetails> subs = ItemSmartPattern.expandPatterns(stack, world);
            if (subs != null) {
                this.craftingList.addAll(subs);
            }
            return;
        }
        if (!(stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem)) return;
        appeng.api.implementations.ICraftingPatternItem patternItem =
                (appeng.api.implementations.ICraftingPatternItem) stack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(stack, this.host.getTileEntity().getWorld());
        if (details != null) {
            this.craftingList.add(details);
        }
    }

    // ---- Binding Management ----

    public List<TargetBinding> getBindings() {
        return Collections.unmodifiableList(this.bindings);
    }

    public String getBoundBlockId() {
        return this.boundBlockId;
    }

    public void addBinding(TargetBinding binding) {
        if (this.boundBlockId == null) {
            this.boundBlockId = binding.blockId;
        } else if (!this.boundBlockId.equals(binding.blockId)) {
            // 只允许绑定同种方块实体
            return;
        }
        if (!this.bindings.contains(binding)) {
            // 安全重置：若该目标曾经处于处理状态(来自持久化数据),先清理其运行时状态
            TargetSession session = this.sessions.get(binding);
            if (session != null) {
                session.reset();
            }

            this.bindings.add(binding);
            getOrCreateSession(binding); // 确保存在 IDLE session
            postPatternChangeEvent();
        }
    }

    public void removeBinding(TargetBinding binding) {
        // 若目标正在处理,先尝试紧急收集产物,避免移除绑定后产物无人接管
        TargetSession session = this.sessions.get(binding);
        if (session != null && !session.isIdle() && !session.isUnavailable()) {
            tryEmergencyCollect(binding);
            session.reset();
        }

        // 通知 handler 清理 per-target 缓存
        notifyBindingRemoved(binding);

        this.bindings.remove(binding);
        this.sessions.remove(binding);
        if (this.bindings.isEmpty()) {
            this.boundBlockId = null;
        }
        postPatternChangeEvent();
    }

    public void clearBindings() {
        // 批量移除前,先紧急收集所有正在处理目标的产物
        for (TargetSession session : new ArrayList<>(this.sessions.values())) {
            if (!session.isIdle() && !session.isUnavailable()) {
                tryEmergencyCollect(session.getBinding());
                session.reset();
            }
        }
        // 通知所有 handler 清理 per-target 缓存
        for (TargetBinding binding : new ArrayList<>(this.bindings)) {
            notifyBindingRemoved(binding);
        }
        this.bindings.clear();
        this.sessions.clear();
        this.boundBlockId = null;
        TargetOwnershipTracker.instance().releaseAll(this);
        postPatternChangeEvent();
    }

    /**
     * 接口销毁时调用，释放所有持有的目标所有权。
     */
    public void destroy() {
        for (TargetSession session : new ArrayList<>(this.sessions.values())) {
            session.reset();
        }
        TargetOwnershipTracker.instance().releaseAll(this);
    }

    /** 紧急收集指定目标的产物(用于移除绑定前清理),收集失败则暂存到 storage slots */
    private void tryEmergencyCollect(TargetBinding binding) {
        this.physicalDispatcher.emergencyCollect(binding);
    }

    /** 通知 handler 目标已解绑，并捕获 handler 异常避免扩散 */
    private void notifyBindingRemoved(TargetBinding binding) {
        try {
            World world = this.host.getTileEntity().getWorld();
            if (world == null || world.provider.getDimension() != binding.dimension) return;
            if (!world.isBlockLoaded(binding.pos)) return;
            IRemoteHandler handler = HandlerRegistry.findHandler(binding.blockId);
            if (handler != null) {
                handler.onBindingRemoved(world, binding.pos);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] onBindingRemoved threw for {} at {}: {}",
                    binding.blockId, binding.pos, e.toString());
        }
    }

    private void postPatternChangeEvent() {
        try {
            AENetworkProxy proxy = this.host.getProxy();
            if (proxy.isReady()) {
                proxy.getGrid().postEvent(new appeng.api.networking.events.MENetworkCraftingPatternChange(this.host, proxy.getNode()));
            }
        } catch (appeng.me.GridAccessException e) {
            // ignore
        }
    }

    // ---- 默认单份材料发配工具方法 ----

    InventoryCrafting copyInventoryCrafting(InventoryCrafting original) {
        int size = original.getSizeInventory();
        int dim = (int) Math.round(Math.sqrt(size));
        if (dim * dim != size) {
            // 非方阵时按 3×3 回退(正常配方均为方阵)
            dim = 3;
        }
        InventoryCrafting copy = new InventoryCrafting(new net.minecraft.inventory.Container() {
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) { return false; }
        }, dim, dim);
        for (int i = 0; i < size && i < copy.getSizeInventory(); i++) {
            ItemStack stack = original.getStackInSlot(i);
            if (!stack.isEmpty()) {
                copy.setInventorySlotContents(i, stack.copy());
            }
        }
        return copy;
    }

    private TileEntity getTargetTile(TargetBinding target) {
        World world = this.host.getTileEntity().getWorld();
        if (world.provider.getDimension() != target.dimension) return null;
        if (!world.isBlockLoaded(target.pos)) return null;
        TileEntity te = world.getTileEntity(target.pos);
        if (te == null) return null;
        return te;
    }

    private IRemoteHandler resolveHandler(String blockId) {
        return HandlerRegistry.findHandler(blockId);
    }

    // ---- NBT ----

    public void readFromNBT(NBTTagCompound data) {
        this.config.readFromNBT(data, "config");
        this.patterns.readFromNBT(data, "patterns");
        this.storage.readFromNBT(data, "storage");
        this.cm.readFromNBT(data);
        this.priority = data.getInteger("priority");

        // 绑定
        this.bindings.clear();
        this.sessions.clear();
        this.boundBlockId = data.hasKey("boundBlockId") ? data.getString("boundBlockId") : null;
        if (data.hasKey("bindings")) {
            NBTTagList list = data.getTagList("bindings", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                TargetBinding binding = TargetBinding.readFromNBT(list.getCompoundTagAt(i));
                this.bindings.add(binding);
                getOrCreateSession(binding); // 初始为 IDLE
            }
        }

        // 恢复待注入的虚拟合成产物
        readPendingVirtualProductsFromNBT(data);

        // 旧版运行时状态（processingState）按用户要求直接丢弃，session 重置为 IDLE

        // 注意：不在 readFromNBT 时调用 updateCraftingList(),
        // 因为 SmartPattern 展开需要 world 对象,而 NBT 读取阶段 world 可能为 null.
        // craftingList 将在 TileCentralMEInterface.update() -> initialize() 中重建.
    }

    public void writeToNBT(NBTTagCompound data) {
        this.config.writeToNBT(data, "config");
        this.patterns.writeToNBT(data, "patterns");
        this.storage.writeToNBT(data, "storage");
        this.cm.writeToNBT(data);
        data.setInteger("priority", this.priority);

        // 绑定
        if (this.boundBlockId != null) {
            data.setString("boundBlockId", this.boundBlockId);
        }
        NBTTagList list = new NBTTagList();
        for (TargetBinding binding : this.bindings) {
            list.appendTag(binding.writeToNBT());
        }
        data.setTag("bindings", list);

        // 持久化待注入的虚拟合成产物
        writePendingVirtualProductsToNBT(data);
    }

    /**
     * 将待注入的虚拟合成产物写入 NBT，避免 chunk 卸载/服务器重启时丢失。
     */
    private void writePendingVirtualProductsToNBT(NBTTagCompound data) {
        NBTTagList list = new NBTTagList();
        for (ItemStack product : this.pendingVirtualProducts) {
            if (product.isEmpty()) continue;
            list.appendTag(product.serializeNBT());
        }
        if (list.tagCount() > 0) {
            data.setTag("pendingVirtualProducts", list);
        } else {
            data.removeTag("pendingVirtualProducts");
        }
    }

    /**
     * 从 NBT 恢复待注入的虚拟合成产物。
     */
    private void readPendingVirtualProductsFromNBT(NBTTagCompound data) {
        this.pendingVirtualProducts.clear();
        if (!data.hasKey("pendingVirtualProducts")) {
            return;
        }
        NBTTagList list = data.getTagList("pendingVirtualProducts", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack stack = new ItemStack(list.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                this.pendingVirtualProducts.add(stack);
            }
        }
    }

    // ---- Cleanup ----

    public void clearContents() {
        // 掉落物品并清空库存
        World world = this.host.getTileEntity().getWorld();
        BlockPos pos = this.host.getTileEntity().getPos();
        for (int i = 0; i < this.patterns.getSlots(); i++) {
            ItemStack stack = this.patterns.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, Collections.singletonList(stack));
                this.patterns.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < this.storage.getSlots(); i++) {
            ItemStack stack = this.storage.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, Collections.singletonList(stack));
                this.storage.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    public void onStackReturnedToNetwork(IAEItemStack stack) {
        // 空实现,备用
    }

    public IConfigManager getConfigManager() {
        return this.cm;
    }

    public AENetworkProxy getProxy() {
        return this.host.getProxy();
    }
}
