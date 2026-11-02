package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.World;

import appeng.api.config.Actionable;
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
import com.github.aeddddd.ae2enhanced.observation.Observations;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;

/**
 * LP 合成计算器:冷凝分层求解 → 整数化对账重演 → 物化原生树,取代原生递归树计算.
 * <p>继承原生 {@link CraftingJob} 复用其时间片调度/暂停/线程池骨架;
 * LP 路径任何失败/异常都退回原生 {@code super.run()}.</p>
 * <p>1.12.2 中树即计划且是提交载体:LP 计数解经 {@link FlowReconciler} 整数化重演
 * 对账 + {@link LpPlanMaterializer} 物化为原生树后,提交/显示/执行全部复用原生路径.
 * 含成环样板执行的计划标记特殊,执行走超因果计算核心(无限库存 + 门控/配额调度).</p>
 */
public class LpCraftingJob extends CraftingJob
        implements com.github.aeddddd.ae2enhanced.craftingapi.CraftingJobBudgetAccess {

    protected final World world;

    /** 每 job 只捕获一次；FALLBACK 委托原生 LP run 时也沿用同一份。 */
    @Nullable
    private RequestConfigSnapshot requestConfig;

    /** 本次计划是否含成环样板执行（物化成功后用于特殊标记）. */
    protected boolean hasCycleBoundary;

    /** 本次计划中未解出(降级/截断)的单元数:非 0 时计划不可提交且不按缺料显示. */
    private int unresolvedUnits;

    /** 未解出单元数(SpecialPlanInfo 同步用). */
    public int unresolvedUnits() {
        return this.unresolvedUnits;
    }

    @Nullable
    private ResolvedPlan resolvedPlan;
    @Nullable
    private LpPlanMaterializer.Report materializationReport;

    /** 回落原因计数:reason 取冒号前段(排除物品键等变长部分),键不存在计 unknown. */
    private static void recordFallback(@Nullable String reason) {
        String key = reason == null ? "unknown" : reason.split(":")[0];
        Observations.counter("plan.fallback." + key).increment();
    }

    @Nullable
    public ResolvedPlan resolvedPlan() {
        return this.resolvedPlan;
    }

    /** 原生回落计算预算状态（MixinCraftingJob 的 handlePausing 心跳读取）. */
    private long nativeCalcDeadlineNanos;
    private boolean nativeCalcAborted;

    /** 最近一次 LP 回落原因（冒号前段作为指标键）,供 run() 回落路径计数. */
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

    public LpCraftingJob(World w, IGrid grid, IActionSource actionSrc, IAEItemStack what,
            ICraftingCallback callback) {
        super(w, grid, actionSrc, what, callback);
        this.world = w;
    }

    @Nullable
    public LpPlanMaterializer.Report materializationReport() {
        return this.materializationReport;
    }

    @Override
    public void run() {
        long planStart = System.nanoTime();
        RequestConfigSnapshot config = requestConfig();
        boolean delegatedToNative = false;
        try {
            TickHandler.INSTANCE.registerCraftingSimulation(this.world, this);
            Ae2CraftingReflect.handlePausing(this);

            CraftingTreeNode root = this.computeLpPlan(config);
            if (root == null) {
                prepareNativeFallback();
                recordFallback(this.lastFallbackReason);
                Observations.timer("plan.computeMs.lpPhase").record(System.nanoTime() - planStart);
                NativeCalcBudget.arm(this, config.nativeCalcBudgetMs);
                delegatedToNative = true;
                super.run();
                NativeCalcBudget.warnIfAborted(this, config.nativeCalcBudgetMs);
                return;
            }
            Ae2CraftingReflect.setTree(this, root);
            Ae2CraftingReflect.nodeDive(root, this);
            revalidateMaterialization(root);
            if (this.hasCycleBoundary && !this.isSimulation() && config.specialCrafting) {
                SpecialPlanMarker.mark(this);
            }
            SpecialPlanDisplayHook.sendPlanInfo(this);
            Ae2CraftingReflect.finish(this);
        } catch (InterruptedException e) {
            SpecialLog.info("[LP计划] 计算被取消");
            NativeCalcBudget.warnIfAborted(this, config.nativeCalcBudgetMs);
            Ae2CraftingReflect.finish(this);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("LP 计划异常,回落原生计算", t);
            prepareNativeFallback();
            recordFallback(this.lastFallbackReason != null ? this.lastFallbackReason : "exception");
            Observations.timer("plan.computeMs.lpPhase").record(System.nanoTime() - planStart);
            NativeCalcBudget.arm(this, config.nativeCalcBudgetMs);
            delegatedToNative = true;
            super.run();
            NativeCalcBudget.warnIfAborted(this, config.nativeCalcBudgetMs);
        } finally {
            if (!delegatedToNative) {
                PlanObservationFactory.onJobComputed(this, System.nanoTime() - planStart);
            }
        }
    }

    /** dive 后重验最终树；任何物化差异都钉为不可提交。 */
    protected final void revalidateMaterialization(CraftingTreeNode root) {
        if (this.materializationReport == null) {
            return;
        }
        this.materializationReport = this.materializationReport.revalidate(root);
        if (!this.materializationReport.committable()) {
            Ae2CraftingReflect.setSimulate(this, true);
            AE2Enhanced.LOGGER.warn("[LP计划] 物化契约拒绝提交: {}", this.materializationReport.reasons());
        }
    }

    /** 原生 request 会从根节点就地建树，回落前必须丢弃所有 LP/原生半成品。 */
    protected final void prepareNativeFallback() {
        ICraftingGrid cc = Ae2CraftingReflect.getCc(this);
        Ae2CraftingReflect.setTree(this,
                new CraftingTreeNode(cc, this, this.getOutput().copy(), null, -1, 0));
        Ae2CraftingReflect.setAvailableCheck(this, null);
        this.resolvedPlan = null;
        this.materializationReport = null;
        this.hasCycleBoundary = false;
        this.unresolvedUnits = 0;
    }

    /** run 与 computeLpPlan 的共用入口；直接调用 computeLpPlan 的兼容路径也在此捕获。 */
    protected final RequestConfigSnapshot requestConfig() {
        if (this.requestConfig == null) {
            this.requestConfig = RequestConfigSnapshot.capture();
        }
        return this.requestConfig;
    }

    /**
     * LP 计划路径:CondensationPlanner 冷凝求解 → FlowReconciler 整数化重演对账
     * → LpPlanMaterializer 物化原生树.失败/异常返回 null(调用方回落原生).
     */
    @Nullable
    protected CraftingTreeNode computeLpPlan() throws InterruptedException {
        return computeLpPlan(requestConfig());
    }

    /** 整单配置只经此参数进入求解链，不在单元循环或回落路径重读。 */
    @Nullable
    protected CraftingTreeNode computeLpPlan(RequestConfigSnapshot config) throws InterruptedException {
        IActionSource src = Ae2CraftingReflect.getActionSrc(this);
        ICraftingGrid cc = Ae2CraftingReflect.getCc(this);
        IAEItemStack output = this.getOutput();
        this.unresolvedUnits = 0;
        try {
            NetworkPatternIndex index = NetworkPatternIndex.of(cc);
            if (index == null) {
                this.lastFallbackReason = "no_pattern_index";
                return null;
            }
            MECraftingInventory original = Ae2CraftingReflect.getOriginal(this);
            Ae2CraftingReflect.setAvailableCheck(this,
                    new MECraftingInventory(original, false, false, false));
            JobStockSource stockSource = new JobStockSource(original, src);
            CondensationPlanner.LpPlanOutcome outcome = CondensationPlanner.solve(cc, index, output,
                    output.getStackSize(), stockSource, config);
            Ae2CraftingReflect.handlePausing(this);
            this.unresolvedUnits = outcome.degradedUnits;
            // 降级单元埋点(库存直通/截断,正常应为 0)
            for (int i = 0; i < outcome.degradedUnits; i++) {
                Observations.counter("plan.lp.degradedUnit").increment();
            }
            CraftingTreeNode root = new CraftingTreeNode(cc, this, output.copy(), null, -1, 0);
            // R5 快路径:无正计数执行时对账层不触碰库存(FlowReconciler 的 times 空分支),
            // 故跳过对账用库存副本的构建(省下一次全量库存遍历)
            MECraftingInventory inv = outcome.executions.isEmpty()
                    ? null
                    : stockSource.mutatingStockCopy();
            FlowReconciler.Reconciled reconciled = FlowReconciler.reconcile(cc, index, output,
                    output.getStackSize(), outcome, inv, root, src, this);
            this.resolvedPlan = ResolvedPlan.of(output, outcome, reconciled, root, index);
            this.unresolvedUnits = this.resolvedPlan.unresolvedUnits();
            this.hasCycleBoundary = this.resolvedPlan.hasCycleBoundary();
            this.materializationReport = LpPlanMaterializer.attach(cc, this, root, this.resolvedPlan);
            if (!this.materializationReport.committable()) {
                Ae2CraftingReflect.setSimulate(this, true);
            }
            SpecialLog.info(
                    "[LP计划] 完成: {}×{},单元 {}(活跃 LP {},降级 {}),执行记录 {},缺料 {} 种,迭代 {},求解 {}ms",
                    output, output.getStackSize(), outcome.units, outcome.lpUnits, outcome.degradedUnits,
                    this.resolvedPlan.executions().size(), this.resolvedPlan.missingView().size(),
                    outcome.iterations, outcome.wallMs);
            dumpPlanDetail(output, stockSource, outcome, reconciled);
            return root;
        } catch (InterruptedException e) {
            throw e;
        } catch (Throwable t) {
            this.lastFallbackReason = "lp_exception";
            AE2Enhanced.LOGGER.warn("[LP计划] 异常,回落原生计算", t);
            return null;
        }
    }

    /**
     * 库存来源:{@code amountOf} 逐覆盖键向网络库存做 SIMULATE 查询
     * ({@code extractItems} 在存量小于请求量时返回存量,故请求量取 {@code Long.MAX_VALUE}
     * 即得真实存量,与全量遍历口径逐位一致);{@link #fullStock()} 为旧全量遍历路径
     * (仅 {@code ae2e.planCache=off} 对拍与诊断落盘使用).
     */
    private static final class JobStockSource implements CondensationPlanner.StockSource {
        private final MECraftingInventory original;
        private final IActionSource src;
        /** 全量路径建立的库存副本(与对账共用;旧行为:一单一次副本). */
        private MECraftingInventory fullCopy;

        JobStockSource(MECraftingInventory original, IActionSource src) {
            this.original = original;
            this.src = src;
        }

        @Override
        public long amountOf(IAEItemStack canonKey) {
            IAEItemStack request = canonKey.copy();
            request.setStackSize(Long.MAX_VALUE);
            IAEItemStack got = this.original.extractItems(request, Actionable.SIMULATE, this.src);
            return got == null ? 0L : got.getStackSize();
        }

        @Override
        public Map<IAEItemStack, Long> fullStock() {
            MECraftingInventory copy = new MECraftingInventory(this.original, true, false, true);
            this.fullCopy = copy;
            Map<IAEItemStack, Long> stock = new HashMap<>();
            for (IAEItemStack stack : copy.getItemList()) {
                if (stack.getStackSize() > 0) {
                    stock.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(), Long::sum);
                }
            }
            return stock;
        }

        /** 对账用可变库存副本(仅在有正计数执行时构建;全量路径复用同一份). */
        MECraftingInventory mutatingStockCopy() {
            if (this.fullCopy != null) {
                return this.fullCopy;
            }
            return new MECraftingInventory(this.original, true, false, true);
        }
    }

    /**
     * 计划明细日志（{@code /ae2e debug specialcrafting on} 时生效）:输出变体选择、
     * 库存取用、缺料与相关键库存.
     */
    private static void dumpPlanDetail(IAEItemStack output, CondensationPlanner.StockSource stock,
            CondensationPlanner.LpPlanOutcome outcome, FlowReconciler.Reconciled reconciled) {
        if (!SpecialLog.isEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[LP计划] 明细: 根=").append(output.getStackSize()).append('×').append(output);
        java.util.Set<IAEItemStack> involved = new java.util.HashSet<>();
        for (Map.Entry<Execution, Long> e : reconciled.times.entrySet()) {
            if (e.getValue() <= 0) {
                continue;
            }
            Execution exec = e.getKey();
            sb.append("\n  执行×").append(e.getValue())
                    .append(": in=").append(java.util.Arrays.toString(exec.pattern.getCondensedInputs()))
                    .append(" out=").append(java.util.Arrays.toString(exec.pattern.getCondensedOutputs()));
            if (!exec.variantInputs.isEmpty()) {
                sb.append(" 变体=").append(exec.variantInputs);
            }
            for (IAEItemStack in : exec.pattern.getCondensedInputs()) {
                if (in != null) {
                    involved.add(RecursiveCraftingHelper.canon(in));
                }
            }
            for (IAEItemStack out : exec.pattern.getCondensedOutputs()) {
                if (out != null) {
                    involved.add(RecursiveCraftingHelper.canon(out));
                }
            }
        }
        for (Map.Entry<IAEItemStack, Long> e : reconciled.networkSourced.entrySet()) {
            sb.append("\n  库存取用: ").append(e.getValue()).append('×').append(e.getKey());
        }
        for (Map.Entry<IAEItemStack, Long> e : reconciled.missing.entrySet()) {
            sb.append("\n  缺料: ").append(e.getValue()).append('×').append(e.getKey());
        }
        for (IAEItemStack key : involved) {
            long s = stock.amountOf(key);
            if (s > 0) {
                sb.append("\n  相关库存: ").append(s).append('×').append(key);
            }
        }
        SpecialLog.info(sb.toString());
    }

    /**
     * 原生 dive 只在节点被扫描到时做 missing→plan 转移,未扫描的缺料键会丢失.
     * 先清零 missing 走原生转移(保 used/requestable 不变),再由本类按同口径
     * (仅 stackSize)补回 plan.
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
            for (CraftingTreeNode child : Ae2CraftingReflect.sortedProcessNodes(pro)) {
                collectMissingNodes(child, out);
            }
        }
    }
}
