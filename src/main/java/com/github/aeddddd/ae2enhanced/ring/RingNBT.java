package com.github.aeddddd.ae2enhanced.ring;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 先进网络链接指环的 NBT 键与读写辅助.
 * 所有配置均持久化在物品 NBT 上,客户端可直接读取(背包/饰品自动同步),无需额外状态同步包.
 */
public final class RingNBT {

    private RingNBT() {}

    /** 内部 RF 缓存 */
    public static final String ENERGY = "RingEnergy";
    /**
     * 阶段: 0=I(基础), 1=II, 2=III, 飞升(Ascended)视为 IV.
     * 阶段 I: 挖掘惩罚/夜视/触及/供能/药水拦截/行走速度
     * 阶段 II: 飞行/飞行速度/惯性取消/跳跃/回血
     * 阶段 III: 穿墙/致命伤害阻挡(带冷却)
     * 阶段 IV(飞升): 强制飞行/免费药水免疫/全伤害阻挡(无冷却)/饱食/物品与位移保护
     */
    public static final String TIER = "RingTier";
    /** 飞升进度(0~16)与飞升完成标记 */
    public static final String ASCEND_PROGRESS = "AscendProgress";
    public static final String ASCENDED = "Ascended";

    public static final String FLIGHT = "RFlight";
    public static final String FORCE_FLIGHT = "RForceFlight";
    public static final String FLY_SPEED = "RFlySpeed";
    public static final String WALK_SPEED = "RWalkSpeed";
    /** 行走速度调整总开关(关闭时不接管 walkSpeed,保留其他模组的速度来源) */
    public static final String WALK_TWEAK = "RWalkTweak";
    public static final String JUMP_PCT = "RJumpPct";
    public static final String NO_INERTIA = "RNoInertia";
    public static final String WALL_PHASE = "RWallPhase";
    public static final String NIGHT_VISION = "RNightVision";
    public static final String REACH = "RReach";
    public static final String FEED = "RFeedItems";
    /** 供能范围: 0=全部物品栏, 1=仅装备+饰品+双手 */
    public static final String FEED_MODE = "RFeedMode";
    public static final String HEAL_AUTO = "RHealAuto";
    public static final String HEAL_PCT = "RHealPct";
    /** 药水移除: 0=关, 1=仅负面, 2=全部 */
    public static final String POTION_MODE = "RPotionMode";
    public static final String MINING_FIX = "RMiningFix";
    public static final String DMG_BLOCK = "RDmgBlock";

    public static final int MAX_ASCEND = 16;

    private static NBTTagCompound tag(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    private static NBTTagCompound read(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
    }

    // ---- 能量 ----

    public static int getEnergy(ItemStack stack) {
        return read(stack).getInteger(ENERGY);
    }

    public static void setEnergy(ItemStack stack, int energy) {
        tag(stack).setInteger(ENERGY, Math.max(0, energy));
    }

    // ---- 飞升 ----

    public static int getAscendProgress(ItemStack stack) {
        return read(stack).getInteger(ASCEND_PROGRESS);
    }

    public static void setAscendProgress(ItemStack stack, int progress) {
        tag(stack).setInteger(ASCEND_PROGRESS, Math.min(progress, MAX_ASCEND));
    }

    public static boolean isAscended(ItemStack stack) {
        return read(stack).getBoolean(ASCENDED) || getAscendProgress(stack) >= MAX_ASCEND;
    }

    public static void setAscended(ItemStack stack, boolean ascended) {
        tag(stack).setBoolean(ASCENDED, ascended);
    }

    // ---- 阶段 ----

    public static int getTier(ItemStack stack) {
        return read(stack).getInteger(TIER);
    }

    public static void setTier(ItemStack stack, int tier) {
        tag(stack).setInteger(TIER, Math.min(tier, 2));
    }

    /** 阶段门槛判定：飞升指环视为满足一切阶段要求. */
    public static boolean tierAtLeast(ItemStack stack, int tier) {
        return isAscended(stack) || getTier(stack) >= tier;
    }

    // ---- 布尔开关 ----

    public static boolean getBool(ItemStack stack, String key, boolean def) {
        NBTTagCompound t = read(stack);
        return t.hasKey(key) ? t.getBoolean(key) : def;
    }

    public static void setBool(ItemStack stack, String key, boolean value) {
        tag(stack).setBoolean(key, value);
    }

    public static boolean isFlightEnabled(ItemStack stack) { return getBool(stack, FLIGHT, false); }
    public static boolean isForceFlightEnabled(ItemStack stack) { return isAscended(stack) && getBool(stack, FORCE_FLIGHT, false); }
    public static boolean isNoInertiaEnabled(ItemStack stack) { return getBool(stack, NO_INERTIA, false); }
    public static boolean isWallPhaseEnabled(ItemStack stack) { return getBool(stack, WALL_PHASE, false); }
    public static boolean isNightVisionEnabled(ItemStack stack) { return getBool(stack, NIGHT_VISION, false); }
    public static boolean isFeedEnabled(ItemStack stack) { return getBool(stack, FEED, false); }
    public static boolean isAutoHealEnabled(ItemStack stack) { return getBool(stack, HEAL_AUTO, false); }
    public static boolean isMiningFixEnabled(ItemStack stack) { return getBool(stack, MINING_FIX, true); }
    public static boolean isDamageBlockEnabled(ItemStack stack) { return getBool(stack, DMG_BLOCK, true); }
    public static boolean isWalkTweakEnabled(ItemStack stack) { return getBool(stack, WALK_TWEAK, false); }

    // ---- 数值 ----

    /** 飞行速度(0.05~2.0,原版默认 0.05). */
    public static float getFlySpeed(ItemStack stack) {
        NBTTagCompound t = read(stack);
        return t.hasKey(FLY_SPEED) ? t.getFloat(FLY_SPEED) : 0.05f;
    }

    /** 行走速度(0.05~2.0,原版默认 0.1). */
    public static float getWalkSpeed(ItemStack stack) {
        NBTTagCompound t = read(stack);
        return t.hasKey(WALK_SPEED) ? t.getFloat(WALK_SPEED) : 0.1f;
    }

    /** 跳跃高度百分比(100~500). */
    public static int getJumpPercent(ItemStack stack) {
        NBTTagCompound t = read(stack);
        return t.hasKey(JUMP_PCT) ? t.getInteger(JUMP_PCT) : 100;
    }

    /** 触及距离(5.0 为原版默认). */
    public static float getReach(ItemStack stack) {
        NBTTagCompound t = read(stack);
        return t.hasKey(REACH) ? t.getFloat(REACH) : 5.0f;
    }

    public static int getFeedMode(ItemStack stack) {
        return read(stack).getInteger(FEED_MODE);
    }

    /** 自动回血阈值百分比(1~100,默认 50). */
    public static int getHealThreshold(ItemStack stack) {
        NBTTagCompound t = read(stack);
        return t.hasKey(HEAL_PCT) ? t.getInteger(HEAL_PCT) : 50;
    }

    public static int getPotionMode(ItemStack stack) {
        return read(stack).getInteger(POTION_MODE);
    }
}
