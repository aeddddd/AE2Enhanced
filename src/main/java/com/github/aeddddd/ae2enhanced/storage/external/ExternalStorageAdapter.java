package com.github.aeddddd.ae2enhanced.storage.external;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.storage.Descriptor;
import com.github.aeddddd.ae2enhanced.storage.EnergyDescriptor;
import com.github.aeddddd.ae2enhanced.storage.IStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.storage.ManaDescriptor;
import com.github.aeddddd.ae2enhanced.storage.StorageConstants;
import com.github.aeddddd.ae2enhanced.storage.StorageSection;
import net.minecraft.nbt.NBTTagCompound;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * 外部存储通道适配器.
 * <p>
 * 当 Flux_Applied / Botania_Applie 等外部模组已注册对应资源的存储通道时,
 * 本适配器将超维度仓储中枢的 BigInteger 数量通过外部通道的 {@code createFromNBT} 转换为外部 AE 堆叠,
 * 从而参与到外部通道的 ME 网络存储体系中.
 * </p>
 * <p>
 * 由于外部通道的具体堆叠类型在编译期不可见,本类直接以原始类型实现
 * {@link IMEMonitor} 与 {@link IMEInventoryHandler},避免 AE2 泛型 API 对
 * {@code IAEStack<T extends IAEStack<T>>} 的自引用约束.
 * </p>
 *
 * @param <D> 描述符类型,如 {@link com.github.aeddddd.ae2enhanced.storage.EnergyDescriptor}
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ExternalStorageAdapter<D extends Descriptor> implements IMEMonitor, IMEInventoryHandler, IStorageAdapter {

    protected final Map<D, BigInteger> storage = new ConcurrentHashMap<>();
    protected final IStorageChannel<?> channel;
    protected final HyperdimensionalStorageFile file;
    protected final List<IMEMonitorHandlerReceiver> listeners = new CopyOnWriteArrayList<>();
    protected final AtomicReference<BigInteger> totalCount = new AtomicReference<>(BigInteger.ZERO);
    protected final D descriptor;
    protected final String nbtKey;

    protected Runnable onChangeCallback = null;
    protected BiConsumer<IAEStack, IActionSource> postChangeCallback = null;

    /**
     * 构造外部存储通道适配器.
     *
     * @param file       超维度仓储中枢持久化文件
     * @param channel    外部存储通道实例
     * @param nbtKey     用于 {@code createFromNBT} 的数量 NBT 键
     * @param descriptor 该资源类型的单例描述符
     */
    public ExternalStorageAdapter(HyperdimensionalStorageFile file, IStorageChannel<?> channel, String nbtKey, D descriptor) {
        this.file = file;
        this.channel = channel;
        this.nbtKey = nbtKey;
        this.descriptor = descriptor;
    }

    /**
     * 返回本适配器对应的存储分区，用于 section 级脏标记。
     */
    protected StorageSection getStorageSection() {
        if (descriptor instanceof EnergyDescriptor) {
            return StorageSection.ENERGY;
        }
        if (descriptor instanceof ManaDescriptor) {
            return StorageSection.MANA;
        }
        return StorageSection.ENERGY;
    }

    protected D createDescriptor(IAEStack input) {
        return descriptor;
    }

    protected IAEStack createResult(IAEStack request, BigInteger amount) {
        long size = amount.compareTo(StorageConstants.LONG_MAX) > 0 ? Long.MAX_VALUE : amount.longValue();
        return createChannelStack(size);
    }

    protected IAEStack getAETemplate(D descriptor) {
        return createChannelStack(1L);
    }

    /**
     * 创建当前外部通道类型的 AE 堆叠，数量使用 nbtKey 编码.
     */
    protected IAEStack createChannelStack(long size) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong(nbtKey, size);

        IAEStack result = ExternalStackFactory.createFromNBT(channel, nbt);
        if (result == null) {
            result = ExternalStackFactory.createStack(channel, size);
        }
        if (result != null) {
            result.setStackSize(size);
        }
        return result;
    }

    /**
     * 为变更通知创建带符号数量的通道堆叠.
     */
    protected IAEStack createChangeStack(BigInteger amount, boolean isInjection) {
        long size = amount.compareTo(StorageConstants.LONG_MAX) > 0 ? Long.MAX_VALUE : amount.longValue();
        IAEStack stack = createChannelStack(size);
        if (stack != null) {
            stack.setStackSize(isInjection ? size : -size);
        }
        return stack;
    }

    @Override
    public IStorageChannel getChannel() {
        return channel;
    }

    @Override
    public IAEStack injectItems(IAEStack input, Actionable type, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;
        if (file != null && file.isSafeMode()) {
            return input;
        }
        if (type != Actionable.MODULATE) {
            return null;
        }
        D key = createDescriptor(input);
        if (key == null) {
            return input;
        }
        BigInteger amount = BigInteger.valueOf(input.getStackSize());

        BigInteger old = storage.get(key);
        if (old == null) {
            storage.put(key, amount);
        } else {
            storage.put(key, old.add(amount));
        }
        addToTotal(amount);
        if (file != null) file.markDirty(getStorageSection());

        if (hasChangeConsumers()) {
            IAEStack change = createChangeStack(BigInteger.valueOf(input.getStackSize()), true);
            if (change != null) {
                notifyPostChange(change, src);
            }
        }
        return null;
    }

    @Override
    public IAEStack extractItems(IAEStack request, Actionable type, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;
        D key = createDescriptor(request);
        if (key == null) {
            return null;
        }
        BigInteger requested = BigInteger.valueOf(request.getStackSize());
        BigInteger available = storage.get(key);
        if (available == null) {
            return null;
        }
        BigInteger toExtract = available.min(requested);
        if (toExtract.signum() <= 0) {
            return null;
        }

        if (type == Actionable.MODULATE) {
            BigInteger remaining = available.subtract(toExtract);
            if (remaining.signum() <= 0) {
                storage.remove(key);
            } else {
                storage.put(key, remaining);
            }
            subtractFromTotal(toExtract);
            if (file != null) file.markDirty(getStorageSection());

            if (hasChangeConsumers()) {
                IAEStack change = createChangeStack(toExtract, false);
                if (change != null) {
                    notifyPostChange(change, src);
                }
            }
        }

        return createResult(request, toExtract);
    }

    @Override
    public IItemList getAvailableItems(IItemList out) {
        for (Map.Entry<D, BigInteger> entry : storage.entrySet()) {
            D desc = entry.getKey();
            IAEStack aeStack = getAETemplate(desc);
            if (aeStack == null) continue;

            BigInteger count = entry.getValue();
            IAEStack copy = aeStack.copy();
            if (count.compareTo(StorageConstants.LONG_MAX) > 0) {
                copy.setStackSize(Long.MAX_VALUE);
            } else {
                copy.setStackSize(count.longValue());
            }
            out.add(copy);
        }
        return out;
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

    @Override
    public IItemList getStorageList() {
        IItemList list = channel.createList();
        return getAvailableItems(list);
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver l, Object verificationToken) {
        listeners.add(l);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver l) {
        listeners.remove(l);
    }

    public void recalcTotal() {
        BigInteger sum = BigInteger.ZERO;
        for (BigInteger v : storage.values()) {
            sum = sum.add(v);
        }
        totalCount.set(sum);
    }

    public Map<D, BigInteger> getStorageMap() {
        return storage;
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

    @Override
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    public void setPostChangeCallback(BiConsumer<IAEStack, IActionSource> callback) {
        this.postChangeCallback = callback;
    }

    private boolean hasChangeConsumers() {
        return !listeners.isEmpty() || onChangeCallback != null || postChangeCallback != null;
    }

    private void addToTotal(BigInteger delta) {
        BigInteger prev, next;
        do {
            prev = totalCount.get();
            next = prev.add(delta);
        } while (!totalCount.compareAndSet(prev, next));
    }

    private void subtractFromTotal(BigInteger delta) {
        BigInteger prev, next;
        do {
            prev = totalCount.get();
            next = prev.subtract(delta);
        } while (!totalCount.compareAndSet(prev, next));
    }

    protected void notifyPostChange(IAEStack change, IActionSource src) {
        if (!listeners.isEmpty()) {
            for (IMEMonitorHandlerReceiver listener : listeners) {
                listener.postChange(this, SingleItemIterable.of(change), src);
            }
        }
        if (postChangeCallback != null) {
            postChangeCallback.accept(change, src);
        }
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    /**
     * 单元素不可变 Iterable.
     */
    private static final class SingleItemIterable<E> implements Iterable<E> {
        private final E item;

        private SingleItemIterable(E item) {
            this.item = item;
        }

        static <E> SingleItemIterable<E> of(E item) {
            return new SingleItemIterable<>(item);
        }

        @Override
        public Iterator<E> iterator() {
            return new SingleItemIterator<>(item);
        }
    }

    private static final class SingleItemIterator<E> implements Iterator<E> {
        private final E item;
        private boolean hasNext = true;

        private SingleItemIterator(E item) {
            this.item = item;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public E next() {
            if (!hasNext) {
                throw new java.util.NoSuchElementException();
            }
            hasNext = false;
            return item;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
