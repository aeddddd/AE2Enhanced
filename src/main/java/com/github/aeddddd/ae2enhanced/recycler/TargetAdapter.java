package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 回收目标适配器接口.
 */
public interface TargetAdapter {

    /**
     * 扫描目标并返回可提取的物品列表.
     *
     * @param simulate 是否仅模拟
     * @return 可提取的物品列表
     */
    @Nonnull
    List<ItemStack> scan(boolean simulate);

    /**
     * 提取指定数量的某种物品.
     *
     * @param requested 请求的物品类型与数量
     * @param simulate  是否仅模拟
     * @return 实际提取的物品堆叠
     */
    @Nullable
    ItemStack extract(@Nonnull IAEItemStack requested, boolean simulate);

    /**
     * 释放资源引用.
     */
    default void invalidate() {
    }
}
