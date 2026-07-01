package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

/**
 * 超维度仓储的单一通道抽象。
 * <p>每个通道负责一种 AE key type（如物品、流体、能量）的存储与读写。</p>
 *
 * @param <T> 该通道使用的具体 AE key 类型
 */
public interface StorageChannel<T extends AEKey> {

    /**
     * @return 该通道对应的 AE key type
     */
    AEKeyType getKeyType();

    /**
     * 向通道存入指定数量的 key。
     *
     * @param what   要存入的 key
     * @param amount 请求存入的数量
     * @param mode   是否真正执行
     * @return 实际可存入的数量（不超过 {@link Long#MAX_VALUE}）
     */
    long insert(AEKey what, long amount, Actionable mode);

    /**
     * 从通道取出指定数量的 key。
     *
     * @param what   要取出的 key
     * @param amount 请求取出的数量
     * @param mode   是否真正执行
     * @return 实际可取出的数量（不超过 {@link Long#MAX_VALUE}）
     */
    long extract(AEKey what, long amount, Actionable mode);

    /**
     * 将当前可用内容写入 AE2 网络统计容器。
     *
     * @param out 输出容器
     */
    void getAvailableStacks(KeyCounter out);

    /**
     * @return 当前通道的内容快照（key 到数量的映射）
     */
    Map<AEKey, java.math.BigInteger> getContents();

    /**
     * 返回当前通道内容的序列化视图（key 到数量的映射）。
     * <p>与 {@link #getContents()} 行为一致，专门用于二进制文件序列化。</p>
     */
    Map<AEKey, java.math.BigInteger> getEntries();

    /**
     * 从给定的数据映射加载通道内容。
     * <p>会先清空当前内容，再写入所有有效条目。</p>
     *
     * @param data key 到数量的映射
     */
    void loadFrom(Map<AEKey, java.math.BigInteger> data);

    /**
     * 将当前通道内容持久化到 NBT。
     *
     * @param tag 输出 NBT 标签
     */
    void persist(CompoundTag tag);

    /**
     * 从 NBT 加载通道内容。
     *
     * @param tag 输入 NBT 标签
     */
    void load(CompoundTag tag);
}
