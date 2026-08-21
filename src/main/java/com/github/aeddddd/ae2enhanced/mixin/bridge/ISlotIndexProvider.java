package com.github.aeddddd.ae2enhanced.mixin.bridge;

import net.minecraft.item.ItemStack;

/**
 * 槽位索引提供者（MMCE IItemHandlerImpl 的 duck 接口）.
 *
 * <p>AE2 侧 {@code MixinAdaptorItemHandler} 通过该接口查询候选插入槽位,
 * 避免对不支持索引的 IItemHandler 产生任何依赖与类加载风险（instanceof 判定）。</p>
 */
public interface ISlotIndexProvider {

    /** 标记槽位索引脏（任何槽位写入后调用）. */
    void ae2e$markSlotIndexDirty();

    /**
     * 查询可能接受该物品的候选槽位（升序）：
     * 同 (Item, meta, NBT) 的可堆叠非空 inSlots ∪ 空 inSlots。
     * 候选是"原逻辑可能接受"的精确超集,调用方必须逐个 insertItem 复验。
     *
     * @return 候选槽位数组;返回 null 表示索引不适用（如 allowAnySlots 模式）,调用方回退全槽扫描
     */
    int[] ae2e$getInsertCandidates(ItemStack stack);
}
