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
 * 共形不变�?—�?黑洞退火产物，T3 材料�?
 */
public class ItemConformalCharge extends Item {

    public ItemConformalCharge() {
        setRegistryName(AE2Enhanced.MOD_ID, "conformal_invariant_charge");
        setTranslationKey(AE2Enhanced.MOD_ID + ".conformal_invariant_charge");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB); // 不显示在创造模式物品栏，通过黑洞合成获得
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        String[] lines = I18n.format("item.ae2enhanced.conformal_invariant_charge.tooltip")
                .replace("\\n", "\n").split("\n");
        for (String line : lines) {
            tooltip.add(line);
        }
    }
}
