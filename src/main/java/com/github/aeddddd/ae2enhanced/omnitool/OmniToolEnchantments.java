package com.github.aeddddd.ae2enhanced.omnitool;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

/**
 * 先进 ME 全能工具的存储附魔读写与同步。
 * <p>1.20.1 附魔以注册表字符串 id 标识（如 "minecraft:fortune"），
 * 存储区格式为 ListTag[{id: string, lvl: short, max: short}]。</p>
 */
public final class OmniToolEnchantments {

    private OmniToolEnchantments() {}

    public static boolean hasStoredEnchantments(ItemStack stack) {
        return getStoredEnchantments(stack).size() > 0;
    }

    public static ListTag getStoredEnchantments(ItemStack stack) {
        if (!stack.hasTag()) return new ListTag();
        return stack.getTag().getList(OmniToolNBT.ENCHANTMENTS, Tag.TAG_COMPOUND);
    }

    public static int getStoredEnchantmentLevel(ItemStack stack, ResourceLocation enchantmentId) {
        ListTag list = getStoredEnchantments(stack);
        String id = enchantmentId.toString();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (id.equals(tag.getString("id"))) {
                return tag.getShort("lvl");
            }
        }
        return 0;
    }

    public static int getEnchantmentSourceLevel(ItemStack stack, ResourceLocation enchantmentId) {
        ListTag list = getStoredEnchantments(stack);
        String id = enchantmentId.toString();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (id.equals(tag.getString("id"))) {
                return tag.contains("max", Tag.TAG_SHORT) ? tag.getShort("max") : tag.getShort("lvl");
            }
        }
        return 0;
    }

    public static void setStoredEnchantmentLevel(ItemStack stack, ResourceLocation enchantmentId, int level) {
        ListTag list = getStoredEnchantments(stack);
        String id = enchantmentId.toString();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (id.equals(tag.getString("id"))) {
                int max = tag.contains("max", Tag.TAG_SHORT) ? tag.getShort("max") : tag.getShort("lvl");
                if (level <= 0) {
                    list.remove(i);
                } else {
                    tag.putShort("lvl", (short) Math.min(level, max));
                }
                found = true;
                break;
            }
        }
        if (!found && level > 0) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.putShort("lvl", (short) level);
            tag.putShort("max", (short) level);
            list.add(tag);
        }
        setStoredEnchantments(stack, list);
    }

    public static void setStoredEnchantments(ItemStack stack, ListTag list) {
        if (list == null || list.size() == 0) {
            if (stack.hasTag()) {
                stack.getTag().remove(OmniToolNBT.ENCHANTMENTS);
            }
        } else {
            stack.getOrCreateTag().put(OmniToolNBT.ENCHANTMENTS, list);
        }
        updateEnchantments(stack);
    }

    /**
     * 从附魔书复制附魔列表，等级受配置上限钳制。
     */
    public static ListTag copyEnchantmentsFromBook(ItemStack book) {
        ListTag result = new ListTag();
        ListTag stored = net.minecraft.world.item.EnchantedBookItem.getEnchantments(book);
        int configMax = AE2EnhancedConfig.COMMON.omniToolMaxEnchantmentLevel.get();
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag src = stored.getCompound(i);
            short lvl = src.getShort("lvl");
            short max = configMax > 0 ? (short) Math.min(lvl, configMax) : lvl;
            CompoundTag dst = new CompoundTag();
            dst.putString("id", src.getString("id"));
            dst.putShort("lvl", max);
            dst.putShort("max", max);
            result.add(dst);
        }
        return result;
    }

    /**
     * 将存储区附魔与精准采集开关同步到物品可见附魔（"Enchantments" 标签）。
     */
    public static void updateEnchantments(ItemStack stack) {
        Map<Enchantment, Integer> map = new HashMap<>();

        // 从书中导入的附魔（以存储区为准）
        ListTag stored = getStoredEnchantments(stack);
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag src = stored.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(src.getString("id"));
            if (id == null) continue;
            Enchantment ench = BuiltInRegistries.ENCHANTMENT.get(id);
            int lvl = src.getShort("lvl");
            if (ench == null || lvl <= 0) continue;
            map.put(ench, lvl);
        }

        // 工具自带的精准采集开关（若书中已有精准采集，以书中的为准）
        if (OmniToolUpgrades.isSilkTouchEnabled(stack) && !map.containsKey(Enchantments.SILK_TOUCH)) {
            map.put(Enchantments.SILK_TOUCH, 1);
        }

        EnchantmentHelper.setEnchantments(map, stack);
    }
}
