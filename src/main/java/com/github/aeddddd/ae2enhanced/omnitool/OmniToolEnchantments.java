package com.github.aeddddd.ae2enhanced.omnitool;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * 先进 ME 全能工具的存储附魔读写、迁移与同步。
 */
public final class OmniToolEnchantments {

    private OmniToolEnchantments() {}

    public static boolean hasStoredEnchantments(ItemStack stack) {
        return getStoredEnchantments(stack).tagCount() > 0;
    }

    public static NBTTagList getStoredEnchantments(ItemStack stack) {
        if (!stack.hasTagCompound()) return new NBTTagList();
        NBTTagCompound tag = stack.getTagCompound();

        // 从旧版 Fortune 迁移
        if (tag.hasKey(OmniToolNBT.FORTUNE, Constants.NBT.TAG_INT) && !tag.hasKey(OmniToolNBT.ENCHANTMENTS)) {
            int fortune = tag.getInteger(OmniToolNBT.FORTUNE);
            if (fortune > 0) {
                NBTTagList list = new NBTTagList();
                NBTTagCompound ench = new NBTTagCompound();
                ench.setShort("id", (short) Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE));
                ench.setShort("lvl", (short) fortune);
                ench.setShort("max", (short) fortune);
                list.appendTag(ench);
                tag.setTag(OmniToolNBT.ENCHANTMENTS, list);
            }
            tag.removeTag(OmniToolNBT.FORTUNE);
        }

        return tag.getTagList(OmniToolNBT.ENCHANTMENTS, Constants.NBT.TAG_COMPOUND);
    }

    public static int getStoredEnchantmentLevel(ItemStack stack, short enchantmentId) {
        NBTTagList list = getStoredEnchantments(stack);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getShort("id") == enchantmentId) {
                return tag.getShort("lvl");
            }
        }
        return 0;
    }

    public static int getEnchantmentSourceLevel(ItemStack stack, short enchantmentId) {
        NBTTagList list = getStoredEnchantments(stack);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getShort("id") == enchantmentId) {
                return tag.hasKey("max", Constants.NBT.TAG_SHORT)
                        ? tag.getShort("max") : tag.getShort("lvl");
            }
        }
        return 0;
    }

    public static void setStoredEnchantmentLevel(ItemStack stack, short enchantmentId, int level) {
        NBTTagList list = getStoredEnchantments(stack);
        boolean found = false;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getShort("id") == enchantmentId) {
                int max = tag.hasKey("max", Constants.NBT.TAG_SHORT)
                        ? tag.getShort("max") : tag.getShort("lvl");
                if (level <= 0) {
                    list.removeTag(i);
                } else {
                    tag.setShort("lvl", (short) Math.min(level, max));
                }
                found = true;
                break;
            }
        }
        if (!found && level > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", enchantmentId);
            tag.setShort("lvl", (short) level);
            tag.setShort("max", (short) level);
            list.appendTag(tag);
        }
        setStoredEnchantments(stack, list);
    }

    public static void setStoredEnchantments(ItemStack stack, NBTTagList list) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        if (list == null || list.tagCount() == 0) {
            stack.getTagCompound().removeTag(OmniToolNBT.ENCHANTMENTS);
        } else {
            stack.getTagCompound().setTag(OmniToolNBT.ENCHANTMENTS, list);
        }
        updateEnchantments(stack);
    }

    public static NBTTagList copyEnchantmentsFromBook(ItemStack book) {
        NBTTagList result = new NBTTagList();
        if (!book.hasTagCompound()) return result;
        NBTTagList stored = book.getTagCompound().getTagList("StoredEnchantments", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < stored.tagCount(); i++) {
            NBTTagCompound src = stored.getCompoundTagAt(i);
            short lvl = src.getShort("lvl");
            short max = AE2EnhancedConfig.omniTool.maxEnchantmentLevel > 0
                    ? (short) Math.min(lvl, AE2EnhancedConfig.omniTool.maxEnchantmentLevel)
                    : lvl;
            NBTTagCompound dst = new NBTTagCompound();
            dst.setShort("id", src.getShort("id"));
            dst.setShort("lvl", max);
            dst.setShort("max", max);
            result.appendTag(dst);
        }
        return result;
    }

    public static void updateEnchantments(ItemStack stack) {
        NBTTagList enchList = new NBTTagList();

        // 从书中导入的附魔（以存储区为准）
        NBTTagList stored = getStoredEnchantments(stack);
        for (int i = 0; i < stored.tagCount(); i++) {
            NBTTagCompound src = stored.getCompoundTagAt(i);
            short id = src.getShort("id");
            short lvl = src.getShort("lvl");
            if (lvl <= 0) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", id);
            tag.setShort("lvl", lvl);
            enchList.appendTag(tag);
        }

        // 工具自带的精准采集开关（若书中已有时运/精准采集，以书中的为准，避免冲突时重复生成）
        boolean hasSilkTouch = false;
        boolean hasFortune = false;
        for (int i = 0; i < enchList.tagCount(); i++) {
            short id = enchList.getCompoundTagAt(i).getShort("id");
            if (id == Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.SILK_TOUCH)) hasSilkTouch = true;
            if (id == Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE)) hasFortune = true;
        }

        if (OmniToolUpgrades.isSilkTouchEnabled(stack) && !hasSilkTouch) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", (short) Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.SILK_TOUCH));
            tag.setShort("lvl", (short) 1);
            enchList.appendTag(tag);
        }

        // 清理过时的 Fortune（迁移逻辑已在 getStoredEnchantments 中处理）
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(OmniToolNBT.FORTUNE)) {
            stack.getTagCompound().removeTag(OmniToolNBT.FORTUNE);
        }

        if (enchList.tagCount() > 0) {
            if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound().setTag("ench", enchList);
        } else if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag("ench");
        }
    }
}
