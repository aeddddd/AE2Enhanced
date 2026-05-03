package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class ItemUpgradeCard extends Item {

    public static final int COUNT = 6;

    public static final int META_PARALLEL = 0;
    public static final int META_SPEED = 1;
    public static final int META_EFFICIENCY = 2;
    public static final int META_CAPACITY = 3;
    public static final int META_RESERVED1 = 4;
    public static final int META_RESERVED2 = 5;

    public ItemUpgradeCard() {
        setRegistryName(AE2Enhanced.MOD_ID, "upgrade_card");
        setTranslationKey(AE2Enhanced.MOD_ID + ".upgrade_card");
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = stack.getMetadata();
        switch (meta) {
            case META_PARALLEL:    return "item." + AE2Enhanced.MOD_ID + ".upgrade_card.parallel";
            case META_SPEED:       return "item." + AE2Enhanced.MOD_ID + ".upgrade_card.speed";
            case META_EFFICIENCY:  return "item." + AE2Enhanced.MOD_ID + ".upgrade_card.efficiency";
            case META_CAPACITY:    return "item." + AE2Enhanced.MOD_ID + ".upgrade_card.capacity";
            case META_RESERVED1:   return "item." + AE2Enhanced.MOD_ID + ".upgrade_card.reserved1";
            case META_RESERVED2:   return "item." + AE2Enhanced.MOD_ID + ".upgrade_card.reserved2";
            default:               return super.getTranslationKey(stack);
        }
    }

    /**
     * 获取指定类型升级卡在装配枢纽升级槽中的最大堆叠数量�?
     * 槽位索引�?metadata 一一对应�?=并行, 1=速度, 2=效率, 3=扩容, 4=预留1, 5=预留2
     */
    public static int getMaxStackForMeta(int meta) {
        switch (meta) {
            case META_PARALLEL:   return 5;   // 5�?�?Long.MAX_VALUE
            case META_SPEED:      return 5;   // 5�?�?1 tick
            case META_EFFICIENCY: return 10;  // 预留
            case META_CAPACITY:   return 10;  // 预留
            case META_RESERVED1:  return 1;   // 自动上传模块：每个控制器只需1�?
            default:              return 10;  // 预留
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        int meta = stack.getMetadata();
        int max = getMaxStackForMeta(meta);
        String[] lines;
        switch (meta) {
            case META_PARALLEL:
                tooltip.add(I18n.format("item.ae2enhanced.upgrade_card.parallel.tooltip"));
                lines = I18n.format("item.ae2enhanced.upgrade_card.parallel.tooltip.detail", max).replace("\\n", "\n").split("\n");
                for (String line : lines) tooltip.add(line);
                break;
            case META_SPEED:
                tooltip.add(I18n.format("item.ae2enhanced.upgrade_card.speed.tooltip"));
                lines = I18n.format("item.ae2enhanced.upgrade_card.speed.tooltip.detail", max).replace("\\n", "\n").split("\n");
                for (String line : lines) tooltip.add(line);
                break;
            case META_EFFICIENCY:
                tooltip.add(I18n.format("item.ae2enhanced.upgrade_card.efficiency.tooltip"));
                lines = I18n.format("item.ae2enhanced.upgrade_card.efficiency.tooltip.detail", max).replace("\\n", "\n").split("\n");
                for (String line : lines) tooltip.add(line);
                break;
            case META_CAPACITY:
                tooltip.add(I18n.format("item.ae2enhanced.upgrade_card.capacity.tooltip"));
                lines = I18n.format("item.ae2enhanced.upgrade_card.capacity.tooltip.detail", max).replace("\\n", "\n").split("\n");
                for (String line : lines) tooltip.add(line);
                break;
            case META_RESERVED1:
                tooltip.add(I18n.format("item.ae2enhanced.upgrade_card.upload.tooltip"));
                lines = I18n.format("item.ae2enhanced.upgrade_card.upload.tooltip.detail", max).replace("\\n", "\n").split("\n");
                for (String line : lines) tooltip.add(line);
                break;
            default:
                tooltip.add(I18n.format("item.ae2enhanced.upgrade_card.reserved2.tooltip"));
        }
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int i = 0; i < COUNT; i++) {
            items.add(new ItemStack(this, 1, i));
        }
    }
}
