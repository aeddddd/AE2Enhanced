package com.github.aeddddd.ae2enhanced.util;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.termination.TerminationRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用强制击杀/强制移除辅助类。
 * <p>
 * 击杀主路径为 L4 终止事务：一次 {@link EntityLivingBase#onDeath} 保留掉落与归属，
 * 直写 {@link Entity#isDead} 交给世界 tick 移除，再由终止登记做有界复核。
 * 反射仅用于内部保护开关、isDead 字段兜底、Boss 管理器通知与多部件清理，
 * 不再在击杀路径上枚举或推断健康数据通道。
 */
public final class ForceKillHelper {

    private ForceKillHelper() {}

    // ---- Reflection caches for direct data manager manipulation ----

    private static final Field ENTITY_DATA_MANAGER;
    private static final Field ELB_HEALTH_PARAM;
    private static final List<Method> DATA_MANAGER_CANDIDATES = new ArrayList<>();
    private static final Field ENTITY_IS_DEAD;
    private static final Field EDM_ENTRIES;
    private static final Field EDM_DIRTY;
    private static final Method DATA_PARAM_GET_ID;
    private static final Method DATA_ENTRY_SET_VALUE;
    private static final Method DATA_ENTRY_SET_DIRTY;

    static {
        Field dm = null;
        Field hp = null;
        Field isDead = null;
        Field edmEntries = null;
        Field edmDirty = null;
        Method dataParamGetId = null;
        Method dataEntrySetValue = null;
        Method dataEntrySetDirty = null;

        try {
            // 1. Entity.dataManager
            for (Field f : Entity.class.getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("EntityDataManager")) {
                    dm = f;
                    dm.setAccessible(true);
                    break;
                }
            }

            // 2. EntityLivingBase.HEALTH (DataParameter<Float>)
            for (Field f : EntityLivingBase.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) &&
                    f.getType().getSimpleName().equals("DataParameter")) {
                    Type genericType = f.getGenericType();
                    if (genericType instanceof ParameterizedType) {
                        Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
                        if (args.length > 0 && "java.lang.Float".equals(args[0].getTypeName())) {
                            hp = f;
                            hp.setAccessible(true);
                            break;
                        }
                    }
                }
            }

            // 3. EntityDataManager methods and fields
            if (dm != null) {
                Class<?> dataManagerClass = dm.getType();

                // 3a. set / setEntry candidates
                for (Method m : dataManagerClass.getDeclaredMethods()) {
                    if (m.getParameterCount() == 2 &&
                        m.getParameterTypes()[0].getSimpleName().equals("DataParameter") &&
                        m.getParameterTypes()[1] == Object.class) {
                        String name = m.getName();
                        boolean isSet = name.equals("set") || name.equals("func_187227_b");
                        boolean isSetEntry = name.equals("setEntry") || name.equals("func_187226_a");
                        if (isSet || isSetEntry) {
                            m.setAccessible(true);
                            if (isSet) {
                                DATA_MANAGER_CANDIDATES.add(0, m);
                            } else {
                                DATA_MANAGER_CANDIDATES.add(m);
                            }
                        }
                    }
                }

                // 3b. entries map (the only Map field)
                List<Field> boolFields = new ArrayList<>();
                for (Field f : dataManagerClass.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        edmEntries = f;
                        edmEntries.setAccessible(true);
                    } else if (f.getType() == boolean.class) {
                        boolFields.add(f);
                    }
                }
                // dirty field: try name match first, otherwise take the last boolean field
                for (Field f : boolFields) {
                    String name = f.getName();
                    if (name.equals("dirty") || name.contains("187232")) {
                        edmDirty = f;
                        edmDirty.setAccessible(true);
                        break;
                    }
                }
                if (edmDirty == null && !boolFields.isEmpty()) {
                    edmDirty = boolFields.get(boolFields.size() - 1);
                    edmDirty.setAccessible(true);
                }
            }

            // 4. DataParameter.getId()
            Class<?> paramClass = Class.forName("net.minecraft.network.datasync.DataParameter");
            for (Method m : paramClass.getDeclaredMethods()) {
                if (m.getReturnType() == int.class && m.getParameterCount() == 0) {
                    dataParamGetId = m;
                    dataParamGetId.setAccessible(true);
                    break;
                }
            }

            // 5. DataEntry.setValue() / setDirty()
            Class<?> entryClass = Class.forName("net.minecraft.network.datasync.EntityDataManager$DataEntry");
            for (Method m : entryClass.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Object.class && m.getReturnType() == void.class) {
                    dataEntrySetValue = m;
                    dataEntrySetValue.setAccessible(true);
                } else if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class && m.getReturnType() == void.class) {
                    dataEntrySetDirty = m;
                    dataEntrySetDirty.setAccessible(true);
                }
            }

            // 6. Entity.isDead (multiple name strategies)
            try {
                isDead = Entity.class.getDeclaredField("field_70128_L");
            } catch (NoSuchFieldException e1) {
                try {
                    isDead = Entity.class.getDeclaredField("isDead");
                } catch (NoSuchFieldException e2) {
                    for (Field f : Entity.class.getDeclaredFields()) {
                        if (f.getType() == boolean.class && Modifier.isPublic(f.getModifiers())) {
                            isDead = f;
                            break;
                        }
                    }
                }
            }
            if (isDead != null) {
                isDead.setAccessible(true);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] ForceKillHelper reflection init failed", e);
        }
        ENTITY_DATA_MANAGER = dm;
        ELB_HEALTH_PARAM = hp;
        ENTITY_IS_DEAD = isDead;
        EDM_ENTRIES = edmEntries;
        EDM_DIRTY = edmDirty;
        DATA_PARAM_GET_ID = dataParamGetId;
        DATA_ENTRY_SET_VALUE = dataEntrySetValue;
        DATA_ENTRY_SET_DIRTY = dataEntrySetDirty;
    }

    // ==================== Public API ====================

    /**
     * 读取实体的真实血量。
     * <p>
     * 终止窗口内的 {@code getHealth()} 会被 {@code MixinEntityLivingBase} 伪装为 0，
     * 因此窗口内的存活判断必须通过 DataManager 读取底层真实值。L4 击杀路径本身不再依赖
     * 该值，此方法保留给诊断与非击杀调用方。读取失败时回退到 {@code getHealth()}。
     */
    public static float getRawHealth(EntityLivingBase target) {
        try {
            net.minecraft.network.datasync.DataParameter<Float> healthParam =
                    com.github.aeddddd.ae2enhanced.util.reflection.VanillaReflectionHelper.getHealthParameter();
            if (healthParam != null) {
                Float value = target.getDataManager().get(healthParam);
                if (value != null) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
            // 反射不可用等场景,回退到 getHealth()
        }
        return target.getHealth();
    }

    /**
     * 综合强制击杀流程（L4）。
     * <p>
     * 只依赖引擎的死亡契约：一次 {@code onDeath} 保留掉落与归属，直写 {@code isDead}
     * 交给世界 tick 移除，并在有界窗口内复核。不再写 DataManager、不再猜健康通道。
     *
     * @param target     目标实体
     * @param attacker   攻击者（用于设置复仇目标等；可为 null）
     * @param damage     历史参数，L4 路径不再使用（保留签名以兼容现有调用）
     * @param deathSource 死亡事件使用的 DamageSource
     */
    public static void applyForceKill(EntityLivingBase target, Entity attacker, float damage, net.minecraft.util.DamageSource deathSource) {
        if (target == null || target.world == null || target.world.isRemote) return;
        // 飞升网络链接指环的绝对防护：拦截成功则整个强杀流程中止
        if (com.github.aeddddd.ae2enhanced.ring.RingProtection.tryBlockForceKill(target)) return;
        applyTermination(target, attacker, deathSource);
    }

    /**
     * 不经指环防护判定的终止入口，供真实伤害等已经完成目标选择的路径使用。
     * <p>
     * 仍然只走 L4：一次死亡入口 + isDead + 有界复核。
     */
    public static void applyTermination(EntityLivingBase target, Entity attacker, net.minecraft.util.DamageSource deathSource) {
        if (target == null || target.world == null || target.world.isRemote || deathSource == null) return;
        if (attacker instanceof EntityLivingBase) {
            target.setRevengeTarget((EntityLivingBase) attacker);
        }
        // 仅保留"内部保护开关"绕过；血量写入交由终止登记一次完成。
        forceBypassProtection(target);
        TerminationRegistry.terminate(target, attacker, deathSource);
        removeMultipartChildren(target);
        tryNotifyBossManager(target);
    }

    /**
     * 环境型强制击杀（黑洞 / 微型奇点等，L4）。
     * <p>
     * 玩家保留原版管线（避免 isDead 直写造成畸形状态），随后进入有限次血量重申；
     * 非玩家直接进入终止登记：一次死亡入口 + isDead + 有界复核。
     *
     * @param target 目标实体
     * @param source 伤害源
     * @param damage 伤害值
     */
    public static void applyEnvironmentDamage(EntityLivingBase target, net.minecraft.util.DamageSource source, float damage) {
        if (target == null || target.world == null || target.world.isRemote || source == null) return;
        if (target.getHealth() <= 0.0f) return;
        // 飞升网络链接指环的绝对防护：拦截成功则整个强杀流程中止
        if (com.github.aeddddd.ae2enhanced.ring.RingProtection.tryBlockForceKill(target)) return;

        if (target instanceof net.minecraft.entity.player.EntityPlayer) {
            target.hurtResistantTime = 0;
            target.hurtTime = 0;
            boolean hurt = target.attackEntityFrom(source, damage);
            if (!hurt || target.isEntityAlive()) {
                target.setHealth(0.0f);
                target.onDeath(source);
            }
            TerminationRegistry.trackPlayer((net.minecraft.entity.player.EntityPlayer) target, source);
        } else {
            forceBypassProtection(target);
            TerminationRegistry.terminate(target, source.getTrueSource(), source);
            removeMultipartChildren(target);
            tryNotifyBossManager(target);
        }
    }

    /**
     * 强制绕过实体的内部保护机制。
     * <p>
     * 某些实体类通过私有布尔开关阻止外部对其血量或存活状态的修改；
     * 本方法在运行时遍历该实体类及其父类的全部字段，找到名字匹配
     * {@code allowProtectedHealthChange} / {@code allowProtectedRemoval}
     * 或包含 {@code ProtectedHealth} / {@code ProtectedRemoval} 的布尔字段并将其设为 {@code true}。
     *
     * @param entity 需要绕过保护的目标实体
     */
    public static void forceBypassProtection(EntityLivingBase entity) {
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
     * 直接通过底层数据管理器修改实体的血量参数（兼容/诊断用）。
     * <p>
     * L4 击杀主路径不再调用本方法：它只对"重写 setHealth 但仍用 DataManager 存血"的实体有效，
     * 且对虚拟血量、事件层免伤与 isDead 守卫都没有帮助。保留它是为了让旧的兼容代码与诊断
     * 工具仍能按需使用。实现会先扫描实体类及其父类中所有 {@code DataParameter<Float>} 静态字段，
     * 然后直接修改对应的 DataEntry.value 并标记 dirty，最后反射调用 {@code set}/{@code setEntry}。
     *
     * @param entity 目标实体
     * @param health 欲设置的血量值（会被钳制在 [0, maxHealth] 区间）
     */
    public static void forceSetHealthViaDataManager(EntityLivingBase entity, float health) {
        if (ENTITY_DATA_MANAGER == null) return;
        try {
            Object dataManager = ENTITY_DATA_MANAGER.get(entity);
            if (dataManager == null) return;

            // Collect all DataParameter<Float> static fields from the entity hierarchy
            List<Object> healthParams = new ArrayList<>();
            Class<?> clazz = entity.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) &&
                        f.getType().getSimpleName().equals("DataParameter")) {
                        Type genericType = f.getGenericType();
                        if (genericType instanceof ParameterizedType) {
                            Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
                            if (args.length > 0 && "java.lang.Float".equals(args[0].getTypeName())) {
                                f.setAccessible(true);
                                Object param = f.get(null);
                                if (param != null) healthParams.add(param);
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            // Fallback: always include EntityLivingBase.HEALTH
            if (ELB_HEALTH_PARAM != null && !healthParams.contains(ELB_HEALTH_PARAM)) {
                healthParams.add(ELB_HEALTH_PARAM);
            }
            if (healthParams.isEmpty()) return;

            float clamped = MathHelper.clamp(health, 0.0f, entity.getMaxHealth());

            // Method 1: directly modify DataEntry.value + dirty, bypassing any EntityDataManager subclass override on set()
            if (EDM_ENTRIES != null && DATA_PARAM_GET_ID != null && DATA_ENTRY_SET_VALUE != null) {
                Map<?, ?> entries = (Map<?, ?>) EDM_ENTRIES.get(dataManager);
                boolean anyModified = false;
                for (Object param : healthParams) {
                    int id = (Integer) DATA_PARAM_GET_ID.invoke(param);
                    Object entry = entries.get(id);
                    if (entry == null) continue;
                    try {
                        // Standard DataEntry
                        DATA_ENTRY_SET_VALUE.invoke(entry, Float.valueOf(clamped));
                        if (DATA_ENTRY_SET_DIRTY != null) {
                            DATA_ENTRY_SET_DIRTY.invoke(entry, true);
                        }
                        anyModified = true;
                    } catch (IllegalArgumentException e) {
                        // Custom entry object: brute-force probe value field / setter
                        boolean customModified = false;
                        for (Method m : entry.getClass().getDeclaredMethods()) {
                            if (m.getParameterCount() == 1) {
                                Class<?> pt = m.getParameterTypes()[0];
                                if (pt == Object.class || pt == Float.class || pt == float.class) {
                                    try {
                                        m.setAccessible(true);
                                        m.invoke(entry, Float.valueOf(clamped));
                                        customModified = true;
                                        break;
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                        if (!customModified) {
                            for (Field f : entry.getClass().getDeclaredFields()) {
                                if (f.getType() == Object.class || f.getType() == Float.class || f.getType() == float.class) {
                                    try {
                                        f.setAccessible(true);
                                        f.set(entry, Float.valueOf(clamped));
                                        customModified = true;
                                        break;
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                        if (customModified) {
                            // Attempt to mark dirty (custom dirty method / field)
                            for (Method m : entry.getClass().getDeclaredMethods()) {
                                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
                                    try {
                                        m.setAccessible(true);
                                        m.invoke(entry, true);
                                        break;
                                    } catch (Exception ignored) {}
                                }
                            }
                            for (Field f : entry.getClass().getDeclaredFields()) {
                                if (f.getType() == boolean.class) {
                                    try {
                                        f.setAccessible(true);
                                        f.setBoolean(entry, true);
                                        break;
                                    } catch (Exception ignored) {}
                                }
                            }
                            anyModified = true;
                        }
                    }
                }
                if (anyModified && EDM_DIRTY != null) {
                    try {
                        EDM_DIRTY.setBoolean(dataManager, true);
                    } catch (Exception ignored) {}
                }
            }

            // Method 2: reflectively invoke set / setEntry (fallback, triggers notifyDataManagerChange)
            for (Method m : DATA_MANAGER_CANDIDATES) {
                try {
                    for (Object param : healthParams) {
                        m.invoke(dataManager, param, Float.valueOf(clamped));
                    }
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IllegalArgumentException
                            && cause.getMessage() != null
                            && cause.getMessage().contains("Duplicate")) {
                        continue;
                    }
                    AE2Enhanced.LOGGER.warn("[AE2E] forceSetHealthViaDataManager candidate {} failed: {}", m.getName(), cause);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] forceSetHealthViaDataManager failed", e);
        }
    }

    /**
     * 强制设置实体的 {@code isDead} 标志，绕过任何对 {@code setDead()} 的覆盖。
     * <p>
     * 优先使用已缓存的反射字段；若缓存未命中，则在运行时动态查找
     * {@code field_70128_L} / {@code isDead} / {@code dead}。
     *
     * @param entity 目标实体
     * @param dead   欲设置的死亡标志
     */
    public static void forceSetIsDead(Entity entity, boolean dead) {
        // Try cached field first (performance)
        if (ENTITY_IS_DEAD != null) {
            try {
                ENTITY_IS_DEAD.setBoolean(entity, dead);
                return;
            } catch (Exception ignored) {}
        }
        // Runtime dynamic lookup
        String[] candidates = {"field_70128_L", "isDead", "dead"};
        for (String name : candidates) {
            Class<?> clazz = entity.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    f.setBoolean(entity, dead);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.error("[AE2E] forceSetIsDead failed", e);
                    return;
                }
            }
        }
        AE2Enhanced.LOGGER.error("[AE2E] forceSetIsDead: could not find isDead field on {}", entity.getClass().getName());
    }

    /**
     * 尝试通知关联的管理器对象：目标实体已死亡。
     * <p>
     * 某些复杂实体由外部管理器对象负责生命周期（例如记录击杀状态、触发阶段转换、
     * 阻止重新生成）。如果该实体死亡后未通知其管理器，管理器可能在超时后重新生成该实体。
     * 本方法通过模式匹配在实体类上查找返回类型名包含 {@code manager} / {@code fight} / {@code boss}
     * 的无参方法，获取管理器实例后，调用名字包含 {@code death} / {@code complete} / {@code finish}
     * 的方法；若找不到合适的回调方法，则尝试将管理器中名字包含 {@code killed} / {@code dead} /
     * {@code defeated} 的布尔字段设为 {@code true}。
     *
     * @param boss 目标实体（通常为 Boss 或具有管理器的复杂实体）
     */
    public static void tryNotifyBossManager(EntityLivingBase boss) {
        try {
            Object manager = null;
            Method managerGetter = null;
            for (Method m : boss.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() != 0) continue;
                String retName = m.getReturnType().getSimpleName().toLowerCase();
                if (retName.contains("manager") || retName.contains("fight") || retName.contains("boss")) {
                    m.setAccessible(true);
                    Object candidate = m.invoke(boss);
                    if (candidate != null) {
                        manager = candidate;
                        managerGetter = m;
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
     * 强制移除实体的多碰撞箱子实体（Multipart children）。
     * <p>
     * 某些大型实体使用 {@link MultiPartEntityPart} 数组表示多个碰撞箱；
     * 父实体死亡后，子实体也应同步被标记为死亡。
     *
     * @param parent 父实体
     */
    public static void removeMultipartChildren(Entity parent) {
        for (Field f : parent.getClass().getDeclaredFields()) {
            Class<?> type = f.getType();
            if (type.isArray() && type.getComponentType().getSimpleName().equals("MultiPartEntityPart")) {
                try {
                    f.setAccessible(true);
                    Object[] parts = (Object[]) f.get(parent);
                    if (parts != null) {
                        for (Object part : parts) {
                            if (part instanceof Entity) {
                                forceSetIsDead((Entity) part, true);
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }
}
