package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.IConfigManager;
import appeng.me.GridAccessException;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 中枢 ME 接口的核心逻辑类,复刻 AE2 {@link appeng.helpers.DualityInterface} 的结构.
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>库存持有（config / patterns / storage）与库存回调</li>
 *   <li>pushPattern 调度：按 {@link ExecutionMode} 分派（物理 / 工作台常驻虚拟 / 装卡纯虚拟）与全局冷却管理</li>
 *   <li>绑定目标与 {@link TargetSession} 生命周期管理</li>
 *   <li>tick 编排：物理 session 推进、虚拟产物注入、storage 回推网络</li>
 *   <li>NBT 持久化（委托 {@link PendingProductQueue} / {@link CraftingPatternList}）</li>
 * </ul>
 *
 * <p>协作组件：</p>
 * <ul>
 *   <li>{@link PhysicalDispatcher} —— 物理发配与产物收集</li>
 *   <li>{@link VirtualBatchEngine} —— 虚拟批量合成与粒子</li>
 *   <li>{@link FluidTransferHelper} —— 目标机器流体 IO（无状态）</li>
 *   <li>{@link NetworkAccess} —— AE2 内部实现层访问门面</li>
 *   <li>{@link CraftingPatternList} —— 样板列表与配方注册</li>
 *   <li>{@link PendingProductQueue} —— 虚拟产物暂存队列（NBT 持久化）</li>
 * </ul>
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

    // 样板列表与配方注册（优先级随 NBT 持久化）
    private final CraftingPatternList patternList = new CraftingPatternList();

    // 远程绑定
    private final List<TargetBinding> bindings = new ArrayList<>();
    private String boundBlockId = null;
    // 每个绑定目标对应一个 TargetSession,集中管理 PUSHING/PROCESSING/COLLECTING/IDLE/UNAVAILABLE
    final Map<TargetBinding, TargetSession> sessions = new HashMap<>();
    // 虚拟合成产物暂存队列(等待 waitingFor 注册后再注入网络)
    final PendingProductQueue pendingProducts = new PendingProductQueue();

    // 流体缓冲：流体通道暂时收不下的流体产物在这里等重试（每 tick 一次，仍走流体通道）
    private final CentralFluidBuffer fluidBuffer = new CentralFluidBuffer();

    // 虚拟合成冷却：每个目标成功执行一批后进入冷却
    final Map<TargetBinding, Integer> virtualCooldowns = new HashMap<>();

    // 全局虚拟批间冷却：每个 pushPattern 调用最多触发一次虚拟批处理，成功/失败后均进入冷却
    int globalVirtualCooldown = 0;

    // 上一次虚拟批量合成的实际并行数，供 CraftingCPUCluster Mixin 修正任务计数
    private long lastVirtualBatchSize = 0;

    // Mixin 传入的下一次虚拟批量上限（通常为 CPU 任务剩余数），0 表示未设置
    private long nextVirtualBatchLimit = 0;

    // Mixin 传入的 CPU 内部物品缓存（MECraftingInventory）。虚拟批量所需的额外物品
    // 必须从此处核算/提取：AE2 CPU 在 submitJob 时已把整单材料从网络预提到 CPU 缓存，
    // 网络通常已被任务预留掏空，若从网络核算并行数会恒退化为 1。
    private appeng.api.storage.IMEInventory<appeng.api.storage.data.IAEItemStack> virtualItemSource = null;

    private final PhysicalDispatcher physicalDispatcher;
    private final VirtualBatchEngine virtualBatchEngine;

    /**
     * 判断本实例对应的宿主 Tile 是否仍然有效。
     * 供 {@link TargetOwnershipTracker} 回收残留所有权：服务器重启/单机跨存档重载后，
     * 旧实例的宿主 Tile 会被标记 invalid 或 world 置空。
     */
    public boolean isAlive() {
        try {
            net.minecraft.tileentity.TileEntity tile = this.host.getTileEntity();
            return tile != null && !tile.isInvalid() && tile.getWorld() != null;
        } catch (Throwable t) {
            return false;
        }
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

    // ---- Crafting Provider ----

    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        this.patternList.provideCrafting(craftingTracker, this.host, gridConnection().isActive(), !this.bindings.isEmpty());
    }

    /**
     * 获取当前网格连接 seam（每次调用实时包装 host proxy，代理内部网格引用变化时自动跟随）。
     */
    IGridConnection gridConnection() {
        return NetworkAccess.connection(this.host.getProxy());
    }

    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        this.lastVirtualBatchSize = 0;
        long pendingLimit = this.nextVirtualBatchLimit;
        this.nextVirtualBatchLimit = 0;
        IGridConnection grid = gridConnection();
        if (!grid.isActive() || !this.patternList.isInitialized()
                || !this.patternList.contains(patternDetails)) {
            return false;
        }

        // 虚拟卡 = 模式开关：dual 设备无卡走物理，装卡后整台接口进入纯虚拟。
        boolean hasCard = hasVirtualCard();
        long cardParallel = hasCard ? getVirtualParallel() : 1L;
        boolean globalVirtualCooling = isOnGlobalVirtualCooldown();
        List<TargetBinding> candidates = findIdleTargets();
        boolean attemptedNonSkippableVirtual = false;

        for (TargetBinding target : candidates) {
            IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
            if (handler == null) {
                continue;
            }

            ExecutionMode mode = ExecutionMode.resolve(handler, hasCard);
            AE2Enhanced.LOGGER.debug("[AE2E-Diag] pushPattern target={} handler={} mode={} cardParallel={} pendingLimit={} globalCooldown={}",
                    target.pos, handler.getClass().getSimpleName(), mode, cardParallel, pendingLimit, globalVirtualCooling);

            if (mode == ExecutionMode.PHYSICAL) {
                if (handler.hasCapability(HandlerCapabilities.PHYSICAL)
                        && this.physicalDispatcher.dispatch(grid, patternDetails, table, target, handler)) {
                    this.lastVirtualBatchSize = 1;
                    return true;
                }
                continue;
            }

            // 虚拟路径（VIRTUAL_NATIVE / VIRTUAL_CARDED）。两者都不回退物理：
            // NATIVE 设备没有物理交互空间；CARDED 是“装卡就纯虚拟”的语义要求。
            if (globalVirtualCooling) {
                continue;
            }

            IVirtualBatchCraftingHandler vh = (IVirtualBatchCraftingHandler) handler;
            long virtualParallel = resolveVirtualParallel(mode, vh, cardParallel, pendingLimit);
            if (!vh.skipCooldownOnSingleBatch()) {
                attemptedNonSkippableVirtual = true;
            }

            long actualParallel = this.virtualBatchEngine.execute(grid, patternDetails, table, target, vh, virtualParallel);
            if (actualParallel > 0) {
                this.lastVirtualBatchSize = actualParallel;
                if (actualParallel > 1 || !vh.skipCooldownOnSingleBatch()) {
                    this.globalVirtualCooldown = AE2EnhancedConfig.centralInterface.virtualCooldownGlobalTicks;
                }
                tryWakeTickDevice();
                return true;
            }
        }

        // 尝试了虚拟批量但未成功，进入全局冷却防止 CPU 同 tick 反复重试。
        // 若所有尝试过的虚拟 handler 都声明“单份跳过冷却”（如 Extended Crafting 工作台），则不设置冷却。
        if (attemptedNonSkippableVirtual) {
            this.globalVirtualCooldown = AE2EnhancedConfig.centralInterface.virtualCooldownGlobalTicks;
            tryWakeTickDevice();
        }
        return false;
    }

    /**
     * 是否装有虚拟并行卡.
     *
     * <p>卡的最低等级（tier 1）并行数即为 8，而 {@link #getVirtualParallel()} 在无卡时返回 1，
     * 因此 {@code > 1} 精确等价于“升级槽中存在虚拟并行卡”。</p>
     */
    boolean hasVirtualCard() {
        return getVirtualParallel() > 1;
    }

    /**
     * 计算本次虚拟合成的并行上限.
     *
     * @param mode         虚拟模式（调用方已排除 {@link ExecutionMode#PHYSICAL}）
     * @param handler      虚拟批量 handler
     * @param cardParallel 卡等级并行数（无卡时为 1）
     * @param pendingLimit 由 CPU 传入的任务剩余数上限，0 表示未设置
     * @return 至少为 1 的并行上限
     */
    private static long resolveVirtualParallel(ExecutionMode mode, IVirtualBatchCraftingHandler handler,
                                               long cardParallel, long pendingLimit) {
        long parallel;
        if (mode == ExecutionMode.VIRTUAL_NATIVE) {
            // 设备自带的并行度（如工作台 8）；装卡时可被卡等级抬高
            parallel = Math.max(1L, handler.getDefaultParallel());
            parallel = Math.max(parallel, cardParallel);
        } else {
            // VIRTUAL_CARDED：卡等级即上限，getDefaultParallel 不参与
            parallel = cardParallel;
        }
        if (pendingLimit > 0) {
            parallel = Math.min(parallel, pendingLimit);
        }
        return Math.max(parallel, 1L);
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
     * 由 MixinCraftingCPUCluster 在调用 pushPattern 前传入 CPU 的 MECraftingInventory，
     * 调用结束后复位为 null。虚拟批量从 CPU 缓存提取物品，而非从网络提取。
     */
    public void setVirtualItemSource(appeng.api.storage.IMEInventory<appeng.api.storage.data.IAEItemStack> source) {
        this.virtualItemSource = source;
    }

    appeng.api.storage.IMEInventory<appeng.api.storage.data.IAEItemStack> getVirtualItemSource() {
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
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemVirtualParallelCard) {
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
     * 流体缓冲（{@link PhysicalDispatcher} 里网络暂时收不下的流体的落脚点）.
     *
     * <p>留在这里的流体逐 tick 重试流体通道，不转成物品：一旦离开流体通道，
     * 玩家在流体终端里就看不到它。</p>
     */
    CentralFluidBuffer fluidBuffer() {
        return this.fluidBuffer;
    }

    /**
     * 将物品列表注入 AE 网络,溢出部分先进入 storage slots,再溢出则掉落.
     * 流体假物品走物品通道,由 ae2fc 的 FakeMonitor 体系接管(若 ae2fc 未安装,
     * 则由本 mod 的 MixinNetworkMonitorFluid 转注入流体通道).
     */
    boolean injectItemsToNetwork(IGridConnection grid, World world, List<ItemStack> items) {
        try {
            for (ItemStack product : items) {
                if (product.isEmpty()) continue;
                IAEItemStack toInsert = AEItemStack.fromItemStack(product);
                IAEItemStack remaining = NetworkAccess.poweredInsertItem(grid, this.host, toInsert);
                if (remaining != null && remaining.getStackSize() > 0) {
                    ItemStack leftover = remaining.createItemStack();
                    stashItemToStorage(world, leftover);
                }
            }
            return true;
        } catch (GridAccessException e) {
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
            // UNAVAILABLE 目标暂时无法工作，也应视为忙碌，防止 CPU 反复调度失败任务
            if (session == null || session.isIdle()) {
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

            // handler 反射可能抛异常/Error,隔离防止网格 tick 崩溃
            boolean valid;
            try {
                valid = handler.isValidTarget(world, binding.pos);
            } catch (Throwable t) {
                AE2Enhanced.LOGGER.warn("[AE2E] recoverUnavailableTargets: isValidTarget threw for {} at {}: {}",
                        binding.blockId, binding.pos, t.toString());
                continue;
            }
            if (valid) {
                session.recoverFromUnavailable();
            }
        }
    }

    // ---- Ticking ----

    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 5, !hasWorkToDo(), true);
    }

    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        IGridConnection grid = gridConnection();
        if (!grid.isActive()) {
            return TickRateModulation.SLEEP;
        }

        // 递减虚拟合成冷却
        boolean cooldownExpired = decrementVirtualCooldowns();

        // 先尝试恢复 UNAVAILABLE 目标：如果目标重新有效，则恢复为 IDLE
        recoverUnavailableTargets();

        boolean didWork = false;
        World world = this.host.getTileEntity().getWorld();
        int timeoutTicks = AE2EnhancedConfig.centralInterface.processingTimeoutTicks;

        // 物理 session 处理（PROCESSING / COLLECTING）
        if (this.physicalDispatcher.tick(grid, world, timeoutTicks)) {
            didWork = true;
        }

        // 流体缓冲重试：仍走流体通道，成功即出队
        if (this.physicalDispatcher.retryBufferedFluids(grid)) {
            didWork = true;
        }

        // 虚拟产物注入 + 粒子包
        if (this.virtualBatchEngine.tick(grid)) {
            didWork = true;
        }

        // 将 storage slots 中的物品推入网络(如果有空间)
        pushStorageToNetwork(grid);

        return (hasWorkToDo() || this.virtualBatchEngine.hasActiveParticles())
                ? (didWork || cooldownExpired ? TickRateModulation.URGENT : TickRateModulation.SLOWER)
                : TickRateModulation.SLEEP;
    }

    private void pushStorageToNetwork(IGridConnection grid) {
        try {
            for (int s = 0; s < this.storage.getSlots(); s++) {
                ItemStack stack = this.storage.getStackInSlot(s);
                if (stack.isEmpty()) continue;
                IAEItemStack notInserted = NetworkAccess.poweredInsertItem(grid, this.host, AEItemStack.fromItemStack(stack));
                if (notInserted == null || notInserted.getStackSize() == 0) {
                    this.storage.extractItem(s, stack.getCount(), false);
                } else {
                    int inserted = (int) (stack.getCount() - notInserted.getStackSize());
                    if (inserted > 0) {
                        this.storage.extractItem(s, inserted, false);
                    }
                }
            }
        } catch (GridAccessException e) {
            // 网络未连接,保持 storage 中
        }
    }

    private boolean hasWorkToDo() {
        if (isOnGlobalVirtualCooldown()) {
            return true;
        }
        if (!this.pendingProducts.isEmpty()) {
            return true;
        }
        // 流体缓冲里还有待重试的产物：必须保持 tick，否则缓冲永远不会被重试
        if (!this.fluidBuffer.isEmpty()) {
            return true;
        }
        for (TargetSession session : this.sessions.values()) {
            // UNAVAILABLE 目标同样需要保持 tick：恢复入口 recoverUnavailableTargets
            // 只在 tickingRequest 中运行，若此处返回 false 设备会进入 SLEEP，
            // 该目标将永久冻结，只能靠解绑重绑恢复。
            if (!session.isIdle()) {
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
            refreshPatterns();
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
        refreshPatterns();
        // 加载完成后,若存在持久化的处理状态,唤醒 tick 以继续收集
        if (hasWorkToDo()) {
            tryWakeTickDevice();
        }
    }

    void tryWakeTickDevice() {
        gridConnection().wakeTickDevice(this.host);
    }

    /**
     * 重建配方列表并在有增删时向网格发送 MENetworkCraftingPatternChange。
     */
    private void refreshPatterns() {
        World world = this.host.getTileEntity().getWorld();
        this.patternList.refresh(this.patterns, world,
                () -> gridConnection().postPatternChange(this.host));
    }

    // ---- Binding Management ----

    public List<TargetBinding> getBindings() {
        return Collections.unmodifiableList(this.bindings);
    }

    /**
     * 用客户端描边快照替换绑定列表（<b>仅客户端</b>调用）.
     *
     * <p>服务端的绑定列表由 {@link #addBinding}/{@link #removeBinding}/{@link #clearBindings} 维护；
     * 客户端不再接收 duality 全量 NBT，绑定坐标改由
     * {@code PacketCentralBindingOutline} 与瘦身后的 {@code getUpdateTag} 下发，
     * 通过本方法写回同一个 {@link #getBindings()} 列表，使描边渲染（BindingLineRenderer）
     * 与记忆卡 tooltip 的绑定计数继续读取同一处数据。</p>
     *
     * <p>快照里的 {@code blockId} 允许为 null（描边与计数都不使用它）；本方法不创建
     * {@link TargetSession}，客户端不会用这些绑定发起发配。</p>
     */
    public void setBindingSnapshot(List<TargetBinding> snapshot) {
        this.bindings.clear();
        if (snapshot != null) {
            this.bindings.addAll(snapshot);
        }
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

    private void postPatternChangeEvent() {
        gridConnection().postPatternChange(this.host);
    }

    // ---- 默认单份材料发配工具方法 ----

    InventoryCrafting copyInventoryCrafting(InventoryCrafting original) {
        int size = original.getSizeInventory();
        int dim = (int) Math.ceil(Math.sqrt(size));
        if (dim < 3) dim = 3;
        if (dim > 10) dim = 10;
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

    // ---- NBT ----

    public void readFromNBT(NBTTagCompound data) {
        this.config.readFromNBT(data, "config");
        this.patterns.readFromNBT(data, "patterns");
        this.storage.readFromNBT(data, "storage");
        this.cm.readFromNBT(data);
        this.patternList.setPriority(data.getInteger("priority"));

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
        this.pendingProducts.readFromNBT(data);

        // 恢复流体缓冲（chunk 卸载 / 服务器重启后继续重试注入流体通道）
        this.fluidBuffer.readFromNBT(data);

        // 旧版运行时状态（processingState）不再读取，session 一律从 IDLE 开始

        // 注意：不在 readFromNBT 时重建配方列表,
        // 因为 SmartPattern 展开需要 world 对象,而 NBT 读取阶段 world 可能为 null.
        // craftingList 将在 TileCentralMEInterface.update() -> initialize() 中重建.
    }

    public void writeToNBT(NBTTagCompound data) {
        this.config.writeToNBT(data, "config");
        this.patterns.writeToNBT(data, "patterns");
        this.storage.writeToNBT(data, "storage");
        this.cm.writeToNBT(data);
        data.setInteger("priority", this.patternList.getPriority());

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
        this.pendingProducts.writeToNBT(data);

        // 持久化流体缓冲
        this.fluidBuffer.writeToNBT(data);
    }

    // ---- Cleanup ----

    public void clearContents() {
        // 掉落物品并清空库存
        World world = this.host.getTileEntity().getWorld();
        BlockPos pos = this.host.getTileEntity().getPos();
        for (int i = 0; i < this.patterns.getSlots(); i++) {
            ItemStack stack = this.patterns.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NetworkAccess.spawnDrops(world, pos, Collections.singletonList(stack));
                this.patterns.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < this.storage.getSlots(); i++) {
            ItemStack stack = this.storage.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NetworkAccess.spawnDrops(world, pos, Collections.singletonList(stack));
                this.storage.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        // 流体缓冲里还留着的流体：设备即将消失，缓冲没有别的落脚点，
        // 趁网格节点在 breakBlock 阶段仍然有效，先送进 ME 流体通道。
        injectBufferedFluids();
    }

    /**
     * 把流体缓冲的内容送进 ME 流体通道（仅在方块被拆掉时调用）.
     *
     * <p>缓冲随方块一并消失，所以这里只做一次注入尝试；送不走的量会真正损失，按数量与流体名
     * 留下 warn。不转成流体假物品：那会让流体离开流体通道，玩家在流体终端里看不到它。</p>
     */
    private void injectBufferedFluids() {
        if (this.fluidBuffer.isEmpty()) {
            return;
        }
        List<FluidStack> buffered = new ArrayList<>();
        for (CentralFluidBuffer.Entry entry : this.fluidBuffer.entries()) {
            if (entry.fluid() != null && entry.fluid().amount > 0) {
                buffered.add(entry.fluid().copy());
            }
        }
        this.fluidBuffer.clear();
        if (buffered.isEmpty()) {
            return;
        }
        IGridConnection grid = gridConnection();
        if (!grid.isActive()) {
            long lost = 0;
            for (FluidStack fluid : buffered) {
                lost += fluid.amount;
            }
            AE2Enhanced.LOGGER.warn("[AE2E] CentralInterface broken with {} fluid stack(s) buffered and no active grid; {} mb lost",
                    buffered.size(), lost);
            return;
        }
        List<FluidStack> rejected = NetworkAccess.injectFluidsToNetwork(grid, this.host, buffered);
        for (FluidStack fluid : rejected) {
            AE2Enhanced.LOGGER.warn("[AE2E] CentralInterface broken: {} mb of {} could not enter the ME fluid channel",
                    fluid.amount, fluid.getFluid() != null ? fluid.getFluid().getName() : "unknown");
        }
    }

    public IConfigManager getConfigManager() {
        return this.cm;
    }
}
