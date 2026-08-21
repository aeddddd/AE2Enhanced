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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 {@link MECraftingInventory} 附加修改版本号.
 *
 * <p>用途：canCraft 失败缓存（{@code MixinCraftingCPUClusterCanCraft}）以版本号判定
 * CPU 本地库存是否变化——版本未变则 canCraft 结果必然不变（canCraft 是
 * (details, 本地库存) 的纯函数，不访问网络库存/能量/medium 状态）。</p>
 *
 * <p>覆盖的写入口：injectItems/extractItems 的 MODULATE 分支、ignore()。
 * 直接操作 getItemList() 的写入（如批量结算的产物注入）由写入方显式 bump。</p>
 */
@Mixin(value = MECraftingInventory.class, remap = false)
public class MixinMECraftingInventory implements IMeInventoryVersionAccess {

    @Unique
    private long ae2e$modVersion;

    @Override
    public long ae2e$getModVersion() {
        return ae2e$modVersion;
    }

    @Override
    public void ae2e$bumpVersion() {
        ae2e$modVersion++;
    }

    @Inject(method = "injectItems", at = @At("RETURN"), require = 0)
    private void ae2e$onInject(IAEItemStack input, Actionable mode, IActionSource src,
            CallbackInfoReturnable<IAEItemStack> cir) {
        if (mode == Actionable.MODULATE && input != null) {
            ae2e$modVersion++;
        }
    }

    @Inject(method = "extractItems", at = @At("RETURN"), require = 0)
    private void ae2e$onExtract(IAEItemStack request, Actionable mode, IActionSource src,
            CallbackInfoReturnable<IAEItemStack> cir) {
        // 仅实际提取到物品才算内容变化
        if (mode == Actionable.MODULATE && cir.getReturnValue() != null) {
            ae2e$modVersion++;
        }
    }

    @Inject(method = "ignore", at = @At("RETURN"), require = 0)
    private void ae2e$onIgnore(IAEItemStack what, CallbackInfo ci) {
        ae2e$modVersion++;
    }
}
