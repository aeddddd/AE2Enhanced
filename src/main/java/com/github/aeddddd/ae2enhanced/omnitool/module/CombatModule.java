package com.github.aeddddd.ae2enhanced.omnitool.module;

import java.util.List;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;

/**
 * 战斗模块：真实伤害与 Shift+左键 AOE 攻击。
 * 注：战斗逻辑通过 onLeftClickEntity 触发，工具处于任意模式均可生效。
 * 1.12 中混沌核心相关的全部逻辑（混沌伤害、禁疗、强制击杀、Boss 掉落）未移植。
 */
public class CombatModule implements IOmniToolModule {

    private static final double AOE_RADIUS = 4.0;

    @Override
    public int getMode() {
        return AdvancedMEOmniToolItem.MODE_UNIVERSAL;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        // 处理多碰撞箱生物（如末影龙）：点击的是 part，实际伤害 parent
        Entity targetEntity = entity;
        if (targetEntity instanceof PartEntity<?> part) {
            targetEntity = part.getParent();
        }

        if (targetEntity instanceof LivingEntity target) {
            // Shift+左键：范围攻击
            if (player.isShiftKeyDown()) {
                performAreaAttack(player, target);
                return true;
            }

            applyTrueDamage(target, player, getBaseDamage());
            return true; // 阻止默认攻击逻辑（绕过攻击冷却衰减）
        }
        return false;
    }

    private float getBaseDamage() {
        return AE2EnhancedConfig.COMMON.omniToolBaseAttackDamage.get().floatValue();
    }

    private void performAreaAttack(Player player, LivingEntity primaryTarget) {
        if (player.level().isClientSide()) return;

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
            applyTrueDamage(target, player, baseDamage);
        }
    }

    /**
     * 应用完全锁定的真实伤害：直接修改血量，绕过 LivingHurtEvent / LivingDamageEvent / 护甲 / 药水 / 难度缩放。
     * 受击动画、无敌帧与击退手动复现。
     */
    private void applyTrueDamage(LivingEntity target, Player player, float damage) {
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
            target.setLastHurtByPlayer(player);
            target.setHealth(0.0f);
            DamageSource source = player.damageSources().source(AdvancedMEOmniToolItem.OMNITOOL_DAMAGE_TYPE, player,
                    player);
            target.die(source);
        } else {
            target.setHealth(newHealth);
        }
    }
}
