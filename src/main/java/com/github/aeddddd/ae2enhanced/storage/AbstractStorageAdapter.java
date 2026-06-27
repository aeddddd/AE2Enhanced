package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * 超维度仓储中枢的泛型存储适配器基类.
 *
 * 统一物品/流体/气体/源质四种存储类型的公共逻辑：
 * - injectItems / extractItems 的 SIMULATE/MODULATE 事务
 * - getAvailableItems / getStorageList 的遍历与数量转换
 * - recalcTotal / getTotalCount / isSafeMode 等状态查询
 * - Listener 管理与 postChange 通知
 *
 * 子类只需实现：
 * - {@link #createDescriptor(T)}：从 AE 堆叠构造描述符
 * - {@link #createResult(T, BigInteger)}：从请求堆叠 + 提取量构造结果
 * - {@link #getAETemplate(D)}：从描述符获取 AE 模板(stackSize=1)
 * - {@link #getChannel()}：返回对应的 IStorageChannel
 *
 * @param <T> AE 堆叠类型,如 IAEItemStack / IAEFluidStack / IAEGasStack / IAEEssentiaStack
 * @param <D> 描述符类型,如 ItemDescriptor / FluidDescriptor / GasDescriptor / EssentiaDescriptor
 */
public abstract class AbstractStorageAdapter<T extends IAEStack<T>, D extends Descriptor>
        implements IMEMonitor<T>, IMEInventoryHandler<T>, IStorageAdapter {

    protected final Map<D, BigInteger> storage = new ConcurrentHashMap<>();
    protected IStorageChannel<T> channel;
    protected final HyperdimensionalStorageFile file;
    protected final List<IMEMonitorHandlerReceiver<T>> listeners = new CopyOnWriteArrayList<>();
    protected final AtomicReference<BigInteger> totalCount = new AtomicReference<>(BigInteger.ZERO);
    protected Runnable onChangeCallback = null;
    protected BiConsumer<T, IActionSource> postChangeCallback = null;

    protected AbstractStorageAdapter(HyperdimensionalStorageFile file) {
        this.file = file;
    }

    // ---- 子类必须实现 ----

    /**
     * 从输入 AE 堆叠构造描述符,用于 Map 的 Key.
     */
    protected abstract D createDescriptor(T input);

    /**
     * 从请求堆叠和实际提取量构造结果堆叠.
     * 实现方式因类型而异：
     * - 物品/流体：通常通过 channel.createStack(...) 创建后设置 size
     * - 气体/源质：通常直接 request.copy() 后设置 size
     */
    protected abstract T createResult(T request, BigInteger amount);

    /**
     * 从描述符获取缓存的 AE 模板(stackSize=1).
     */
    protected abstract T getAETemplate(D descriptor);

    @Override
    public abstract IStorageChannel<T> getChannel();

    /**
     * 返回本适配器对应的存储分区，用于 section 级脏标记。
     */
    protected abstract StorageSection getStorageSection();

    // ---- 核心存储操作(通用实现) ----

    @Override
    public T injectItems(T input, Actionable type, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;
        if (file != null && file.isSafeMode()) {
            return input; // 安全模式：拒绝写入
        }
        // SIMULATE 分支不修改 storage，直接返回 null（全部接受）。
        // 跳过 createDescriptor 可避免大量合成预演时的描述符分配。
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
            onDescriptorAdded(key);
        } else {
            storage.put(key, old.add(amount));
        }
        addToTotal(amount);
        if (file != null) file.markDirty(getStorageSection());

        // 只在真正有监听器时才构造 change 与通知
        if (hasChangeConsumers()) {
            notifyPostChange(input.copy(), src);
        }
        return null; // 无限容量,全部接受
    }

    @Override
    public T extractItems(T request, Actionable type, IActionSource src) {
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
                onDescriptorRemoved(key);
            } else {
                storage.put(key, remaining);
            }
            subtractFromTotal(toExtract);
            if (file != null) file.markDirty(getStorageSection());

            if (hasChangeConsumers()) {
                T change = request.copy();
                // 绝大部分提取量都在 long 范围内,避免 min() 分配
                long changeSize;
                if (toExtract.bitLength() < 63) {
                    changeSize = -toExtract.longValue();
                } else {
                    changeSize = -toExtract.min(StorageConstants.LONG_MAX).longValue();
                }
                change.setStackSize(changeSize);
                notifyPostChange(change, src);
            }
        }

        return createResult(request, toExtract);
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out) {
        for (Map.Entry<D, BigInteger> entry : storage.entrySet()) {
            D desc = entry.getKey();
            T aeStack = getAETemplate(desc);
            if (aeStack == null) continue;

            BigInteger count = entry.getValue();
            T copy = aeStack.copy();
            if (count.compareTo(StorageConstants.LONG_MAX) > 0) {
                copy.setStackSize(Long.MAX_VALUE);
            } else {
                copy.setStackSize(count.longValue());
            }
            out.add(copy);
        }
        return out;
    }

    // ---- 状态查询(通用实现) ----

    public void recalcTotal() {
        BigInteger sum = BigInteger.ZERO;
        for (BigInteger v : storage.values()) {
            sum = sum.add(v);
        }
        totalCount.set(sum);
    }

    @Override
    public Map<D, BigInteger> getStorageMap() {
        return storage;
    }

    @Override
    public BigInteger getTotalCount() {
        return totalCount.get();
    }

    @Override
    public boolean isSafeMode() {
        return file != null && file.isSafeMode();
    }

    public HyperdimensionalStorageFile getFile() {
        return file;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(T input) {
        return false;
    }

    @Override
    public boolean canAccept(T input) {
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
    public IItemList<T> getStorageList() {
        IItemList<T> list = channel.createList();
        return getAvailableItems(list);
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver<T> l, Object verificationToken) {
        listeners.add(l);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<T> l) {
        listeners.remove(l);
    }

    @Override
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    public void setPostChangeCallback(BiConsumer<T, IActionSource> callback) {
        this.postChangeCallback = callback;
    }

    // ---- 索引 Hook（子类可覆盖） ----

    protected void onDescriptorAdded(D descriptor) {
    }

    protected void onDescriptorRemoved(D descriptor) {
    }

    // ---- 内部辅助 ----

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

    protected void notifyPostChange(T change, IActionSource src) {
        if (!listeners.isEmpty()) {
            for (IMEMonitorHandlerReceiver<T> listener : listeners) {
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
     * 单元素不可变 Iterable，替代 {@link java.util.Collections#singletonList(Object)}，
     * 避免每次通知都创建 List 与 Iterator 对象。
     */
    private static final class SingleItemIterable<E> implements Iterable<E> {
        private final E item;

        private SingleItemIterable(E item) {
            this.item = item;
        }

        @SuppressWarnings("unchecked")
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
