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
import com.github.aeddddd.ae2enhanced.storage.FluidDescriptor;
import com.github.aeddddd.ae2enhanced.storage.FluidStorageAdapter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
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
    private final List<NBTTagCompound> orphanedOutboxEntries = new ArrayList<>();

    private long lastRecycledCount = 0;
    private int tickCounter = 0;

    public RecyclerFluidNetworkHandler(TileMENetworkRecycler tile) {
        this.tile = tile;
        this.actionSource = new MachineSource(tile);
    }

    public void onLoad() {
    }

    public void readOutbox(NBTTagCompound outbox) {
        collector.drain();
        orphanedOutboxEntries.clear();
        NBTTagList fluids = outbox.getTagList("Fluids", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < fluids.tagCount(); i++) {
            NBTTagCompound entry = fluids.getCompoundTagAt(i);
            IAEFluidStack stack = null;
            try {
                stack = AEFluidStack.fromNBT(entry);
            } catch (RuntimeException ignored) {
                // 缺失模组或损坏条目按原样保留，不能影响其余待注入流体。
            }
            if (stack != null && stack.getStackSize() > 0) {
                collector.add(stack);
            } else {
                orphanedOutboxEntries.add(entry.copy());
            }
        }
    }

    public void writeOutbox(NBTTagCompound outbox) {
        NBTTagList fluids = new NBTTagList();
        for (NBTTagCompound orphaned : orphanedOutboxEntries) {
            fluids.appendTag(orphaned.copy());
        }
        for (IAEFluidStack stack : collector.snapshot()) {
            NBTTagCompound entry = new NBTTagCompound();
            stack.writeToNBT(entry);
            fluids.appendTag(entry);
        }
        outbox.setTag("Fluids", fluids);
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
        if (tile.isInvalid() || tile.getWorld() == null || !tile.isActive()) {
            return output;
        }

        IAEFluidStack toInject = AEFluidStack.fromFluidStack(output);
        if (toInject == null) {
            return output;
        }

        IAEFluidStack remainder;
        try {
            FluidStorageAdapter hyperAdapter = hyperStorageLink.find(
                    tile.getProxy(), tile.getWorld().getTotalWorldTime());
            if (hyperAdapter != null) {
                syncHyperStorageAdapter(hyperAdapter);
                remainder = hyperAdapter.injectItems(toInject.copy(), Actionable.MODULATE, actionSource);
            } else if (!com.github.aeddddd.ae2enhanced.config.ServerConfig.current().recycler.requireHyperStorageForRedirect) {
                remainder = injectToNetwork(toInject);
            } else {
                return output;
            }
        } catch (GridAccessException | RuntimeException e) {
            return output;
        }

        long requested = output.amount;
        long remaining = remainder == null ? 0 : remainder.getStackSize();
        if (remaining < 0 || remaining > requested) {
            return output;
        }
        long injectedCount = requested - remaining;
        if (injectedCount > 0) {
            IAEFluidStack change = toInject.copy();
            change.setStackSize(injectedCount);
            postFluidAlterations(java.util.Collections.singletonList(change));
        }
        if (remaining == 0) {
            return null;
        }
        FluidStack result = output.copy();
        result.amount = (int) remaining;
        return result;
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

        int fullScanInterval = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().recycler.fullScanIntervalTicks;
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
        boolean heartbeat = tickCounter % com.github.aeddddd.ae2enhanced.config.ServerConfig.current().recycler.heartbeatIntervalTicks == 0;

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
                    tile.markDirty();
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

        List<IAEFluidStack> requests = collector.drain();
        List<IAEFluidStack> acceptedChanges = new ArrayList<>();
        lastRecycledCount = 0;
        for (IAEFluidStack stack : requests) {
            IAEFluidStack remainder;
            try {
                if (com.github.aeddddd.ae2enhanced.config.ServerConfig.current().recycler.forceHyperdimensionalStorage) {
                    FluidStorageAdapter adapter = hyperStorageLink.find(
                            tile.getProxy(), tile.getWorld().getTotalWorldTime());
                    if (adapter != null) {
                        syncHyperStorageAdapter(adapter);
                        remainder = adapter.injectItems(stack.copy(), Actionable.MODULATE, actionSource);
                    } else {
                        remainder = injectToNetwork(stack);
                    }
                } else {
                    remainder = injectToNetwork(stack);
                }
            } catch (GridAccessException | RuntimeException e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Recycler fluid injection failed; retaining buffered stack", e);
                remainder = stack;
            }

            long requested = stack.getStackSize();
            long remaining = remainder == null ? 0 : remainder.getStackSize();
            if (remaining < 0 || remaining > requested) {
                remaining = requested;
            }
            long accepted = requested - remaining;
            if (accepted > 0) {
                IAEFluidStack change = stack.copy();
                change.setStackSize(accepted);
                acceptedChanges.add(change);
                lastRecycledCount += accepted;
            }
            if (remaining > 0) {
                IAEFluidStack retry = stack.copy();
                retry.setStackSize(remaining);
                collector.add(retry);
            }
        }
        tile.markDirty();
        if (!acceptedChanges.isEmpty()) {
            postFluidAlterations(acceptedChanges);
        }
    }

    private IAEFluidStack injectToNetwork(IAEFluidStack stack) throws GridAccessException {
        IStorageGrid storageGrid = tile.getProxy().getGrid().getCache(IStorageGrid.class);
        if (storageGrid == null) {
            return stack;
        }
        IMEMonitor<IAEFluidStack> inv = storageGrid.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
        return inv.injectItems(stack.copy(), Actionable.MODULATE, actionSource);
    }

    public void dropBufferedContents(World world, net.minecraft.util.math.BlockPos pos) {
        List<IAEFluidStack> buffered = collector.drain();
        if (!buffered.isEmpty()) {
            tile.markDirty();
        }
        for (IAEFluidStack stack : buffered) {
            long remaining = stack.getStackSize();
            while (remaining > 0) {
                FluidStack fluid = stack.getFluidStack();
                fluid.amount = (int) Math.min(remaining, Integer.MAX_VALUE);
                net.minecraft.item.ItemStack drop =
                        com.github.aeddddd.ae2enhanced.item.ItemFluidDrop.createStack(fluid);
                if (!drop.isEmpty()) {
                    world.spawnEntity(new net.minecraft.entity.item.EntityItem(world,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));
                }
                remaining -= fluid.amount;
            }
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
            IAEFluidStack remainingRequest = request.copy();
            remainingRequest.setStackSize(requestedCount);
            FluidStack got = adapter.extract(remainingRequest, type == Actionable.SIMULATE);
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
