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
 * 稳态时空流�?—�?黑洞退火产物，T1 材料�?
 */
public class ItemStableSpacetimeManifold extends Item {

    public ItemStableSpacetimeManifold() {
        setRegistryName(AE2Enhanced.MOD_ID, "stable_spacetime_manifold");
        setTranslationKey(AE2Enhanced.MOD_ID + ".stable_spacetime_manifold");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        String[] lines = I18n.format("item.ae2enhanced.stable_spacetime_manifold.tooltip")
                .replace("\\n", "\n").split("\n");
        for (String line : lines) {
            tooltip.add(line);
        }
    }
}
