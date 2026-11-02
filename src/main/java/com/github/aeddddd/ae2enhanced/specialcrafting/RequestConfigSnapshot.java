package com.github.aeddddd.ae2enhanced.specialcrafting;

import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpHotStart;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.RevisedSimplex;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve;

/**
 * 单个 LP 计划请求的不可变配置快照。
 * <p>系统属性和服务端静态配置只在请求入口读取；热启动模式在此冻结，实际的可变
 * {@link LpHotStart.Chain} 仍由每个请求独立创建。客户端显示偏好不属于本快照。</p>
 */
public final class RequestConfigSnapshot {

    public final long serverConfigVersion;
    public final PruneSettings prune;
    public final LpPlanCache.Settings cache;
    public final LpHotStart.Mode hotStart;
    public final String solverMode;
    public final boolean specialCrafting;
    public final int nativeCalcBudgetMs;
    public final int bootstrapIterBudget;
    public final long bootstrapFuseNs;
    public final boolean lpDebug;
    public final RevisedSimplex.Settings legacy;

    public RequestConfigSnapshot(PruneSettings prune, LpPlanCache.Settings cache,
            LpHotStart.Mode hotStart, String solverMode, boolean specialCrafting, int nativeCalcBudgetMs,
            int bootstrapIterBudget, long bootstrapFuseNs, boolean lpDebug,
            RevisedSimplex.Settings legacy) {
        this(0L, prune, cache, hotStart, solverMode, specialCrafting, nativeCalcBudgetMs,
                bootstrapIterBudget, bootstrapFuseNs, lpDebug, legacy);
    }

    public RequestConfigSnapshot(long serverConfigVersion, PruneSettings prune, LpPlanCache.Settings cache,
            LpHotStart.Mode hotStart, String solverMode, boolean specialCrafting, int nativeCalcBudgetMs,
            int bootstrapIterBudget, long bootstrapFuseNs, boolean lpDebug,
            RevisedSimplex.Settings legacy) {
        this.serverConfigVersion = serverConfigVersion;
        this.prune = prune;
        this.cache = cache;
        this.hotStart = hotStart;
        this.solverMode = solverMode;
        this.specialCrafting = specialCrafting;
        this.nativeCalcBudgetMs = nativeCalcBudgetMs;
        this.bootstrapIterBudget = bootstrapIterBudget;
        this.bootstrapFuseNs = bootstrapFuseNs;
        this.lpDebug = lpDebug;
        this.legacy = legacy;
    }

    /** 在 LP 请求入口调用一次，之后整单只传递返回对象。 */
    public static RequestConfigSnapshot capture() {
        return capture(ServerConfig.current());
    }

    public static RequestConfigSnapshot capture(ServerConfig serverConfig) {
        return new RequestConfigSnapshot(serverConfig.version, PruneSettings.fromSystemProperties(),
                LpPlanCache.Settings.fromSystemProperties(), LpHotStart.modeFromSystemProperties(),
                SccLpSolve.solverModeFromSystemProperties(), serverConfig.crafting.specialCrafting,
                serverConfig.crafting.nativeCalcBudgetMs,
                Integer.getInteger("ae2e.bootstrapIterBudget", 200_000),
                Long.getLong("ae2e.bootstrapFuseNs", 60_000_000_000L),
                Boolean.getBoolean("ae2e.lpDebug"), RevisedSimplex.Settings.fromSystemProperties());
    }

    /** 旧显式开关 API 的适配：只覆盖调用方已经传入的求解选项。 */
    public RequestConfigSnapshot withSolverSettings(PruneSettings prune, LpPlanCache.Settings cache,
            LpHotStart.Mode hotStart) {
        return new RequestConfigSnapshot(this.serverConfigVersion, prune, cache, hotStart, this.solverMode,
                this.specialCrafting, this.nativeCalcBudgetMs, this.bootstrapIterBudget, this.bootstrapFuseNs,
                this.lpDebug, this.legacy);
    }

    /** 每次求解创建独立热启动状态，快照本身可安全跨请求共享。 */
    public LpHotStart.Chain newHotStartChain() {
        return LpHotStart.Chain.of(this.hotStart);
    }
}
