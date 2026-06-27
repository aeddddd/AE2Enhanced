package com.github.aeddddd.ae2enhanced.omnitool;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

/**
 * 先进 ME 全能工具的升级、模式与状态读写中心。
 */
public final class OmniToolUpgrades {

    private OmniToolUpgrades() {}

    // ==================== Mode ====================

    public static int getMode(ItemStack stack) {
        if (!stack.hasTagCompound()) return ItemAdvancedMEOmniTool.MODE_UNIVERSAL;
        return stack.getTagCompound().getInteger(OmniToolNBT.MODE);
    }

    public static void setMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(OmniToolNBT.MODE, mode % ItemAdvancedMEOmniTool.MODE_COUNT);
    }

    public static void cycleMode(ItemStack stack) {
        setMode(stack, getMode(stack) + 1);
    }

    // ==================== Drop Mode ====================

    public static int getDropMode(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getInteger(OmniToolNBT.DROP_MODE) : ItemAdvancedMEOmniTool.DROP_NORMAL;
    }

    public static void setDropMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(OmniToolNBT.DROP_MODE, mode % 3);
    }

    public static void cycleDropMode(ItemStack stack) {
        setDropMode(stack, getDropMode(stack) + 1);
    }

    // ==================== Silk Touch ====================

    public static boolean isSilkTouchEnabled(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(OmniToolNBT.SILK_TOUCH);
    }

    public static void setSilkTouchEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(OmniToolNBT.SILK_TOUCH, enabled);
        OmniToolEnchantments.updateEnchantments(stack);
    }

    public static void toggleSilkTouch(ItemStack stack) {
        setSilkTouchEnabled(stack, !isSilkTouchEnabled(stack));
    }

    public static boolean isAdvancedSilkTouchEnabled(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(OmniToolNBT.ADVANCED_SILK_TOUCH);
    }

    public static void setAdvancedSilkTouchEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(OmniToolNBT.ADVANCED_SILK_TOUCH, enabled);
    }

    // ==================== Bedrock Breaker ====================

    public static boolean hasBedrockBreaker(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(OmniToolNBT.BEDROCK_BREAKER);
    }

    public static void setBedrockBreaker(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(OmniToolNBT.BEDROCK_BREAKER, has);
    }

    // ==================== Chaos Core ====================

    public static boolean hasChaosCore(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(OmniToolNBT.CHAOS_CORE);
    }

    public static void setChaosCore(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(OmniToolNBT.CHAOS_CORE, has);
    }

    public static boolean isChaosForceKillEnabled(ItemStack stack) {
        if (!stack.hasTagCompound()) return true;
        if (!stack.getTagCompound().hasKey(OmniToolNBT.CHAOS_FORCE_KILL)) return true;
        return stack.getTagCompound().getBoolean(OmniToolNBT.CHAOS_FORCE_KILL);
    }

    public static void setChaosForceKillEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(OmniToolNBT.CHAOS_FORCE_KILL, enabled);
    }

    // ==================== Conformal Charge ====================

    public static boolean hasConformalCharge(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(OmniToolNBT.CONFORMAL_CHARGE);
    }

    public static void setConformalCharge(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(OmniToolNBT.CONFORMAL_CHARGE, has);
    }

    // ==================== Travel Staff ====================

    public static boolean hasTravelStaff(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(OmniToolNBT.TRAVEL_STAFF);
    }

    public static void setTravelStaff(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(OmniToolNBT.TRAVEL_STAFF, has);
    }

    // ==================== Wall Phase ====================

    public static boolean isWallPhaseEnabled(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return AE2EnhancedConfig.omniTool.enableWallPhase;
        }
        if (!stack.getTagCompound().hasKey(OmniToolNBT.WALL_PHASE)) {
            return AE2EnhancedConfig.omniTool.enableWallPhase;
        }
        return stack.getTagCompound().getBoolean(OmniToolNBT.WALL_PHASE);
    }

    public static void setWallPhaseEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(OmniToolNBT.WALL_PHASE, enabled);
    }

    // ==================== Param Enabled ====================

    public static boolean isParamEnabled(ItemStack stack, int paramIdx) {
        if (paramIdx < 0 || paramIdx > 31) return true;
        if (!stack.hasTagCompound()) return true;
        int mask = stack.getTagCompound().getInteger(OmniToolNBT.PARAM_ENABLED);
        if (mask == 0 && !stack.getTagCompound().hasKey(OmniToolNBT.PARAM_ENABLED)) return true;
        return (mask & (1 << paramIdx)) != 0;
    }

    public static void setParamEnabled(ItemStack stack, int paramIdx, boolean enabled) {
        if (paramIdx < 0 || paramIdx > 31) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        int mask = stack.getTagCompound().getInteger(OmniToolNBT.PARAM_ENABLED);
        if (enabled) mask |= (1 << paramIdx);
        else mask &= ~(1 << paramIdx);
        stack.getTagCompound().setInteger(OmniToolNBT.PARAM_ENABLED, mask);
    }

    // ==================== Break Cooldown ====================

    public static int getBreakCooldown(ItemStack stack) {
        int max = AE2EnhancedConfig.omniTool.maxBreakCooldown;
        int cooldown = stack.hasTagCompound() ? stack.getTagCompound().getInteger(OmniToolNBT.BREAK_COOLDOWN) : max;
        return Math.min(cooldown, max);
    }

    public static void setBreakCooldown(ItemStack stack, int ticks) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(OmniToolNBT.BREAK_COOLDOWN, ticks);
    }

    public static long getLastBreakTick(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getLong(OmniToolNBT.LAST_BREAK) : 0;
    }

    public static void setLastBreakTick(ItemStack stack, long tick) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setLong(OmniToolNBT.LAST_BREAK, tick);
    }

    // ==================== Blink Distance / Cooldown ====================

    public static double getBlinkDistance(ItemStack stack) {
        double max = AE2EnhancedConfig.omniTool.maxBlinkDistance;
        if (!stack.hasTagCompound()) return max;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(OmniToolNBT.BLINK_DIST, Constants.NBT.TAG_DOUBLE)) {
            tag.setDouble(OmniToolNBT.BLINK_DIST, max);
        }
        double dist = tag.getDouble(OmniToolNBT.BLINK_DIST);
        return Math.min(dist, max);
    }

    public static void setBlinkDistance(ItemStack stack, double dist) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setDouble(OmniToolNBT.BLINK_DIST, dist);
    }

    public static long getLastBlinkTick(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getLong(OmniToolNBT.LAST_BLINK) : 0;
    }

    public static void setLastBlinkTick(ItemStack stack, long tick) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setLong(OmniToolNBT.LAST_BLINK, tick);
    }

    // ==================== Fortune (stored enchantment shortcut) ====================

    public static boolean hasFortuneUpgrade(ItemStack stack) {
        return getFortuneLevel(stack) > 0;
    }

    public static int getFortuneLevel(ItemStack stack) {
        return OmniToolEnchantments.getStoredEnchantmentLevel(stack,
                (short) net.minecraft.enchantment.Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE));
    }

    public static void setFortuneLevel(ItemStack stack, int level) {
        OmniToolEnchantments.setStoredEnchantmentLevel(stack,
                (short) net.minecraft.enchantment.Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE), level);
    }

    // ==================== Anti-Heal ====================

    public static boolean hasAntiHeal(EntityLivingBase entity) {
        return CombatModule.hasAntiHeal(entity);
    }

    public static void applyAntiHeal(EntityLivingBase entity) {
        CombatModule.applyAntiHeal(entity);
    }

    public static void clearAntiHeal(EntityLivingBase entity) {
        CombatModule.clearAntiHeal(entity);
    }

    // ==================== Travel Anchor Binding ====================

    public static boolean isTravelAnchorBound(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(OmniToolNBT.TRAVEL_ANCHOR_BOUND);
    }

    public static void setBoundTravelAnchor(ItemStack stack, BlockPos pos, int dim) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        tag.setBoolean(OmniToolNBT.TRAVEL_ANCHOR_BOUND, true);
        tag.setInteger(OmniToolNBT.TRAVEL_ANCHOR_X, pos.getX());
        tag.setInteger(OmniToolNBT.TRAVEL_ANCHOR_Y, pos.getY());
        tag.setInteger(OmniToolNBT.TRAVEL_ANCHOR_Z, pos.getZ());
        tag.setInteger(OmniToolNBT.TRAVEL_ANCHOR_DIM, dim);
    }

    public static BlockPos getBoundTravelAnchorPos(ItemStack stack) {
        if (!isTravelAnchorBound(stack)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return new BlockPos(tag.getInteger(OmniToolNBT.TRAVEL_ANCHOR_X),
                tag.getInteger(OmniToolNBT.TRAVEL_ANCHOR_Y),
                tag.getInteger(OmniToolNBT.TRAVEL_ANCHOR_Z));
    }

    public static int getBoundTravelAnchorDim(ItemStack stack) {
        if (!isTravelAnchorBound(stack)) return Integer.MIN_VALUE;
        return stack.getTagCompound().getInteger(OmniToolNBT.TRAVEL_ANCHOR_DIM);
    }

    public static void clearBoundTravelAnchor(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            tag.removeTag(OmniToolNBT.TRAVEL_ANCHOR_BOUND);
            tag.removeTag(OmniToolNBT.TRAVEL_ANCHOR_X);
            tag.removeTag(OmniToolNBT.TRAVEL_ANCHOR_Y);
            tag.removeTag(OmniToolNBT.TRAVEL_ANCHOR_Z);
            tag.removeTag(OmniToolNBT.TRAVEL_ANCHOR_DIM);
            if (tag.getSize() == 0) {
                stack.setTagCompound(null);
            }
        }
    }
}
