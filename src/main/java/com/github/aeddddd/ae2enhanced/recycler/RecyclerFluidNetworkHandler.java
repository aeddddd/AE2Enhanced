package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.fluids.util.AEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.storage.FluidDescriptor;
import com.github.aeddddd.ae2enhanced.storage.FluidStorageAdapter;
import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ME 网络回收节点向 AE2 网络注册的流体 handler.
 *
 * <p>对 AE2 网络表现为一个流体存储源,实际回收操作直接写入超维度仓储中枢.</p>
 */
public class RecyclerFluidNetworkHandler implements IMEInventoryHandler<IAEFluidStack>, IMEMonitor<IAEFluidStack> {

    private final TileMENetworkRecycler tile;
    private final RecyclerFluidIndex index = new RecyclerFluidIndex();
    private final Map<TargetManager.TargetRef, FluidTargetAdapter> adapters = new Object2ObjectOpenHashMap<>();
    private final Map<TargetManager.TargetRef, RecyclerFluidIndex.TargetAdapterSnapshot> snapshots = new Object2ObjectOpenHashMap<>();
    private final FluidBulkCollector collector = new FluidBulkCollector();
    private final HyperStorageLinkFluid hyperStorageLink = new HyperStorageLinkFluid();
    private final IActionSource actionSource;

    private long lastRecycledCount = 0;
    private int tickCounter = 0;

    public RecyclerFluidNetworkHandler(TileMENetworkRecycler tile) {
        this.tile = tile;
        this.actionSource = new MachineSource(tile);
    }

    public void onLoad() {
    }

    public void onInvalidate() {
        hyperStorageLink.invalidate();
        adapters.values().forEach(FluidTargetAdapter::invalidate);
        adapters.clear();
        snapshots.clear();
        index.clear();
    }

    /**
     * 尝试把机器流体产物直接注入当前网络的超维度仓储（或普通网络存储）。
     *
     * @param output 产物流体堆叠
     * @return 未能注入的部分；全部注入成功返回 null
     */
    public FluidStack tryInjectMachineOutput(FluidStack output) {
        if (output == null || output.amount <= 0) {
            return null;
        }

        IAEFluidStack toInject = AEFluidStack.fromFluidStack(output);
        if (toInject == null) {
            return output;
        }

        FluidStorageAdapter hyperAdapter = hyperStorageLink.find(
                tile.getProxy(), tile.getWorld().getTotalWorldTime());
        if (hyperAdapter != null) {
            syncHyperStorageAdapter(hyperAdapter);
        }

        IAEFluidStack remainder;
        List<IAEFluidStack> changes = new ArrayList<>();

        if (hyperAdapter != null) {
            remainder = hyperAdapter.injectItems(toInject.copy(), Actionable.MODULATE, actionSource);

            long injectedCount = output.amount - (remainder != null ? remainder.getStackSize() : 0);
            if (injectedCount > 0) {
                IAEFluidStack change = toInject.copy();
                change.setStackSize(injectedCount);
                changes.add(change);
            }
        } else if (!AE2EnhancedConfig.recycler.requireHyperStorageForRedirect) {
            try {
                IStorageGrid storageGrid = tile.getProxy().getGrid().getCache(IStorageGrid.class);
                if (storageGrid == null) {
                    return output;
                }
                IMEMonitor<IAEFluidStack> inv = storageGrid.getInventory(
                        AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
                remainder = inv.injectItems(toInject.copy(), Actionable.MODULATE, actionSource);

                long injectedCount = output.amount - (remainder != null ? remainder.getStackSize() : 0);
                if (injectedCount > 0) {
                    IAEFluidStack change = toInject.copy();
                    change.setStackSize(injectedCount);
                    changes.add(change);
                }
            } catch (GridAccessException e) {
                return output;
            }
        } else {
            return output;
        }

        if (!changes.isEmpty()) {
            postFluidAlterations(changes);
        }

        if (remainder == null || remainder.getStackSize() <= 0) {
            return null;
        }
        return remainder.getFluidStack();
    }

    private void syncHyperStorageAdapter(FluidStorageAdapter adapter) {
        RecyclerBindingRegistry registry = RecyclerBindingRegistry.getInstance();
        for (TargetManager.TargetRef ref : tile.getTargetManager().getTargets()) {
            registry.updateFluidAdapter(ref, adapter);
        }
    }

    private void postFluidAlterations(List<IAEFluidStack> changes) {
        try {
            IGrid grid = tile.getProxy().getGrid();
            if (grid == null) return;
            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return;
            storageGrid.postAlterationOfStoredItems(
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class),
                    changes, actionSource);
        } catch (GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Recycler failed to post machine fluid output alterations", e);
        }
    }

