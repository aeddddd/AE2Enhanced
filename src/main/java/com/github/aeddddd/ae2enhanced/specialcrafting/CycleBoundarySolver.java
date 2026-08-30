package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.MECraftingInventory;

/**
 * 深层循环边界求解器（DAG 引擎 4.3,1.12.2 移植）:把含环子图当黑盒,
 * 以**当前模拟库存状态**求解 what×target 子需求并就地记账
 * （种子贷款、环外输入经原生树、产出回插、crafts 记账）.
 * <p>支持:① 净增殖自引用(selfKey == 边界 key,贷款法闭式);
 * ② 跨样板增殖环（并集联立优先,逐环迭代兜底,复用 {@link CycleSolver});
 * ③ 催化环（边界 key 是某中性/增殖环发射的环外副产物）.</p>
 * <p>结算语义与根请求求解一致:交付量（= 边界需求量）从库存取走,
 * 种子保留——防止同一批产出被 DAG 其他节点重复取用.</p>
 */
public final class CycleBoundarySolver {

    /**
     * 边界求解结果.
     */
    public enum BoundaryResult {
        /** 求解成功并已记账. */
        SOLVED,
        /** 数值不可表示（天文数字需求）:调用方应就地把边界需求记为缺料(O(1)),
         * 而非整单回落原生——回落在大网络上即高请求计算卡死. */
        MISSING,
        /** 不适用（种子不足/输入不足等）,调用方应整单回落原生. */
        FALLBACK
    }

    private CycleBoundarySolver() {
    }

