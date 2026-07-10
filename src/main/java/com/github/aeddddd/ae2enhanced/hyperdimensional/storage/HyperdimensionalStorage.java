package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.AbstractStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.FluidStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.GenericStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.ItemStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannel;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 超维度仓储的内部存储容器。
 * <p>以多个 {@link StorageChannel} 管理不同 AE key type：默认支持物品、流体与内部能量，
 * 并动态为游戏中所有已注册的 AEKeyType（包括第三方模组注册的类型）创建通用通道。
 * 实际通道数据通过 {@link HyperdimensionalStorageFile} 持久化为二进制文件，NBT 中不再嵌入完整内容。</p>
 */
public class HyperdimensionalStorage {

    private static final String TAG_VERSION = "version";
    private static final int PERSIST_VERSION = 1;

    private final UUID nexusId;
    private final HyperdimensionalStorageFile file;
    private final Map<AEKeyType, StorageChannel<?>> channels = new HashMap<>();
    private final List<StorageListener> listeners = new ArrayList<>();
    private final Consumer<HyperdimensionalStorage> changeCallback;

    private KeyCounter availableStacksCache = null;
    private volatile boolean cacheValid = false;

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

        // 注册内置物品、流体、能量通道
        registerChannel(new ItemStorageChannel());
        registerChannel(new FluidStorageChannel());
        registerChannel(new EnergyStorageChannel());

        // 动态为所有已注册的 AEKeyType 创建通用通道（不注册新的 AEKeyType）
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            if (keyType.equals(AEKeyType.items()) || keyType.equals(AEKeyType.fluids())) {
                continue; // 已由专用通道处理
            }
            registerChannel(new GenericStorageChannel(keyType));
        }

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
     *
     * @param channel 要注册的通道
     */
    public void registerChannel(StorageChannel<?> channel) {
        channels.put(channel.getKeyType(), channel);
        invalidateCache();
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
            markDirty(what.getType());
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
            markDirty(what.getType());
        }
        return actual;
    }

    /**
     * 将各通道可用内容写入统计容器。
     * <p>结果会被内部缓存，避免每次网络同步都遍历全量条目；缓存会在存储变化时自动失效。</p>
     */
    public void getAvailableStacks(KeyCounter out) {
        rebuildCacheIfNeeded();
        if (availableStacksCache != null) {
            out.addAll(availableStacksCache);
        }
    }

    /**
     * 强制重建可用内容缓存。
     */
    public void rebuildCache() {
        availableStacksCache = new KeyCounter();
        for (StorageChannel<?> channel : channels.values()) {
            // 能量为内部自定义 key type，不直接暴露给 AE2 网络
            if (EnergyKey.ENERGY_KEY_TYPE.equals(channel.getKeyType())) {
                continue;
            }
            channel.getAvailableStacks(availableStacksCache);
        }
        cacheValid = true;
    }

    private void rebuildCacheIfNeeded() {
        if (!cacheValid || availableStacksCache == null) {
            rebuildCache();
        }
    }

    private void invalidateCache() {
        cacheValid = false;
    }

    /**
     * 按查询字符串过滤当前可用内容，返回匹配条目的快照。
     * <p>查询匹配不区分大小写，支持本地化名称与 ID 字符串。</p>
     *
     * @param query 查询字符串（可为 null 或空，表示返回全部）
     * @return 匹配条目列表，每个条目包含 key 与数量
     */
    public List<SearchEntry> searchAvailableStacks(@Nullable String query) {
        rebuildCacheIfNeeded();
        List<SearchEntry> result = new ArrayList<>();
        if (availableStacksCache == null) {
            return result;
        }
        String normalized = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT).trim();
        for (var entry : availableStacksCache) {
            AEKey key = entry.getKey();
            long count = entry.getLongValue();
            if (count <= 0) {
                continue;
            }
            if (normalized.isEmpty()
                    || key.getDisplayName().getString().toLowerCase(java.util.Locale.ROOT).contains(normalized)
                    || key.getId().toString().toLowerCase(java.util.Locale.ROOT).contains(normalized)) {
                result.add(new SearchEntry(key, count));
            }
        }
        return result;
    }

    /**
     * 将当前可用内容按每页固定大小分页返回。
     *
     * @param page     页码（从 0 开始）
     * @param pageSize 每页大小
     * @return 指定页的搜索条目
     */
    public List<SearchEntry> getAvailableStacksPaged(int page, int pageSize) {
        List<SearchEntry> all = searchAvailableStacks(null);
        if (pageSize <= 0) {
            return all;
        }
        int from = page * pageSize;
        if (from < 0 || from >= all.size()) {
            return new ArrayList<>();
        }
        int to = Math.min(from + pageSize, all.size());
        return all.subList(from, to);
    }

    /**
     * 搜索条目记录。
     *
     * @param key   AE key
     * @param count 数量（上限为 {@link Long#MAX_VALUE}）
     */
    public record SearchEntry(AEKey key, long count) {
    }

    /**
     * 直接设置指定 key 的数量（用于迁移与初始化）。
     */
    public void set(AEKey key, BigInteger amount) {
        if (isSafeMode() || key == null) {
            return;
        }
        StorageChannel<?> channel = channels.get(key.getType());
        if (channel instanceof AbstractStorageChannel<?, ?> abstractChannel) {
            abstractChannel.set(key, amount);
            markDirty(key.getType());
        }
    }

    public void addListener(StorageListener listener) {
        listeners.add(Objects.requireNonNull(listener));
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
            if (channel instanceof AbstractStorageChannel<?, ?> abstractChannel) {
                abstractChannel.loadFromFile(file);
            }
        }
        invalidateCache();
    }

    /**
     * 将各通道数据持久化到二进制文件。
     * <p>每个 section 的保存由异步线程完成，且仅在成功后才清除对应的脏代际；
     * 本方法不再在末尾统一调用 {@code markClean()}，避免保存失败时丢失数据。</p>
     */
    public void persist() {
        if (file == null || isSafeMode()) {
            return;
        }
        for (StorageChannel<?> channel : channels.values()) {
            if (channel instanceof AbstractStorageChannel<?, ?> abstractChannel) {
                AEKeyType keyType = abstractChannel.getAdapter().getKeyType();
                if (file.getDirtyGeneration(keyType) > 0) {
                    abstractChannel.saveToFile(file);
                }
            }
        }
        // 注意：不再无条件 markClean()，各 section 的干净状态由 HyperdimensionalStorageFile 在异步保存成功后自行维护。
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
        return file != null && file.isDirty();
    }

    public void markClean() {
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
        invalidateCache();
    }

    private void markDirty(AEKeyType type) {
        if (file != null) {
            file.markDirty(type);
        }
        invalidateCache();
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
