package com.github.aeddddd.ae2enhanced.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * 稳态时空流形 — 黑洞退火产物，T1 材料。
 */
public class StableSpacetimeManifoldItem extends Item {

    public StableSpacetimeManifoldItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        String text = Component.translatable("item.ae2enhanced.stable_spacetime_manifold.tooltip").getString();
        for (String line : text.replace("\\n", "\n").split("\n")) {
            tooltip.add(Component.literal(line));
        }
    }
}
