package com.github.aeddddd.ae2enhanced.memorycard.upgrade;

import net.minecraft.world.item.ItemStack;

/**
 * 升级槽的抽象接口.
 * 无论底层是 Forge IItemHandler 还是 AE2 的 InternalInventory/IUpgradeInventory,
 * 都通过这个接口统一操作(第三方 Mod 升级组件由后续任务通过适配器接入).
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