    public void tick(int tickCounter) {
        this.tickCounter = tickCounter;
        if (!tile.isActive()) return;

        refreshAdapters();
        collectFromTargets();
        flushCollector();

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
        adapters.entrySet().removeIf(entry -> {
            TargetManager.TargetRef ref = entry.getKey();
            if (!tile.getTargetManager().getTargets().contains(ref)) {
                entry.getValue().invalidate();
                snapshots.remove(ref);
                return true;
            }
            return false;
        });

        for (TargetManager.TargetRef ref : tile.getTargetManager().getTargets()) {
            if (adapters.containsKey(ref)) continue;
            FluidTargetAdapter adapter = createAdapter(ref);
            if (adapter != null) {
                adapters.put(ref, adapter);
            }
        }
    }

    private FluidTargetAdapter createAdapter(TargetManager.TargetRef ref) {
        World targetWorld = DimensionManager.getWorld(ref.dimId);
        if (targetWorld == null || !targetWorld.isBlockLoaded(ref.pos)) return null;
        TileEntity te = targetWorld.getTileEntity(ref.pos);
        if (te == null) return null;
        return AdapterFactory.createFluidAdapter(te, ref.face);
    }

    private void rebuildIndex() {
        snapshots.clear();
        for (Map.Entry<TargetManager.TargetRef, FluidTargetAdapter> entry : adapters.entrySet()) {
            List<FluidStack> contents = entry.getValue().scan(true);
            snapshots.put(entry.getKey(), new RecyclerFluidIndex.TargetAdapterSnapshot(contents));
        }
        index.rebuild(snapshots);
    }

    // ---- 回收逻辑 ----

    private void collectFromTargets() {
        boolean heartbeat = tickCounter % AE2EnhancedConfig.recycler.heartbeatIntervalTicks == 0;

        for (Map.Entry<TargetManager.TargetRef, FluidTargetAdapter> entry : adapters.entrySet()) {
            TargetManager.TargetRef ref = entry.getKey();
            FluidTargetAdapter adapter = entry.getValue();

            List<FluidStack> current = adapter.scan(true);
            RecyclerFluidIndex.TargetAdapterSnapshot oldSnapshot = snapshots.get(ref);

            if (!heartbeat && oldSnapshot != null && isSnapshotEqual(oldSnapshot.contents, current)) {
                continue;
            }

            for (FluidStack stack : current) {
                if (stack == null || stack.amount <= 0) continue;
                IAEFluidStack request = AEFluidStack.fromFluidStack(stack);
                if (request == null) continue;
                FluidStack extracted = adapter.extract(request, false);
                if (extracted != null && extracted.amount > 0) {
                    collector.add(extracted);
                }
            }

            List<FluidStack> afterExtract = adapter.scan(true);
            snapshots.put(ref, new RecyclerFluidIndex.TargetAdapterSnapshot(afterExtract));
        }
    }

    private boolean isSnapshotEqual(List<FluidStack> old, List<FluidStack> current) {
        if (old.size() != current.size()) return false;
        long oldCount = 0, newCount = 0;
        for (FluidStack s : old) oldCount += s.amount;
        for (FluidStack s : current) newCount += s.amount;
        return oldCount == newCount;
    }

    private void flushCollector() {
        if (collector.isEmpty()) return;

        List<IAEFluidStack> changes = collector.drain();
        lastRecycledCount = changes.stream().mapToLong(IAEFluidStack::getStackSize).sum();

        if (AE2EnhancedConfig.recycler.forceHyperdimensionalStorage) {
            injectToHyperStorage(changes);
        } else {
            injectToNetwork(changes);
        }
    }

