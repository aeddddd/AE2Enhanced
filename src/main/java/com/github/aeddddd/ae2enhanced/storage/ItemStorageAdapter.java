package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.config.AccessRestriction;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import net.minecraft.item.ItemStack;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 物品存储适配器，实现 AE2 的 IMEInventory 接口。
 * 内部使用 BigInteger 维护数量，突破 long 上限。
 */
public class ItemStorageAdapter implements IMEMonitor<IAEItemStack> {

    private final Map<ItemDescriptor, BigInteger> storage = new ConcurrentHashMap<>();
    private final IItemStorageChannel channel;
    private final HyperdimensionalStorageFile file;
    private final List<IMEMonitorHandlerReceiver<IAEItemStack>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<BigInteger> totalCount = new AtomicReference<>(BigInteger.ZERO);
    private Runnable onChangeCallback = null;

    public ItemStorageAdapter(HyperdimensionalStorageFile file) {
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        this.file = file;
        file.load(storage);
    }

    public Map<ItemDescriptor, BigInteger> getStorageMap() {
        return storage;
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;
        ItemStack itemStack = input.createItemStack();
        ItemDescriptor key = new ItemDescriptor(itemStack);
        BigInteger amount = BigInteger.valueOf(input.getStackSize());

        if (type == Actionable.MODULATE) {
            storage.merge(key, amount, BigInteger::add);
            totalCount.updateAndGet(t -> t.add(amount));
            file.markDirty();
            notifyPostChange(input.copy(), src);
            return null; // 无限容量，全部接受
        }
        // SIMULATE: 无限容量，全部接受
        return null;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable type, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;
        ItemStack itemStack = request.createItemStack();
        ItemDescriptor key = new ItemDescriptor(itemStack);
        BigInteger available = storage.getOrDefault(key, BigInteger.ZERO);
        BigInteger requested = BigInteger.valueOf(request.getStackSize());
        BigInteger toExtract = available.min(requested);

        if (toExtract.signum() <= 0) {
            return null; // 无法提取任何
        }

        if (type == Actionable.MODULATE) {
            BigInteger remaining = available.subtract(toExtract);
            if (remaining.signum() <= 0) {
                storage.remove(key);
            } else {
                storage.put(key, remaining);
            }
            totalCount.updateAndGet(t -> t.subtract(toExtract));
            file.markDirty();
            IAEItemStack change = request.copy();
            change.setStackSize(-toExtract.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
            notifyPostChange(change, src);
        }

        IAEItemStack result = channel.createStack(itemStack);
        if (result == null) return null;

        // BigInteger -> long 分片：若超过 Long.MAX_VALUE，本次只提取 Long.MAX_VALUE
        if (toExtract.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            result.setStackSize(Long.MAX_VALUE);
        } else {
            result.setStackSize(toExtract.longValueExact());
        }
        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        for (Map.Entry<ItemDescriptor, BigInteger> entry : storage.entrySet()) {
            ItemDescriptor desc = entry.getKey();
            IAEItemStack aeStack = desc.getAETemplate(channel);
            if (aeStack == null) continue;

            BigInteger count = entry.getValue();
            IAEItemStack copy = aeStack.copy();
            if (count.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                copy.setStackSize(Long.MAX_VALUE);
            } else {
                copy.setStackSize(count.longValue());
            }
            out.addStorage(copy);
        }
        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return channel;
    }

    public BigInteger getTotalCount() {
        return totalCount.get();
    }

    public HyperdimensionalStorageFile getFile() {
        return file;
    }

    // ---- IMEInventoryHandler ----

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

    // ---- IMEMonitor / IBaseMonitor ----

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        IItemList<IAEItemStack> list = channel.createList();
        return getAvailableItems(list);
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEItemStack> l, Object verificationToken) {
        listeners.add(l);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEItemStack> l) {
        listeners.remove(l);
    }

    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    private void notifyPostChange(IAEItemStack change, IActionSource src) {
        if (listeners.isEmpty() && onChangeCallback == null) return;
        if (!listeners.isEmpty()) {
            List<IAEItemStack> changes = java.util.Collections.singletonList(change);
            for (IMEMonitorHandlerReceiver<IAEItemStack> listener : listeners) {
                listener.postChange(this, changes, src);
            }
        }
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }
}
