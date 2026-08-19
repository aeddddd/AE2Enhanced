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
import appeng.me.helpers.AENetworkProxy;
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
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 中枢 ME 接口的核心逻辑类,复刻 AE2 {@link appeng.helpers.DualityInterface} 的结构.
 *
 * <p>职责边界（重构后）：</p>
 * <ul>
 *   <li>库存持有（config / patterns / storage）与库存回调</li>
 *   <li>pushPattern 调度：虚拟批量优先、混合栈类型回退物理、全局冷却管理</li>
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

    public LockCraftingMode getCraftingLockedReason() {
        // 当前未实现红石状态检测和 unlockEvent 追踪,默认返回 NONE
        return LockCraftingMode.NONE;
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

        World world = this.host.getTileEntity().getWorld();
        IAEItemStack[] outputs = patternDetails.getOutputs();
        long baseVirtualParallel = getVirtualParallel();
        if (pendingLimit > 0) {
            baseVirtualParallel = Math.min(baseVirtualParallel, pendingLimit);
        }
        boolean globalVirtualCooling = isOnGlobalVirtualCooldown();
        List<TargetBinding> candidates = findIdleTargets();
        boolean attemptedVirtual = false;
        boolean attemptedNonSkippableVirtual = false;

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

            AE2Enhanced.LOGGER.debug("[AE2E-Diag] pushPattern target={} handler={} baseParallel={} pendingLimit={} virtualParallel={} globalCooldown={}",
                    target.pos, handler.getClass().getSimpleName(), baseVirtualParallel, pendingLimit, virtualParallel, globalVirtualCooling);

            // 优先尝试虚拟批量合成（handler 支持虚拟批量即可，virtualParallel 仅限制最大并行数）。
            // 包含非物品 IAEStack 成本（Mana、源质、LP、星光、流体、气体等）的配方同样走此路径：
            // VirtualBatchEngine 的双池模型对非物品资源从网络全额核算提取，无需物理优先。
            // 注意：纯虚拟 handler（如 Extended Crafting 工作台）即使 virtualParallel=1 也必须走此路径，
            // 否则订单剩余 1 份时会因无物理能力而直接失败。
            if (canUseVirtual
                    && !globalVirtualCooling) {
                attemptedVirtual = true;
                IVirtualBatchCraftingHandler vh = (IVirtualBatchCraftingHandler) handler;
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

                // 虚拟合成失败时，若该目标同时支持物理发配，则回退到同目标物理发配
                if (handler.hasCapability(HandlerCapabilities.PHYSICAL)) {
                    if (this.physicalDispatcher.dispatch(grid, patternDetails, table, target, handler)) {
                        this.lastVirtualBatchSize = 1;
                        return true;
                    }
                }
                continue;
            }

            // 否则尝试物理发配
            if (handler.hasCapability(HandlerCapabilities.PHYSICAL)) {
                if (this.physicalDispatcher.dispatch(grid, patternDetails, table, target, handler)) {
                    this.lastVirtualBatchSize = 1;
                    return true;
                }
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

        // 旧版运行时状态（processingState）按用户要求直接丢弃，session 重置为 IDLE

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
