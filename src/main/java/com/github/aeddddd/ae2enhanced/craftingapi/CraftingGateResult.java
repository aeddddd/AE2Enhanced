package com.github.aeddddd.ae2enhanced.craftingapi;

import appeng.api.storage.data.IAEItemStack;

import javax.annotation.Nullable;

/** CPU 注入门控结果。 */
public class CraftingGateResult {

    public final boolean handled;
    @Nullable
    public final IAEItemStack leftover;

    public CraftingGateResult(boolean handled, @Nullable IAEItemStack leftover) {
        this.handled = handled;
        this.leftover = leftover;
    }
}
