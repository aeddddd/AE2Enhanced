package com.github.aeddddd.ae2enhanced.item;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 微型奇点�?ItemBlock，用于显�?tooltip 说明生成方法�?
 */
public class ItemBlockMicroSingularity extends ItemBlock {

    public ItemBlockMicroSingularity(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        String[] lines = I18n.format("tile.ae2enhanced.micro_singularity.tooltip")
            .replace("\\n", "\n").split("\n");
        for (String line : lines) {
            tooltip.add(line);
        }
    }
}
