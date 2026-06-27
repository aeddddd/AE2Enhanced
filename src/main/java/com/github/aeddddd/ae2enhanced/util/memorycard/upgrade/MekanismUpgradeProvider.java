package com.github.aeddddd.ae2enhanced.util.memorycard.upgrade;

import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;

/**
 * 将 Mekanism 的 TileComponentUpgrade 适配为 IUpgradeProvider.
 *
 * Mekanism 的升级系统是基于 Upgrade 枚举的 map(类型 → 数量),
 * 每个 Upgrade 类型对应一个 ItemStack.
 * 我们通过 IUpgradeProvider 的 slot 索引映射到 Upgrade 类型数组的索引.
 */
public class MekanismUpgradeProvider implements IUpgradeProvider {

    private final Object component;
    private final Object[] upgradeTypes;
    private final Method getUpgrades;
    private final Method addUpgrade;
    private final Method removeUpgrade;
    private final Method getStack;

    public MekanismUpgradeProvider(Object component, Object[] upgradeTypes,
                                   Method getUpgrades, Method addUpgrade,
                                   Method removeUpgrade, Method getStack) {
        this.component = component;
        this.upgradeTypes = upgradeTypes;
        this.getUpgrades = getUpgrades;
        this.addUpgrade = addUpgrade;
        this.removeUpgrade = removeUpgrade;
        this.getStack = getStack;
    }

    @Override
    public int getSlotCount() {
        return upgradeTypes != null ? upgradeTypes.length : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (upgradeTypes == null || slot < 0 || slot >= upgradeTypes.length) return ItemStack.EMPTY;
        try {
            Object type = upgradeTypes[slot];
            int count = (Integer) getUpgrades.invoke(component, type);
            if (count <= 0) return ItemStack.EMPTY;
            ItemStack stack = ((ItemStack) getStack.invoke(type)).copy();
            stack.setCount(count);
            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (upgradeTypes == null || slot < 0 || slot >= upgradeTypes.length) return;
        try {
            Object type = upgradeTypes[slot];
            int current = (Integer) getUpgrades.invoke(component, type);
            int target = stack.isEmpty() ? 0 : stack.getCount();
            while (current < target) {
                addUpgrade.invoke(component, type);
                current++;
            }
            while (current > target) {
                removeUpgrade.invoke(component, type);
                current--;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void clearSlots() {
        for (int i = 0; i < getSlotCount(); i++) {
            setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
