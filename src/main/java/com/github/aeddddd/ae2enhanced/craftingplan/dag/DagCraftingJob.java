package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.MECraftingInventory;
import appeng.hooks.TickHandler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.diag.plan.PlanTracker;
import com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner;
import com.github.aeddddd.ae2enhanced.specialcrafting.FlowReconciler;
import com.github.aeddddd.ae2enhanced.specialcrafting.LpPlanMaterializer;
import com.github.aeddddd.ae2enhanced.specialcrafting.NativeCalcBudget;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialLog;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanDisplayHook;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;

/**
 * DAG 合成计算器（阶段 4,1.12.2 移植）:以"编译 DAG + 拓扑单趟扫描"取代原生递归树.
 * <p>继承原生 {@link CraftingJob} 复用其时间片调度/暂停/线程池骨架;
 * DAG 路径任何回落信号或异常都退回原生 {@code super.run()}（宁可慢不可错）.</p>
 * <p><b>1.12.2 关键差异</b>:树即计划且是提交载体,DAG 结果经 {@link DagExecutor}
 * 物化为原生树（每节点挂一次、used/缺料回填、边界子树接入）后,提交/显示/执行
 * 全部复用原生路径.含循环边界的计划标记特殊,硬路由到超因果计算核心
 * （无限库存 + 门控/配额调度在场）.</p>
 */
