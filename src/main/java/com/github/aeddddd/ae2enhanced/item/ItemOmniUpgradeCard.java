package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Omni Terminal 专用升级卡.
 */
public class ItemOmniUpgradeCard extends Item {

    public static final int COUNT = 2;

    public static final int META_MAGNET = 0;   // 高级磁引卡
    public static final int META_PICKER = 1;   // 选取交互卡

    public ItemOmniUpgradeCard() {
        setRegistryName(AE2Enhanced.MOD_ID, "omni_upgrade_card");
        setTranslationKey(AE2Enhanced.MOD_ID + ".omni_upgrade_card");
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = stack.getMetadata();
        switch (meta) {
            case META_MAGNET: return "item." + AE2Enhanced.MOD_ID + ".omni_upgrade_card.magnet";
            case META_PICKER: return "item." + AE2Enhanced.MOD_ID + ".omni_upgrade_card.picker";
            default: return super.getTranslationKey(stack);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        int meta = stack.getMetadata();
        switch (meta) {
            case META_MAGNET:
                tooltip.add(I18n.format("item.ae2enhanced.omni_upgrade_card.magnet.tooltip"));
                break;
            case META_PICKER:
                tooltip.add(I18n.format("item.ae2enhanced.omni_upgrade_card.picker.tooltip"));
                break;
        }
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int i = 0; i < COUNT; i++) {
            items.add(new ItemStack(this, 1, i));
        }
    }

    // === 磁引卡模式 NBT 读写 ===
    private static final String NBT_MAGNET_MODE = "ae2e_magnet_mode";

    public static int getMagnetMode(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != ItemRegistry.OMNI_UPGRADE_CARD || stack.getMetadata() != META_MAGNET) {
            return 0;
        }
        return stack.getOrCreateSubCompound("ae2e").getInteger(NBT_MAGNET_MODE);
    }

    public static void setMagnetMode(ItemStack stack, int mode) {
        if (stack.isEmpty() || stack.getItem() != ItemRegistry.OMNI_UPGRADE_CARD || stack.getMetadata() != META_MAGNET) {
            return;
        }
        stack.getOrCreateSubCompound("ae2e").setInteger(NBT_MAGNET_MODE, mode);
    }
}
