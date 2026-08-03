package com.github.aeddddd.ae2enhanced.omnitool.module;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolNBT;
import com.github.aeddddd.ae2enhanced.util.BossDropHelper;
import com.github.aeddddd.ae2enhanced.util.ForceKillHelper;

/**
 * 战斗模块：真实伤害、AOE 攻击、混沌伤害与禁疗效果。
 * 注：战斗逻辑通过 onLeftClickEntity 触发，工具处于任意模式均可生效。
 */
public class CombatModule implements IOmniToolModule {

    private static final double AOE_RADIUS = 4.0;
    /** 混沌伤害：绝对伤害,对任意保护级别的实体均致死（对应 1.12 的 float.max 强杀）. */
    private static final float CHAOS_DAMAGE_VALUE = Float.MAX_VALUE;

    @Override
    public int getMode() {
        return AdvancedMEOmniToolItem.MODE_UNIVERSAL;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        // 通用守护水晶实体检测：类名包含 GuardianCrystal 或以 CrystalEntity 结尾（覆盖 DE / dechaosislandlegacy 等）
        String className = entity.getClass().getName();
        if ((className.contains("GuardianCrystal") || className.endsWith("CrystalEntity"))
                && AdvancedMEOmniToolItem.hasChaosCore(stack) && !entity.level().isClientSide()) {
            entity.discard();
            return true;
        }

        // 处理多碰撞箱生物（如末影龙、混沌守卫）：点击的是 part，实际伤害 parent
        Entity targetEntity = entity;
        if (targetEntity instanceof PartEntity<?> part) {
            targetEntity = part.getParent();
        }

        if (targetEntity instanceof LivingEntity target) {
            // Shift+左键：范围攻击
            if (player.isShiftKeyDown()) {
                performAreaAttack(stack, player, target, AdvancedMEOmniToolItem.getFortuneLevel(stack));
                return true;
            }

            if (AdvancedMEOmniToolItem.hasChaosCore(stack)
                    && AdvancedMEOmniToolItem.isChaosForceKillEnabled(stack)) {
                applyChaosDamage(target, player, AdvancedMEOmniToolItem.getFortuneLevel(stack));
            } else {
                applyTrueDamage(target, player, getBaseDamage(), AdvancedMEOmniToolItem.getFortuneLevel(stack));
            }
            return true; // 阻止默认攻击逻辑（绕过攻击冷却衰减）
        }
        return false;
    }

    private float getBaseDamage() {
        return AE2EnhancedConfig.COMMON.omniToolBaseAttackDamage.get().floatValue();
    }

    private void performAreaAttack(ItemStack stack, Player player, LivingEntity primaryTarget, int fortune) {
        if (player.level().isClientSide()) return;

        boolean chaosKill = AdvancedMEOmniToolItem.hasChaosCore(stack)
                && AdvancedMEOmniToolItem.isChaosForceKillEnabled(stack);
        float baseDamage = getBaseDamage();

        AABB aoe = new AABB(
                primaryTarget.getX() - AOE_RADIUS, primaryTarget.getY() - AOE_RADIUS, primaryTarget.getZ() - AOE_RADIUS,
                primaryTarget.getX() + AOE_RADIUS, primaryTarget.getY() + AOE_RADIUS, primaryTarget.getZ() + AOE_RADIUS);

        List<LivingEntity> hits = player.level().getEntitiesOfClass(LivingEntity.class, aoe,
                e -> e != null && e.isAlive() && e != player);

        // 确保主目标被包含且只处理一次
        if (!hits.contains(primaryTarget) && primaryTarget.isAlive()) {
            hits.add(primaryTarget);
        }

        for (LivingEntity target : hits) {
            if (target == null || !target.isAlive()) continue;
            if (chaosKill) {
                applyChaosDamage(target, player, fortune);
            } else {
                applyTrueDamage(target, player, baseDamage, fortune);
            }
        }
    }

