package com.github.aeddddd.ae2enhanced.integration.drawer;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.me.storage.ITickingMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽屉模组监视器包装器.
 *
 * <p>将 {@link IDrawerIndexAdapter} 包装为 AE2 的 {@link IMEMonitor} + {@link ITickingMonitor},
 * 统一处理 onTick() 差异检测、listener 通知、以及主动操作后的增量更新.</p>
 *
 * <p>本类是无条件加载的公共类,不引用任何第三方抽屉模组类,常量池安全.</p>
 */
public class DrawerMonitorWrapper implements IMEMonitor<IAEItemStack>, ITickingMonitor {

    private final IDrawerIndexAdapter adapter;
    private final IStorageChannel<IAEItemStack> channel;
    private final Map<IMEMonitorHandlerReceiver<IAEItemStack>, Object> listeners = new ConcurrentHashMap<>();
    private IActionSource mySource;
    private IItemList<IAEItemStack> currentlyCached;

    public DrawerMonitorWrapper(IDrawerIndexAdapter adapter, IStorageChannel<IAEItemStack> channel) {
        this.adapter = adapter;
        this.channel = channel;
        this.currentlyCached = channel.createList();
        this.adapter.getAvailableItems(this.currentlyCached);
    }

    // ---- IBaseMonitor ----

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEItemStack> l, Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEItemStack> l) {
        this.listeners.remove(l);
    }

    private void postDifference(Iterable<IAEItemStack> changes) {
        for (Map.Entry<IMEMonitorHandlerReceiver<IAEItemStack>, Object> entry : this.listeners.entrySet()) {
            IMEMonitorHandlerReceiver<IAEItemStack> receiver = entry.getKey();
            if (receiver.isValid(entry.getValue())) {
                receiver.postChange(this, changes, this.mySource);
            }
        }
    }

    // ---- ITickingMonitor ----

    @Override
    public TickRateModulation onTick() {
        IItemList<IAEItemStack> currentList = this.channel.createList();
        this.adapter.getAvailableItems(currentList);

        for (IAEItemStack cached : this.currentlyCached) {
            cached.setStackSize(-cached.getStackSize());
        }
        for (IAEItemStack current : currentList) {
            this.currentlyCached.add(current);
        }

        List<IAEItemStack> changes = new ArrayList<>();
        for (IAEItemStack entry : this.currentlyCached) {
            if (entry.getStackSize() != 0) {
                changes.add(entry);
            }
        }
        this.currentlyCached = currentList;

        if (!changes.isEmpty()) {
            this.postDifference(changes);
            return TickRateModulation.URGENT;
        }
        return TickRateModulation.SLOWER;
    }

    @Override
    public void setActionSource(IActionSource source) {
        this.mySource = source;
    }

    // ---- IMEInventoryHandler (via IMEMonitor) ----

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        IAEItemStack result = this.adapter.injectItems(input, type, src);
        if (type == Actionable.MODULATE && result != null && result.getStackSize() < input.getStackSize()) {
            long injected = input.getStackSize() - result.getStackSize();
            IAEItemStack diff = input.copy();
            diff.setStackSize(injected);
            this.currentlyCached.add(diff);
            this.postDifference(Collections.singletonList(diff));
        } else if (type == Actionable.MODULATE && result == null) {
            IAEItemStack diff = input.copy();
            this.currentlyCached.add(diff);
            this.postDifference(Collections.singletonList(diff));
        }
        return result;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        IAEItemStack result = this.adapter.extractItems(request, mode, src);
        if (mode == Actionable.MODULATE && result != null) {
            IAEItemStack diff = result.copy();
            diff.setStackSize(-result.getStackSize());
            this.currentlyCached.add(diff);
            this.postDifference(Collections.singletonList(diff));
        }
        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        return this.adapter.getAvailableItems(out);
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return this.channel;
    }

    @Override
    public AccessRestriction getAccess() {
        return this.adapter.getAccess();
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        return this.adapter.isPrioritized(input);
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        return this.adapter.canAccept(input);
    }

    @Override
    public int getPriority() {
        return this.adapter.getPriority();
    }

    @Override
    public int getSlot() {
        return this.adapter.getSlot();
    }

    @Override
    public boolean validForPass(int pass) {
        return this.adapter.validForPass(pass);
    }

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        IItemList<IAEItemStack> list = this.channel.createList();
        return this.adapter.getAvailableItems(list);
    }
}
