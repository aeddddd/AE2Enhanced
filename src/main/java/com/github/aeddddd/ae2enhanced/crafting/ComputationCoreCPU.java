package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.helpers.MachineSource;
import appeng.util.item.ItemList;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.resources.I18n;

import java.util.HashSet;
import java.util.Set;

/**
 * Internal proxy that implements {@link ICraftingCPU} on behalf of {@link TileComputationCore}.
 *
 * <p>Key responsibility: provide enormous crafting storage ({@link Long#MAX_VALUE} bytes)
 * and manage an internal {@link ItemList} inventory for items held during active crafting jobs.
 * Actual recipe execution is delegated to network ICraftingMediums (assemblers, ME interfaces).</p>
 */
public class ComputationCoreCPU implements ICraftingCPU {

    private final TileComputationCore core;
    private final MachineSource actionSource;
    private final ItemList inventory = new ItemList();
    private final Set<appeng.api.storage.IMEMonitorHandlerReceiver<IAEItemStack>> listeners = new HashSet<>();
    private String name;

    public ComputationCoreCPU(TileComputationCore core) {
        this.core = core;
        this.actionSource = new MachineSource((IActionHost) core);
        this.name = I18n.format("tile.ae2enhanced.computation_core.name");
    }

    // ==================== ICraftingCPU ====================

    @Override
    public boolean isBusy() {
        return !core.isFormed() || core.getScheduler().getActiveCount() >= core.getParallelLimit();
    }

    @Override
    public IActionSource getActionSource() {
        return actionSource;
    }

    @Override
    public long getAvailableStorage() {
        return Long.MAX_VALUE;
    }

    @Override
    public int getCoProcessors() {
        return ParallelAllocator.MAX_PARALLEL;
    }

    @Override
    public String getName() {
        return name;
    }

    public void updateName(String name) {
        this.name = name;
    }

    @Override
    public IAEItemStack getFinalOutput() {
        // P1-S5: return final output of the first active order
        return null;
    }

    @Override
    public long getRemainingItemCount() {
        // P1-S5: sum remaining items across all active orders
        return 0;
    }

    @Override
    public long getStartItemCount() {
        return 0;
    }

    // ==================== Job Submission ====================

    public ICraftingLink submitJob(IGrid grid, ICraftingJob job, IActionSource src, ICraftingRequester req) {
        // P1-S5: convert ICraftingJob into CraftingOrder and enqueue
        // For now return a placeholder so the terminal recognises the core as available.
        return new ComputationCoreCraftingLink();
    }

    // ==================== IBaseMonitor ====================

    @Override
    public void addListener(appeng.api.storage.IMEMonitorHandlerReceiver<IAEItemStack> receiver, Object verificationToken) {
        listeners.add(receiver);
    }

    @Override
    public void removeListener(appeng.api.storage.IMEMonitorHandlerReceiver<IAEItemStack> receiver) {
        listeners.remove(receiver);
    }

    // ==================== Internal Crafting Storage ====================

    /**
     * Inject items into the internal crafting buffer (infinite capacity).
     *
     * @return null if everything was accepted, otherwise the remainder.
     */
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) {
            return null;
        }
        if (mode == Actionable.SIMULATE) {
            // Infinite capacity — everything fits
            return null;
        }
        IAEItemStack toAdd = input.copy();
        inventory.add(toAdd);
        notifyListeners(toAdd);
        return null;
    }

    /**
     * Extract items from the internal crafting buffer.
     *
     * @return the extracted stack, or null if none was available.
     */
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) {
            return null;
        }
        IAEItemStack stored = inventory.findPrecise(request);
        if (stored == null || stored.getStackSize() <= 0) {
            return null;
        }
        long extract = Math.min(request.getStackSize(), stored.getStackSize());
        IAEItemStack result = request.copy();
        result.setStackSize(extract);
        if (mode == Actionable.MODULATE) {
            IAEItemStack diff = result.copy();
            diff.setStackSize(-extract);
            stored.add(diff); // deduct count
            if (stored.getStackSize() <= 0) {
                stored.reset();
            }
            notifyListeners(diff);
        }
        return result;
    }

    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        for (IAEItemStack stack : inventory) {
            if (stack != null && stack.isMeaningful()) {
                out.add(stack.copy());
            }
        }
        return out;
    }

    public IStorageChannel<IAEItemStack> getChannel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    public ItemList getInventory() {
        return inventory;
    }

    // ==================== IMEInventoryHandler stubs ====================
    // Used by CraftingGridCache mixins for inject/extract forwarding

    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    public boolean isPrioritized(IAEItemStack stack) {
        return false;
    }

    public boolean canAccept(IAEItemStack stack) {
        return true;
    }

    public int getPriority() {
        return 0;
    }

    public int getSlot() {
        return 0;
    }

    public boolean validForPass(int pass) {
        return true;
    }

    // ==================== Private ====================

    private void notifyListeners(IAEItemStack change) {
        for (appeng.api.storage.IMEMonitorHandlerReceiver<IAEItemStack> listener : listeners) {
            listener.postChange(null, ImmutableList.of(change), actionSource);
        }
    }
}
