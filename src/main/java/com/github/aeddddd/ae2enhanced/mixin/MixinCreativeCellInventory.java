package com.github.aeddddd.ae2enhanced.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 将创造 ME 存储元件（物品/流体）在终端中的显示数量上限
 * 从 Integer.MAX_VALUE（2.1G）提升到 Long.MAX_VALUE（9.2E）。
 */
@Mixin(targets = "appeng.me.cells.CreativeCellInventory", remap = false)
public class MixinCreativeCellInventory {

    @Redirect(method = "getAvailableStacks",
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/stacks/KeyCounter;add(Lappeng/api/stacks/AEKey;J)V"),
            remap = false)
    private void ae2e$reportLongMax(KeyCounter out, AEKey key, long amount) {
        out.add(key, Long.MAX_VALUE);
    }
}
