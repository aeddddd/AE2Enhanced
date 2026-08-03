package com.github.aeddddd.ae2enhanced.memorycard.upgrade;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;

/**
 * 将 AE2 的 InternalInventory(含 IUpgradeInventory)适配为 IUpgradeProvider.
 * 1.20 AE2 设备/部件的升级槽统一为 {@link appeng.api.upgrades.IUpgradeableObject#getUpgrades()},
 * 返回的 IUpgradeInventory 继承 InternalInventory.
 */
public class AE2UpgradeInventoryAdapter implements IUpgradeProvider {

    private final InternalInventory inventory;

    public AE2UpgradeInventoryAdapter(InternalInventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public int getSlotCount() {
        return inventory.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        inventory.setItemDirect(slot, stack);
    }

    @Override
    public void clearSlots() {
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setItemDirect(i, ItemStack.EMPTY);
        }
    }
}
