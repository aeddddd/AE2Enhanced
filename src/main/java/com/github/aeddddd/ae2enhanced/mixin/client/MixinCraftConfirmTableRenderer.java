package com.github.aeddddd.ae2enhanced.mixin.client;

import java.util.List;

import net.minecraft.network.chat.Component;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import com.github.aeddddd.ae2enhanced.client.specialcrafting.SpecialPlanClientCache;
import com.github.aeddddd.ae2enhanced.client.specialcrafting.SpecialPlanTooltip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 计划确认界面:样板调用信息显示增强.
 * <p>自增殖/循环链条目:行内追加发配轮次,悬停追加完整结构详情;
 * 普通处理样板条目:行内追加"调用 N 次(约 R 轮发配)"(R 按当前选中 CPU
 * 的协处理器数估算).缓存为空时零影响.</p>
 */
@Mixin(value = CraftConfirmTableRenderer.class, remap = false)
public abstract class MixinCraftConfirmTableRenderer {

    @Inject(method = "getEntryDescription", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void ae2e$appendRoundsDescription(CraftingPlanSummaryEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        var info = SpecialPlanClientCache.entryFor(entry.getWhat());
        if (info != null) {
            cir.getReturnValue().add(SpecialPlanTooltip.descriptionLine(entry.getWhat(), info));
            return;
        }
        long calls = SpecialPlanClientCache.callCountOf(entry.getWhat());
        if (calls > 0) {
            cir.getReturnValue().add(SpecialPlanTooltip.normalDescriptionLine(calls, ae2e$pushesPerRound()));
        }
    }

    @Inject(method = "getEntryTooltip", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void ae2e$appendSpecialTooltip(CraftingPlanSummaryEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        var info = SpecialPlanClientCache.entryFor(entry.getWhat());
        if (info != null) {
            cir.getReturnValue().addAll(SpecialPlanTooltip.tooltipLines(entry.getWhat(), info));
        }
    }

    /**
     * 每拍推送预算(1 + 当前选中 CPU 协处理器数);无法确定时按 1 估算.
     */
    private long ae2e$pushesPerRound() {
        var screen = ((AbstractTableRendererAccessor) this).getScreen();
        if (screen instanceof CraftConfirmScreen confirmScreen) {
            return 1L + Math.max(0, confirmScreen.getMenu().getCpuCoProcessors());
        }
        return 1L;
    }
}
