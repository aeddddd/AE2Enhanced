package com.github.aeddddd.ae2enhanced.util.reflection;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.util.DamageSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 原版实体成员(Entity / EntityLivingBase)的反射访问.
 *
 * <p>原本由 accessor mixin(IEntityAccessor / IEntityLivingBaseAccessor)提供, 但生产环境下
 * MC 成员名是 SRG 名(如 {@code field_70178_ae}), accessor 需要经 refmap 把 MCP 名映射过去,
 * 而 Mixin AP 0.8.5 生成的 refmap 键与运行期 MixinBooter 10.7 的查找键不一致, 两个 accessor
 * mixin 应用失败(日志: InvalidAccessorException No candidates were found matching isImmuneToFire /
 * attackingPlayer). 失败的 accessor mixin 仍会把接口合并进目标类, 于是调用点抛
 * AbstractMethodError —— 且该错误发生在 {@code initGui} 内时会让 GUI 停在未初始化状态.
 * 反射按"MCP 名 → SRG 名"顺序探测, 开发环境与生产环境均可用.</p>
 */
public final class VanillaReflectionHelper {

    private static final Field IMMUNE_TO_FIRE =
            ReflectionHelper.findAnyFieldInHierarchy(Entity.class, "isImmuneToFire", "field_70178_ae");
    private static final Field ATTACKING_PLAYER =
            ReflectionHelper.findAnyFieldInHierarchy(EntityLivingBase.class, "attackingPlayer", "field_70717_bb");
    private static final Field RECENTLY_HIT =
            ReflectionHelper.findAnyFieldInHierarchy(EntityLivingBase.class, "recentlyHit", "field_70718_bc");
    private static final Field HEALTH_PARAMETER =
            ReflectionHelper.findAnyFieldInHierarchy(EntityLivingBase.class, "HEALTH", "field_184632_c");
    private static final Method DAMAGE_ENTITY = ReflectionHelper.findAnyMethodInHierarchy(
            EntityLivingBase.class, new Class<?>[] {DamageSource.class, float.class},
            "damageEntity", "func_70665_d");

    private VanillaReflectionHelper() {}

    /** 设置实体免疫火焰, 返回 false 表示字段不可用(调用方自行降级). */
    public static boolean setImmuneToFire(Entity entity, boolean immune) {
        if (IMMUNE_TO_FIRE == null) {
            return false;
        }
        try {
            IMMUNE_TO_FIRE.setBoolean(entity, immune);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 设置"被玩家击杀"标记(attackingPlayer), 供依赖该标记的掉落逻辑触发; 失败返回 false. */
    public static boolean setAttackingPlayer(EntityLivingBase target, EntityPlayer player) {
        if (ATTACKING_PLAYER == null) {
            return false;
        }
        try {
            ATTACKING_PLAYER.set(target, player);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 设置 recentlyHit(受击倒计时, 100 表示玩家击杀), 失败返回 false. */
    public static boolean setRecentlyHit(EntityLivingBase target, int value) {
        if (RECENTLY_HIT == null) {
            return false;
        }
        try {
            RECENTLY_HIT.setInt(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** EntityLivingBase.HEALTH 数据参数(静态字段), 不可用时返回 null. */
    @SuppressWarnings("unchecked")
    public static DataParameter<Float> getHealthParameter() {
        if (HEALTH_PARAMETER == null) {
            return null;
        }
        try {
            return (DataParameter<Float>) HEALTH_PARAMETER.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 直接调用 damageEntity, 绕过 Forge 伤害事件; 失败返回 false. */
    public static boolean damageEntity(EntityLivingBase target, DamageSource source, float amount) {
        if (DAMAGE_ENTITY == null) {
            return false;
        }
        try {
            DAMAGE_ENTITY.invoke(target, source, amount);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
