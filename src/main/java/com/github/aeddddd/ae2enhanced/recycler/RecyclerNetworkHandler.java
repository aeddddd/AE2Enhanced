package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ME 网络回收节点向 AE2 网络注册的单一 handler.
 *
 * <p>对 AE2 网络表现为一个存储源,实际回收操作直接写入超维度仓储中枢.</p>
 */
public class RecyclerNetworkHandler implements IMEInventoryHandler<IAEItemStack>, IMEMonitor<IAEItemStack> {

    private final TileMENetworkRecycler tile;
    private final RecyclerIndex index = new RecyclerIndex();
    private final Map<TargetManager.TargetRef, TargetAdapter> adapters = new Object2ObjectOpenHashMap<>();
    private final Map<TargetManager.TargetRef, RecyclerIndex.TargetAdapterSnapshot> snapshots = new Object2ObjectOpenHashMap<>();
    private final BulkCollector collector = new BulkCollector();
    private final HyperStorageLink hyperStorageLink = new HyperStorageLink();
    private final IActionSource actionSource;

    private long lastRecycledCount = 0;
    private int tickCounter = 0;

    public RecyclerNetworkHandler(TileMENetworkRecycler tile) {
        this.tile = tile;
        this.actionSource = new MachineSource(tile);
    }

    public void onLoad() {
        // handler 由 tile 在接入网络时注册
    }

    public void onInvalidate() {
        hyperStorageLink.invalidate();
        adapters.values().forEach(TargetAdapter::invalidate);
        adapters.clear();
        snapshots.clear();
        index.clear();
    }

    public appeng.api.networking.security.IActionSource getActionSource() {
        return actionSource;
    }

    /**
     * 尝试把机器产物直接注入当前网络的超维度仓储（或普通网络存储）。
     *
     * @param output 产物堆叠
     * @return 未能注入的部分；全部注入成功返回 {@link ItemStack#EMPTY}
     */
    public ItemStack tryInjectMachineOutput(ItemStack output) {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }

        IAEItemStack toInject = AEItemStack.fromItemStack(output);
        if (toInject == null) {
            return output;
        }

        ItemStorageAdapter hyperAdapter = hyperStorageLink.find(
                tile.getProxy(), tile.getWorld().getTotalWorldTime());
        if (hyperAdapter != null) {
            syncHyperStorageAdapter(hyperAdapter);
        }

        IAEItemStack remainder;
        List<IAEItemStack> changes = new ArrayList<>();

        if (hyperAdapter != null) {
            // 注入超维度仓储
            remainder = hyperAdapter.injectItems(toInject.copy(), Actionable.MODULATE, actionSource);

            long injectedCount = output.getCount() - (remainder != null ? remainder.getStackSize() : 0);
            if (injectedCount > 0) {
                IAEItemStack change = toInject.copy();
                change.setStackSize(injectedCount);
                changes.add(change);
            }
        } else if (!AE2EnhancedConfig.recycler.requireHyperStorageForRedirect) {
            // 无超维度中枢但配置允许时，回退到普通网络注入
            try {
                IStorageGrid storageGrid = tile.getProxy().getGrid().getCache(IStorageGrid.class);
                if (storageGrid == null) {
                    return output;
                }
                IMEMonitor<IAEItemStack> inv = storageGrid.getInventory(
                        appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
                remainder = inv.injectItems(toInject.copy(), Actionable.MODULATE, actionSource);

                long injectedCount = output.getCount() - (remainder != null ? remainder.getStackSize() : 0);
                if (injectedCount > 0) {
                    IAEItemStack change = toInject.copy();
                    change.setStackSize(injectedCount);
                    changes.add(change);
                }
            } catch (GridAccessException e) {
                return output;
            }
        } else {
            // 必须存在超维度中枢，不存在则回退
            return output;
        }

        // 通知网络变化
        if (!changes.isEmpty()) {
            postItemAlterations(changes);
        }

        if (remainder == null || remainder.getStackSize() <= 0) {
            return ItemStack.EMPTY;
        }
        return remainder.createItemStack();
    }

    /**
     * 把当前找到的超维度物品适配器同步给 RecyclerBindingRegistry，
     * 使机器 Hook 侧无需实时查询网格即可快速判断是否能直注。
     */
    private void syncHyperStorageAdapter(ItemStorageAdapter adapter) {
        RecyclerBindingRegistry registry = RecyclerBindingRegistry.getInstance();
        for (TargetManager.TargetRef ref : tile.getTargetManager().getTargets()) {
            registry.updateAdapter(ref, adapter);
        }
    }

