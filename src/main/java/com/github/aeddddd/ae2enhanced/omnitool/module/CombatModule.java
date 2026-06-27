package com.github.aeddddd.ae2enhanced.omnitool.module;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolNBT;
import com.github.aeddddd.ae2enhanced.util.BossDropHelper;
import com.github.aeddddd.ae2enhanced.util.ForceKillHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;

/**
 * 战斗模块：真实伤害、AOE 攻击与禁疗效果。
 * 注：战斗逻辑通过 onLeftClickEntity 触发，工具处于任意模式均可生效。
 */
public class CombatModule implements IOmniToolModule {

    private static final double AOE_RADIUS = 4.0;
    private static final float CHAOS_DAMAGE_VALUE = 1000.0f;
    private static final String DE_CHAOS_CRYSTAL_CLASS = "com.brandon3055.draconicevolution.blocks.ChaosCrystal";

    @Override
    public int getMode() {
        return ItemAdvancedMEOmniTool.MODE_UNIVERSAL;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        // 通用守护水晶实体检测：类名包含 GuardianCrystal 或 CrystalEntity（覆盖 DE / dechaosislandlegacy 等）
        String className = entity.getClass().getName();
        if ((className.contains("GuardianCrystal") || className.endsWith("CrystalEntity"))
                && ItemAdvancedMEOmniTool.hasChaosCore(stack) && !entity.world.isRemote) {
            entity.setDead();
            return true;
        }

        // 处理多碰撞箱生物（如末影龙、混沌守卫）：点击的是 part，实际伤害 parent
        Entity targetEntity = entity;
        if (targetEntity instanceof MultiPartEntityPart) {
            targetEntity = (Entity) ((MultiPartEntityPart) targetEntity).parent;
        }

        if (targetEntity instanceof EntityLivingBase) {
            EntityLivingBase target = (EntityLivingBase) targetEntity;

            // Shift+左键：范围攻击
            if (player.isSneaking()) {
                performAreaAttack(stack, player, target, ItemAdvancedMEOmniTool.getFortuneLevel(stack));
                return true;
            }

            if (ItemAdvancedMEOmniTool.hasChaosCore(stack) && ItemAdvancedMEOmniTool.isChaosForceKillEnabled(stack)) {
                applyChaosDamage(target, player, ItemAdvancedMEOmniTool.getFortuneLevel(stack));
            } else {
                applyTrueDamage(target, player, getBaseDamage(), ItemAdvancedMEOmniTool.OMNITOOL_DAMAGE,
                        ItemAdvancedMEOmniTool.getFortuneLevel(stack));
            }
            return true; // 阻止默认攻击逻辑（绕过攻击冷却衰减）
        }
        return false;
    }

    private float getBaseDamage() {
        return (float) AE2EnhancedConfig.omniTool.baseAttackDamage;
    }

    private void performAreaAttack(ItemStack stack, EntityPlayer player, EntityLivingBase primaryTarget, int fortune) {
        if (player.world.isRemote) return;

        boolean chaosKill = ItemAdvancedMEOmniTool.hasChaosCore(stack) && ItemAdvancedMEOmniTool.isChaosForceKillEnabled(stack);
        float baseDamage = getBaseDamage();

        AxisAlignedBB aoe = new AxisAlignedBB(
                primaryTarget.posX - AOE_RADIUS, primaryTarget.posY - AOE_RADIUS, primaryTarget.posZ - AOE_RADIUS,
                primaryTarget.posX + AOE_RADIUS, primaryTarget.posY + AOE_RADIUS, primaryTarget.posZ + AOE_RADIUS);

        List<EntityLivingBase> hits = player.world.getEntitiesWithinAABB(EntityLivingBase.class, aoe,
                e -> e != null && e.isEntityAlive() && e != player);

        // 确保主目标被包含且只处理一次
        if (!hits.contains(primaryTarget) && primaryTarget.isEntityAlive()) {
            hits.add(primaryTarget);
        }

        for (EntityLivingBase target : hits) {
            if (target == null || !target.isEntityAlive()) continue;
            if (chaosKill) {
                applyChaosDamage(target, player, fortune);
            } else {
                applyTrueDamage(target, player, baseDamage, ItemAdvancedMEOmniTool.OMNITOOL_DAMAGE, fortune);
            }
        }
    }

    /**
     * 应用混沌伤害：扣除配置指定的混沌伤害值，越过 LivingHurtEvent、护甲、药水、难度缩放、护盾等一切保护。
     * 视觉效果（受击动画、击退）保留在本方法中；核心强制击杀逻辑委托给 {@link ForceKillHelper}。
     */
    private void applyChaosDamage(EntityLivingBase target, EntityPlayer player, int fortune) {
        if (target.world.isRemote) return;
        if (target.getHealth() <= 0.0f) return;

        // 玩家特殊检查（唤醒睡眠）
        if (target instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) target;
            if (targetPlayer.isPlayerSleeping() && !targetPlayer.world.isRemote) {
                targetPlayer.wakeUpPlayer(true, true, false);
            }
        }

