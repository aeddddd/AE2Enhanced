package com.github.aeddddd.ae2enhanced.omnitool;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;

/**
 * 先进 ME 全能工具的升级、模式与状态读写中心。
 */
public final class OmniToolUpgrades {

    private OmniToolUpgrades() {}

    // ==================== Mode ====================

    public static int getMode(ItemStack stack) {
        if (!stack.hasTag()) return AdvancedMEOmniToolItem.MODE_UNIVERSAL;
        return stack.getTag().getInt(OmniToolNBT.MODE);
    }

    public static void setMode(ItemStack stack, int mode) {
        stack.getOrCreateTag().putInt(OmniToolNBT.MODE, mode % AdvancedMEOmniToolItem.MODE_COUNT);
    }

    public static void cycleMode(ItemStack stack) {
        setMode(stack, getMode(stack) + 1);
    }

    // ==================== Drop Mode ====================

    public static int getDropMode(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(OmniToolNBT.DROP_MODE) : AdvancedMEOmniToolItem.DROP_NORMAL;
    }

    public static void setDropMode(ItemStack stack, int mode) {
        stack.getOrCreateTag().putInt(OmniToolNBT.DROP_MODE, mode % 3);
    }

    public static void cycleDropMode(ItemStack stack) {
        setDropMode(stack, getDropMode(stack) + 1);
    }

    // ==================== Silk Touch ====================

    public static boolean isSilkTouchEnabled(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(OmniToolNBT.SILK_TOUCH);
    }

    public static void setSilkTouchEnabled(ItemStack stack, boolean enabled) {
        stack.getOrCreateTag().putBoolean(OmniToolNBT.SILK_TOUCH, enabled);
        OmniToolEnchantments.updateEnchantments(stack);
    }

    public static void toggleSilkTouch(ItemStack stack) {
        setSilkTouchEnabled(stack, !isSilkTouchEnabled(stack));
    }

    public static boolean isAdvancedSilkTouchEnabled(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(OmniToolNBT.ADVANCED_SILK_TOUCH);
    }

    public static void setAdvancedSilkTouchEnabled(ItemStack stack, boolean enabled) {
        stack.getOrCreateTag().putBoolean(OmniToolNBT.ADVANCED_SILK_TOUCH, enabled);
    }

    // ==================== Bedrock Breaker ====================

    public static boolean hasBedrockBreaker(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(OmniToolNBT.BEDROCK_BREAKER);
    }

    public static void setBedrockBreaker(ItemStack stack, boolean has) {
        stack.getOrCreateTag().putBoolean(OmniToolNBT.BEDROCK_BREAKER, has);
    }

    // ==================== Conformal Charge ====================

    public static boolean hasConformalCharge(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(OmniToolNBT.CONFORMAL_CHARGE);
    }

    public static void setConformalCharge(ItemStack stack, boolean has) {
        stack.getOrCreateTag().putBoolean(OmniToolNBT.CONFORMAL_CHARGE, has);
    }

    // ==================== Wall Phase ====================

    public static boolean isWallPhaseEnabled(ItemStack stack) {
        if (!stack.hasTag()) {
            return AE2EnhancedConfig.COMMON.omniToolEnableWallPhase.get();
        }
        if (!stack.getTag().contains(OmniToolNBT.WALL_PHASE)) {
            return AE2EnhancedConfig.COMMON.omniToolEnableWallPhase.get();
        }
        return stack.getTag().getBoolean(OmniToolNBT.WALL_PHASE);
    }

    public static void setWallPhaseEnabled(ItemStack stack, boolean enabled) {
        stack.getOrCreateTag().putBoolean(OmniToolNBT.WALL_PHASE, enabled);
    }

    // ==================== Param Enabled ====================

    public static boolean isParamEnabled(ItemStack stack, int paramIdx) {
        if (paramIdx < 0 || paramIdx > 31) return true;
        if (!stack.hasTag()) return true;
        int mask = stack.getTag().getInt(OmniToolNBT.PARAM_ENABLED);
        if (mask == 0 && !stack.getTag().contains(OmniToolNBT.PARAM_ENABLED)) return true;
        return (mask & (1 << paramIdx)) != 0;
    }

    public static void setParamEnabled(ItemStack stack, int paramIdx, boolean enabled) {
        if (paramIdx < 0 || paramIdx > 31) return;
        int mask = stack.getOrCreateTag().getInt(OmniToolNBT.PARAM_ENABLED);
        if (enabled) mask |= (1 << paramIdx);
        else mask &= ~(1 << paramIdx);
        stack.getOrCreateTag().putInt(OmniToolNBT.PARAM_ENABLED, mask);
    }

    // ==================== Break Cooldown ====================

    public static int getBreakCooldown(ItemStack stack) {
        int max = AE2EnhancedConfig.COMMON.omniToolMaxBreakCooldown.get();
        int cooldown = stack.hasTag() ? stack.getTag().getInt(OmniToolNBT.BREAK_COOLDOWN) : max;
        return Math.min(cooldown, max);
    }

    public static void setBreakCooldown(ItemStack stack, int ticks) {
        stack.getOrCreateTag().putInt(OmniToolNBT.BREAK_COOLDOWN, ticks);
    }

    public static long getLastBreakTick(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getLong(OmniToolNBT.LAST_BREAK) : 0;
    }

    public static void setLastBreakTick(ItemStack stack, long tick) {
        stack.getOrCreateTag().putLong(OmniToolNBT.LAST_BREAK, tick);
    }

    // ==================== Blink Distance / Cooldown ====================

    public static double getBlinkDistance(ItemStack stack) {
        double max = AE2EnhancedConfig.COMMON.omniToolMaxBlinkDistance.get();
        if (!stack.hasTag()) return max;
        var tag = stack.getTag();
        if (!tag.contains(OmniToolNBT.BLINK_DIST, Tag.TAG_DOUBLE)) {
            tag.putDouble(OmniToolNBT.BLINK_DIST, max);
        }
        double dist = tag.getDouble(OmniToolNBT.BLINK_DIST);
        return Math.min(dist, max);
    }

    public static void setBlinkDistance(ItemStack stack, double dist) {
        stack.getOrCreateTag().putDouble(OmniToolNBT.BLINK_DIST, dist);
    }

    public static long getLastBlinkTick(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getLong(OmniToolNBT.LAST_BLINK) : 0;
    }

    public static void setLastBlinkTick(ItemStack stack, long tick) {
        stack.getOrCreateTag().putLong(OmniToolNBT.LAST_BLINK, tick);
    }

    // ==================== Fortune (stored enchantment shortcut) ====================

    private static ResourceLocation fortuneId() {
        return BuiltInRegistries.ENCHANTMENT.getKey(Enchantments.BLOCK_FORTUNE);
    }

    public static boolean hasFortuneUpgrade(ItemStack stack) {
        return getFortuneLevel(stack) > 0;
    }

    public static int getFortuneLevel(ItemStack stack) {
        return OmniToolEnchantments.getStoredEnchantmentLevel(stack, fortuneId());
    }

    public static void setFortuneLevel(ItemStack stack, int level) {
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, fortuneId(), level);
    }
}
