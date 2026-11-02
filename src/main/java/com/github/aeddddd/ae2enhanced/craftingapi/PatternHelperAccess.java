package com.github.aeddddd.ae2enhanced.craftingapi;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;

import javax.annotation.Nullable;

/** PatternHelper 配方剩余物访问端口。 */
public interface PatternHelperAccess {

    @Nullable
    IRecipe ae2enhanced$standardRecipe();

    @Nullable
    InventoryCrafting ae2enhanced$craftingTemplate();
}
