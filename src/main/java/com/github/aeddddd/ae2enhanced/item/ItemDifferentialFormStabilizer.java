package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 微分形式稳定单元 —�?黑洞退火产物，T2 材料�?
 */
public class ItemDifferentialFormStabilizer extends Item {

    public ItemDifferentialFormStabilizer() {
        setRegistryName(AE2Enhanced.MOD_ID, "differential_form_stabilizer");
        setTranslationKey(AE2Enhanced.MOD_ID + ".differential_form_stabilizer");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.addAll(java.util.Arrays.asList(I18n.format("item.ae2enhanced.differential_form_stabilizer.tooltip")
                .replace("\\n", "\n").split("\n")));
    }
}
