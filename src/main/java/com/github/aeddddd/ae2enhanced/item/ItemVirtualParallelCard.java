package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 虚拟并行卡：安装到 Central ME Interface 后，所有支持的远程目标切换为虚拟合成模式。
 *
 * <p>通过 NBT {@code Tier} 区分 8 个等级，每个等级对应固定的并行数。
 * 多张卡同时安装时取最高 tier。</p>
 */
public class ItemVirtualParallelCard extends Item {

    public static final String NBT_TIER = "Tier";

    /**
     * 8 个等级对应的并行数：8 / 32 / 128 / 512 / 32768 / 2097152 / 134217728 / Long.MAX_VALUE
     */
    public static final long[] PARALLEL_VALUES = {
            8L,
            32L,
            128L,
            512L,
            32768L,
            2097152L,
            134217728L,
            Long.MAX_VALUE
    };

    public static final int MAX_TIER = PARALLEL_VALUES.length;

    public ItemVirtualParallelCard() {
        setRegistryName(AE2Enhanced.MOD_ID, "virtual_parallel_card");
        setTranslationKey(AE2Enhanced.MOD_ID + ".virtual_parallel_card");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(64);
    }

    public static int getTier(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) {
            return 0;
        }
        return stack.getTagCompound().getInteger(NBT_TIER);
    }

    public static void setTier(ItemStack stack, int tier) {
        if (stack.isEmpty()) {
            return;
        }
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setInteger(NBT_TIER, Math.max(0, Math.min(tier, MAX_TIER - 1)));
        stack.setTagCompound(tag);
    }

    public static long getParallel(ItemStack stack) {
        int tier = getTier(stack);
        if (tier < 0 || tier >= PARALLEL_VALUES.length) {
            return 1;
        }
        return PARALLEL_VALUES[tier];
    }

    public static ItemStack createStack(int tier) {
        ItemStack stack = new ItemStack(ItemRegistry.VIRTUAL_PARALLEL_CARD);
        setTier(stack, tier);
        return stack;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return getTranslationKey() + ".tier" + (getTier(stack) + 1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String name = super.getItemStackDisplayName(stack);
        TextFormatting color = getTierNameFormatting(getTier(stack));
        return color + name + TextFormatting.RESET;
    }

    private static TextFormatting getTierNameFormatting(int tier) {
        switch (tier) {
            case 0: return TextFormatting.DARK_GRAY;
            case 1: return TextFormatting.GRAY;
            case 2: return TextFormatting.WHITE;
            case 3: return TextFormatting.YELLOW;
            case 4: return TextFormatting.GREEN;
            case 5: return TextFormatting.AQUA;
            case 6: return TextFormatting.LIGHT_PURPLE;
            case 7: return getAnimatedColor();
            default: return TextFormatting.WHITE;
        }
    }

    private static TextFormatting getAnimatedColor() {
        TextFormatting[] colors = {
                TextFormatting.RED, TextFormatting.GOLD, TextFormatting.YELLOW,
                TextFormatting.GREEN, TextFormatting.AQUA, TextFormatting.BLUE,
                TextFormatting.LIGHT_PURPLE
        };
        int index = (int) ((System.currentTimeMillis() / 500) % colors.length);
        return colors[index];
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) {
            return;
        }
        for (int i = 0; i < MAX_TIER; i++) {
            items.add(createStack(i));
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        int tier = getTier(stack);
        long parallel = getParallel(stack);
        String parallelText = parallel == Long.MAX_VALUE ? "∞" : String.valueOf(parallel);

        String phraseKey = "item.ae2enhanced.virtual_parallel_card.tooltip.tier" + (tier + 1);
        String phrase = I18n.format(phraseKey);
        if (!phrase.equals(phraseKey)) {
            tooltip.add(phrase);
        }
        tooltip.add(I18n.format("item.ae2enhanced.virtual_parallel_card.tooltip.parallel", parallelText));
        tooltip.add(I18n.format("item.ae2enhanced.virtual_parallel_card.tooltip.detail",
                AE2EnhancedConfig.centralInterface.virtualParallelEnergyCost));
    }
}
