package com.github.aeddddd.ae2enhanced.storage.energy;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IItemList;

import java.util.ArrayList;
import java.util.List;

/**
 * RF 能量的存储适配器.
 * 直接实现 IMEMonitor,内部以 long 维护储量与容量(能量无子类型,无需 Map 结构).
 */
public class EnergyStorageAdapter implements IMEMonitor<IAEEnergyStack> {

    private final IStorageChannel<IAEEnergyStack> channel;
    private long storedRF = 0;
    private long capacityRF;
    private final List<IMEMonitorHandlerReceiver<IAEEnergyStack>> listeners = new ArrayList<>();

    public EnergyStorageAdapter(long capacityRF) {
        this.capacityRF = capacityRF;
        this.channel = appeng.api.AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class);
    }

    @Override
    public IAEEnergyStack injectItems(IAEEnergyStack input, Actionable mode, IActionSource src) {
        if (input == null || !input.isMeaningful()) {
            return null;
        }

        long canAccept = Math.min(input.getStackSize(), this.capacityRF - this.storedRF);
        if (canAccept <= 0) {
            return input.copy();
        }

        if (mode == Actionable.MODULATE) {
            this.storedRF += canAccept;
        }

        long remaining = input.getStackSize() - canAccept;
        return remaining > 0 ? AEEnergyStack.create(remaining) : null;
    }

    @Override
    public IAEEnergyStack extractItems(IAEEnergyStack request, Actionable mode, IActionSource src) {
        if (request == null || !request.isMeaningful()) {
            return null;
        }

        long canProvide = Math.min(request.getStackSize(), this.storedRF);
        if (canProvide <= 0) {
            return null;
        }

        if (mode == Actionable.MODULATE) {
            this.storedRF -= canProvide;
        }

        return AEEnergyStack.create(canProvide);
    }

    @Override
    public IItemList<IAEEnergyStack> getAvailableItems(IItemList<IAEEnergyStack> out) {
        if (this.storedRF > 0) {
            out.addStorage(AEEnergyStack.create(this.storedRF));
        }
        return out;
    }

    @Override
    public IStorageChannel<IAEEnergyStack> getChannel() {
        return this.channel;
    }

    @Override
    public IItemList<IAEEnergyStack> getStorageList() {
        return getAvailableItems(this.channel.createList());
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEEnergyStack> listener, Object verificationToken) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEEnergyStack> listener) {
        this.listeners.remove(listener);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEEnergyStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEEnergyStack input) {
        return input != null && this.storedRF < this.capacityRF;
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
    public boolean isSticky() {
        return false;
    }

    public long getStoredRF() {
        return storedRF;
    }

    public long getCapacityRF() {
        return capacityRF;
    }

    public void setCapacityRF(long capacity) {
        this.capacityRF = capacity;
        if (this.storedRF > this.capacityRF) {
            this.storedRF = this.capacityRF;
        }
    }

    public long addRF(long amount) {
        long actual = Math.min(amount, this.capacityRF - this.storedRF);
        this.storedRF += actual;
        return actual;
    }

    public long removeRF(long amount) {
        long actual = Math.min(amount, this.storedRF);
        this.storedRF -= actual;
        return actual;
    }

    public void setStoredRF(long amount) {
        this.storedRF = Math.max(0, Math.min(amount, this.capacityRF));
    }

    private void notifyListeners() {
        if (this.listeners.isEmpty()) return;
        List<IAEEnergyStack> changes = java.util.Collections.singletonList(AEEnergyStack.create(this.storedRF));
        for (IMEMonitorHandlerReceiver<IAEEnergyStack> listener : this.listeners) {
            listener.postChange(this, changes, null);
        }
    }
}
