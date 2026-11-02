package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.github.aeddddd.ae2enhanced.observation.Observations;
import com.github.aeddddd.ae2enhanced.observation.PlanObservation;

/** 在业务边界完成路径分类和计划不变量校验，只向诊断发布结果快照。 */
public final class PlanObservationFactory {

    private PlanObservationFactory() {
    }

    public static void onJobComputed(CraftingJob job, long elapsedNanos) {
        String path = classify(job);
        String violation = job.isSimulation() ? null : verify(job);
        Observations.planComputed(new PlanObservation(path, String.valueOf(job.getOutput()),
                elapsedNanos, job.isSimulation(), violation));
    }

    private static String classify(CraftingJob job) {
        if (job instanceof FallbackLpCraftingJob) {
            return "lpFallback";
        }
        if (job instanceof LpCraftingJob) {
            return ((LpCraftingJob) job).ae2enhanced$nativeCalcDeadlineNanos() != 0L ? "lpToNative" : "lp";
        }
        return "native";
    }

    private static String verify(CraftingJob job) {
        CraftingTreeNode tree = job.getTree();
        if (tree == null) {
            return "非模拟计划树为 null";
        }
        long missingTotal = sumMissing(tree);
        if (missingTotal > 0) {
            return "非模拟计划存在缺料: missing=" + missingTotal;
        }
        if (job instanceof LpCraftingJob) {
            LpCraftingJob lpJob = (LpCraftingJob) job;
            if (lpJob.resolvedPlan() != null) {
                LpPlanMaterializer.Report report = lpJob.materializationReport();
                if (report == null) {
                    return "LP 计划缺少物化报告";
                }
                LpPlanMaterializer.Report current = report.revalidate(tree);
                if (!current.committable()) {
                    return "LP 物化契约不完整: " + current.reasons();
                }
            }
        }
        return null;
    }

    private static long sumMissing(CraftingTreeNode node) {
        if (node == null) {
            return 0L;
        }
        long total = Ae2CraftingReflect.getNodeMissing(node);
        for (CraftingTreeProcess process : Ae2CraftingReflect.sortedNodeProcesses(node)) {
            for (CraftingTreeNode child : Ae2CraftingReflect.sortedProcessNodes(process)) {
                total += sumMissing(child);
            }
        }
        return total;
    }
}
