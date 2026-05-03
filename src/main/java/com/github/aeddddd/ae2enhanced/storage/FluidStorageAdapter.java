package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流体存储适配器，实现 AE2 的 IMEMonitor<IAEFluidStack> 接口。
 * 内部使用 BigInteger 维护数量，突破 long 上限。
 */
public class FluidStorageAdapter implements IMEMonitor<IAEFluidStack> {

    private final Map<FluidDescriptor, BigInteger> storage = new ConcurrentHashMap<>();
    private final IFluidStorageChannel channel;
    private final HyperdimensionalStorageFile file;
    private final List<IMEMonitorHandlerReceiver<IAEFluidStack>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<BigInteger> totalCount = new AtomicReference<>(BigInteger.ZERO);
    private Runnable onChangeCallback = null;
    private java.util.function.BiConsumer<IAEFluidStack, IActionSource> postChangeCallback = null;

    public FluidStorageAdapter(HyperdimensionalStorageFile file) {
        this.channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        this.file = file;
        file.loadFluids(storage);
        recalcTotal(); // 从文件加载后必须重新计算总数
    }

    private void recalcTotal() {
        BigInteger sum = BigInteger.ZERO;
        for (BigInteger v : storage.values()) {
            sum = sum.add(v);
        }
        totalCount.set(sum);
    }

    public Map<FluidDescriptor, BigInteger> getStorageMap() {
        return storage;
    }

    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable type, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;
        if (file != null && file.isSafeMode()) {
            return input; // 安全模式：拒绝写入，返回原流体
        }
        FluidStack fluidStack = input.getFluidStack();
        if (fluidStack == null) return null;
        FluidDescriptor key = new FluidDescriptor(fluidStack);
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
    public IAEFluidStack extractItems(IAEFluidStack request, Actionable type, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;
        FluidStack fluidStack = request.getFluidStack();
        if (fluidStack == null) return null;
        FluidDescriptor key = new FluidDescriptor(fluidStack);
        BigInteger requested = BigInteger.valueOf(request.getStackSize());
        BigInteger toExtract;

        if (type == Actionable.MODULATE) {
            final BigInteger[] extracted = new BigInteger[1];
            storage.compute(key, (k, available) -> {
                BigInteger avail = available == null ? BigInteger.ZERO : available;
                BigInteger extract = avail.min(requested);
                if (extract.signum() <= 0) {
                    return available;
                }
                extracted[0] = extract;
                BigInteger remaining = avail.subtract(extract);
                return remaining.signum() <= 0 ? null : remaining;
            });
            toExtract = extracted[0];
            if (toExtract == null) {
                return null;
            }
            totalCount.updateAndGet(t -> t.subtract(toExtract));
            file.markDirty();
            IAEFluidStack change = request.copy();
            change.setStackSize(-toExtract.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
            notifyPostChange(change, src);
        } else {
            BigInteger available = storage.getOrDefault(key, BigInteger.ZERO);
            toExtract = available.min(requested);
            if (toExtract.signum() <= 0) {
                return null;
            }
        }

        IAEFluidStack result = channel.createStack(fluidStack);
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
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        for (Map.Entry<FluidDescriptor, BigInteger> entry : storage.entrySet()) {
            FluidDescriptor desc = entry.getKey();
            IAEFluidStack aeStack = desc.getAETemplate(channel);
            if (aeStack == null) continue;

            BigInteger count = entry.getValue();
            IAEFluidStack copy = aeStack.copy();
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
    public IStorageChannel<IAEFluidStack> getChannel() {
        return channel;
    }

    public BigInteger getTotalCount() {
        return totalCount.get();
    }

    public boolean isSafeMode() {
        return file != null && file.isSafeMode();
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
    public boolean isPrioritized(IAEFluidStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEFluidStack input) {
        return true;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 1;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    // ---- IMEMonitor / IBaseMonitor ----

    @Override
    public IItemList<IAEFluidStack> getStorageList() {
        IItemList<IAEFluidStack> list = channel.createList();
        return getAvailableItems(list);
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEFluidStack> l, Object verificationToken) {
        listeners.add(l);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEFluidStack> l) {
        listeners.remove(l);
    }

    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    public void setPostChangeCallback(java.util.function.BiConsumer<IAEFluidStack, IActionSource> callback) {
        this.postChangeCallback = callback;
    }

    private void notifyPostChange(IAEFluidStack change, IActionSource src) {
        if (listeners.isEmpty() && onChangeCallback == null && postChangeCallback == null) return;
        if (!listeners.isEmpty()) {
            List<IAEFluidStack> changes = java.util.Collections.singletonList(change);
            for (IMEMonitorHandlerReceiver<IAEFluidStack> listener : listeners) {
                listener.postChange(this, changes, src);
            }
        }
        if (postChangeCallback != null) {
            postChangeCallback.accept(change, src);
        }
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }
}
