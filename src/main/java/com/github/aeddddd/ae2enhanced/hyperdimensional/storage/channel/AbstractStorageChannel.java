package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter.AbstractStorageAdapter;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.Descriptor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用存储通道实现,作为 {@link StorageChannel} 的适配器支持 facade.
 * <p>所有核心逻辑均委托给 {@link AbstractStorageAdapter},本子类仅负责暴露
 * 适配器能力并保留 NBT 序列化接口（用于兼容旧版 WorldSavedData 调用）.</p>
 *
 * @param <T> 该通道使用的具体 AE key 类型
 * @param <D> 该通道使用的描述符类型
 */
public abstract class AbstractStorageChannel<T extends AEKey, D extends Descriptor> implements StorageChannel<T> {

    private static final String TAG_CONTENTS = "contents";
    private static final String TAG_KEY = "key";
    private static final String TAG_AMOUNT = "amount";

    protected final AbstractStorageAdapter<T, D> adapter;

    protected AbstractStorageChannel(AbstractStorageAdapter<T, D> adapter) {
        this.adapter = adapter;
    }

    /**
     * 获取底层存储适配器.
     */
    public AbstractStorageAdapter<T, D> getAdapter() {
        return adapter;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode) {
        return adapter.insert(what, amount, mode);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode) {
        return adapter.extract(what, amount, mode);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        adapter.getAvailableStacks(out);
    }

    @Override
    public Map<AEKey, BigInteger> getContents() {
        return adapter.getEntries();
    }

    @Override
    public Map<AEKey, BigInteger> getEntries() {
        return adapter.getEntries();
    }

    /**
     * 直接设置指定 key 的数量（用于迁移/初始化）.
     */
    public void set(AEKey key, BigInteger amount) {
        T typedKey = adapter.cast(key);
        if (typedKey == null) {
            return;
        }
        adapter.set(typedKey, amount);
    }

    @Override
    public void loadFrom(Map<AEKey, BigInteger> data) {
        adapter.loadFrom(data);
    }

    /**
     * 从二进制文件加载通道内容.
     */
    public void loadFromFile(HyperdimensionalStorageFile file) {
        adapter.loadFromFile(file);
    }

    /**
     * 将通道内容保存到二进制文件.
     */
    public void saveToFile(HyperdimensionalStorageFile file) {
        adapter.saveToFile(file);
    }

    @Override
    public void persist(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<D, BigInteger> entry : adapter.getStorageMap().entrySet()) {
            CompoundTag item = new CompoundTag();
            // 必须写 toTagGeneric(含 #c 类型标记),与 load 的 AEKey.fromTagGeneric 对称;
            // 直接写 descriptor.toNBT() 会丢类型标记,导致回读时条目被静默丢弃
            item.put(TAG_KEY, entry.getKey().getAEKey().toTagGeneric());
            item.putString(TAG_AMOUNT, entry.getValue().toString());
            list.add(item);
        }
        tag.put(TAG_CONTENTS, list);
    }

    @Override
    public void load(CompoundTag tag) {
        if (!tag.contains(TAG_CONTENTS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(TAG_CONTENTS, Tag.TAG_COMPOUND);
        Map<AEKey, BigInteger> data = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains(TAG_KEY, Tag.TAG_COMPOUND) || !entry.contains(TAG_AMOUNT, Tag.TAG_STRING)) {
                continue;
            }
            AEKey key = AEKey.fromTagGeneric(entry.getCompound(TAG_KEY));
            if (key == null && adapter.getKeyType() == EnergyKey.ENERGY_KEY_TYPE) {
                // 能量 key type 刻意不注册进 AE2 注册表,fromTagGeneric 无法解析,
                // 能量为单例,按通道类型兜底
                key = EnergyKey.INSTANCE;
            }
            if (key == null) {
                continue;
            }
            BigInteger amount = new BigInteger(entry.getString(TAG_AMOUNT));
            if (amount.signum() > 0) {
                data.put(key, amount);
            }
        }
        loadFrom(data);
    }
}
