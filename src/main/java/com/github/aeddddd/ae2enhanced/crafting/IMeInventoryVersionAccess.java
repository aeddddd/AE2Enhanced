package com.github.aeddddd.ae2enhanced.crafting;

/** MECraftingInventory 注入版本号的普通 Java SPI。 */
public interface IMeInventoryVersionAccess {

    long ae2e$getInjectVersion();
    void ae2e$bumpInjectVersion();
}
