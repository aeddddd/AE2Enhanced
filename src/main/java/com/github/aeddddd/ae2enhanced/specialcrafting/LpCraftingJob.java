package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayList;
import java.util.HashMap;
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
        implements com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingJobBudgetAccess {

    protected final World world;

    /** 本次计划是否含成环样板执行（物化成功后用于特殊标记）. */
    protected boolean hasCycleBoundary;

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

    @Override
    public void run() {
        long planStart = System.nanoTime();
        // 是否经 super.run() 回落原生;该路径已由 MixinCraftingJob 的 run RETURN 钩子计数,避免重复
        boolean delegatedToNative = false;
        try {
            TickHandler.INSTANCE.registerCraftingSimulation(this.world, this);
            Ae2CraftingReflect.handlePausing(this);

            CraftingTreeNode root = this.computeLpPlan();
            if (root == null) {
                Ae2CraftingReflect.setAvailableCheck(this, null);
                // 回落原生:挂计算预算——病态计划的原生递归可能永不结束,
                // 而下单流程(如 RandomComplement 的 setJob 混入)会同步阻塞服务器线程
                recordFallback(this.lastFallbackReason);
                MetricsRegistry.timer("plan.computeMs.lpPhase").record(System.nanoTime() - planStart);
                NativeCalcBudget.arm(this);
                delegatedToNative = true;
                super.run();
                NativeCalcBudget.warnIfAborted(this);
                return;
            }
            Ae2CraftingReflect.setTree(this, root);
            Ae2CraftingReflect.nodeDive(root, this);
            // 缺料(模拟)计划不标记:原生 submitJob 本就拒绝模拟计划;
            // 功能开关关闭时不标记(提交/执行零干预)
            if (this.hasCycleBoundary && !this.isSimulation()
                    && SpecialCraftingRuntime.isEnabled()) {
                SpecialPlanMarker.mark(this);
            }
            SpecialPlanDisplayHook.sendPlanInfo(this);
            Ae2CraftingReflect.finish(this);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("LP 计划异常,回落原生计算", t);
            Ae2CraftingReflect.setAvailableCheck(this, null);
            recordFallback(this.lastFallbackReason != null ? this.lastFallbackReason : "exception");
            MetricsRegistry.timer("plan.computeMs.lpPhase").record(System.nanoTime() - planStart);
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

    /**
     * LP 计划路径:CondensationPlanner 冷凝求解 → FlowReconciler 整数化重演对账
     * → LpPlanMaterializer 物化原生树.失败/异常返回 null(调用方回落原生).
     */
    @Nullable
    protected CraftingTreeNode computeLpPlan() {
        IActionSource src = Ae2CraftingReflect.getActionSrc(this);
        ICraftingGrid cc = Ae2CraftingReflect.getCc(this);
        IAEItemStack output = this.getOutput();
        try {
            NetworkPatternIndex index = NetworkPatternIndex.of(cc);
            if (index == null) {
                this.lastFallbackReason = "no_pattern_index";
                return null;
            }
            MECraftingInventory inv = new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), true, false,
                    true);
            Ae2CraftingReflect.setAvailableCheck(this,
                    new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), false, false, false));
            Map<IAEItemStack, Long> stock = new HashMap<>();
            for (IAEItemStack stack : inv.getItemList()) {
                if (stack.getStackSize() > 0) {
                    stock.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(), Long::sum);
                }
            }
            CondensationPlanner.LpPlanOutcome outcome = CondensationPlanner.solve(cc, index, output,
                    output.getStackSize(), stock);
            // 降级单元埋点(库存直通/截断,正常应为 0)
            for (int i = 0; i < outcome.degradedUnits; i++) {
                MetricsRegistry.counter("plan.lp.degradedUnit").increment();
            }
            CraftingTreeNode root = new CraftingTreeNode(cc, this, output.copy(), null, -1, 0);
            FlowReconciler.Reconciled reconciled = FlowReconciler.reconcile(cc, index, output,
                    output.getStackSize(), outcome, inv, root, src);
            LpPlanMaterializer.attach(cc, this, root, output, outcome, reconciled.times, reconciled.missing,
                    reconciled.totalExtracted);
            if (!reconciled.missing.isEmpty()) {
                // 缺料计划显式置模拟标志(原生失败重试同语义),
                // 否则产出"有缺料却标记可提交"的不一致计划
                Ae2CraftingReflect.setSimulate(this, true);
            }
            // 含成环样板执行的 LP 计划:执行层走特殊 CPU 门控
            for (Execution exec : outcome.executions) {
                if (reconciled.times.getOrDefault(exec, 0L) > 0 && index.isCycleStep(exec.pattern)) {
                    this.hasCycleBoundary = true;
                    break;
                }
            }
            SpecialLog.info(
                    "[LP计划] 完成: {}×{},单元 {}(LP {},降级 {}),执行记录 {},缺料 {} 种,迭代 {},求解 {}ms",
                    output, output.getStackSize(), outcome.units, outcome.lpUnits, outcome.degradedUnits,
                    outcome.executions.size(), reconciled.missing.size(), outcome.iterations, outcome.wallMs);
            dumpPlanDetail(output, stock, outcome, reconciled);
            return root;
        } catch (Throwable t) {
            this.lastFallbackReason = "lp_exception";
            AE2Enhanced.LOGGER.warn("[LP计划] 异常,回落原生计算", t);
            return null;
        }
    }

    /**
     * 计划明细日志（{@code /ae2e debug specialcrafting on} 时生效）:输出变体选择、
     * 库存取用、缺料与相关键库存.
     */
    private static void dumpPlanDetail(IAEItemStack output, Map<IAEItemStack, Long> stock,
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
            Long s = stock.get(key);
            if (s != null && s > 0) {
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
            for (CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pro).keySet()) {
                collectMissingNodes(child, out);
            }
        }
    }
}
