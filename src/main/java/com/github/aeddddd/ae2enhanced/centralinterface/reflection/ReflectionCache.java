package com.github.aeddddd.ae2enhanced.centralinterface.reflection;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 反射查找缓存（中枢接口 handler 层共用）.
 *
 * <p>统一提供 Class / Method / Field / Constructor 的查找，并且<b>失败结果同样被缓存</b>
 * （latch 语义）：某一项首次查找失败后，后续调用直接命中"失败"这条记录，不再重复
 * {@link Class#forName}、不再重复执行 JDK 的成员查找、也不再重复构造
 * {@link NoSuchMethodException} / {@link NoSuchFieldException} 等异常对象。</p>
 *
 * <p>解决的问题：机器 handler 的 {@code isIdle} / {@code catalogFor} 等路径每 tick 都要反射取一次
 * 成员（例如 Actually Additions 的 {@code processTime} 字段、NuclearCraft 的
 * {@code isProcessing} 方法），而在"该 mod 未安装 / 版本不匹配 / 传入的不是目标方块"的情况下
 * 每次调用都要重新走一遍失败的查找。热路径上的这部分开销由此退化为一次 Map 查找。</p>
 *
 * <p><b>与 {@code util.reflection.ReflectionHelper} 的分工</b>：后者只做"查找 + 沿继承链 walk +
 * setAccessible"，不缓存任何结果；本类只做缓存，<b>不做继承链 walk</b> —— 每个方法只包住调用点的
 * 那一次 JDK 查找（{@code getMethod} / {@code getDeclaredMethod} / {@code getField} /
 * {@code getDeclaredField} / {@code getConstructor}）。
 * {@code declared*} 系列按调用点的习惯，查找成功后调用 {@code setAccessible(true)}。</p>
 *
 * <p><b>不吞 Error</b>：查找只捕获 {@link Exception}。
 * {@link NoClassDefFoundError} / {@link ExceptionInInitializerError} 之类的 {@link Error}
 * 仍向上传播，因此依赖缺失时整个静态初始化会失败。
 * 缓存的可见性由 {@link ConcurrentMap} 保证：多线程首次并发查找同一项时，最坏情况是重复查找一次，
 * 结果一致。</p>
 */
public final class ReflectionCache {

    private static final ConcurrentMap<String, Optional<Class<?>>> CLASSES = new ConcurrentHashMap<>();

    /** public 方法（含继承）缓存 */
    private static final ConcurrentMap<MemberKey, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    /** declared 方法（仅本类声明，含 setAccessible）缓存 */
    private static final ConcurrentMap<MemberKey, Optional<Method>> DECLARED_METHODS = new ConcurrentHashMap<>();

    /** public 字段（含继承）缓存 */
    private static final ConcurrentMap<MemberKey, Optional<Field>> FIELDS = new ConcurrentHashMap<>();

    /** declared 字段（仅本类声明，含 setAccessible）缓存 */
    private static final ConcurrentMap<MemberKey, Optional<Field>> DECLARED_FIELDS = new ConcurrentHashMap<>();

    /** public 构造器（含 setAccessible）缓存 */
    private static final ConcurrentMap<MemberKey, Optional<Constructor<?>>> CONSTRUCTORS = new ConcurrentHashMap<>();

    private static final Class<?>[] EMPTY_PARAM_TYPES = new Class<?>[0];

    private static final String CONSTRUCTOR_NAME = "<init>";

    private ReflectionCache() {
    }

    /**
     * 查找并缓存类.
     *
     * @param name 全限定类名
     * @return 类；类不存在或加载失败返回 {@code null}（失败结果被缓存，不会重复 {@code Class.forName}）
     */
    @Nullable
    public static Class<?> clazz(String name) {
        return CLASSES.computeIfAbsent(name, n -> {
            try {
                return Optional.of(Class.forName(n));
            } catch (Exception e) {
                logMiss("class " + n, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * 查找并缓存 public 方法（含继承链上的 public 方法），等价于一次 {@link Class#getMethod}.
     *
     * @param owner      宿主类；{@code null} 时直接返回 {@code null}（不缓存：宿主类可能稍后才被解析出来）
     * @param name       方法名
     * @param paramTypes 形参类型；含 {@code null} 元素时视为无法查找并返回 {@code null}
     * @return 方法；不存在返回 {@code null}（失败结果被缓存）
     */
    @Nullable
    public static Method method(@Nullable Class<?> owner, String name, Class<?>... paramTypes) {
        if (owner == null || hasNull(paramTypes)) {
            return null;
        }
        MemberKey key = new MemberKey(owner, name, paramTypes);
        return METHODS.computeIfAbsent(key, k -> {
            try {
                return Optional.of(owner.getMethod(name, paramTypes));
            } catch (Exception e) {
                logMiss(k.describe(), e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * 查找并缓存本类声明的方法（不沿继承链 walk），等价于一次
     * {@link Class#getDeclaredMethod} + {@code setAccessible(true)}.
     *
     * @param owner      宿主类；{@code null} 时直接返回 {@code null}
     * @param name       方法名
     * @param paramTypes 形参类型；含 {@code null} 元素时返回 {@code null}
     * @return 方法；不存在返回 {@code null}（失败结果被缓存）
     */
    @Nullable
    public static Method declaredMethod(@Nullable Class<?> owner, String name, Class<?>... paramTypes) {
        if (owner == null || hasNull(paramTypes)) {
            return null;
        }
        MemberKey key = new MemberKey(owner, name, paramTypes);
        return DECLARED_METHODS.computeIfAbsent(key, k -> {
            try {
                Method found = owner.getDeclaredMethod(name, paramTypes);
                found.setAccessible(true);
                return Optional.of(found);
            } catch (Exception e) {
                logMiss(k.describe(), e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * 查找并缓存 public 字段（含继承链上的 public 字段），等价于一次 {@link Class#getField}.
     *
     * @param owner 宿主类；{@code null} 时直接返回 {@code null}
     * @param name  字段名
     * @return 字段；不存在返回 {@code null}（失败结果被缓存）
     */
    @Nullable
    public static Field field(@Nullable Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        MemberKey key = new MemberKey(owner, name, EMPTY_PARAM_TYPES);
        return FIELDS.computeIfAbsent(key, k -> {
            try {
                return Optional.of(owner.getField(name));
            } catch (Exception e) {
                logMiss(k.describe(), e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * 查找并缓存本类声明的字段（不沿继承链 walk），等价于一次
     * {@link Class#getDeclaredField} + {@code setAccessible(true)}.
     *
     * @param owner 宿主类；{@code null} 时直接返回 {@code null}
     * @param name  字段名
     * @return 字段；不存在返回 {@code null}（失败结果被缓存）
     */
    @Nullable
    public static Field declaredField(@Nullable Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        MemberKey key = new MemberKey(owner, name, EMPTY_PARAM_TYPES);
        return DECLARED_FIELDS.computeIfAbsent(key, k -> {
            try {
                Field found = owner.getDeclaredField(name);
                found.setAccessible(true);
                return Optional.of(found);
            } catch (Exception e) {
                logMiss(k.describe(), e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * 查找并缓存 public 构造器，等价于一次 {@link Class#getConstructor} + {@code setAccessible(true)}.
     *
     * @param owner      宿主类；{@code null} 时直接返回 {@code null}
     * @param paramTypes 形参类型；含 {@code null} 元素时返回 {@code null}
     * @return 构造器；不存在返回 {@code null}（失败结果被缓存）
     */
    @Nullable
    public static Constructor<?> constructor(@Nullable Class<?> owner, Class<?>... paramTypes) {
        if (owner == null || hasNull(paramTypes)) {
            return null;
        }
        MemberKey key = new MemberKey(owner, CONSTRUCTOR_NAME, paramTypes);
        return CONSTRUCTORS.computeIfAbsent(key, k -> {
            try {
                Constructor<?> found = owner.getConstructor(paramTypes);
                found.setAccessible(true);
                return Optional.of(found);
            } catch (Exception e) {
                logMiss(k.describe(), e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    // ---- 内部实现 ----

    /** 形参类型里含 null（= 某个类型本身就没解析出来）时无法查找，按未命中处理. */
    private static boolean hasNull(Class<?>[] paramTypes) {
        for (Class<?> paramType : paramTypes) {
            if (paramType == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 未命中日志.
     *
     * <p>用 debug 级别：失败也缓存 —— 每个键最多一条，且"某个 mod 版本里方法名不同"这类
     * 正常回退（如 NuclearCraft 的 {@code isProcessing} → {@code getIsProcessing}）不应该
     * 污染生产日志。需要"缺 mod / 版本不兼容"级别的可见性时，由调用方（如各
     * {@code *ReflectionHelper} 的静态块）自行 warn。</p>
     */
    private static void logMiss(String what, Exception cause) {
        AE2Enhanced.LOGGER.debug("[AE2E] ReflectionCache miss: {}", what, cause);
    }

    /** 查找键：宿主类 + 成员名 + 形参类型（形参数组做防御性拷贝，避免调用方改动数组后污染缓存键）. */
    private static final class MemberKey {

        private final Class<?> owner;
        private final String name;
        private final Class<?>[] paramTypes;
        private final int hash;

        MemberKey(Class<?> owner, String name, Class<?>[] paramTypes) {
            this.owner = owner;
            this.name = name;
            this.paramTypes = paramTypes.clone();
            this.hash = (owner.hashCode() * 31 + name.hashCode()) * 31 + Arrays.hashCode(this.paramTypes);
        }

        String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.owner.getName()).append('#').append(this.name).append('(');
            for (int i = 0; i < this.paramTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(this.paramTypes[i].getName());
            }
            return sb.append(')').toString();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MemberKey)) {
                return false;
            }
            MemberKey that = (MemberKey) other;
            return this.owner == that.owner
                    && this.name.equals(that.name)
                    && Arrays.equals(this.paramTypes, that.paramTypes);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }
}
