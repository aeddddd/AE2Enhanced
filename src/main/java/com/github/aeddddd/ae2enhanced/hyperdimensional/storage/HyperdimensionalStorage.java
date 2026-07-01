package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.AbstractStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.FluidStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.ItemStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannel;

/**
 * 超维度仓储的内部存储容器。
 * <p>以多个 {@link StorageChannel} 管理不同 AE key type，默认支持物品、流体与内部能量，
 * 并预留第三方通道的注册接口。实际通道数据通过 {@link HyperdimensionalStorageFile}
 * 持久化为二进制文件，NBT 中不再嵌入完整内容。</p>
 */
public class HyperdimensionalStorage {

    private static final String TAG_VERSION = "version";
    private static final int PERSIST_VERSION = 1;

    private final UUID nexusId;
    private final HyperdimensionalStorageFile file;
    private final Map<AEKeyType, StorageChannel<?>> channels = new HashMap<>();
    private final List<StorageListener> listeners = new ArrayList<>();
    private final Consumer<HyperdimensionalStorage> changeCallback;

    private boolean dirty = false;

    public HyperdimensionalStorage(UUID nexusId) {
        this(nexusId, null, null);
    }

    public HyperdimensionalStorage(UUID nexusId, @Nullable Consumer<HyperdimensionalStorage> changeCallback) {
        this(nexusId, null, changeCallback);
    }

    public HyperdimensionalStorage(UUID nexusId, @Nullable HyperdimensionalStorageFile file) {
        this(nexusId, file, null);
    }

    public HyperdimensionalStorage(UUID nexusId, @Nullable HyperdimensionalStorageFile file,
            @Nullable Consumer<HyperdimensionalStorage> changeCallback) {
        this.nexusId = nexusId;
        this.file = file;
        this.changeCallback = changeCallback;
        // 默认注册物品、流体与能量通道
        registerChannel(new ItemStorageChannel());
        registerChannel(new FluidStorageChannel());
        registerChannel(new EnergyStorageChannel());
        loadFromFile();
    }

    public UUID getNexusId() {
        return nexusId;
    }

    /**
     * @return 是否处于安全模式（只读）
     */
    public boolean isSafeMode() {
        return file != null && file.isSafeMode();
    }

    /**
     * 注册一个新的存储通道。如果该 key type 已存在，将覆盖旧通道。
     * <p>此方法为后续通过反射加载第三方通道（mana、starlight、gas、essentia 等）预留。</p>
     *
     * @param channel 要注册的通道
     */
    public void registerChannel(StorageChannel<?> channel) {
        channels.put(channel.getKeyType(), channel);
    }

    /**
     * @param keyType AE key type
     * @return 对应通道，若未注册则返回 null
     */
    @Nullable
    public StorageChannel<?> getChannel(AEKeyType keyType) {
        return channels.get(keyType);
    }

    /**
     * @return 只读的通道映射
     */
    public Map<AEKeyType, StorageChannel<?>> getChannels() {
        return Collections.unmodifiableMap(channels);
    }

    /**
     * 聚合所有通道的内容快照。
     *
     * @return 所有 key 到数量的不可修改映射
     */
    public Map<AEKey, BigInteger> getContents() {
        Map<AEKey, BigInteger> result = new HashMap<>();
        for (StorageChannel<?> channel : channels.values()) {
            result.putAll(channel.getContents());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 向对应通道存入 key。
     *
     * @return 实际存入数量
     */
    public long insert(AEKey what, long amount, Actionable mode) {
        if (isSafeMode() || what == null || amount <= 0) {
            return 0;
        }
        StorageChannel<?> channel = channels.get(what.getType());
        if (channel == null) {
            return 0;
        }
        long actual = channel.insert(what, amount, mode);
        if (actual > 0 && mode == Actionable.MODULATE) {
            markDirty();
        }
        return actual;
    }

    /**
     * 从对应通道取出 key。
     *
     * @return 实际取出数量
     */
    public long extract(AEKey what, long amount, Actionable mode) {
        if (isSafeMode() || what == null || amount <= 0) {
            return 0;
        }
        StorageChannel<?> channel = channels.get(what.getType());
        if (channel == null) {
            return 0;
        }
        long actual = channel.extract(what, amount, mode);
        if (actual > 0 && mode == Actionable.MODULATE) {
            markDirty();
        }
        return actual;
    }

    /**
     * 将各通道可用内容写入统计容器。
     */
    public void getAvailableStacks(KeyCounter out) {
        for (StorageChannel<?> channel : channels.values()) {
            channel.getAvailableStacks(out);
        }
    }

    /**
     * 直接设置指定 key 的数量（用于迁移与初始化）。
     */
    public void set(AEKey key, BigInteger amount) {
        if (isSafeMode() || key == null) {
            return;
        }
        StorageChannel<?> channel = channels.get(key.getType());
        if (channel instanceof AbstractStorageChannel<?> abstractChannel) {
            setInternal(abstractChannel, key, amount);
            markDirty();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends AEKey> void setInternal(AbstractStorageChannel<T> channel, AEKey key, BigInteger amount) {
        channel.set((T) key, amount);
    }

    public void addListener(StorageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StorageListener listener) {
        listeners.remove(listener);
    }

    /**
     * 从二进制文件加载各通道数据。
     */
    public void loadFromFile() {
        if (file == null || isSafeMode()) {
            return;
        }
        for (StorageChannel<?> channel : channels.values()) {
            if (channel instanceof AbstractStorageChannel<?> abstractChannel) {
                Map<AEKey, BigInteger> loaded = new HashMap<>();
                file.loadChannel(channel.getKeyType(), loaded::put);
                abstractChannel.loadFrom(loaded);
            }
        }
    }

    /**
     * 将各通道数据持久化到二进制文件。
     */
    public void persist() {
        if (file == null || isSafeMode()) {
            return;
        }
        for (StorageChannel<?> channel : channels.values()) {
            file.saveChannel(channel.getKeyType(), channel.getEntries());
        }
        markClean();
    }

    /**
     * 若数据已脏，则执行持久化。
     */
    public void flush() {
        if (isDirty()) {
            persist();
        }
    }

    public boolean isDirty() {
        return dirty || (file != null && file.isDirty());
    }

    public void markClean() {
        dirty = false;
        if (file != null) {
            file.markClean();
        }
    }

    /**
     * 将多通道元数据持久化到 NBT。
     * <p>实际通道数据由 {@link HyperdimensionalStorageFile} 写入二进制文件，
     * 本方法仅保留版本等少量元数据，用于兼容 SavedData 调用。</p>
     *
     * @param tag 输出标签
     */
    public void persist(CompoundTag tag) {
        tag.putInt(TAG_VERSION, PERSIST_VERSION);
    }

    /**
     * 从 NBT 加载多通道元数据。
     * <p>通道数据已从二进制文件加载，本方法仅做版本校验。</p>
     *
     * @param tag 输入标签
     */
    public void load(CompoundTag tag) {
        // 二进制文件负责加载实际数据；NBT 仅保留版本标记。
    }

    private void markDirty() {
        dirty = true;
        if (file != null) {
            file.markDirty();
        }
        for (StorageListener listener : listeners) {
            listener.onStorageChanged(this);
        }
        if (changeCallback != null) {
            changeCallback.accept(this);
        }
    }

    /**
     * 存储变化监听器。
     */
    public interface StorageListener {
        void onStorageChanged(HyperdimensionalStorage storage);
    }
}