public class DagCraftingJob extends CraftingJob
        implements com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingJobBudgetAccess {

    protected final World world;

    /** 本次计划是否含循环边界（物化成功后用于特殊标记）. */
    protected boolean hasCycleBoundary;

    /** 原生回落计算预算状态（MixinCraftingJob 的 handlePausing 心跳读取）. */
    private long nativeCalcDeadlineNanos;
    private boolean nativeCalcAborted;

    /** 最近一次 DAG 回落原因（冒号前段作为指标键）,供 run() 回落路径计数. */
    @Nullable
    private String lastFallbackReason;

    @Override
    public long ae2enhanced$nativeCalcDeadlineNanos() {
        return this.nativeCalcDeadlineNanos;
    }

    @Override
    public void ae2enhanced$armNativeCalcBudget(long deadlineNanos) {
        this.nativeCalcDeadlineNanos = deadlineNanos;
    }

    @Override
    public void ae2enhanced$markNativeCalcAborted() {
        this.nativeCalcAborted = true;
    }

    @Override
    public boolean ae2enhanced$nativeCalcAborted() {
        return this.nativeCalcAborted;
    }

    public DagCraftingJob(World w, IGrid grid, IActionSource actionSrc, IAEItemStack what,
            ICraftingCallback callback) {
        super(w, grid, actionSrc, what, callback);
        this.world = w;
    }

    @Override
    public void run() {
        long planStart = System.nanoTime();
        // 标记是否经 super.run() 回落原生——该路径已由 MixinCraftingJob 的 run RETURN 钩子计数,避免重复
        boolean delegatedToNative = false;
        try {
            TickHandler.INSTANCE.registerCraftingSimulation(this.world, this);
            Ae2CraftingReflect.handlePausing(this);

            CraftingTreeNode root = this.computeDagPlan();
            if (root == null) {
                Ae2CraftingReflect.setAvailableCheck(this, null);
                // 回落原生:挂计算预算——病态计划的原生递归可能永不结束,
                // 而下单流程(如 RandomComplement 的 setJob 混入)会同步阻塞服务器线程
                recordFallback(this.lastFallbackReason);
                MetricsRegistry.timer("plan.computeMs.dagPhase")
                        .record(System.nanoTime() - planStart);
                NativeCalcBudget.arm(this);
                delegatedToNative = true;
                super.run();
                NativeCalcBudget.warnIfAborted(this);
                return;
            }
            Ae2CraftingReflect.setTree(this, root);
            Ae2CraftingReflect.nodeDive(root, this);
            // 缺料(模拟)计划不标记:原生 submitJob 本就拒绝模拟计划
            if (this.hasCycleBoundary && !this.isSimulation()) {
                SpecialPlanMarker.mark(this);
            }
            SpecialPlanDisplayHook.sendPlanInfo(this);
            Ae2CraftingReflect.finish(this);
        } catch (InterruptedException e) {
            SpecialLog.info("[DAG] 计算被取消");
            NativeCalcBudget.warnIfAborted(this);
            Ae2CraftingReflect.finish(this);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("DAG 计划异常,回落原生计算: {}", t.toString());
            Ae2CraftingReflect.setAvailableCheck(this, null);
            recordFallback(this.lastFallbackReason != null ? this.lastFallbackReason : "exception");
            MetricsRegistry.timer("plan.computeMs.dagPhase")
                    .record(System.nanoTime() - planStart);
            NativeCalcBudget.arm(this);
            delegatedToNative = true;
            super.run();
            NativeCalcBudget.warnIfAborted(this);
        } finally {
            if (!delegatedToNative) {
                PlanTracker.onJobComputed(this, System.nanoTime() - planStart);
            }
        }
    }

    /** 回落原因计数:reason 取冒号前段(排除物品键等变长部分),键不存在计 unknown. */
    private static void recordFallback(@Nullable String reason) {
        String key = reason == null ? "unknown" : reason.split(":")[0];
        MetricsRegistry.counter("plan.fallback." + key).increment();
    }

    /** 环盲降级重试上限:每次把一个不可解边界键转为环盲重编译. */
    private static final int MAX_BLIND_RETRIES = 8;

    /**
     * @return 物化完成的根节点;任何不适用情形返回 null（调用方回落原生）.
     */
    @Nullable
    protected CraftingTreeNode computeDagPlan() throws InterruptedException {
        IActionSource src = Ae2CraftingReflect.getActionSrc(this);
        ICraftingGrid cc = Ae2CraftingReflect.getCc(this);
        IAEItemStack output = this.getOutput();

        // 开发期 LP 路径影子开关(-Dae2e.lpPlannerShadow=true):完整求解并对照日志,
        // 不产物化,实际计划仍由 DAG 旧路径产出——任何影子异常不影响主路径
        if (Boolean.getBoolean("ae2e.lpPlannerShadow")) {
            this.runLpPlannerShadow(cc, output);
        }

        // LP 计划器真实路由(-Dae2e.lpPlanner=true,开发期):冷凝分层求解 → 整数化
        // 对账重演 → 物化原生树;任何失败返回 null 继续 DAG 旧路径(开发期双保险)
        if (Boolean.getBoolean("ae2e.lpPlanner")) {
            CraftingTreeNode lpRoot = this.computeLpPlan(cc, src, output);
            if (lpRoot != null) {
                return lpRoot;
            }
            SpecialLog.info("[LP计划] 求解失败,继续 DAG 旧路径: {}", output);
        }

        DagGraph graph = null;
        CraftingTreeNode root = null;
        DagExecutor.Result result = null;
        // 环盲降级:边界求解失败(伪环/耗散环本就无解)时,把该键转为环盲重编译——
        // 按原生 notRecursive 语义展开(回边切纯库存叶子),逐键降级直到计划成型
        java.util.Set<IAEItemStack> cycleBlind = new java.util.HashSet<>();
        // 跨波次共享的编译上下文:边界判定/矿词展开按不可变样板记忆,波次间复用
        com.github.aeddddd.ae2enhanced.specialcrafting.CycleAnalyzer.ProducerIndex sharedProducerIndex =
                new com.github.aeddddd.ae2enhanced.specialcrafting.CycleAnalyzer.ProducerIndex(cc,
                        this.world);
        java.util.Map<appeng.api.networking.crafting.ICraftingPatternDetails, java.util.Map<IAEItemStack, java.util.List<IAEItemStack>>> sharedSubstituteCache =
                new java.util.IdentityHashMap<>();
        // 最终兜底波次:重试预算耗尽后置位,下一波编译一切回边目标都按环盲处理
        // (巨网中"盲化→暴露新边界"的打地鼠可无限继续,该波保证收敛)
        boolean allCyclesBlind = false;
        for (int attempt = 0;; attempt++) {
            long compileStart = System.nanoTime();
            try {
                graph = DagCompiler.compile(cc, output, this.world, cycleBlind, sharedProducerIndex,
                        sharedSubstituteCache, allCyclesBlind);
            } catch (DagFallback fallback) {
                this.lastFallbackReason = fallback.reason;
                SpecialLog.info("[DAG] 编译回落({}): {}", fallback.reason, output);
                return null;
            } finally {
                MetricsRegistry.timer("plan.phase.dag.compileMs")
                        .record(System.nanoTime() - compileStart);
            }

            // 趟 1:非模拟语义(多样板节点分支按供给容量封顶 = 真分支耗尽)
            long pass1Start = System.nanoTime();
            try {
                root = this.executePass(graph, output, cc, src, false);
                result = this.lastPassResult;
            } catch (DagFallback fallback) {
                if (!fallback.blindKeys.isEmpty() && attempt < MAX_BLIND_RETRIES
                        && cycleBlind.addAll(fallback.blindKeys)) {
                    SpecialLog.info("[DAG] 边界求解失败,降级环盲重编译(波次 {},新增 {} 键): {}",
                            attempt + 1, fallback.blindKeys.size(), fallback.blindKeys);
                    MetricsRegistry.counter("plan.dag.cycleBlindRetry").increment();
                    continue;
                }
                // 重试预算耗尽:下一波编译进入全环盲兜底(一切回边目标切库存叶子,
                // 不再产生循环边界)——病态巨网订单逐波打地鼠可能超预算,跌回原生
                // 递归即高请求计算卡死(生产事故语义);全盲波缺料如实上报且必然收敛
                if (!fallback.blindKeys.isEmpty() && attempt >= MAX_BLIND_RETRIES
                        && !allCyclesBlind) {
                    allCyclesBlind = true;
                    SpecialLog.info("[DAG] 环盲重试预算耗尽,进入全环盲兜底波次(剩余 {} 键不再尝试求解)",
                            fallback.blindKeys.size());
                    MetricsRegistry.counter("plan.dag.cycleBlindFinalSweep").increment();
                    continue;
                }
                this.lastFallbackReason = fallback.reason;
                SpecialLog.info("[DAG] 执行回落({}): {}", fallback.reason, output);
                return null;
            } finally {
                MetricsRegistry.timer("plan.phase.dag.pass1Ms")
                        .record(System.nanoTime() - pass1Start);
            }

            if (!result.missingItems.isEmpty()) {
                // 原生 simulation 标志由失败重试置位;DAG 缺料不抛异常,须显式置位,
                // 否则产出"有缺料却标记可提交"的不一致计划
                Ae2CraftingReflect.setSimulate(this, true);
                if (graph.hasMultiBranch) {
                    // 趟 2(移植自 1.20.1):镜像原生失败重试——模拟语义下首分支不封顶
                    // ("乐观幻影生产"),缺料浮现于分支 1 原料层,分支 2 不参与;
                    // 单分支图两趟等价,无需重算(既有单趟行为,parity 测试锁定)
                    long pass2Start = System.nanoTime();
                    try {
                        root = this.executePass(graph, output, cc, src, true);
                        result = this.lastPassResult;
                    } catch (DagFallback fallback) {
                        if (!fallback.blindKeys.isEmpty() && attempt < MAX_BLIND_RETRIES
                                && cycleBlind.addAll(fallback.blindKeys)) {
                            SpecialLog.info("[DAG] 模拟趟边界求解失败,降级环盲重编译(新增 {} 键): {}",
                                    fallback.blindKeys.size(), fallback.blindKeys);
                            MetricsRegistry.counter("plan.dag.cycleBlindRetry").increment();
                            continue;
                        }
                        // 与趟 1 同理:重试预算耗尽后进入全环盲兜底波次保证收敛,
                        // 否则模拟趟在此跌回原生递归(高请求计算卡死)
                        if (!fallback.blindKeys.isEmpty() && attempt >= MAX_BLIND_RETRIES
                                && !allCyclesBlind) {
                            allCyclesBlind = true;
                            SpecialLog.info("[DAG] 模拟趟环盲重试预算耗尽,进入全环盲兜底波次(剩余 {} 键不再尝试求解)",
                                    fallback.blindKeys.size());
                            MetricsRegistry.counter("plan.dag.cycleBlindFinalSweep").increment();
                            continue;
                        }
                        this.lastFallbackReason = fallback.reason;
                        SpecialLog.info("[DAG] 模拟趟执行回落({}): {}", fallback.reason, output);
                        return null;
                    } finally {
                        MetricsRegistry.timer("plan.phase.dag.pass2Ms")
                                .record(System.nanoTime() - pass2Start);
                    }
                }
            }
            break;
        }
        this.hasCycleBoundary = result.hasCycleBoundary;
        SpecialLog.info("[DAG] 计划完成: {}×{},节点 {},循环边界 {}", output, output.getStackSize(),
                graph.topoOrder.size(), this.hasCycleBoundary);
        return root;
    }

    /**
     * LP 计划路径(M5):CondensationPlanner 冷凝求解 → FlowReconciler 整数化重演对账
     * → LpPlanMaterializer 物化原生树.失败/异常返回 null(调用方继续 DAG 旧路径).
     */
    @Nullable
    private CraftingTreeNode computeLpPlan(ICraftingGrid cc, IActionSource src, IAEItemStack output) {
        try {
            NetworkPatternIndex index = NetworkPatternIndex.of(cc);
            if (index == null) {
                return null;
            }
            MECraftingInventory inv = new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), true, false,
                    true);
            Ae2CraftingReflect.setAvailableCheck(this,
                    new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), false, false, false));
            Map<IAEItemStack, Long> stock = new java.util.HashMap<>();
            for (IAEItemStack stack : inv.getItemList()) {
                if (stack.getStackSize() > 0) {
                    stock.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(), Long::sum);
                }
            }
            CondensationPlanner.LpPlanOutcome outcome = CondensationPlanner.solve(cc, index, output,
                    output.getStackSize(), stock);
            // 降级埋点(D4 库存直通/截断;验收口径要求恒 0)
            for (int i = 0; i < outcome.degradedUnits; i++) {
                MetricsRegistry.counter("plan.lp.degradedUnit").increment();
            }
            CraftingTreeNode root = new CraftingTreeNode(cc, this, output.copy(), null, -1, 0);
            FlowReconciler.Reconciled reconciled = FlowReconciler.reconcile(cc, output,
                    output.getStackSize(), outcome, inv, root, src);
            LpPlanMaterializer.attach(cc, this, root, output, outcome, reconciled.times, reconciled.missing,
                    reconciled.totalExtracted);
            if (!reconciled.missing.isEmpty()) {
                // 与 DAG 路径同口径:缺料计划显式置模拟标志(原生失败重试同语义)
                Ae2CraftingReflect.setSimulate(this, true);
            }
            // 含成环样板执行的 LP 计划:执行层走特殊 CPU 门控(与循环边界计划同待遇)
            for (com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution exec : outcome.executions) {
                if (reconciled.times.getOrDefault(exec, 0L) > 0 && index.isCycleStep(exec.pattern)) {
                    this.hasCycleBoundary = true;
                    break;
                }
            }
            SpecialLog.info(
                    "[LP计划] 完成: {}×{},单元 {}(LP {},降级 {}),执行记录 {},缺料 {} 种,迭代 {},求解 {}ms",
                    output, output.getStackSize(), outcome.units, outcome.lpUnits, outcome.degradedUnits,
                    outcome.executions.size(), reconciled.missing.size(), outcome.iterations, outcome.wallMs);
            return root;
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[LP计划] 异常,回落 DAG 旧路径: {}", t.toString());
            return null;
        }
    }

    /**
     * LP 路径影子求解(开发期对照):冷凝分层驱动器完整求解,日志输出
     * 单元数/迭代数/赤字规模,结果不物化、不影响 DAG 主路径.
     */
    private void runLpPlannerShadow(ICraftingGrid cc, IAEItemStack output) {
        try {
            com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex index =
                    com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex.of(cc);
            if (index == null) {
                return;
            }
            Map<IAEItemStack, Long> stock = new java.util.HashMap<>();
            for (IAEItemStack stack : Ae2CraftingReflect.getOriginal(this).getItemList()) {
                if (stack.getStackSize() > 0) {
                    stock.merge(com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper
                            .canon(stack), stack.getStackSize(), Long::sum);
                }
            }
            com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome outcome =
                    com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.solve(
                            cc, index, output, output.getStackSize(), stock);
            SpecialLog.info(
                    "[LP影子] {}×{}: 单元 {}(LP {}),执行记录 {},赤字 {} 种,迭代 {},降级 {},耗时 {}ms",
                    output, output.getStackSize(), outcome.units, outcome.lpUnits, outcome.executions.size(),
                    outcome.deficits.size(), outcome.iterations, outcome.degradedUnits, outcome.wallMs);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[LP影子] 求解异常(不影响主路径): {}", t.toString());
        }
    }

    /** 单趟执行结果暂存(供 computeDagPlan 读取,避免包装类). */
    private DagExecutor.Result lastPassResult;

    /**
     * 单趟 DAG 执行:全新模拟库存 + 全新物化树(趟间互不染指,
     * availableCheck 同步重置——镜像原生 CraftingJob.run 失败重试的重建).
     */
    private CraftingTreeNode executePass(DagGraph graph, IAEItemStack output, ICraftingGrid cc,
            IActionSource src, boolean simulation) throws DagFallback, InterruptedException {
        MECraftingInventory inv = new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), true, false,
                true);
        Ae2CraftingReflect.invIgnore(inv, output); // 镜像原生:请求物自身库存不参与计划扣除
        Ae2CraftingReflect.setAvailableCheck(this,
                new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), false, false, false));
        CraftingTreeNode root = new CraftingTreeNode(cc, this, output.copy(), null, -1, 0);
        this.lastPassResult = DagExecutor.execute(graph, output.getStackSize(), inv, this, cc, this.world,
                root, src, simulation);
        return root;
    }

    /**
     * 缺失显示确定性修复：实测原生 {@code CraftingTreeNode.getPlan} 的 missing→plan
     * 转移在部分环境下不稳定（节点 missing>0 时方法体概率性不执行 plan.add,呈启动级随机）,
     * 导致 ContainerCraftConfirm 按 plan 重算缺失得到 0,缺失条目（如流体）不显示.
     * 这里改为确定性方案：先收集并清零树上 missing,super 构建完 used/requestable 后,
     * 由本类按原生同语义（what.copy + missing 量）统一注入 plan.used/requestable 分支
     * 经多轮日志验证始终正常,仅 missing 分支存在该不稳定性.
     */
    @Override
    public void populatePlan(IItemList<IAEItemStack> plan) {
        List<CraftingTreeNode> missingNodes = new ArrayList<>();
        collectMissingNodes(this.getTree(), missingNodes);
        if (missingNodes.isEmpty()) {
            super.populatePlan(plan);
            return;
        }
        List<Long> saved = new ArrayList<>(missingNodes.size());
        for (CraftingTreeNode node : missingNodes) {
            saved.add(Ae2CraftingReflect.getNodeMissing(node));
            Ae2CraftingReflect.setNodeMissing(node, 0L);
        }
        try {
            super.populatePlan(plan);
        } finally {
            for (int i = 0; i < missingNodes.size(); i++) {
                Ae2CraftingReflect.setNodeMissing(missingNodes.get(i), saved.get(i));
            }
        }
        for (int i = 0; i < missingNodes.size(); i++) {
            IAEItemStack o = Ae2CraftingReflect.getNodeWhat(missingNodes.get(i)).copy();
            o.setStackSize(saved.get(i));
            plan.add(o);
        }
    }

    private static void collectMissingNodes(CraftingTreeNode node, List<CraftingTreeNode> out) {
        if (node == null) {
            return;
        }
        if (Ae2CraftingReflect.getNodeMissing(node) > 0) {
            out.add(node);
        }
        for (CraftingTreeProcess pro : Ae2CraftingReflect.getNodeProcesses(node)) {
            for (CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pro).keySet()) {
                collectMissingNodes(child, out);
            }
        }
    }
}
