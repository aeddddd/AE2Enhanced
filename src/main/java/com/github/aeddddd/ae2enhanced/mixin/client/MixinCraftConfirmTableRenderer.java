package com.github.aeddddd.ae2enhanced.mixin.client;

import java.util.List;

import net.minecraft.network.chat.Component;

import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import com.github.aeddddd.ae2enhanced.client.specialcrafting.SpecialPlanClientCache;
import com.github.aeddddd.ae2enhanced.client.specialcrafting.SpecialPlanTooltip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 计划确认界面:自增殖/循环链条目的显示增强.
 * <p>行内描述追加发配轮次(自增殖:调用次数);悬停追加完整结构详情
 * (每轮消耗/产出、总轮次、初始提取).普通计划缓存为空,零影响.</p>
 */
@Mixin(value = CraftConfirmTableRenderer.class, remap = false)
public abstract class MixinCraftConfirmTableRenderer {

    @Inject(method = "getEntryDescription", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void ae2e$appendRoundsDescription(CraftingPlanSummaryEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        var info = SpecialPlanClientCache.entryFor(entry.getWhat());
        if (info != null) {
            cir.getReturnValue().add(SpecialPlanTooltip.descriptionLine(entry.getWhat(), info));
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
}
