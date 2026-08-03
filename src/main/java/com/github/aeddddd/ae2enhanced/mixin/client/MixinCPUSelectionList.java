package com.github.aeddddd.ae2enhanced.mixin.client;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.core.localization.Tooltips;
import appeng.menu.me.crafting.CraftingStatusMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 CPU 选择列表按钮上的存储容量直接显示.
 * <p>原生 {@code formatStorage} 只做 {@code storage / 1024 + "k"},
 * 面对超大虚拟 CPU 存储（如 Long.MAX_VALUE）会显示一整行裸数字；
 * 虚拟 CPU 的无限存储（Long.MAX_VALUE）直接显示为 ∞,
 * 其余数值与 tooltip 一致的字节格式化（依赖 MixinTooltips 修正后的 BYTE_NUMS）.</p>
 * <p>兼容 NeoECOAE:其 {@code formatStorage} RETURN 处理器会把 ≥1GB 的存储
 * （含 Long.MAX_VALUE）覆写为 "9.2E".{@code priority = 900} 使本 mixin 的
 * RETURN 处理器在其之后执行,拿回 ∞ 的最终决定权.</p>
 */
@Mixin(value = CPUSelectionList.class, priority = 900, remap = false)
public class MixinCPUSelectionList {

    @Inject(method = "formatStorage", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$formatStorage(CraftingStatusMenu.CraftingCpuListEntry cpu,
            CallbackInfoReturnable<String> cir) {
        if (cpu.storage() == Long.MAX_VALUE) {
            cir.setReturnValue("∞");
            return;
        }
        var amount = Tooltips.getByteAmount(cpu.storage());
        cir.setReturnValue(amount.digit() + amount.unit());
    }

    /**
     * 兼容 NeoECOAE:本 mixin 优先级 900 低于其默认 1000,
     * RETURN 处理器在其覆写之后执行.
     */
    @Inject(method = "formatStorage", at = @At("RETURN"), cancellable = true, remap = false)
    private void ae2e$formatStorageAfterEco(CraftingStatusMenu.CraftingCpuListEntry cpu,
            CallbackInfoReturnable<String> cir) {
        if (cpu.storage() == Long.MAX_VALUE) {
            cir.setReturnValue("∞");
        }
    }
}
