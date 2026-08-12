package com.github.aeddddd.ae2enhanced.ring;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 指环防护体系对 Mixin / 外部系统(ForceKillHelper)暴露的统一判定入口.
 *
 * <p>所有能量判定仅在服务端执行；客户端仅做 NBT 级的表现层判断(夜视/药水免疫显示).</p>
 */
public final class RingProtection {

    private RingProtection() {}

    /** 传送白名单：本 mod 自身的合法位移(Blink/穿墙自救/个人维度传送)设置后跳过拦截 */
    private static final Map<UUID, Long> TELEPORT_ALLOW = new HashMap<>();
    /** 内部 setHealth 白名单：指环自身回血/保底血量写入不被 setHealth 拦截 */
    private static final Set<UUID> HEALTH_BYPASS = new HashSet<>();

    // ==================== 基础判定 ====================

    /** 是否佩戴飞升指环(任意侧,纯 NBT 判断). */
    public static boolean hasAscendedRing(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) return false;
        ItemStack ring = RingLocator.findRing((EntityPlayer) entity);
        return !ring.isEmpty() && RingNBT.isAscended(ring);
    }

    /** 取玩家当前生效指环(未佩戴返回 EMPTY). */
    public static ItemStack getRing(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) return ItemStack.EMPTY;
        return RingLocator.findRing((EntityPlayer) entity);
    }

    /**
     * 飞升指环的绝对防护是否激活(服务端).
     * 每次拦截按 strongKillBlockCost(飞升倍率计价)节流扣费；能量不足全额时防护失效.
     */
    public static boolean isAbsoluteProtectionActive(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer) || entity.world.isRemote) return false;
        ItemStack ring = getRing(entity);
        if (ring.isEmpty() || !RingNBT.isAscended(ring)) return false;
        long cost = RingEnergyHandler.price(ring, AE2EnhancedConfig.ring.strongKillBlockCost);
        return RingEnergyHandler.consumeThrottled(
                (EntityPlayer) entity, ring, cost, RingEnergyHandler.Category.BLOCK);
    }

    /**
     * 强杀拦截入口(ForceKillHelper 调用).
     *
     * @return true 表示目标受飞升指环保护,强杀流程必须中止
     */
    public static boolean tryBlockForceKill(EntityLivingBase target) {
        return isAbsoluteProtectionActive(target);
    }

    // ==================== 药水拦截(注入前取消) ====================

    /**
     * 指环是否应在注入前拦截该药水效果(全阶段生效).
     *
     * <p>模式复用指环 NBT 的 PotionMode: 1=仅负面, 2=全部(0=不拦截).
     * 飞升指环免费拦截；I~III 阶段指环每次拦截在服务端按 potionRemoveCost 计价扣费,
     * 能量不足时放行(效果正常注入).客户端仅按 NBT 模式镜像拦截,
     * 服务端拦截成功则效果包根本不会下发,天然无显示残留.</p>
     */
    public static boolean isPotionSuppressed(EntityLivingBase entity, PotionEffect effect) {
        ItemStack ring = getRing(entity);
        if (ring.isEmpty()) return false;
        int mode = RingNBT.getPotionMode(ring);
        if (mode <= 0) return false;
        if (mode == 1 && !effect.getPotion().isBadEffect()) return false;
        if (RingNBT.isAscended(ring)) return true;
        if (entity.world.isRemote) {
            // 客户端无能量判定能力,按模式镜像(能量耗尽时可能有短暂表现差异)
            return true;
        }
        long cost = RingEnergyHandler.price(ring, AE2EnhancedConfig.ring.potionRemoveCost);
        return RingEnergyHandler.consumeFully((EntityPlayer) entity, ring, cost);
    }

    // ==================== 传送/位移 ====================

    /** 标记玩家接下来 ticks 内的位移为本 mod 合法行为(如穿墙自救、Blink). */
    public static void allowTeleport(UUID playerId, long expiryWorldTime) {
        TELEPORT_ALLOW.put(playerId, expiryWorldTime);
    }

    public static boolean isTeleportAllowed(EntityPlayer player) {
        Long expiry = TELEPORT_ALLOW.get(player.getUniqueID());
        return expiry != null && expiry >= player.world.getTotalWorldTime();
    }

    public static void discard(UUID playerId) {
        TELEPORT_ALLOW.remove(playerId);
        HEALTH_BYPASS.remove(playerId);
    }

    // ==================== 内部血量写入 ====================

    /** 指环自身逻辑写血量(保底/恢复)时调用,绕过 setHealth 防护拦截. */
    public static void setHealthInternal(EntityPlayer player, float health) {
        HEALTH_BYPASS.add(player.getUniqueID());
        try {
            player.setHealth(health);
        } finally {
            HEALTH_BYPASS.remove(player.getUniqueID());
        }
    }

    public static boolean isHealthBypassed(EntityPlayer player) {
        return HEALTH_BYPASS.contains(player.getUniqueID());
    }

    // ==================== 永久饱食(飞升) ====================

    /** 飞升指环的饥饿消耗拦截(服务端能量判定在 tick 管理器中完成,此处仅做快速 NBT 判定). */
    public static boolean hasSaturationFeature(EntityPlayer player) {
        ItemStack ring = getRing(player);
        return !ring.isEmpty() && RingNBT.isAscended(ring);
    }
}
