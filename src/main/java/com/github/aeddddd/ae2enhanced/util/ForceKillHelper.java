package com.github.aeddddd.ae2enhanced.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.mixin.accessor.EntityAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.LivingEntityAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.SynchedDataItemInvoker;
import com.github.aeddddd.ae2enhanced.mixin.accessor.SynchedEntityDataAccessor;

/**
 * 通用强制击杀/强制移除辅助类（1.20.1 重写版,对应 1.12 的 EntityDataManager 体系）.
 * <p>
 * 提供绕过实体内部保护机制、直接修改底层同步数据（SynchedEntityData）、强制标记移除、
 * 通知外部管理器以及清理多碰撞箱子实体等底层工具.
 * 原版类的私有成员访问全部通过 mixin accessor/invoker 完成（编译期安全）；
 * 反射仅用于对未知模组实体类的动态模式匹配（保护开关扫描、自定义血量参数扫描、管理器通知）,
 * 不依赖任何特定模组的具体类名.
 */
public final class ForceKillHelper {

    private ForceKillHelper() {}

    // ==================== Public API ====================

    /** 真空衰变伤害类型（黑洞 / 微型奇点事件视界与全能工具混沌核心共用）. */
    public static final ResourceKey<DamageType> VACUUM_DECAY_DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(AE2Enhanced.MOD_ID, "vacuum_decay"));

    /** 构造无攻击者的真空衰变伤害源（环境击杀用）. */
    public static DamageSource vacuumDecay(Level level) {
        return level.damageSources().source(VACUUM_DECAY_DAMAGE_TYPE);
    }

    /**
     * 环境型强制击杀（黑洞 / 微型奇点事件视界,对应 1.12 的 applyEnvironmentDamage）.
     * <p>
     * 针对玩家和非玩家实体采用不同策略：
     * <ul>
     *   <li>玩家：仅使用标准 {@code hurt} / {@code setHealth(0)} + {@code die},
     *       避免触发强力装备（如神龙套 / 无尽套）的复活、拦截或反伤副作用.</li>
     *   <li>非玩家实体：使用完整递进流程（绕过保护 → 标准伤害 → 直接改血 → 底层同步数据
     *       → 强制移除 → 多碰撞箱子清理 → Boss 管理器通知）,确保混沌守卫等受保护实体被彻底移除.</li>
     * </ul>
     *
     * @param target 目标实体
     * @param source 伤害源
     * @param damage 伤害值
     */
    public static void applyEnvironmentDamage(LivingEntity target, DamageSource source, float damage) {
        if (target.level().isClientSide()) return;
        if (target.getHealth() <= 0.0f) return;

        target.invulnerableTime = 0;
        target.hurtTime = 0;

        if (target instanceof Player) {
            if (!target.hurt(source, damage)) {
                target.setHealth(0.0f);
                target.die(source);
            }
            return;
        }

        forceBypassProtection(target);
        boolean killed = false;
        if (target.hurt(source, damage)) {
            if (!target.isAlive()) {
                killed = true;
            }
        }
        if (!killed) {
            target.setHealth(0.0f);
            target.die(source);
        }
        if (target.isAlive()) {
            forceSetHealthViaSyncedData(target, 0.0f);
            target.die(source);
            if (!target.isRemoved()) {
                forceSetRemoved(target);
            }
            removeMultipartChildren(target);
            tryNotifyBossManager(target);
        }
    }

    /**
     * 综合强制击杀流程.
     * <p>
     * 依次执行：绕过内部保护开关 → 通过 invoker 走实体自身伤害管线施加致死伤害
     * （对应 1.12 的 {@code damageEntity} 调用,对混沌守卫级别实体的自定义管线同样生效）
     * → 血量未归零时回退到直接改血 / 底层 DataItem 修改
     * → 触发 {@link LivingEntity#die} → 强制标记移除并清理子实体 → 尝试通知外部管理器.
     * 全流程不依赖任何特定模组类名,对任意保护级别的实体通用.
     *
     * @param target      目标实体
     * @param attacker    攻击者（用于设置复仇目标等；可为 null）
     * @param damage      伤害值（调用方应传入 {@link Float#MAX_VALUE} 级别的绝对伤害）
     * @param deathSource 死亡事件使用的 DamageSource
     */
    public static void applyForceKill(LivingEntity target, Entity attacker, float damage, DamageSource deathSource) {
        if (target.level().isClientSide()) return;
        if (target.getHealth() <= 0.0f) return;

        if (attacker instanceof LivingEntity living) {
            target.setLastHurtByMob(living);
        }

        // 绕过内部保护开关
        forceBypassProtection(target);

        // 主路径：驱动实体自身的伤害管线处理致死伤害（混沌伤害类型无视护甲,不被吸收减免）
        ((LivingEntityAccessor) target).ae2e$actuallyHurt(deathSource, damage);

        // 兜底：伤害管线被子类拦截（血量未归零）→ 直接改血,再退到底层同步数据
        if (target.getHealth() > 0.0f) {
            target.setHealth(0.0f);
            if (target.getHealth() > 0.0f) {
                forceSetHealthViaSyncedData(target, 0.0f);
            }
        }

        // 触发死亡回调
        target.die(deathSource);
        if (!target.isRemoved()) {
            forceBypassProtection(target);
            forceSetRemoved(target);
            removeMultipartChildren(target);
            tryNotifyBossManager(target);
        }
    }

    /**
     * 强制绕过实体的内部保护机制.
     * <p>
     * 某些实体类通过私有布尔开关阻止外部对其血量或存活状态的修改；
     * 本方法在运行时遍历该实体类及其父类的全部字段,找到名字匹配
     * {@code allowProtectedHealthChange} / {@code allowProtectedRemoval}
     * 或包含 {@code ProtectedHealth} / {@code ProtectedRemoval} 的布尔字段并将其设为 {@code true}.
     */
    public static void forceBypassProtection(LivingEntity entity) {
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() != boolean.class) continue;
                String name = f.getName();
                if (name.equals("allowProtectedHealthChange") || name.contains("ProtectedHealth")) {
                    try {
                        f.setAccessible(true);
                        f.setBoolean(entity, true);
                    } catch (Exception e) {
                        AE2Enhanced.LOGGER.error("[AE2E] forceBypassProtection (health) failed", e);
                    }
                }
                if (name.equals("allowProtectedRemoval") || name.contains("ProtectedRemoval")) {
                    try {
                        f.setAccessible(true);
                        f.setBoolean(entity, true);
                    } catch (Exception e) {
                        AE2Enhanced.LOGGER.error("[AE2E] forceBypassProtection (removal) failed", e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 直接通过底层同步数据管理器修改实体的血量参数.
     * <p>
     * 适用于实体的 {@code setHealth()} 被子类覆盖、导致常规血量修改被拦截的场景.
     * 本方法会先扫描实体类及其父类中所有 {@code EntityDataAccessor<Float>} 静态字段
     * （动态模式匹配,覆盖模组实体的自定义血量参数）,然后直接修改对应的 DataItem.value
     * 并标记 dirty,最后以公开的 {@code SynchedEntityData#set} 作为回退.
     */
    public static void forceSetHealthViaSyncedData(LivingEntity entity, float health) {
        try {
            SynchedEntityData syncedData = ((EntityAccessor) entity).ae2e$getEntityData();

            // 收集实体类层次中全部 EntityDataAccessor<Float> 静态字段
            List<EntityDataAccessor<Float>> healthParams = collectFloatDataAccessors(entity.getClass());
            // 回退：始终包含 LivingEntity.DATA_HEALTH_ID
            EntityDataAccessor<Float> vanillaHealth = LivingEntityAccessor.ae2e$getDataHealthId();
            if (!healthParams.contains(vanillaHealth)) {
                healthParams.add(vanillaHealth);
            }

            float clamped = Mth.clamp(health, 0.0f, entity.getMaxHealth());

            // 方法 1：直接修改 DataItem.value + dirty,绕过任何对 set() 的覆盖
            Int2ObjectMap<SynchedEntityData.DataItem<?>> items =
                    ((SynchedEntityDataAccessor) syncedData).ae2e$getItemsById();
            boolean anyModified = false;
            for (EntityDataAccessor<Float> param : healthParams) {
                SynchedEntityData.DataItem<?> dataItem = items.get(param.getId());
                if (dataItem == null) continue;
                ((SynchedDataItemInvoker) dataItem).ae2e$setValue(clamped);
                ((SynchedDataItemInvoker) dataItem).ae2e$setDirty(true);
                anyModified = true;
            }
            if (anyModified) {
                return;
            }

            // 方法 2：公开 set() 回退（触发 onSyncedDataUpdated 通知）
            for (EntityDataAccessor<Float> param : healthParams) {
                syncedData.set(param, clamped);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] forceSetHealthViaSyncedData failed", e);
        }
    }

    /**
     * 强制将实体标记为已移除（对应 1.12 的强制 isDead）.
     * <p>
     * 优先调用公开的 {@code remove(RemovalReason.KILLED)}；若实体仍存活
     * （例如子类覆盖了 {@code remove()}）,则直接调用 {@code setRemoved()} ——
     * 该方法为 public final,不可被覆盖,且内部会触发 {@code levelCallback.onRemove}
     * 等完整移除回调,不会跳过清理逻辑.
     */
    public static void forceSetRemoved(Entity entity) {
        try {
            entity.remove(Entity.RemovalReason.KILLED);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] forceSetRemoved: remove() failed", e);
        }
        if (!entity.isRemoved()) {
            try {
                entity.setRemoved(Entity.RemovalReason.KILLED);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] forceSetRemoved: setRemoved() failed", e);
            }
        }
    }

    /**
     * 尝试通知关联的管理器对象：目标实体已死亡.
     * <p>
     * 某些复杂实体由外部管理器对象负责生命周期（例如记录击杀状态、触发阶段转换、
     * 阻止重新生成）.如果该实体死亡后未通知其管理器,管理器可能在超时后重新生成该实体.
     * 本方法通过模式匹配在实体类上查找返回类型名包含 {@code manager} / {@code fight} / {@code boss}
     * 的无参方法,获取管理器实例后,调用名字包含 {@code death} / {@code complete} / {@code finish}
     * 的方法；若找不到合适的回调方法,则尝试将管理器中名字包含 {@code killed} / {@code dead} /
     * {@code defeated} 的布尔字段设为 {@code true}.
     */
    public static void tryNotifyBossManager(LivingEntity boss) {
        try {
            Object manager = null;
            for (Method m : boss.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() != 0) continue;
                String retName = m.getReturnType().getSimpleName().toLowerCase();
                if (retName.contains("manager") || retName.contains("fight") || retName.contains("boss")) {
                    m.setAccessible(true);
                    Object candidate = m.invoke(boss);
                    if (candidate != null) {
                        manager = candidate;
                        break;
                    }
                }
            }
            if (manager == null) return;

            boolean deathCalled = false;
            for (Method m : manager.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() != 1) continue;
                String name = m.getName().toLowerCase();
                if (name.contains("death") || name.contains("complete") || name.contains("finish")) {
                    m.setAccessible(true);
                    try {
                        m.invoke(manager, boss);
                        deathCalled = true;
                    } catch (Exception ignored) {}
                }
            }

            if (!deathCalled) {
                for (Field f : manager.getClass().getDeclaredFields()) {
                    if (f.getType() != boolean.class) continue;
                    String name = f.getName().toLowerCase();
                    if (name.contains("killed") || name.contains("dead") || name.contains("defeated")) {
                        f.setAccessible(true);
                        f.setBoolean(manager, true);
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] tryNotifyBossManager failed", e);
        }
    }

    /**
     * 强制移除实体的多碰撞箱子实体（PartEntity children）.
     * <p>
     * 某些大型实体使用 {@link PartEntity} 数组表示多个碰撞箱；
     * 父实体死亡后,子实体也应同步被标记移除.
     */
    public static void removeMultipartChildren(Entity parent) {
        Class<?> clazz = parent.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                Class<?> type = f.getType();
                if (!type.isArray() || !PartEntity.class.isAssignableFrom(type.getComponentType())) continue;
                try {
                    f.setAccessible(true);
                    Object[] parts = (Object[]) f.get(parent);
                    if (parts != null) {
                        for (Object part : parts) {
                            if (part instanceof Entity partEntity) {
                                forceSetRemoved(partEntity);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 收集类层次中全部 {@code EntityDataAccessor<Float>} 静态字段的值.
     * 此为对未知模组实体类的动态模式匹配,mixin 无法覆盖,故保留反射.
     */
    @SuppressWarnings("unchecked")
    private static List<EntityDataAccessor<Float>> collectFloatDataAccessors(Class<?> entityClass) {
        List<EntityDataAccessor<Float>> params = new ArrayList<>();
        Class<?> clazz = entityClass;
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() != EntityDataAccessor.class) continue;
                Type genericType = f.getGenericType();
                if (!(genericType instanceof ParameterizedType parameterized)) continue;
                Type[] args = parameterized.getActualTypeArguments();
                if (args.length == 0 || !"java.lang.Float".equals(args[0].getTypeName())) continue;
                try {
                    f.setAccessible(true);
                    Object param = f.get(null);
                    if (param instanceof EntityDataAccessor) {
                        params.add((EntityDataAccessor<Float>) param);
                    }
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return params;
    }
}
