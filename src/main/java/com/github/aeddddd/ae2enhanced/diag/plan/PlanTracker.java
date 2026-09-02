package com.github.aeddddd.ae2enhanced.diag.plan;

import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.DiagEvents;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect;

/**
 * 合成计划执行验证与效果评估.
 *
 * <p>在每个 {@link CraftingJob} 计算完成（{@code run()} 返回）时由
 * {@code MixinCraftingJob}（原生路径）与各子类 {@code run()} 的 finally 块
 * （DAG/特殊路径）调用：</p>
 * <ul>
 *   <li><b>效果评估</b>：按计划路径分类计数（special / dag / dagFallback / native）
 *       并记录各路径计算耗时（{@code plan.computeMs.*} 指标）</li>
 *   <li><b>守恒校验</b>：非模拟计划遍历合成树累加节点 missing（经
 *       {@link Ae2CraftingReflect#getNodeMissing}），不变量为"缺料总量 == 0 且树非空"；
 *       违反即计数并写入 {@link DiagEvents}</li>
 * </ul>
 *
 * <p>校验开关：{@code /ae2e debug planverify on|off}（默认开启）。</p>
 */
public final class PlanTracker {

    private PlanTracker() {
    }

    /**
     * job 计算完成回调（在合成线程池线程上执行，必须线程安全且不抛异常）.
     *
     * @param job          已完成的 job（run 收尾处，tree 仍可读）
     * @param elapsedNanos run() 实际耗时
     */
    public static void onJobComputed(CraftingJob job, long elapsedNanos) {
        try {
            String path = classify(job);
            MetricsRegistry.counter("plan.path." + path).increment();
            MetricsRegistry.timer("plan.computeMs." + path).record(elapsedNanos);
            if (DiagSwitch.isEnabled(DiagSwitch.PLAN_VERIFY)) {
                verify(job, path);
            }
        } catch (Throwable t) {
            // 验证器自身绝不影响合成链路
            AE2Enhanced.LOGGER.warn("[AE2E-Plan] 计划验证异常: {}", t.toString());
        }
    }

    /** 计划路径分类（与 MixinCraftingGridCache 的路由规则一致,M7 起 LP 计划器）. */
    private static String classify(CraftingJob job) {
        if (job instanceof com.github.aeddddd.ae2enhanced.specialcrafting.FallbackLpCraftingJob) {
            return "lpFallback";
        }
        if (job instanceof com.github.aeddddd.ae2enhanced.specialcrafting.LpCraftingJob) {
            // LP 回落原生(super.run())的耗时由 MixinCraftingJob RETURN 钩子计时——
            // 已挂原生预算(deadline 非 0)即处于回落段,归入 lpToNative,
            // 否则 lp 尾部样本会把"LP 快速失败 + 原生烧预算"误记成 LP 慢
            if (job instanceof com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingJobBudgetAccess
                    && ((com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingJobBudgetAccess) job)
                            .ae2enhanced$nativeCalcDeadlineNanos() != 0L) {
                return "lpToNative";
            }
            return "lp";
        }
        return "native";
    }

    /**
     * 物料守恒不变量校验.
     *
     * <p>非模拟计划的树中不应存在缺料计数（模拟计划按定义记录 missing，直接跳过）。
     * 校验失败说明计划器（DAG/特殊求解器/原生）产生了不一致的树。</p>
     */
    private static void verify(CraftingJob job, String path) {
        if (job.isSimulation()) {
            MetricsRegistry.counter("plan.verify.skippedSimulation").increment();
            return;
        }
        CraftingTreeNode tree = job.getTree();
        if (tree == null) {
            violation(path, job, "非模拟计划树为 null");
            return;
        }
        long missingTotal = sumMissing(tree);
        if (missingTotal > 0) {
            violation(path, job, "非模拟计划存在缺料: missing=" + missingTotal);
            return;
        }
        MetricsRegistry.counter("plan.verify.passed").increment();
    }

    private static void violation(String path, CraftingJob job, String detail) {
        MetricsRegistry.counter("plan.verify.violation").increment();
        DiagEvents.error("plan", detail + ": path=" + path + " output=" + job.getOutput());
        AE2Enhanced.LOGGER.warn("[AE2E-Plan] {}: path={} output={}", detail, path, job.getOutput());
    }

    /** 递归累加整棵树的缺料计数（与 SpecialPlanInfo.collectFromTree 同一套树遍历反射）。 */
    private static long sumMissing(CraftingTreeNode node) {
        if (node == null) {
            return 0L;
        }
        long total = Ae2CraftingReflect.getNodeMissing(node);
        for (CraftingTreeProcess pro : Ae2CraftingReflect.getNodeProcesses(node)) {
            for (CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pro).keySet()) {
                total += sumMissing(child);
            }
        }
        return total;
    }
}
