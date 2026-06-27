package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.helpers.PatternHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 将 PatternHelper 中的 InventoryCrafting 在 processing 模式下从 4×4 扩展为 10×10,
 * 以支持超过 16 个输入的 processing pattern.
 */
@Mixin(value = PatternHelper.class, remap = false)
public class MixinPatternHelper {

    @Shadow
    @Final
    private boolean isCrafting;

    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "net/minecraft/inventory/InventoryCrafting",
            remap = true
        )
    )
    public InventoryCrafting onNewInventoryCrafting(Container eventHandler, int width, int height) {
        if (!this.isCrafting) {
            return new InventoryCrafting(eventHandler, 10, 10);
        }
        return new InventoryCrafting(eventHandler, width, height);
    }
}
