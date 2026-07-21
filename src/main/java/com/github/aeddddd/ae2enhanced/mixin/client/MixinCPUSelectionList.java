package com.github.aeddddd.ae2enhanced.mixin.client;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.core.localization.Tooltips;
import appeng.menu.me.crafting.CraftingStatusMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 CPU 选择列表按钮上的存储容量直接显示。
 * <p>原生 {@code formatStorage} 只做 {@code storage / 1024 + "k"}，
 * 面对超大虚拟 CPU 存储（如 Long.MAX_VALUE）会显示一整行裸数字；
 * 改为与 tooltip 一致的字节格式化（依赖 MixinTooltips 修正后的 BYTE_NUMS）。</p>
 */
@Mixin(value = CPUSelectionList.class, remap = false)
public class MixinCPUSelectionList {

    @Inject(method = "formatStorage", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$formatStorage(CraftingStatusMenu.CraftingCpuListEntry cpu,
            CallbackInfoReturnable<String> cir) {
        var amount = Tooltips.getByteAmount(cpu.storage());
        cir.setReturnValue(amount.digit() + amount.unit());
    }
}
