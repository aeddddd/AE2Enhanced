package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * 奇点约束器 — 右键微型奇点将其约束为物品形态.
 * 约束后本物品转化为被约束的微型奇点；扔出奇点恢复为方块时在原地返还空的约束器.
 */
public class ItemSingularityConstrictor extends Item {

    public ItemSingularityConstrictor() {
        setRegistryName(AE2Enhanced.MOD_ID, "singularity_constrictor");
        setTranslationKey(AE2Enhanced.MOD_ID + ".singularity_constrictor");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.addAll(Arrays.asList(I18n.format("item.ae2enhanced.singularity_constrictor.tooltip")
                .replace("\\n", "\n").split("\n")));
    }
}
