package com.github.aeddddd.ae2enhanced.diag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局诊断开关注册表.
 *
 * <p>所有诊断开关<b>完全由指令控制</b>（{@code /ae2e debug <name> on|off|reset}），
 * 不再提供配置文件项。reset 恢复为该开关的内置默认值（全部默认关闭）。</p>
 *
 * <p>历史开关迁移对照：</p>
 * <ul>
 *   <li>{@link #SPECIAL_CRAFTING} ← 原配置 {@code debug.debugMode}（已移除）</li>
 *   <li>{@link #VIRTUAL_BATCH} ← 原配置 {@code centralInterface.debugVirtualBatch}（已移除）</li>
 *   <li>{@link #BATCH_AGGREGATE} ← 原 JVM 属性 {@code -Dae2e.batchDebug}（已废弃）</li>
 * </ul>
 */
public final class DiagSwitch {

    /** 特殊配方/DAG 计划引擎诊断日志 */
    public static final String SPECIAL_CRAFTING = "specialcrafting";
    /** 中枢 ME 接口虚拟批量合成诊断日志 */
    public static final String VIRTUAL_BATCH = "virtualbatch";
    /** 合成 CPU 批量聚合判定诊断（5s 节流） */
    public static final String BATCH_AGGREGATE = "batchaggregate";
    /** 性能数据采集总开关（TPS 采样 + L2 埋点；关闭后 /ae2e perf 只显示已采集的历史快照） */
    public static final String PERF = "perf";
    /** 合成计划守恒校验（每个 job 计算完成时自动执行 populatePlan 不变量检查） */
    public static final String PLAN_VERIFY = "planverify";
    /**
     * 中枢 ME 接口流体生命周期诊断日志（逐节点：抽取 → 流体通道注入 → 兜底各级）.
     *
     * <p>节点名出现在每行日志前缀里（{@code [AE2E-Fluid][节点]}），按 (节点, 流体) 节流，
     * 默认关闭。开启方式：{@code /ae2e debug centralfluid on}。</p>
     */
    public static final String CENTRAL_FLUID = "centralfluid";

    /** 开关名 → 内置默认状态（reset 时恢复） */
    private static final Map<String, Boolean> DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, Boolean> OVERRIDES = new ConcurrentHashMap<>();

    static {
        DEFAULTS.put(SPECIAL_CRAFTING, false);
        DEFAULTS.put(VIRTUAL_BATCH, false);
        DEFAULTS.put(BATCH_AGGREGATE, false);
        // 性能采集开销极低（每 tick 一次 nanoTime 差值 + 1/20 采样埋点），默认开启
        DEFAULTS.put(PERF, true);
        // 计划校验仅在 job 完成时执行一次，开销与合成频率成正比，默认开启
        DEFAULTS.put(PLAN_VERIFY, true);
        // 流体生命周期诊断只在玩家显式开启后才有输出（默认关闭）
        DEFAULTS.put(CENTRAL_FLUID, false);
    }

    private DiagSwitch() {
    }

    /** 当前生效状态：override 优先，其次内置默认，未知开关返回 false。 */
    public static boolean isEnabled(String name) {
        Boolean override = OVERRIDES.get(name);
        if (override != null) {
            return override;
        }
        Boolean def = DEFAULTS.get(name);
        return def != null && def;
    }

    public static boolean isKnown(String name) {
        return DEFAULTS.containsKey(name);
    }

    public static Set<String> names() {
        return Collections.unmodifiableSet(DEFAULTS.keySet());
    }

    /** 运行期覆盖（重启后失效）。 */
    public static void setOverride(String name, boolean enabled) {
        OVERRIDES.put(name, enabled);
    }

    /** 清除运行期覆盖，回到内置默认。 */
    public static void clearOverride(String name) {
        OVERRIDES.remove(name);
    }

    public static boolean hasOverride(String name) {
        return OVERRIDES.containsKey(name);
    }
}