    private void injectToHyperStorage(List<IAEFluidStack> changes) {
        FluidStorageAdapter adapter = hyperStorageLink.find(tile.getProxy(), tile.getWorld().getTotalWorldTime());
        if (adapter == null) {
            injectToNetwork(changes);
            return;
        }
        syncHyperStorageAdapter(adapter);

        for (IAEFluidStack stack : changes) {
            logDroppedByStorage(stack, adapter.injectItems(stack.copy(), Actionable.MODULATE, actionSource));
        }

        try {
            IGrid grid = tile.getProxy().getGrid();
            if (grid != null) {
                IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
                if (storageGrid != null) {
                    storageGrid.postAlterationOfStoredItems(
                            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class),
                            changes, actionSource);
                }
            }
        } catch (GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Recycler failed to post fluid alterations", e);
        }
    }

    private void injectToNetwork(List<IAEFluidStack> changes) {
        try {
            IStorageGrid storageGrid = tile.getProxy().getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) {
                logDroppedBatch(changes, "storage grid unavailable");
                return;
            }
            IMEMonitor<IAEFluidStack> inv = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            for (IAEFluidStack stack : changes) {
                logDroppedByStorage(stack, inv.injectItems(stack.copy(), Actionable.MODULATE, actionSource));
            }
        } catch (GridAccessException e) {
            logDroppedBatch(changes, "grid access exception");
        }
    }

    /**
     * 记录被目标存储拒绝而丢弃的回收流体。
     *
     * <p>流体在 {@code collectFromTargets} 中已从机器抽出且缓冲区已清空，
     * 注入被拒绝时没有回注通道，只能丢弃；这里留下日志，避免静默丢失。</p>
     */
    private void logDroppedByStorage(IAEFluidStack requested, IAEFluidStack remainder) {
        if (requested == null || remainder == null || remainder.getStackSize() <= 0) {
            return;
        }
        FluidStack fluid = requested.getFluidStack();
        AE2Enhanced.LOGGER.warn("[AE2E] Recycler dropped {} mb of {} rejected by storage",
                remainder.getStackSize(), fluid != null ? fluid.getFluid().getName() : "?");
    }

    private void logDroppedBatch(List<IAEFluidStack> changes, String reason) {
        long total = 0;
        for (IAEFluidStack stack : changes) {
            total += stack.getStackSize();
        }
        if (total > 0) {
            AE2Enhanced.LOGGER.warn("[AE2E] Recycler dropped {} mb of fluid because {}", total, reason);
        }
    }

    // ---- IMEInventoryHandler ----

    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable type, IActionSource src) {
        return input;
    }

    @Override
    public IAEFluidStack extractItems(IAEFluidStack request, Actionable type, IActionSource src) {
        List<TargetManager.TargetRef> refs = new ArrayList<>(
                index.getTargets(new FluidDescriptor(request.getFluidStack())));
        if (refs.isEmpty()) return null;

        long requestedCount = request.getStackSize();
        FluidStack collected = null;

        for (TargetManager.TargetRef ref : refs) {
            FluidTargetAdapter adapter = adapters.get(ref);
            if (adapter == null) continue;
            FluidStack got = adapter.extract(request, type == Actionable.SIMULATE);
            if (got == null || got.amount <= 0) continue;

            if (collected == null) {
                collected = got;
            } else {
                collected.amount += got.amount;
            }

            requestedCount -= got.amount;
            if (requestedCount <= 0) break;
        }

        if (collected == null || collected.amount <= 0) return null;

        IAEFluidStack result = AEFluidStack.fromFluidStack(collected);
        return result;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        for (FluidDescriptor desc : index.getAllTypes()) {
            long total = 0;
            for (TargetManager.TargetRef ref : index.getTargets(desc)) {
                RecyclerFluidIndex.TargetAdapterSnapshot snap = snapshots.get(ref);
                if (snap == null) continue;
                for (FluidStack stack : snap.contents) {
                    if (new FluidDescriptor(stack).equals(desc)) {
                        total += stack.amount;
                    }
                }
            }
            if (total > 0) {
                IAEFluidStack stack = desc.getAETemplate((IFluidStorageChannel) getChannel());
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
    public IStorageChannel<IAEFluidStack> getChannel() {
        return AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEFluidStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEFluidStack input) {
        return false;
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
    public IItemList<IAEFluidStack> getStorageList() {
        return getAvailableItems(getChannel().createList());
    }

    // ---- IMEMonitor ----

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEFluidStack> l, Object verificationToken) {
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEFluidStack> l) {
    }
}
