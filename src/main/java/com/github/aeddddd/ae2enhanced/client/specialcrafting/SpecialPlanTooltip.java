package com.github.aeddddd.ae2enhanced.client.specialcrafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;

/**
 * 特殊计划显示文案构建(自增殖/循环链的轮次与每轮明细).
 */
public final class SpecialPlanTooltip {

    private SpecialPlanTooltip() {
    }

    /**
     * 行内描述(列表每行数量区):发配轮次/调用次数.
     */
    public static Component descriptionLine(AEKey key, SpecialPlanInfo.Entry entry) {
        if (entry.kind() == SpecialPlanInfo.KIND_SELF_DUP) {
            return Component.translatable("gui.ae2enhanced.special_plan.dup_desc", entry.totalCrafts())
                    .withStyle(ChatFormatting.AQUA);
        }
        return Component.translatable("gui.ae2enhanced.special_plan.rounds_desc", entry.rounds())
                .withStyle(ChatFormatting.AQUA);
    }

    /**
     * 悬停详情:完整结构信息.
     */
    public static List<Component> tooltipLines(AEKey key, SpecialPlanInfo.Entry entry) {
        List<Component> lines = new ArrayList<>();
        if (entry.kind() == SpecialPlanInfo.KIND_SELF_DUP) {
            lines.add(Component.translatable("gui.ae2enhanced.special_plan.dup_header")
                    .withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.ae2enhanced.special_plan.dup_per_craft",
                    format(key, entry.perRoundConsume()),
                    format(key, entry.perRoundProduce()),
                    format(key, entry.perRoundProduce() - entry.perRoundConsume())));
            lines.add(Component.translatable("gui.ae2enhanced.special_plan.dup_total",
                    entry.totalCrafts(), format(key, entry.initialExtract())));
        } else {
            lines.add(Component.translatable("gui.ae2enhanced.special_plan.cycle_header", entry.rounds())
                    .withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.ae2enhanced.special_plan.cycle_per_round",
                    format(key, entry.perRoundConsume()),
                    format(key, entry.perRoundProduce())));
            if (entry.initialExtract() > 0) {
                lines.add(Component.translatable("gui.ae2enhanced.special_plan.initial_extract",
                        format(key, entry.initialExtract())));
            }
        }
        return lines;
    }

    private static String format(AEKey key, long amount) {
        return key.formatAmount(amount, AmountFormat.FULL);
    }
}
