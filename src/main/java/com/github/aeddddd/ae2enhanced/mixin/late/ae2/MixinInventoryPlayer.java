package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * E2a：阻止假物品(流体/气体/源质)进入玩家背包.
 * AE2 终端在提取时可能绕过 NetworkMonitor.extractItems 拦截,
 * 直接在容器层将假物品放入背包.此处兜底拦截.
 *
 * 本 mixin 使用 AbstractNbtDrop.isDrop 进行字符串类名比较,避免直接引用
 * 条件类(ItemGasDrop / ItemEssentiaDrop)导致 NoClassDefFoundError.
 */
@Mixin(value = InventoryPlayer.class, remap = false)
public class MixinInventoryPlayer {

    private static final String FLUID_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemFluidDrop";
    private static final String GAS_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemGasDrop";
    private static final String ESSENTIA_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop";
    private static final String ENERGY_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop";
    private static final String MANA_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemManaDrop";
    private static final String STARLIGHT_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop";

    @Inject(method = "func_70441_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onAddItemStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;

        String className = stack.getItem().getClass().getName();
        if (FLUID_DROP_CLASS.equals(className)
                || GAS_DROP_CLASS.equals(className)
                || ESSENTIA_DROP_CLASS.equals(className)
                || ENERGY_DROP_CLASS.equals(className)
                || MANA_DROP_CLASS.equals(className)
                || STARLIGHT_DROP_CLASS.equals(className)) {
            cir.setReturnValue(false);
        }
    }
}
