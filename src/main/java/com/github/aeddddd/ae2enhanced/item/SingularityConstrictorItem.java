package com.github.aeddddd.ae2enhanced.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 奇点约束器 — 右键微型奇点将其约束为物品形态.
 * 约束后本物品转化为被约束的微型奇点；扔出奇点恢复为方块时在原地返还空的约束器.
 */
public class SingularityConstrictorItem extends Item {

    public SingularityConstrictorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        String text = Component.translatable("item.ae2enhanced.singularity_constrictor.tooltip").getString();
        for (String line : text.replace("\\n", "\n").split("\n")) {
            tooltip.add(Component.literal(line));
        }
    }
}
