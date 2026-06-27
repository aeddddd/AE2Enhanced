package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.storage.IMEMonitorHandlerReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 简单的 IMEMonitor 实现,包装 ItemStorageAdapter.
 * 提供事件监听能力,用于 AE2 网络集成.
 */
public class SimpleMEMonitor implements IMEMonitor<IAEItemStack> {

    private final ItemStorageAdapter adapter;
    private final List<IMEMonitorHandlerReceiver<IAEItemStack>> listeners = new CopyOnWriteArrayList<>();

    public SimpleMEMonitor(ItemStorageAdapter adapter) {
        this.adapter = adapter;
    }

    public ItemStorageAdapter getAdapter() {
        return adapter;
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, appeng.api.config.Actionable type, IActionSource src) {
        IAEItemStack result = adapter.injectItems(input, type, src);
        if (type == appeng.api.config.Actionable.MODULATE && result == null) {
            List<IAEItemStack> changes = new ArrayList<>();
            IAEItemStack change = input.copy();
            change.setStackSize(input.getStackSize());
            changes.add(change);
            notifyPostChange(changes, src);
        }
        return result;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, appeng.api.config.Actionable type, IActionSource src) {
        IAEItemStack result = adapter.extractItems(request, type, src);
        if (type == appeng.api.config.Actionable.MODULATE && result != null && result.getStackSize() > 0) {
            List<IAEItemStack> changes = new ArrayList<>();
            IAEItemStack change = result.copy();
            change.setStackSize(-result.getStackSize());
            changes.add(change);
            notifyPostChange(changes, src);
        }
        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        return adapter.getAvailableItems(out);
    }

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        IItemList<IAEItemStack> list = adapter.getChannel().createList();
        return adapter.getAvailableItems(list);
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return adapter.getChannel();
    }

    // IMEInventoryHandler
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
        return true;
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

    // IBaseMonitor
    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEItemStack> l, Object verificationToken) {
        listeners.add(l);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEItemStack> l) {
        listeners.remove(l);
    }

    /**
     * 通知所有监听器存储发生变化.
     * 【预留】当前未被 getCellArray() 使用(直接返回 adapter),但若未来网络集成需要
     * IMEMonitor 包装,此类已准备就绪.CopyOnWriteArrayList 避免并发修改异常.
     */
    public void notifyPostChange(Iterable<IAEItemStack> changes, IActionSource src) {
        for (IMEMonitorHandlerReceiver<IAEItemStack> listener : listeners) {
            listener.postChange(this, changes, src);
        }
    }
}