    /**
     * @param budget 单趟 DAG 执行内所有循环边界共享的分析预算(总开销封顶);
     *        超预算按 {@link BoundaryResult#FALLBACK} 回落(与不可解同语义)
     * @return 求解结果;仅 {@link BoundaryResult#FALLBACK} 时调用方才整单回落原生.
     */
    public static BoundaryResult solveInto(ICraftingGrid cc, CraftingJob job, IAEItemStack what, long target,
            MECraftingInventory inv, CraftingTreeNode rootNode, IActionSource src, World world,
            AnalysisBudget budget) throws InterruptedException {
        // 看门狗时间盒:原生批量模拟在多生产者子树上退化为逐单位循环(每单位一次
        // 全库存快照),病态整合包中单个边界可烧掉整单预算(实测 20~35s)。原生
        // handlePausing 自查 Thread.interrupted(测试环境无 mixin 亦生效),故用
        // 守护调度线程到期中断本线程;命中内层超时按不可解处理(环盲降级),
        // 与真实取消(用户关 GUI/future.cancel)经标志位区分,不吞真取消
        long budgetMs = Math.max(200L, com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.crafting.cycleBoundarySolveBudgetMs);
        Thread worker = Thread.currentThread();
        java.util.concurrent.atomic.AtomicBoolean timedOut = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.ScheduledFuture<?> watchdog = Timebox.SCHEDULER.schedule(() -> {
            timedOut.set(true);
            worker.interrupt();
        }, budgetMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        // 慢求解诊断:统计各路径尝试次数与结果分布,超 500ms 时打印摘要
        long diagStart = System.nanoTime();
        int[] diag = new int[4]; // [0]=trySolve 次数 [1]=FALLBACK [2]=OVERFLOW [3]=催化候选环数
        long[] stageMs = new long[5]; // [0]=环枚举 [1]=dup判定 [2]=并集分析 [3]=逐环求解 [4]=催化求解
        try {
            return solveIntoCore(cc, job, what, target, inv, rootNode, src, world, budget, diag, stageMs);
        } catch (InterruptedException e) {
            if (timedOut.get()) {
                SpecialLog.info("[DAG] 边界求解超预算({}ms)中断,按不可解转环盲: {}@{}×{}", budgetMs, what,
                        what.getItemDamage(), target);
                return BoundaryResult.FALLBACK;
            }
            throw e; // 真实取消:原样上抛
        } finally {
            watchdog.cancel(false);
            if (timedOut.get()) {
                Thread.interrupted(); // 清除看门狗残留的中断标志,避免污染后续原生调用
            }
            long elapsed = System.nanoTime() - diagStart;
            if (elapsed > 500_000_000L) {
                SpecialLog.info(
                        "[DAG] 慢边界求解明细: {}@{}×{} 耗时 {}ms | 阶段ms: 枚举={} dup={} 并集={} 逐环={} 催化={} | 尝试 {} 次(回落 {} / 溢出 {}),催化候选 {} 条",
                        what, what.getItemDamage(), target, elapsed / 1_000_000L, stageMs[0],
                        stageMs[1], stageMs[2], stageMs[3], stageMs[4], diag[0], diag[1], diag[2],
                        diag[3]);
            }
        }
    }

    /** 看门狗调度器(守护单线程;任务体仅置标志+中断,开销可忽略). */
    private static final class Timebox {
        private static final java.util.concurrent.ScheduledExecutorService SCHEDULER = java.util.concurrent.Executors
                .newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "ae2e-boundary-timebox");
                    t.setDaemon(true);
                    return t;
                });

        private Timebox() {
        }
    }

    private static BoundaryResult solveIntoCore(ICraftingGrid cc, CraftingJob job, IAEItemStack what,
            long target, MECraftingInventory inv, CraftingTreeNode rootNode, IActionSource src, World world,
            AnalysisBudget budget, int[] diag, long[] stageMs) throws InterruptedException {
        long stageStart = System.nanoTime();
        NetworkPatternIndex index = NetworkPatternIndex.of(cc);
        List<List<CycleAnalyzer.CycleStep>> cycles = CycleAnalyzer.findCyclesThrough(cc, what, world);
        stageMs[0] = (System.nanoTime() - stageStart) / 1_000_000L;
        stageStart = System.nanoTime();
        // ① 净增殖自引用（单节点自环快速路径）:仅当其非自身输入不触碰任何过 what 的
        // 环键时安全——否则共输入本身是同环成员,单键贷款法会把它们当普通外部输入经
        // 原生树请求,而原生 notRecursive 会排除祖先样板,交叉增产环不联动必然部分
        // 失败误记缺料;此时必须走②并集联立
        Set<IAEItemStack> cycleKeys = new HashSet<>();
        for (List<CycleAnalyzer.CycleStep> cycle : cycles) {
            for (CycleAnalyzer.CycleStep step : cycle) {
                cycleKeys.add(step.fromKey());
                cycleKeys.add(step.toKey());
            }
        }
        for (ICraftingPatternDetails pattern : cc.getCraftingFor(what, null, -1, world)) {
            if (RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, what)
                    && coInputsOutsideCycles(pattern, what, cycleKeys)) {
                stageMs[1] = -1; // 标记:走了 dup 快速路径(其耗时 = 总耗时 - 其余阶段)
                return solveDup(cc, job, pattern, what, target, inv, rootNode, src, world);
            }
        }
        stageMs[1] = (System.nanoTime() - stageStart) / 1_000_000L;
        stageStart = System.nanoTime();
        // ② 跨样板环:并集优先(θ 形共享结构),再逐环迭代
        boolean overflow = false;
        CycleAnalyzer.Analysis union = budget.expired() ? null
                : CycleAnalyzer.analyzeUnionMemo(index, cycles);
        stageMs[2] = (System.nanoTime() - stageStart) / 1_000_000L;
        stageStart = System.nanoTime();
        if (union != null && union.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE) {
            diag[0]++;
            CycleSolver.SolveResult r = CycleSolver.trySolve(cc, job, union, inv, what, target, rootNode,
                    src, world);
            if (r == CycleSolver.SolveResult.SUCCESS) {
                return BoundaryResult.SOLVED;
            }
            if (r == CycleSolver.SolveResult.FALLBACK) {
                diag[1]++;
            }
            overflow |= r == CycleSolver.SolveResult.OVERFLOW;
            if (r == CycleSolver.SolveResult.OVERFLOW) {
                diag[2]++;
            }
        }
        for (List<CycleAnalyzer.CycleStep> cycle : cycles) {
            if (budget.expired()) {
                SpecialLog.info("[DAG] 循环边界分析超共享预算(>{}ms),整单回落: {}×{}",
                        AnalysisBudget.SOLVE_BUDGET_MS, what, target);
                return BoundaryResult.FALLBACK;
            }
            CycleAnalyzer.Analysis analysis = CycleAnalyzer.analyzeMemo(index, cycle);
            if (analysis == null || analysis.rateClass() != CycleAnalyzer.RateClass.PRODUCTIVE) {
                continue;
            }
            CycleSolver.SolveResult r = CycleSolver.trySolve(cc, job, analysis, inv, what, target, rootNode,
                    src, world);
            diag[0]++;
            if (r == CycleSolver.SolveResult.SUCCESS) {
                return BoundaryResult.SOLVED;
            }
            if (r == CycleSolver.SolveResult.FALLBACK) {
                diag[1]++;
            }
            overflow |= r == CycleSolver.SolveResult.OVERFLOW;
            if (r == CycleSolver.SolveResult.OVERFLOW) {
                diag[2]++;
            }
        }
        // ③ 催化环:边界 key 是某中性/增殖环发射的环外副产物(深层 A→X+B、B→A 中的 X)
        stageMs[3] = (System.nanoTime() - stageStart) / 1_000_000L;
        stageStart = System.nanoTime();
        List<List<CycleAnalyzer.CycleStep>> catalyticCycles = CycleAnalyzer.findCatalyticCycles(cc, what,
                world);
        diag[3] = catalyticCycles.size();
        for (List<CycleAnalyzer.CycleStep> cycle : catalyticCycles) {
            if (budget.expired()) {
                SpecialLog.info("[DAG] 催化环分析超共享预算(>{}ms),整单回落: {}×{}",
                        AnalysisBudget.SOLVE_BUDGET_MS, what, target);
                return BoundaryResult.FALLBACK;
            }
            CycleAnalyzer.Analysis analysis = CycleAnalyzer.analyzeMemo(index, cycle);
            if (analysis == null || analysis.rateClass() == CycleAnalyzer.RateClass.DISSIPATIVE) {
                continue;
            }
            long xPerRound = CycleAnalyzer.byproductPerRound(analysis, what);
            if (xPerRound <= 0) {
                continue;
            }
            CycleSolver.SolveResult r = CycleSolver.trySolveCatalytic(cc, job, analysis, xPerRound, inv,
                    what, target, rootNode, src, world);
            diag[0]++;
            if (r == CycleSolver.SolveResult.SUCCESS) {
                return BoundaryResult.SOLVED;
            }
            if (r == CycleSolver.SolveResult.FALLBACK) {
                diag[1]++;
            }
            overflow |= r == CycleSolver.SolveResult.OVERFLOW;
            if (r == CycleSolver.SolveResult.OVERFLOW) {
                diag[2]++;
            }
        }
        if (overflow) {
            // 天文数字边界需求(轮数/贷款量超 long):对齐根请求路径的 O(1) 缺料语义
            stageMs[4] = (System.nanoTime() - stageStart) / 1_000_000L;
            SpecialLog.info("[DAG] 循环边界天文数字需求,记缺料: {}×{}", what, target);
            return BoundaryResult.MISSING;
        }
        stageMs[4] = (System.nanoTime() - stageStart) / 1_000_000L;
        SpecialLog.info("[DAG] 循环边界不可解: {}@{}×{}", what, what.getItemDamage(), target);
        return BoundaryResult.FALLBACK;
    }

    /**
     * 净增殖自引用样板的非自身输入是否全部不触碰过 what 的环键
     * （环键集为空时恒 true,保持纯 dup 快速路径不变）.
     */
    private static boolean coInputsOutsideCycles(ICraftingPatternDetails pattern, IAEItemStack what,
            Set<IAEItemStack> cycleKeys) {
        if (cycleKeys.isEmpty()) {
            return true;
        }
        IAEItemStack self = RecursiveCraftingHelper.canon(what);
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack key = RecursiveCraftingHelper.canon(input);
            if (key.equals(self)) {
                continue;
            }
            if (cycleKeys.contains(key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 净增殖自引用闭式解（贷款法）,与 SpecialCraftingJob 根路径同语义.
     */
    private static BoundaryResult solveDup(ICraftingGrid cc, CraftingJob job,
            ICraftingPatternDetails selfRef, IAEItemStack what, long target, MECraftingInventory inv,
            CraftingTreeNode rootNode, IActionSource src, World world) throws InterruptedException {
        long inPer = RecursiveCraftingHelper.selfInputPerCraft(selfRef, what);
        long outPer = RecursiveCraftingHelper.selfOutputPerCraft(selfRef, what);
        long gain = outPer - inPer;
        if (gain <= 0 || inPer <= 0) {
            return BoundaryResult.FALLBACK;
        }
        // 种子校验(不 ignore,边界 key 的库存可见)
        long stock = CycleSolver.invAmount(inv, what);
        if (stock < inPer) {
            return BoundaryResult.FALLBACK;
        }
        // 溢出安全 ceilDiv(target、gain 为正,必得 crafts ≥ 1)
        long crafts = target / gain + (target % gain != 0 ? 1 : 0);
        // 产出侧守卫:批量模拟无饱和乘法,crafts×outPer 超 long 会使产出回绕成负数、
        // 库存记账错乱、结算必败 → 整单回落原生.
        // (outPer ≥ inPer+1,故该守卫同时覆盖贷款量 inPer×(crafts-1) 的可表示性)
        if (crafts > Long.MAX_VALUE / outPer) {
            return BoundaryResult.MISSING; // 天文数字子需求 → O(1) 缺料
        }

        // 蛛网子树预检(与 CycleSolver 同规):dup 样板的非自身输入属巨型 SCC 时,
        // 树模型广度爆炸,判不适用 → 环盲降级(DAG 引擎批量展开该子树)
        Set<IAEItemStack> guardExcluded = new HashSet<>();
        guardExcluded.add(RecursiveCraftingHelper.canon(what));
        IAEItemStack risk = CycleSolver.subcraftWebRisk(cc, selfRef, guardExcluded);
        if (risk != null) {
            SpecialLog.info("[DAG] dup 边界预检拦截: {} 属巨型 SCC,树模型广度爆炸转环盲({}×{})", risk,
                    what, target);
            return BoundaryResult.FALLBACK;
        }

        CraftingTreeProcess pro = new CraftingTreeProcess(cc, job, selfRef, rootNode, 1);
        Ae2CraftingReflect.addProcessToNode(rootNode, pro);

        long loan = inPer * (crafts - 1);
        if (loan > 0) {
            IAEItemStack loanStack = RecursiveCraftingHelper.canon(what);
            loanStack.setStackSize(loan);
            inv.injectItems(loanStack, Actionable.MODULATE, src);
        }
        // CrT 不消耗配方(同根请求路径):催化剂预注入虚拟返还(不归还)
        Map<IAEItemStack, Long> catalystInject = new LinkedHashMap<>();
        Map<IAEItemStack, Long> catalystRebate = new LinkedHashMap<>();
        Set<IAEItemStack> catalystExcluded = new HashSet<>();
        catalystExcluded.add(RecursiveCraftingHelper.canon(what));
        CatalystReturns.collect(selfRef, crafts, catalystExcluded, catalystInject, catalystRebate);
        CatalystReturns.inject(catalystInject, inv, src);
        try {
            BatchSubcraft.requestStep(pro, cc, job, inv, crafts, src);
        } catch (CraftBranchFailure failure) {
            return BoundaryResult.FALLBACK; // 非自输入不足 → 整单回落(缺料报告)
        } finally {
            if (loan > 0) {
                IAEItemStack payback = RecursiveCraftingHelper.canon(what);
                payback.setStackSize(loan);
                inv.extractItems(payback, Actionable.MODULATE, src);
            }
        }

        // used 返利:种子语义 = inPer(与根请求路径一致)
        Map<IAEItemStack, Long> seeds = new LinkedHashMap<>();
        seeds.put(RecursiveCraftingHelper.canon(what), inPer);
        seeds.putAll(catalystRebate); // 催化剂种子语义:净消耗+单次投入
        TreeUsedRebate.rebate(rootNode, seeds);

        // 结算:取走交付量(边界需求),种子保留
        long avail = CycleSolver.invAmount(inv, what);
        long keep = avail > target ? inPer : 0;
        long drainable = Math.min(target, Math.max(0, avail - keep));
        if (drainable > 0) {
            IAEItemStack drainStack = RecursiveCraftingHelper.canon(what);
            drainStack.setStackSize(drainable);
            inv.extractItems(drainStack, Actionable.MODULATE, src);
        }
        return drainable == target ? BoundaryResult.SOLVED : BoundaryResult.FALLBACK;
    }
}
