package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;

import com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialLog;

/**
 * DAG 按量尝试(与原生 {@code runCraftAttempt(simulate, amount)} 契约逐条对齐):
 * <ul>
 * <li>入口即置 calculation 的 simulate 标志;</li>
 * <li>{@code simulate=false}:可行返回计划;缺料返回 {@link Outcome#INFEASIBLE}
 * (原生此处返回 null,驱动 CRAFT_LESS 二分/回落模拟尝试);</li>
 * <li>{@code simulate=true}:必定返回非空计划(缺料随计划带出);</li>
 * <li>DAG 自身不适用(环边界失败/不干净样板/异常)→ {@link Outcome#FALLBACK},
 * 调用方放行原生尝试.</li>
 * </ul>
 */
public final class DagPlanAttempt {

    public enum Outcome {
        /** 计划已产出(可能带缺料,仅 simulate=true 时). */
        SUCCESS,
        /** simulate=false 且缺料:该数量做不出(契约上的"返回 null"). */
        INFEASIBLE,
        /** DAG 不适用,放行原生尝试. */
        FALLBACK
    }

    public record Result(Outcome outcome, @Nullable CraftingPlan plan, boolean hasCycleBoundary) {
        static Result of(Outcome outcome, @Nullable CraftingPlan plan, boolean boundary) {
            return new Result(outcome, plan, boundary);
        }
    }

    private static final Result FALLBACK = new Result(Outcome.FALLBACK, null, false);
    private static final Result INFEASIBLE = new Result(Outcome.INFEASIBLE, null, false);

    private DagPlanAttempt() {
    }

    /**
     * @param calc 宿主计算(提供 networkInv/缺料记账/simulate 标志)
     * @param what 请求物(= calc.getOutput())
     * @param amount 本次尝试数量
     * @param simulate true = 模拟尝试(收集缺料,必定产出计划)
     */
    public static Result tryPlan(CraftingCalculation calc, ICraftingService craftingService,
            AEKey what, long amount, boolean simulate) {
        try {
            Ae2CraftingReflect.setSimulate(calc, simulate);
            // 每次尝试独立:清空上次尝试的缺料记录(原生失败尝试不留缺料,
            // 两步/二分流程中不清理会重复计数)
            calc.getMissingItems().clear();

            DagGraph graph;
            try {
                graph = DagCompiler.compile(craftingService, what);
            } catch (DagFallback fallback) {
                SpecialLog.info("[DAG] 编译回落({}): {}", fallback.reason, what);
                return FALLBACK;
            }

            boolean hasCycleBoundary = false;
            for (var node : graph.topoOrder) {
                if (node.kind == DagGraph.Kind.CYCLE) {
                    hasCycleBoundary = true;
                    break;
                }
            }

            var networkInv = Ae2CraftingReflect.getNetworkInv(calc);
            var inv = new ChildCraftingSimulationState(networkInv);
            inv.ignore(what); // 镜像原生:请求物自身库存不参与计划扣除
            try {
                DagExecutor.execute(graph, amount, inv, calc, craftingService);
            } catch (DagFallback fallback) {
                if (fallback.reason != null && fallback.reason.startsWith("cycle_boundary_unsolvable")) {
                    // ④ 切边重试:边界不可解(中性转换环等)→ 剪断回边重编译,
                    // 产出诚实的缺料计划而非整单回落;失败尝试已污染库存/缺料,须重建
                    SpecialLog.info("[DAG] 边界不可解,切边重试: {}", what);
                    calc.getMissingItems().clear();
                    inv = new ChildCraftingSimulationState(networkInv);
                    inv.ignore(what);
                    try {
                        graph = DagCompiler.compile(craftingService, what, true);
                        DagExecutor.execute(graph, amount, inv, calc, craftingService);
                        hasCycleBoundary = false; // 切边图无循环节点
                    } catch (DagFallback retry) {
                        SpecialLog.info("[DAG] 切边重试仍回落({}): {}", retry.reason, what);
                        return FALLBACK;
                    }
                } else {
                    SpecialLog.info("[DAG] 执行回落({}): {}", fallback.reason, what);
                    return FALLBACK;
                }
            }

            boolean missing = !calc.getMissingItems().isEmpty();
            if (missing && !simulate) {
                return INFEASIBLE; // 契约:非模拟尝试失败返回 null
            }
            // simulate=true 且缺料:simulate 标志已在入口置位,计划带缺料返回
            var plan = CraftingSimulationState.buildCraftingPlan(inv, calc, amount);
            return Result.of(Outcome.SUCCESS, plan, hasCycleBoundary);
        } catch (Throwable t) {
            SpecialLog.info("[DAG] 尝试异常,放行原生: {}", t.toString());
            return FALLBACK;
        }
    }
}
