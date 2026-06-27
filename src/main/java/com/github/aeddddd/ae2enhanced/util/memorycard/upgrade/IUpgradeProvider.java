package com.github.aeddddd.ae2enhanced.util.memorycard.upgrade;

import net.minecraft.item.ItemStack;

/**
 * 升级/augment 槽的抽象接口.
 * 无论底层是 IItemHandler、ItemStack[] 还是 Mekanism 的 TileComponentUpgrade,
 * 都通过这个接口统一操作.
 */
public interface IUpgradeProvider {

    /**
     * 升级槽的总数量.
     */
    int getSlotCount();

    /**
     * 获取指定槽位的物品(含 count).
     * @return 空物品表示该槽位无升级
     */
    ItemStack getStackInSlot(int slot);

    /**
     * 设置指定槽位的物品.
     * 传入 EMPTY 表示清除该槽位.
     */
    void setStackInSlot(int slot, ItemStack stack);

    /**
     * 清空所有槽位.
     */
    void clearSlots();
}