    /**
     * 应用混沌伤害：扣除固定的混沌伤害值，越过 LivingHurtEvent、护甲、药水、难度缩放、护盾等一切保护。
     * 视觉效果（受击动画、击退）保留在本方法中；核心强制击杀逻辑委托给 {@link ForceKillHelper}。
     */
    private void applyChaosDamage(LivingEntity target, Player player, int fortune) {
        if (target.level().isClientSide()) return;
        if (target.getHealth() <= 0.0f) return;

        // 玩家特殊检查（唤醒睡眠）
        if (target instanceof Player targetPlayer) {
            if (targetPlayer.isSleeping() && !targetPlayer.level().isClientSide()) {
                targetPlayer.stopSleepInBed(true, true);
            }
        }

        target.walkAnimation.setSpeed(1.5f);
        target.setLastHurtByMob(player);
        target.invulnerableTime = target.invulnerableDuration;
        target.hurtTime = target.hurtDuration;
        target.level().broadcastEntityEvent(target, (byte) 2);
        double dx = player.getX() - target.getX();
        double dz = player.getZ() - target.getZ();
        while (dx * dx + dz * dz < 1.0E-4) {
            dx = (Math.random() - Math.random()) * 0.01;
            dz = (Math.random() - Math.random()) * 0.01;
        }
        target.indicateDamage(dx, dz);
        target.knockback(0.4f, dx, dz);

        // 施加禁疗效果（必须在 die 之前，因为死亡流程可能检查此标志）
        applyAntiHeal(target);

        // 设置玩家击杀标记，帮助自定义 Boss 掉落逻辑识别击杀来源
        markAsPlayerKill(target, player);

        DamageSource chaosSource = player.damageSources().source(AdvancedMEOmniToolItem.CHAOS_DAMAGE_TYPE, player,
                player);

        // 核心强制击杀逻辑
        ForceKillHelper.applyForceKill(target, player, CHAOS_DAMAGE_VALUE, chaosSource);

        // 尝试生成特殊 Boss 掉落物（如额外植物学盖亚 III 等自定义掉落实体）
        if (!target.level().isClientSide() && !target.isAlive()) {
            BossDropHelper.trySpawnBossDrops(target, player, chaosSource, fortune);
        }

        // 最后保险：如果实体仍然没有被移除，在下一 tick 开头强制从 level 剔除
        if (!target.level().isClientSide() && target.level().getServer() != null) {
            final LivingEntity toRemove = target;
            target.level().getServer().execute(() -> {
                if (!toRemove.isRemoved() && toRemove.level() != null) {
                    try {
                        ForceKillHelper.forceSetRemoved(toRemove);
                    } catch (Exception e) {
                        AE2Enhanced.LOGGER.error("[AE2E] forceSetRemoved failed", e);
                    }
                }
            });
        }
    }

    /**
     * 应用完全锁定的真实伤害：直接修改血量，绕过 LivingHurtEvent / LivingDamageEvent / 护甲 / 药水 / 难度缩放。
     * 受击动画、无敌帧与击退手动复现。
     */
    private void applyTrueDamage(LivingEntity target, Player player, float damage, int fortune) {
        if (target.level().isClientSide()) return;
        if (target.getHealth() <= 0.0f) return;

        // 玩家特殊检查（唤醒睡眠）
        if (target instanceof Player targetPlayer) {
            if (targetPlayer.isSleeping() && !targetPlayer.level().isClientSide()) {
                targetPlayer.stopSleepInBed(true, true);
            }
        }

        target.walkAnimation.setSpeed(1.5f);

        float newHealth = target.getHealth() - damage;

        // 复仇目标
        target.setLastHurtByMob(player);

        // 受伤动画与无敌帧
        target.invulnerableTime = target.invulnerableDuration;
        target.hurtTime = target.hurtDuration;
        target.level().broadcastEntityEvent(target, (byte) 2);

        // 击退
        double dx = player.getX() - target.getX();
        double dz = player.getZ() - target.getZ();
        while (dx * dx + dz * dz < 1.0E-4) {
            dx = (Math.random() - Math.random()) * 0.01;
            dz = (Math.random() - Math.random()) * 0.01;
        }
        target.indicateDamage(dx, dz);
        target.knockback(0.4f, dx, dz);

        // 直接血量修改（绕过所有伤害计算事件和修饰）
        if (newHealth <= 0.0f) {
            // 设置玩家击杀标记（写入 lastHurtByPlayer + lastHurtByPlayerTime = 100），
            // 帮助依赖该标记的掉落/经验逻辑正常触发
            markAsPlayerKill(target, player);
            target.setHealth(0.0f);
            DamageSource source = player.damageSources().source(AdvancedMEOmniToolItem.OMNITOOL_DAMAGE_TYPE, player,
                    player);
            target.die(source);
            // 尝试生成特殊 Boss 掉落物
            if (!target.level().isClientSide() && !target.isAlive()) {
                BossDropHelper.trySpawnBossDrops(target, player, source, fortune);
            }
        } else {
            target.setHealth(newHealth);
        }
    }

    /**
     * 设置实体的玩家击杀标记（lastHurtByPlayer + lastHurtByPlayerTime = 100），
     * 帮助依赖该标记的 Boss 掉落逻辑正常触发。
     */
    private static void markAsPlayerKill(LivingEntity target, Player player) {
        target.setLastHurtByPlayer(player);
    }

    // ==================== Anti-Heal ====================

    /**
     * 禁疗实体追踪集合（弱引用,防止泄漏）.
     * tick 兜底只遍历该集合而非全维度实体；存档重载后由 EntityJoinLevelEvent 依据 NBT 重新登记.
     */
    private static final Set<LivingEntity> ANTI_HEAL_TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void applyAntiHeal(LivingEntity entity) {
        entity.getPersistentData().putBoolean(OmniToolNBT.ANTI_HEAL, true);
        ANTI_HEAL_TRACKED.add(entity);
    }

    public static boolean hasAntiHeal(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(OmniToolNBT.ANTI_HEAL);
    }

    public static void clearAntiHeal(LivingEntity entity) {
        entity.getPersistentData().remove(OmniToolNBT.ANTI_HEAL);
        ANTI_HEAL_TRACKED.remove(entity);
    }

    /** 登记已带禁疗标记的实体（存档重载/跨维度后调用）. */
    public static void trackAntiHeal(LivingEntity entity) {
        ANTI_HEAL_TRACKED.add(entity);
    }

    /** 供 tick 兜底遍历（调用方需自行复制快照）. */
    public static Set<LivingEntity> getAntiHealTracked() {
        return ANTI_HEAL_TRACKED;
    }
}