    /**
     * 向网络发送物品存储变化通知。
     */
    private void postItemAlterations(List<IAEItemStack> changes) {
        try {
            IGrid grid = tile.getProxy().getGrid();
            if (grid == null) return;
            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return;
            storageGrid.postAlterationOfStoredItems(
                    appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class),
                    changes, actionSource);
        } catch (GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Recycler failed to post machine output alterations", e);
        }
    }

    public void tick(int tickCounter) {
        this.tickCounter = tickCounter;
        if (!tile.isActive()) return;

        // 1. 检查并维护目标适配器
        refreshAdapters();

        // 2. 执行回收
        collectFromTargets();

        // 3. 批量注入
        flushCollector();

        // 4. 全量校验（低频）
        int fullScanInterval = AE2EnhancedConfig.recycler.fullScanIntervalTicks;
        if (fullScanInterval > 0 && tickCounter % fullScanInterval == 0) {
            rebuildIndex();
        }
    }

    public long getLastRecycledCount() {
        return lastRecycledCount;
    }

    // ---- 目标管理 ----

    private void refreshAdapters() {
        // 移除失效目标
        adapters.entrySet().removeIf(entry -> {
            TargetManager.TargetRef ref = entry.getKey();
            if (!tile.getTargetManager().getTargets().contains(ref)) {
                entry.getValue().invalidate();
                snapshots.remove(ref);
                return true;
            }
            return false;
        });

        // 添加新目标
        for (TargetManager.TargetRef ref : tile.getTargetManager().getTargets()) {
            if (adapters.containsKey(ref)) continue;
            TargetAdapter adapter = createAdapter(ref);
            if (adapter != null) {
                adapters.put(ref, adapter);
            }
        }
    }

    private TargetAdapter createAdapter(TargetManager.TargetRef ref) {
        World targetWorld = DimensionManager.getWorld(ref.dimId);
        if (targetWorld == null || !targetWorld.isBlockLoaded(ref.pos)) return null;
        TileEntity te = targetWorld.getTileEntity(ref.pos);
        if (te == null) return null;
        return AdapterFactory.create(te, ref.face);
    }

    private void rebuildIndex() {
        snapshots.clear();
        for (Map.Entry<TargetManager.TargetRef, TargetAdapter> entry : adapters.entrySet()) {
            List<ItemStack> contents = entry.getValue().scan(true);
            snapshots.put(entry.getKey(), new RecyclerIndex.TargetAdapterSnapshot(
                    tile.getWorld().getTotalWorldTime(), contents));
        }
        index.rebuild(snapshots);
    }

    // ---- 回收逻辑 ----

    private void collectFromTargets() {
        long currentTick = tile.getWorld().getTotalWorldTime();
        boolean heartbeat = tickCounter % AE2EnhancedConfig.recycler.heartbeatIntervalTicks == 0;

        for (Map.Entry<TargetManager.TargetRef, TargetAdapter> entry : adapters.entrySet()) {
            TargetManager.TargetRef ref = entry.getKey();
            TargetAdapter adapter = entry.getValue();

            List<ItemStack> current = adapter.scan(true);
            RecyclerIndex.TargetAdapterSnapshot oldSnapshot = snapshots.get(ref);

            if (!heartbeat && oldSnapshot != null && isSnapshotEqual(oldSnapshot.contents, current)) {
                continue; // 无变化，跳过
            }

            for (ItemStack stack : current) {
                if (stack.isEmpty()) continue;
                // 无过滤：直接提取全部
                ItemStack extracted = adapter.extract(AEItemStack.fromItemStack(stack), false);
                if (extracted != null && !extracted.isEmpty()) {
                    collector.add(extracted);
                }
            }

            // 提取后重新扫描，更新快照
            List<ItemStack> afterExtract = adapter.scan(true);
            snapshots.put(ref, new RecyclerIndex.TargetAdapterSnapshot(currentTick, afterExtract));
        }
    }

    private boolean isSnapshotEqual(List<ItemStack> old, List<ItemStack> current) {
        if (old.size() != current.size()) return false;
        // 简化比较：只比较总数量
        long oldCount = 0, newCount = 0;
        for (ItemStack s : old) oldCount += s.getCount();
        for (ItemStack s : current) newCount += s.getCount();
        return oldCount == newCount;
    }

    private void flushCollector() {
        if (collector.isEmpty()) return;

        List<IAEItemStack> changes = collector.drain();
        lastRecycledCount = changes.stream().mapToLong(IAEItemStack::getStackSize).sum();

        if (AE2EnhancedConfig.recycler.forceHyperdimensionalStorage) {
            injectToHyperStorage(changes);
        } else {
            injectToNetwork(changes);
        }
    }

    private void injectToHyperStorage(List<IAEItemStack> changes) {
        ItemStorageAdapter adapter = hyperStorageLink.find(tile.getProxy(), tile.getWorld().getTotalWorldTime());
        if (adapter == null) {
            // 超维度中枢未找到：回退到标准网络注入
            injectToNetwork(changes);
            return;
        }
        syncHyperStorageAdapter(adapter);

        for (IAEItemStack stack : changes) {
            adapter.injectItems(stack.copy(), Actionable.MODULATE, actionSource);
        }

        // 批量通知网络
        try {
            IGrid grid = tile.getProxy().getGrid();
            if (grid != null) {
                IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
                if (storageGrid != null) {
                    storageGrid.postAlterationOfStoredItems(
                            appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class),
                            changes, actionSource);
                }
            }
        } catch (GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Recycler failed to post alterations", e);
        }
    }

    private void injectToNetwork(List<IAEItemStack> changes) {
        try {
            IStorageGrid storageGrid = tile.getProxy().getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return;
            IMEMonitor<IAEItemStack> inv = storageGrid.getInventory(
                    appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            for (IAEItemStack stack : changes) {
                inv.injectItems(stack.copy(), Actionable.MODULATE, actionSource);
            }
        } catch (GridAccessException e) {
            // ignore
        }
    }

    // ---- IMEInventoryHandler ----

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        return input; // 回收节点不接受外部注入
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable type, IActionSource src) {
        // 从目标容器提取
        List<TargetManager.TargetRef> refs = new ArrayList<>(index.getTargets(new com.github.aeddddd.ae2enhanced.storage.ItemDescriptor(request.createItemStack())));
        if (refs.isEmpty()) return null;

        long requestedCount = request.getStackSize();
        ItemStack collected = ItemStack.EMPTY;

        for (TargetManager.TargetRef ref : refs) {
            TargetAdapter adapter = adapters.get(ref);
            if (adapter == null) continue;
            ItemStack got = adapter.extract(request, type == Actionable.SIMULATE);
            if (got == null || got.isEmpty()) continue;

            if (collected.isEmpty()) {
                collected = got;
            } else {
                collected.grow(got.getCount());
            }

            requestedCount -= got.getCount();
            if (requestedCount <= 0) break;
        }

        if (collected.isEmpty()) return null;

        IAEItemStack result = AEItemStack.fromItemStack(collected);
        if (result != null && type == Actionable.MODULATE) {
            // 更新缓存
            rebuildIndexForRef(null); // 简化处理：下次心跳重建
        }
        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        for (com.github.aeddddd.ae2enhanced.storage.ItemDescriptor desc : index.getAllTypes()) {
            long total = 0;
            for (TargetManager.TargetRef ref : index.getTargets(desc)) {
                RecyclerIndex.TargetAdapterSnapshot snap = snapshots.get(ref);
                if (snap == null) continue;
                for (ItemStack stack : snap.contents) {
                    if (new com.github.aeddddd.ae2enhanced.storage.ItemDescriptor(stack).equals(desc)) {
                        total += stack.getCount();
                    }
                }
            }
            if (total > 0) {
                IAEItemStack stack = AEItemStack.fromItemStack(desc.toItemStack());
                if (stack != null) {
                    stack = stack.copy();
                    stack.setStackSize(total);
                    out.add(stack);
                }
            }
        }
        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        return false; // 不接受注入
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        return getAvailableItems(getChannel().createList());
    }

    // ---- IMEMonitor ----

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEItemStack> l, Object verificationToken) {
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEItemStack> l) {
    }

    private void rebuildIndexForRef(TargetManager.TargetRef ref) {
        // 简化：下次心跳重建
    }
}
