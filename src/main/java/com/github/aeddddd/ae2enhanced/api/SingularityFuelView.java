package com.github.aeddddd.ae2enhanced.api;

import net.minecraft.item.ItemStack;

/** 微型奇点燃料的只读视图。匹配物品和 metadata，忽略 NBT 与数量。 */
public final class SingularityFuelView {
    private final String recipeId;
    private final ItemStack fuelItem;
    private final int addedTicks;
    private final boolean permanent;

    public SingularityFuelView(String recipeId, ItemStack fuelItem, int addedTicks, boolean permanent) {
        this.recipeId = recipeId == null ? "" : recipeId;
        this.fuelItem = fuelItem == null ? ItemStack.EMPTY : fuelItem.copy();
        this.addedTicks = addedTicks;
        this.permanent = permanent;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public ItemStack getFuelItem() {
        return fuelItem.copy();
    }

    public int getAddedTicks() {
        return addedTicks;
    }

    public double getAddedSeconds() {
        return addedTicks / 20.0D;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
