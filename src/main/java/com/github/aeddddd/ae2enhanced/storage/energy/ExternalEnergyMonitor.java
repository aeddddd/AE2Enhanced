package com.github.aeddddd.ae2enhanced.storage.energy;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 外部能量容器的 AE 存储监视器包装.
 * <p>
 * 将相邻方块(电池、龙研能量核心等)的能量存储通过 {@link IEnergyAdapter}
 * 暴露为一个 {@link IMEMonitor},语义对标存储总线的 ItemHandlerAdapter:
 * 注入=向容器充能,提取=从容器抽能,stackSize 单位 1 = 1 RF.
 * </p>
 * <p>
 * 自身的 inject/extract 在 MODULATE 时通过监听器机制实时上报变化;
 * 外部途径(其它 mod 管道等)造成的变化由能源存储总线轮询上报.
 * </p>
 * <p>
 * 当 Flux_Applied 外部通道生效时,通道堆叠类型为 FluxStack 而非 IAEEnergyStack,
 * 因此本类以 raw 类型实现,所有堆叠经 {@link EnergyChannelResolver#createStack(long)} 创建.
 * </p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ExternalEnergyMonitor implements IMEMonitor {

    private final TileEntity tile;
    private final IEnergyAdapter adapter;
    private final IEnergyStorage cap;
    private final IStorageChannel channel;
    private final List<IMEMonitorHandlerReceiver> listeners = new ArrayList<>();

    private StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    public ExternalEnergyMonitor(TileEntity tile, IEnergyAdapter adapter, IEnergyStorage cap) {
        this.tile = tile;
        this.adapter = adapter;
        this.cap = cap;
        this.channel = EnergyChannelResolver.getChannel();
    }

    public void setStorageFilter(StorageFilter filter) {
        this.storageFilter = filter;
    }

    private boolean isTileValid() {
        return this.tile != null && !this.tile.isInvalid();
    }

    /** 当前储量(long 级),供总线轮询使用 */
    public long getStoredEnergy() {
        if (!isTileValid()) {
            return 0;
        }
        return this.adapter.getStoredEnergy(this.tile, this.cap);
    }

    /** 当前容量(long 级) */
    public long getCapacityEnergy() {
        if (!isTileValid()) {
            return 0;
        }
        return this.adapter.getCapacityEnergy(this.tile, this.cap);
    }

    @Override
    public IAEStack injectItems(IAEStack input, Actionable mode, IActionSource src) {
        if (input == null || !input.isMeaningful() || !isTileValid()) {
            return input;
        }
        long requested = input.getStackSize();
        long injected = this.adapter.injectEnergy(this.tile, this.cap, requested, mode == Actionable.SIMULATE);
        if (injected <= 0) {
            return input.copy();
        }
        if (mode == Actionable.MODULATE) {
            notifyListeners(injected, src);
        }
        long remaining = requested - injected;
        return remaining > 0 ? EnergyChannelResolver.createStack(remaining) : null;
    }

    @Override
    public IAEStack extractItems(IAEStack request, Actionable mode, IActionSource src) {
        if (request == null || !request.isMeaningful() || !isTileValid()) {
            return null;
        }
        long requested = request.getStackSize();
        long extracted = this.adapter.extractEnergy(this.tile, this.cap, requested, mode == Actionable.SIMULATE);
        if (extracted <= 0) {
            return null;
        }
        if (mode == Actionable.MODULATE) {
            notifyListeners(-extracted, src);
        }
        return EnergyChannelResolver.createStack(extracted);
    }

    @Override
    public IItemList getAvailableItems(IItemList out) {
        long stored = getStoredEnergy();
        if (stored > 0 && passesStorageFilter()) {
            IAEStack stack = EnergyChannelResolver.createStack(stored);
            if (stack != null) {
                out.addStorage(stack);
            }
        }
        return out;
    }

    /**
     * 对标存储总线的 STORAGE_FILTER 设置:
     * EXTRACTABLE_ONLY 时,无法被提取的容器(如仅可充能设备)不在网络中显示储量.
     */
    private boolean passesStorageFilter() {
        if (this.storageFilter == StorageFilter.EXTRACTABLE_ONLY) {
            return this.adapter.getExtractableEnergy(this.tile, this.cap) > 0;
        }
        return true;
    }

    @Override
    public IStorageChannel getChannel() {
        return this.channel;
    }

    @Override
    public IItemList getStorageList() {
        return getAvailableItems(this.channel.createList());
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver listener, Object verificationToken) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver listener) {
        this.listeners.remove(listener);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEStack input) {
        return input != null && input.isMeaningful() && isTileValid()
                && this.adapter.getReceiveableEnergy(this.tile, this.cap) > 0;
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

    /**
     * 向监听器上报一次变化(带符号增量).
     * 与存储总线一致,变化以 delta 形式通过 IMEMonitorHandlerReceiver 传播.
     */
    private void notifyListeners(long delta, IActionSource src) {
        if (this.listeners.isEmpty() || delta == 0) {
            return;
        }
        IAEStack change = EnergyChannelResolver.createStack(0);
        if (change == null) {
            return;
        }
        change.setStackSize(delta);
        List<IAEStack> changes = Collections.singletonList(change);
        for (IMEMonitorHandlerReceiver listener : new ArrayList<>(this.listeners)) {
            listener.postChange(this, changes, src);
        }
    }
}