        target.limbSwingAmount = 1.5f;
        target.setRevengeTarget(player);
        target.hurtResistantTime = target.maxHurtResistantTime;
        target.hurtTime = target.maxHurtTime;
        target.world.setEntityState(target, (byte) 2);
        double dx = player.posX - target.posX;
        double dz = player.posZ - target.posZ;
        while (dx * dx + dz * dz < 1.0E-4) {
            dx = (Math.random() - Math.random()) * 0.01;
            dz = (Math.random() - Math.random()) * 0.01;
        }
        target.attackedAtYaw = (float)(MathHelper.atan2(dz, dx) * 57.29577951308232 - (double)target.rotationYaw);
        target.knockBack(player, 0.4f, dx, dz);

        // 施加禁疗效果（必须在 onDeath 之前，因为 Mixin 注入会检查此标志）
        applyAntiHeal(target);

        // 设置玩家击杀标记，帮助自定义 Boss 掉落逻辑识别击杀来源
        markAsPlayerKill(target, player);

        // 核心强制击杀逻辑
        ForceKillHelper.applyForceKill(target, player, CHAOS_DAMAGE_VALUE, ItemAdvancedMEOmniTool.CHAOS_DAMAGE);

        // 尝试生成特殊 Boss 掉落物（如额外植物学盖亚 III 等自定义掉落实体）
        if (!target.world.isRemote && !target.isEntityAlive()) {
            BossDropHelper.trySpawnBossDrops(target, player, ItemAdvancedMEOmniTool.CHAOS_DAMAGE, fortune);
        }

        // 最后保险：如果实体仍然没有被移除，在下一 tick 开头强制从 world 剔除
        if (!target.world.isRemote && target.world.getMinecraftServer() != null) {
            final EntityLivingBase toRemove = target;
            target.world.getMinecraftServer().addScheduledTask(() -> {
                if (!toRemove.isDead && toRemove.world != null) {
                    try {
                        toRemove.world.removeEntityDangerously(toRemove);
                    } catch (Exception e) {
                        AE2Enhanced.LOGGER.error("[AE2E] removeEntityDangerously failed", e);
                    }
                }
            });
        }
    }

    /**
     * 应用完全锁定的真实伤害：直接修改血量，绕过 LivingHurtEvent / LivingDamageEvent / 护甲 / 药水 / 难度缩放。
     */
    private void applyTrueDamage(EntityLivingBase target, EntityPlayer player, float damage, DamageSource source, int fortune) {
        if (target.world.isRemote) return;
        if (target.getHealth() <= 0.0f) return;

        // 玩家特殊检查（唤醒睡眠）
        if (target instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) target;
            if (targetPlayer.isPlayerSleeping() && !targetPlayer.world.isRemote) {
                targetPlayer.wakeUpPlayer(true, true, false);
            }
        }

        target.limbSwingAmount = 1.5f;

        float newHealth = target.getHealth() - damage;

        // 复仇目标
        target.setRevengeTarget(player);

        // 受伤动画与无敌帧
        target.hurtResistantTime = target.maxHurtResistantTime;
        target.hurtTime = target.maxHurtTime;
        target.world.setEntityState(target, (byte) 2);

        // 击退
        double dx = player.posX - target.posX;
        double dz = player.posZ - target.posZ;
        while (dx * dx + dz * dz < 1.0E-4) {
            dx = (Math.random() - Math.random()) * 0.01;
            dz = (Math.random() - Math.random()) * 0.01;
        }
        target.attackedAtYaw = (float)(MathHelper.atan2(dz, dx) * 57.29577951308232 - (double)target.rotationYaw);
        target.knockBack(player, 0.4f, dx, dz);

        // 直接血量修改（绕过所有伤害计算事件和修饰）
        if (newHealth <= 0.0f) {
            // 设置玩家击杀标记，帮助自定义 Boss 掉落逻辑识别击杀来源
            markAsPlayerKill(target, player);
            target.setHealth(0.0f);
            target.onDeath(source);
            // 尝试生成特殊 Boss 掉落物
            if (!target.world.isRemote && !target.isEntityAlive()) {
                BossDropHelper.trySpawnBossDrops(target, player, source, fortune);
            }
        } else {
            target.setHealth(newHealth);
        }
    }

    /**
     * 反射设置 EntityLivingBase 的玩家击杀标记，帮助依赖该标记的 Boss 掉落逻辑正常触发。
     */
    private static void markAsPlayerKill(EntityLivingBase target, EntityPlayer player) {
        try {
            java.lang.reflect.Field attackingPlayer = EntityLivingBase.class.getDeclaredField("attackingPlayer");
            attackingPlayer.setAccessible(true);
            attackingPlayer.set(target, player);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to set attackingPlayer", e);
        }
        try {
            java.lang.reflect.Field recentlyHit = EntityLivingBase.class.getDeclaredField("recentlyHit");
            recentlyHit.setAccessible(true);
            recentlyHit.setInt(target, 100);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to set recentlyHit", e);
        }
    }

    // ==================== Anti-Heal ====================

    public static void applyAntiHeal(EntityLivingBase entity) {
        entity.getEntityData().setBoolean(OmniToolNBT.ANTI_HEAL, true);
    }

    public static boolean hasAntiHeal(EntityLivingBase entity) {
        return entity.getEntityData().getBoolean(OmniToolNBT.ANTI_HEAL);
    }

    public static void clearAntiHeal(EntityLivingBase entity) {
        entity.getEntityData().removeTag(OmniToolNBT.ANTI_HEAL);
    }
}
