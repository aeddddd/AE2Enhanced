package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.Locale;

/**
 * 查询域剪枝开关(方案 P0–P3)。
 * <p>开关一律在 solve 入口一次性解析后向下传参:循环内反复读系统属性会让同一次求解
 * 混用多种剪枝策略(轨迹不可复现),也破坏任务 1 建立的跨 JVM 确定性。</p>
 *
 * <ul>
 * <li>{@code ae2e.prune} = {@code off | demand | full}(默认 {@code full});
 * {@code off} = 老行为(对拍基线),{@code demand} = 仅需求门控(P1),
 * {@code full} = 门控 + 平凡单元闭式(P1+P2);</li>
 * <li>{@code ae2e.pruneTrace} = 真值时逐单元输出门控判定/闭式命中/回落原因;</li>
 * <li>{@code ae2e.unitProfile} = P0 埋点(求解结束按耗时打印单元画像)。</li>
 * </ul>
 */
public final class PruneSettings {

    /** 剪枝档位. */
    public enum Mode {
        /** 老行为(对拍基线):不走门控,不做闭式。 */
        OFF,
        /** 仅需求门控(P1)。 */
        DEMAND,
        /** 需求门控 + 平凡单元闭式(P1+P2)。 */
        FULL
    }

    /** 老行为(测试/离线对拍直接引用). */
    public static final PruneSettings OFF = new PruneSettings(Mode.OFF, false, false);

    public final Mode mode;
    /** 逐单元剪枝轨迹输出. */
    public final boolean trace;
    /** P0 单元画像埋点. */
    public final boolean unitProfile;

    public PruneSettings(Mode mode, boolean trace, boolean unitProfile) {
        this.mode = mode;
        this.trace = trace;
        this.unitProfile = unitProfile;
    }

    /** 求解入口一次性解析系统属性(此后不得再读)。 */
    public static PruneSettings fromSystemProperties() {
        String raw = System.getProperty("ae2e.prune", "full");
        Mode mode;
        switch (raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)) {
            case "off":
                mode = Mode.OFF;
                break;
            case "demand":
                mode = Mode.DEMAND;
                break;
            default:
                mode = Mode.FULL;
                break;
        }
        return new PruneSettings(mode, Boolean.getBoolean("ae2e.pruneTrace"),
                Boolean.getBoolean("ae2e.unitProfile"));
    }

    /** 需求门控(P1)是否启用。 */
    public boolean demandGate() {
        return this.mode != Mode.OFF;
    }

    /** 平凡单元闭式化(P2)是否启用。 */
    public boolean closedForm() {
        return this.mode == Mode.FULL;
    }

    @Override
    public String toString() {
        return "prune=" + this.mode + (this.trace ? " trace" : "")
                + (this.unitProfile ? " unitProfile" : "");
    }
}
