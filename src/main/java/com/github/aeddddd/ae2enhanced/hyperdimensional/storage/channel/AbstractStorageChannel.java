package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用存储通道实现，提供基于 {@link BigInteger} 的 insert/extract/统计逻辑。
 * <p>子类只需实现 key 的序列化/反序列化与类型检查。</p>
 *
 * @param <T> 该通道使用的具体 AE key 类型
 */
public abstract class AbstractStorageChannel<T extends AEKey> implements StorageChannel<T> {

    private static final String TAG_CONTENTS = "contents";
    private static final String TAG_KEY = "key";
    private static final String TAG_AMOUNT = "amount";

    protected final Map<T, BigInteger> contents = new HashMap<>();

    /**
     * 将 AE key 转换为具体类型。
     *
     * @param key 输入 key
     * @return 若类型匹配则返回转换后的 key，否则返回 null
     */
    @Nullable
    protected abstract T cast(AEKey key);

    /**
     * 将 key 序列化为 NBT。
     *
     * @param key key
     * @return NBT 标签
     */
    protected abstract CompoundTag writeKey(T key);

    /**
     * 从 NBT 反序列化 key。
     *
     * @param tag NBT 标签
     * @return key，若解析失败可返回 null
     */
    @Nullable
    protected abstract T readKey(CompoundTag tag);

    @Override
    public long insert(AEKey what, long amount, Actionable mode) {
        if (amount <= 0 || what == null) {
            return 0;
        }
        T key = cast(what);
        if (key == null) {
            return 0;
        }
        BigInteger current = contents.getOrDefault(key, BigInteger.ZERO);
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
            contents.remove(key);
        } else {
            contents.put(key, next);
        }
        return actual.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode) {
        if (amount <= 0 || what == null) {
            return 0;
        }
        T key = cast(what);
        if (key == null) {
            return 0;
        }
        BigInteger current = contents.getOrDefault(key, BigInteger.ZERO);
        BigInteger actual = BigInteger.valueOf(amount).min(current);
        if (actual.signum() <= 0) {
            return 0;
        }
        if (mode == Actionable.SIMULATE) {
            return actual.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        }
        BigInteger next = current.subtract(actual);
        if (next.signum() == 0) {
            contents.remove(key);
        } else {
            contents.put(key, next);
        }
        return actual.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (Map.Entry<T, BigInteger> entry : contents.entrySet()) {
            BigInteger amount = entry.getValue();
            if (amount.signum() > 0) {
                long visible = amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
                out.add(entry.getKey(), visible);
            }
        }
    }

    @Override
    public Map<AEKey, BigInteger> getContents() {
        return Collections.unmodifiableMap(new HashMap<>(contents));
    }

    /**
     * 直接设置指定 key 的数量（用于迁移/初始化）。
     */
    public void set(T key, BigInteger amount) {
        if (amount == null || amount.signum() <= 0
                || amount.compareTo(StorageChannelConstants.CAPACITY_PER_KEY) > 0) {
            contents.remove(key);
        } else {
            contents.put(key, amount);
        }
    }

    @Override
    public Map<AEKey, BigInteger> getEntries() {
        return getContents();
    }

    @Override
    public void loadFrom(Map<AEKey, BigInteger> data) {
        contents.clear();
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
            contents.put(key, amount);
        }
    }

    @Override
    public void persist(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<T, BigInteger> entry : contents.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.put(TAG_KEY, writeKey(entry.getKey()));
            item.putString(TAG_AMOUNT, entry.getValue().toString());
            list.add(item);
        }
        tag.put(TAG_CONTENTS, list);
    }

    @Override
    public void load(CompoundTag tag) {
        contents.clear();
        if (!tag.contains(TAG_CONTENTS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(TAG_CONTENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains(TAG_KEY, Tag.TAG_COMPOUND) || !entry.contains(TAG_AMOUNT, Tag.TAG_STRING)) {
                continue;
            }
            T key = readKey(entry.getCompound(TAG_KEY));
            if (key == null) {
                continue;
            }
            BigInteger amount = new BigInteger(entry.getString(TAG_AMOUNT));
            if (amount.signum() > 0) {
                contents.put(key, amount);
            }
        }
    }
}
