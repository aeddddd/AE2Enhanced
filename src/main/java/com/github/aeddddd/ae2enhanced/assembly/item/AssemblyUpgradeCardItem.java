package com.github.aeddddd.ae2enhanced.assembly.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * 装配枢纽升级卡物品。
 * <p>在 tooltip 中明确提示该升级卡仅可用于装配枢纽。</p>
 */
public class AssemblyUpgradeCardItem extends Item {

    public AssemblyUpgradeCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
            TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        tooltipComponents.add(Component.translatable("item.ae2enhanced.assembly_upgrade.tooltip"));
    }
}
