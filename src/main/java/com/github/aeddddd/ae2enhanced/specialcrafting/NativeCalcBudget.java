package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.crafting.CraftingJob;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingJobBudgetAccess;
import com.github.aeddddd.ae2enhanced.observation.Observations;

/**
 * 原生回落计算的时间预算助手.
 * <p>复杂大单回落到原生递归计算时可能长时间不结束,而下单流程会同步等待
 * future,把服务器线程挂起整个计算时长;预算状态由本模组 job 类自持
 * ({@link CraftingJobBudgetAccess}),心跳检查由 MixinCraftingJob
 * 挂在原生 handlePausing 上.</p>
 */
public final class NativeCalcBudget {

    private NativeCalcBudget() {
    }

    /**
     * 进入原生计算路径前挂上时间预算.仅本模组 job 类(实现了预算访问器)生效;
     * 普通原生 job 不受影响.
     */
    public static void arm(CraftingJob job) {
        arm(job, com.github.aeddddd.ae2enhanced.config.ServerConfig.current().crafting.nativeCalcBudgetMs);
    }

    /** LP 请求路径只使用请求快照中的预算。 */
    public static void arm(CraftingJob job, int configuredBudgetMs) {
        if (job instanceof CraftingJobBudgetAccess) {
            long budgetMs = Math.max(1, configuredBudgetMs);
            ((CraftingJobBudgetAccess) job)
                    .ae2enhanced$armNativeCalcBudget(System.nanoTime() + budgetMs * 1_000_000L);
        }
    }

    /**
     * 预算心跳检查(MixinCraftingJob 挂在原生 handlePausing HEAD;逐节点/逐子请求调用).
     * 超预算:先标记中断并把计划钉为模拟态(与后续 finish() 同线程序,
     * 保证容器读到的 isSimulation() 必为 true,不完整计划绝不允许提交),
     * 再抛 InterruptedException 借原生取消语义收尾.
     */
    public static void checkDeadline(CraftingJob job) throws InterruptedException {
        if (!(job instanceof CraftingJobBudgetAccess)) {
            return;
        }
        CraftingJobBudgetAccess access = (CraftingJobBudgetAccess) job;
        long deadline = access.ae2enhanced$nativeCalcDeadlineNanos();
        if (deadline > 0L && System.nanoTime() - deadline >= 0L) {
            access.ae2enhanced$markNativeCalcAborted();
            Ae2CraftingReflect.setSimulate(job, true);
            throw new InterruptedException("native calc budget exceeded");
        }
    }

    /**
     * 原生路径返回后调用:若因超预算被中断(计划已被钉为模拟态,不可提交),输出告警.
     * 真实取消(GUI 关闭/future.cancel)不命中,不告警.
     *
     * @return 是否发生了超预算中断
     */
    public static boolean warnIfAborted(CraftingJob job) {
        return warnIfAborted(job, com.github.aeddddd.ae2enhanced.config.ServerConfig.current().crafting.nativeCalcBudgetMs);
    }

    /** LP 请求路径用同一份快照记录预算，避免配置变更令诊断与实际控制不一致。 */
    public static boolean warnIfAborted(CraftingJob job, int configuredBudgetMs) {
        if (job instanceof CraftingJobBudgetAccess
                && ((CraftingJobBudgetAccess) job).ae2enhanced$nativeCalcAborted()) {
            AE2Enhanced.LOGGER.warn("[合成计划] 原生计算超预算({}ms)已中断,计划按缺料处理(不可提交): {}",
                    configuredBudgetMs, job.getOutput());
            Observations.counter("crafting.nativeCalcAbort").increment();
            Observations.event("WARN", "crafting", "原生计算超预算(" + configuredBudgetMs
                    + "ms)已中断: " + job.getOutput());
            return true;
        }
        return false;
    }
}
