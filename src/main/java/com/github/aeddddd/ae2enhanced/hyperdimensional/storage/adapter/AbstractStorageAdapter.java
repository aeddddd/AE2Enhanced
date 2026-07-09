package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.StorageSection;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannelConstants;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.DescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.Descriptor;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 超维度仓储中枢的泛型存储适配器基类。
 * 统一物品/流体/能量等 AE key type 的公共逻辑：
 * - insert / extract 的 SIMULATE / MODULATE 事务
 * - getAvailableStacks 的遍历与数量转换
 * - 基于 BigInteger 的内部存储
 *
 * 子类只需实现：
 * - {@link #createDescriptor(T)}：从 AE key 构造描述符
 * - {@link #cast(AEKey)}：将 AE key 转换为本类型
 * - {@link #createResult(T, BigInteger)}：构造结果 key
 * - {@link #getTypeByte()}：返回二进制文件中的类型标识
 * - {@link #getCodec()} 与 {@link #getStorageSection()} 已由构造函数固定
 *
 * @param <T> AE key 类型，如 AEItemKey / AEFluidKey / EnergyKey
 * @param <D> 描述符类型，如 ItemDescriptor / FluidDescriptor / EnergyDescriptor
 */
public abstract class AbstractStorageAdapter<T extends AEKey, D extends Descriptor> {

    protected final Map<D, BigInteger> storage = new HashMap<>();
    private final DescriptorCodec<D> codec;
    private final StorageSection section;

    protected AbstractStorageAdapter(DescriptorCodec<D> codec, StorageSection section) {
        this.codec = Objects.requireNonNull(codec);
        this.section = Objects.requireNonNull(section);
    }

    /**
     * 从输入 AE key 构造描述符，用于 Map 的 Key。
     */
    public abstract D createDescriptor(T input);

    /**
     * 将通用 AE key 转换为本适配器处理的具体类型。
     *
     * @param key 输入 key
     * @return 若类型匹配则返回转换后的 key，否则返回 null
     */
    @Nullable
    public abstract T cast(AEKey key);

    /**
     * 从请求 key 和实际数量构造结果 key。
     * 在 AE2 1.20.1 中 key 不携带数量，通常直接返回请求 key。
     */
    public abstract T createResult(T request, BigInteger amount);

    /**
     * 返回二进制文件中的类型标识字节。
     */
    protected abstract byte getTypeByte();

    /**
     * 返回本适配器对应的描述符编解码器。
     */
    public DescriptorCodec<D> getCodec() {
        return codec;
    }

    /**
     * 返回本适配器对应的存储分区。
     */
    public StorageSection getStorageSection() {
        return section;
    }

    /**
     * 向存储中存入指定数量的 key。
     *
     * @return 实际存入数量（不超过 {@link Long#MAX_VALUE}）
     */
    public long insert(AEKey what, long amount, Actionable mode) {
        if (amount <= 0 || what == null) {
            return 0;
        }
        T key = cast(what);
        if (key == null) {
            return 0;
        }
        D descriptor = createDescriptor(key);
        if (descriptor == null) {
            return 0;
        }

        BigInteger current = storage.getOrDefault(descriptor, BigInteger.ZERO);
        BigInteger actual = BigInteger.valueOf(amount)
                .min(StorageChannelConstants.CAPACITY_PER_KEY.subtract(current));
        if (actual.signum() <= 0) {
            return 0;
        }
        if (mode == Actionable.SIMULATE) {
            return actual.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        }

        BigInteger next = current.add(actual);
        if (next.signum() == 0) {
            storage.remove(descriptor);
        } else {
            storage.put(descriptor, next);
        }
        return actual.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    /**
     * 从存储中取出指定数量的 key。
     *
     * @return 实际取出数量（不超过 {@link Long#MAX_VALUE}）
     */
    public long extract(AEKey what, long amount, Actionable mode) {
        if (amount <= 0 || what == null) {
            return 0;
        }
        T key = cast(what);
        if (key == null) {
            return 0;
        }
        D descriptor = createDescriptor(key);
        if (descriptor == null) {
            return 0;
        }

        BigInteger current = storage.getOrDefault(descriptor, BigInteger.ZERO);
        BigInteger actual = BigInteger.valueOf(amount).min(current);
        if (actual.signum() <= 0) {
            return 0;
        }
        if (mode == Actionable.SIMULATE) {
            return actual.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        }

        BigInteger next = current.subtract(actual);
        if (next.signum() == 0) {
            storage.remove(descriptor);
        } else {
            storage.put(descriptor, next);
        }
        return actual.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    /**
     * 将当前可用内容写入 AE2 网络统计容器。
     */
    public void getAvailableStacks(KeyCounter out) {
        for (Map.Entry<D, BigInteger> entry : storage.entrySet()) {
            BigInteger amount = entry.getValue();
            if (amount.signum() > 0) {
                long visible = amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
                out.add(entry.getKey().getAEKey(), visible);
            }
        }
    }

    /**
     * 返回内部存储 Map 的只读副本。
     * <p>返回值为 {@code Map<D, BigInteger>}，用于持久化时直接序列化描述符。</p>
     */
    public Map<D, BigInteger> getStorageMap() {
        return Collections.unmodifiableMap(new HashMap<>(storage));
    }

    /**
     * 返回以 AEKey 为键的条目视图。
     */
    public Map<AEKey, BigInteger> getEntries() {
        Map<AEKey, BigInteger> result = new HashMap<>();
        for (Map.Entry<D, BigInteger> entry : storage.entrySet()) {
            result.put(entry.getKey().getAEKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 直接设置指定 key 的数量（用于迁移/初始化）。
     */
    public void set(T key, BigInteger amount) {
        if (key == null || amount == null || amount.signum() <= 0
                || amount.compareTo(StorageChannelConstants.CAPACITY_PER_KEY) > 0) {
            if (key != null) {
                D descriptor = createDescriptor(key);
                if (descriptor != null) {
                    storage.remove(descriptor);
                }
            }
            return;
        }
        D descriptor = createDescriptor(key);
        if (descriptor != null) {
            storage.put(descriptor, amount);
        }
    }

    /**
     * 从 AEKey 到数量的映射加载内容。
     */
    public void loadFrom(Map<AEKey, BigInteger> data) {
        storage.clear();
        if (data == null) {
            return;
        }
        for (Map.Entry<AEKey, BigInteger> entry : data.entrySet()) {
            T key = cast(entry.getKey());
            if (key == null) {
                continue;
            }
            BigInteger amount = entry.getValue();
            if (amount == null || amount.signum() <= 0
                    || amount.compareTo(StorageChannelConstants.CAPACITY_PER_KEY) > 0) {
                continue;
            }
            D descriptor = createDescriptor(key);
            if (descriptor != null) {
                storage.put(descriptor, amount);
            }
        }
    }

    /**
     * 从描述符到数量的映射加载内容。
     */
    public void loadFromDescriptors(Map<D, BigInteger> data) {
        storage.clear();
        if (data == null) {
            return;
        }
        for (Map.Entry<D, BigInteger> entry : data.entrySet()) {
            D descriptor = entry.getKey();
            BigInteger amount = entry.getValue();
            if (descriptor == null || amount == null || amount.signum() <= 0
                    || amount.compareTo(StorageChannelConstants.CAPACITY_PER_KEY) > 0) {
                continue;
            }
            storage.put(descriptor, amount);
        }
    }

    /**
     * 从二进制文件加载本适配器的全部条目。
     */
    public void loadFromFile(HyperdimensionalStorageFile file) {
        if (file == null || file.isSafeMode()) {
            return;
        }
        storage.clear();
        file.loadSection(section, getTypeByte(), codec, storage::put);
    }

    /**
     * 将本适配器的全部条目保存到二进制文件。
     */
    public void saveToFile(HyperdimensionalStorageFile file) {
        if (file == null || file.isSafeMode()) {
            return;
        }
        file.saveSection(section, getTypeByte(), codec, storage);
    }
}
