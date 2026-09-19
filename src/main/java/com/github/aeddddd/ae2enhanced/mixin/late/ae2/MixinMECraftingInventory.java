package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.MECraftingInventory;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IMeInventoryVersionAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 {@link MECraftingInventory} 附加注入版本号.
 * canCraft 失败缓存用它判定缺料状态是否可能翻转: canCraft 是 (details, 本地库存) 的纯函数,
 * false -> true 翻转的唯一途径是注入事件, 所以版本号只在 injectItems 的 MODULATE 分支递增.
 * 直接操作 getItemList() 产物入栏的批量结算由写入方显式 bump.
 */
@Mixin(value = MECraftingInventory.class, remap = false)
public class MixinMECraftingInventory implements IMeInventoryVersionAccess {

    @Unique
    private long ae2e$injectVersion;

    @Override
    public long ae2e$getInjectVersion() {
        return ae2e$injectVersion;
    }

    @Override
    public void ae2e$bumpInjectVersion() {
        ae2e$injectVersion++;
    }

    @Inject(method = "injectItems", at = @At("RETURN"), require = 0)
    private void ae2e$onInject(IAEItemStack input, Actionable mode, IActionSource src,
            CallbackInfoReturnable<IAEItemStack> cir) {
        if (mode == Actionable.MODULATE && input != null) {
            ae2e$injectVersion++;
        }
    }
}
