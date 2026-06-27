package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.implementations.GuiCraftConfirm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * E2c: 合成计划缺少物品置顶.
 * AE2-UEL 原生的 GuiCraftConfirm 中,visual 列表按服务端推送顺序排列,
 * 缺少的物品虽然标红,但可能夹在中间不易发现.
 * 此 Mixin 在 postUpdate 末尾对 visual 列表重新排序,将 missing > 0 的项置顶.
 */
@Mixin(value = GuiCraftConfirm.class, remap = false)
public class MixinGuiCraftConfirm {

    @Shadow
    private List<IAEItemStack> visual;

    @Shadow
    private IItemList<IAEItemStack> missing;

    @Inject(method = "postUpdate", at = @At("TAIL"))
    private void ae2enhanced$onPostUpdate(List<IAEItemStack> list, byte ref, CallbackInfo ci) {
        if (this.visual == null || this.visual.isEmpty()) {
            return;
        }
        this.visual.sort((a, b) -> {
            boolean aMissing = ae2enhanced$isMissing(a);
            boolean bMissing = ae2enhanced$isMissing(b);
            if (aMissing && !bMissing) {
                return -1;
            }
            if (!aMissing && bMissing) {
                return 1;
            }
            return 0;
        });
    }

    @Unique
    private boolean ae2enhanced$isMissing(IAEItemStack stack) {
        if (this.missing == null || stack == null) {
            return false;
        }
        IAEItemStack m = this.missing.findPrecise(stack);
        return m != null && m.getStackSize() > 0;
    }
}
