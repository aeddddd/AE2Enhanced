package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.MECraftingInventory;
import appeng.hooks.TickHandler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSpecialPlanInfo;

/**
 * 特殊配方合成计算器（移植自 1.20.1 的 SpecialCraftingCalculation,1.12.2 适配）.
 * <p>继承原生 {@link CraftingJob}:未命中特殊配方时 {@link #run()} 直接
 * {@code super.run()},行为与原生逐字节一致;命中时走"种子保留 + 贷款法闭式解"路径.</p>
 * <p><b>1.12.2 关键差异</b>:本版没有独立的 ICraftingPlan 对象——合成树本身就是计划
 * 且是提交载体（{@code submitJob} 强转 CraftingJob 调 {@code getTree().setJob()}).
 * 因此闭式解必须<b>回填原生树</b>:根节点挂载各闭式 process,process 的 crafts 次数与
 * 子节点的 used 列表由原生 {@code CraftingTreeProcess.request} 在贷款模拟中自然记账
 * （used 经 availableCheck 被网络实际库存钳制为种子量,gross 部分由贷款吸收）.</p>
 * <p>特殊路径任何失败/异常都回落到原生 {@code super.run()},最坏情况退化为原版行为
 * （通常报缺料）,绝不产生错误计划.</p>
 */
public class SpecialCraftingJob extends CraftingJob {

    private final World world;

    public SpecialCraftingJob(World w, IGrid grid, IActionSource actionSrc, IAEItemStack what,
            ICraftingCallback callback) {
        super(w, grid, actionSrc, what, callback);
        this.world = w;
    }

    @Override
    public void run() {
        boolean handled = false;
        try {
            handled = this.trySpecialRun();
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[特殊配方] 求解异常,回落原生计算: {}", t.toString());
        }
        if (!handled) {
            super.run();
        }
    }

    /**
     * 特殊求解入口.
     *
     * @return true = 已处理（成功产出计划或已取消）;false = 不适用,调用方走原生.
     */
    private boolean trySpecialRun() {
        if (!SpecialCraftingRuntime.isEnabled()) {
            return false;
        }
        IActionSource src = Ae2CraftingReflect.getActionSrc(this);
        ICraftingGrid cc = Ae2CraftingReflect.getCc(this);
        IAEItemStack what = this.getOutput();
        try {
            // 复制原生 run() 骨架:注册时间片调度 → 让出 → 计算 → 收尾
            TickHandler.INSTANCE.registerCraftingSimulation(this.world, this);
            Ae2CraftingReflect.handlePausing(this);
            Ae2CraftingReflect.setAvailableCheck(this,
                    new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), false, false, false));

            CraftingTreeNode root = this.solveSpecial(cc, what, src);
            if (root == null) {
                Ae2CraftingReflect.setAvailableCheck(this, null);
                return false;
            }
            Ae2CraftingReflect.setTree(this, root);
            Ae2CraftingReflect.nodeDive(root, this);
            // 缺料(模拟)计划不标记:原生 submitJob 本就拒绝模拟计划,标记无意义
            if (!this.isSimulation()) {
                SpecialPlanMarker.mark(this);
            }
            this.sendPlanInfo(src);
            Ae2CraftingReflect.finish(this);
            return true;
        } catch (InterruptedException e) {
            // 与原生一致:取消即收尾,不再回落
            SpecialLog.info("[特殊配方] 计算被取消");
            Ae2CraftingReflect.finish(this);
            return true;
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[特殊配方] 求解异常,回落原生计算: {}", t.toString());
            Ae2CraftingReflect.setAvailableCheck(this, null);
            return false;
        }
    }

    /**
     * 特殊求解分发:阶段 1 净产出自引用 → 广义自引用 → 阶段 2 循环链.
     *
     * @return 已闭式求解的根节点（含缺料计划根）;不适用返回 null（调用方回落原生）.
     */
    @Nullable
    private CraftingTreeNode solveSpecial(ICraftingGrid cc, IAEItemStack what, IActionSource src)
            throws InterruptedException {
        long target = what.getStackSize();

        ICraftingPatternDetails selfRef = null;
        java.util.List<ICraftingPatternDetails> selfRefAnyCandidates = new java.util.ArrayList<>();
        int candidateCount = 0;
        for (ICraftingPatternDetails pattern : cc.getCraftingFor(what, null, -1, this.world)) {
            candidateCount++;
            if (selfRef == null && RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, what)) {
                selfRef = pattern;
            }
            if (RecursiveCraftingHelper.findSelfRefKey(pattern) != null) {
                selfRefAnyCandidates.add(pattern);
            }
        }

        if (selfRef != null) {
            return this.solveSelfRef(cc, selfRef, what, target, src);
        }

        if (!selfRefAnyCandidates.isEmpty()) {
            SpecialLog.info("[特殊配方] 广义自引用路径: {}×{},{} 个候选样板", what, target,
                    selfRefAnyCandidates.size());
            // 广义自引用:自引用 key ≠ 请求 key 的候选迭代求解;催化剂型(X==Y 进出等量)
            // 无法净增殖,留待最后统一报缺料,不阻塞后续 X≠Y 候选.
            boolean sawCatalystSelf = false;
            for (ICraftingPatternDetails candidate : selfRefAnyCandidates) {
                IAEItemStack selfKey = RecursiveCraftingHelper.findSelfRefKey(candidate);
                if (selfKey != null && selfKey.equals(what)) {
                    sawCatalystSelf = true;
                    continue;
                }
                CraftingTreeNode root = this.solveGeneralSelfRef(cc, candidate, selfKey, what, target, src);
                if (root != null) {
                    return root; // 含 O(1) 缺料计划(天文数字),不可解时终止迭代
                }
            }
            if (sawCatalystSelf) {
                // 催化剂型(X==Y,gain=0):请求物无法增殖 → O(1) 缺料计划,
                // 与原生失败语义一致但不挂起
                return this.missingRoot(cc, what, target);
            }
        }

        // 阶段 2:跨样板循环链
        return this.solveCycle(cc, what, target, src);
    }

    /**
     * 阶段 1:净产出自引用闭式解（如 A+2B→2A）.
     */
    @Nullable
    private CraftingTreeNode solveSelfRef(ICraftingGrid cc, ICraftingPatternDetails selfRef,
            IAEItemStack what, long target, IActionSource src) throws InterruptedException {
        long inPer = RecursiveCraftingHelper.selfInputPerCraft(selfRef, what);
        long outPer = RecursiveCraftingHelper.selfOutputPerCraft(selfRef, what);
        long gain = outPer - inPer;
        if (gain <= 0 || inPer <= 0) {
            return null;
        }

        MECraftingInventory inv = new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), true, false, true);
        // 关键差异:不执行 ignore(what),保留网络库存中的种子

        // 1) 种子校验:网络库存至少要有 inPer 个请求物作为增殖种子
        long stock = CycleSolver.invAmount(inv, what);
        if (stock < inPer) {
            return null; // 无种子 → 原生兜底(报缺料),保证不凭空增殖
        }

        // 注意:不做"库存直接交付"——AE2 执行模型只认样板产出作为交付来源,
        // 交付量一律由样板产出:crafts 覆盖全额,种子保留,余量执行结束返回网络.
        // 溢出安全 ceilDiv(target、gain 为正,必得 crafts ≥ 1)
        long crafts = target / gain + (target % gain != 0 ? 1 : 0);
        // 守卫取 max(outPer, inPer):产出 crafts×outPer 经原生无饱和乘法,超 long 即记账错乱
        if (crafts > Long.MAX_VALUE / Math.max(outPer, inPer)) {
            // 天文数字订单（产出/贷款量溢出）:直接构造缺料失败计划,O(1)
            return this.missingRoot(cc, what, target);
        }

        // 2) 贷款法:借入 (crafts-1)×inPer 使整批 request 通过,产出后立即归还.
        CraftingTreeNode root = new CraftingTreeNode(cc, this, what.copy(), null, -1, 0);
        CraftingTreeProcess pro = new CraftingTreeProcess(cc, this, selfRef, root, 1);
        Ae2CraftingReflect.addProcessToNode(root, pro);

        long loan = inPer * (crafts - 1);
        if (loan > 0) {
            IAEItemStack loanStack = RecursiveCraftingHelper.canon(what);
            loanStack.setStackSize(loan);
            inv.injectItems(loanStack, Actionable.MODULATE, src);
        }
        // CrT 不消耗配方(配方级返还,如 .reuse() 催化剂):原生批量模拟只认
        // Item 容器物,催化剂 gross 提取会误报缺料——预注入虚拟返还(不归还)
        Map<IAEItemStack, Long> catalystInject = new LinkedHashMap<>();
        Map<IAEItemStack, Long> catalystRebate = new LinkedHashMap<>();
        Set<IAEItemStack> catalystExcluded = new HashSet<>();
        catalystExcluded.add(RecursiveCraftingHelper.canon(what));
        CatalystReturns.collect(selfRef, crafts, catalystExcluded, catalystInject, catalystRebate);
        CatalystReturns.inject(catalystInject, inv, src);
        try {
            Ae2CraftingReflect.treeProcessRequest(pro, inv, crafts, src);
        } catch (CraftBranchFailure failure) {
            return null; // 非自输入不足 → 原生兜底(缺料报告)
        } finally {
            if (loan > 0) {
                IAEItemStack payback = RecursiveCraftingHelper.canon(what);
                payback.setStackSize(loan);
                inv.extractItems(payback, Actionable.MODULATE, src);
            }
        }

        // 3) used 返利:贷款法下 gross 提取经 availableCheck 被钳制为网络库存量,
        // 但计划语义要求 used = 种子量(inPer)——多余库存不应被 CPU 提取
        Map<IAEItemStack, Long> seeds = new LinkedHashMap<>();
        seeds.put(RecursiveCraftingHelper.canon(what), inPer);
        seeds.putAll(catalystRebate); // 催化剂种子语义:净消耗+单次投入
        this.rebateUsed(root, seeds);
        return root;
    }

    /**
     * 广义自引用求解（自引用 key X ≠ 请求 key Y,如请求 B,样板 A→A+B）.
     * X 只需 inX 份种子,贷款法整批模拟,O(1).
     */
    @Nullable
    private CraftingTreeNode solveGeneralSelfRef(ICraftingGrid cc, ICraftingPatternDetails pattern,
            IAEItemStack selfKey, IAEItemStack what, long target, IActionSource src) throws InterruptedException {
        if (selfKey == null) {
            return null;
        }
        long inX = RecursiveCraftingHelper.selfInputPerCraft(pattern, selfKey);
        long outY = RecursiveCraftingHelper.selfOutputPerCraft(pattern, what);
        if (inX <= 0 || outY <= 0) {
            return null;
        }
        if (selfKey.equals(what)) {
            // 催化剂型由调用方统一报缺料,此处防御性拒绝
            return null;
        }

        MECraftingInventory inv = new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), true, false, true);
        long stockX = CycleSolver.invAmount(inv, selfKey);
        if (stockX < inX) {
            return null; // 无种子 → 原生兜底(首份即缺,快速失败)
        }

        // 溢出安全 ceilDiv(target、outY 为正,必得 crafts ≥ 1)
        long crafts = target / outY + (target % outY != 0 ? 1 : 0);
        // 守卫取 max(outY, inX):产出 crafts×outY 经原生无饱和乘法,超 long 即记账错乱
        if (crafts > Long.MAX_VALUE / Math.max(outY, inX)) {
            return this.missingRoot(cc, what, target);
        }

        CraftingTreeNode root = new CraftingTreeNode(cc, this, what.copy(), null, -1, 0);
        CraftingTreeProcess pro = new CraftingTreeProcess(cc, this, pattern, root, 1);
        Ae2CraftingReflect.addProcessToNode(root, pro);

        long loan = inX * (crafts - 1);
        if (loan > 0) {
            IAEItemStack loanStack = selfKey.copy();
            loanStack.setStackSize(loan);
            inv.injectItems(loanStack, Actionable.MODULATE, src);
        }
        // CrT 不消耗配方(同 solveSelfRef):催化剂预注入虚拟返还
        Map<IAEItemStack, Long> catalystInject = new LinkedHashMap<>();
        Map<IAEItemStack, Long> catalystRebate = new LinkedHashMap<>();
        Set<IAEItemStack> catalystExcluded = new HashSet<>();
        catalystExcluded.add(RecursiveCraftingHelper.canon(selfKey));
        catalystExcluded.add(RecursiveCraftingHelper.canon(what));
        CatalystReturns.collect(pattern, crafts, catalystExcluded, catalystInject, catalystRebate);
        CatalystReturns.inject(catalystInject, inv, src);
        try {
            Ae2CraftingReflect.treeProcessRequest(pro, inv, crafts, src);
        } catch (CraftBranchFailure failure) {
            return null; // 其他输入不足 → 原生兜底(缺料报告)
        } finally {
            if (loan > 0) {
                IAEItemStack payback = selfKey.copy();
                payback.setStackSize(loan);
                inv.extractItems(payback, Actionable.MODULATE, src);
            }
        }

        // used 返利:种子语义 = inX
        Map<IAEItemStack, Long> seeds = new LinkedHashMap<>();
        seeds.put(RecursiveCraftingHelper.canon(selfKey), inX);
        seeds.putAll(catalystRebate); // 催化剂种子语义:净消耗+单次投入
        this.rebateUsed(root, seeds);
        return root;
    }

    /**
     * 阶段 2:跨样板增殖环闭式解.枚举候选环（长环优先）,迭代求解直到成功.
     */
    @Nullable
    private CraftingTreeNode solveCycle(ICraftingGrid cc, IAEItemStack what, long target, IActionSource src)
            throws InterruptedException {
        List<List<CycleAnalyzer.CycleStep>> cycles = CycleAnalyzer.findCyclesThrough(cc, what, this.world);
        if (!cycles.isEmpty()) {
            SpecialLog.info("[特殊配方] 循环链求解: {}×{},找到 {} 个候选环", what, target, cycles.size());
            // θ 形共享结构(多个环共享同一中间样板)先尝试候选环并集联立求解
            CycleAnalyzer.Analysis union = CycleAnalyzer.analyzeUnion(cycles);
            if (union != null && union.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE) {
                CraftingTreeNode root = this.tryCycleAnalysis(cc, union, what, target, src);
                if (root != null) {
                    return root;
                }
            }
            for (List<CycleAnalyzer.CycleStep> cycle : cycles) {
                CycleAnalyzer.Analysis analysis = CycleAnalyzer.analyze(cycle);
                if (analysis == null) {
                    SpecialLog.info("[特殊配方] 候选环({} 步)不可解(秩不足/无正整数解/超 long),跳过",
                            cycle.size());
                    continue;
                }
                if (analysis.rateClass() != CycleAnalyzer.RateClass.PRODUCTIVE) {
                    SpecialLog.info("[特殊配方] 候选环({} 步)为 {},不接管", cycle.size(), analysis.rateClass());
                    continue;
                }
                CraftingTreeNode root = this.tryCycleAnalysis(cc, analysis, what, target, src);
                if (root != null) {
                    return root;
                }
            }
        }
        // 所有候选环均不适用 → 尝试催化环(请求物是某中性/增殖环发射的环外副产物,
        // 如 1A→1X+1B、1B→1A 请求 X——X 不在环键上,常规环枚举不可见)
        for (List<CycleAnalyzer.CycleStep> cycle : CycleAnalyzer.findCatalyticCycles(cc, what, this.world)) {
            CycleAnalyzer.Analysis analysis = CycleAnalyzer.analyze(cycle);
            if (analysis == null || analysis.rateClass() == CycleAnalyzer.RateClass.DISSIPATIVE) {
                continue;
            }
            long xPerRound = CycleAnalyzer.byproductPerRound(analysis, what);
            if (xPerRound <= 0) {
                continue;
            }
            CraftingTreeNode root = this.tryCatalyticAnalysis(cc, analysis, xPerRound, what, target, src);
            if (root != null) {
                return root;
            }
        }
        SpecialLog.info("[特殊配方] 无可用候选环,回落原生计算: {}×{}", what, target);
        return null;
    }

    /**
     * 对催化环(中性/增殖环发射环外副产物)尝试求解并构建树.
     *
     * @return 成功的根节点;失败返回 null,溢出返回缺料计划根.
     */
    @Nullable
    private CraftingTreeNode tryCatalyticAnalysis(ICraftingGrid cc, CycleAnalyzer.Analysis analysis,
            long xPerRound, IAEItemStack what, long target, IActionSource src) throws InterruptedException {
        SpecialLog.info("[特殊配方] 尝试催化环({} 步):副产物 {} 每轮 +{}", analysis.steps().size(),
                what, xPerRound);
        MECraftingInventory inv = new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), true, false, true);
        CraftingTreeNode root = new CraftingTreeNode(cc, this, what.copy(), null, -1, 0);
        CycleSolver.SolveResult result = CycleSolver.trySolveCatalytic(cc, this, analysis, xPerRound, inv,
                what, target, root, src);
        if (result == CycleSolver.SolveResult.OVERFLOW) {
            return this.missingRoot(cc, what, target);
        }
        if (result != CycleSolver.SolveResult.SUCCESS) {
            return null;
        }
        // 与增殖环一致的运行时安全守护
        if (this.hasUnsafeExternalSubcraft(root, analysis)) {
            SpecialLog.info("[特殊配方] 催化环求解回落:环外子合成触及环键,运行时语义不安全");
            return null;
        }
        SpecialLog.info("[特殊配方] 催化环求解成功: {}×{}", what, target);

        // used 返利:环键种子(与增殖环同);交付键 what 无种子
        List<IAEItemStack> keys = analysis.keys();
        long[] times = analysis.timesPerRound();
        long[] seeds = analysis.seedsPerKey();
        long[] batchSeeds = analysis.batchSeedPerKey();
        Map<IAEItemStack, Long> plannedSeeds = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            plannedSeeds.put(keys.get(i), batchSeeds[i] > 0 ? Math.max(seeds[i], batchSeeds[i]) : seeds[i]);
        }
        this.rebateUsed(root, plannedSeeds);
        return root;
    }

    /**
     * 对单个增殖环分析结果尝试求解并构建树.
     *
     * @return 成功的根节点;失败（种子/环外输入不足）返回 null,溢出返回缺料计划根.
     */
    @Nullable
    private CraftingTreeNode tryCycleAnalysis(ICraftingGrid cc, CycleAnalyzer.Analysis analysis,
            IAEItemStack what, long target, IActionSource src) throws InterruptedException {
        MECraftingInventory inv = new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), true, false, true);
        // 关键差异:不执行 ignore(what),保留网络库存中的种子
        CraftingTreeNode root = new CraftingTreeNode(cc, this, what.copy(), null, -1, 0);
        CycleSolver.SolveResult result = CycleSolver.trySolve(cc, this, analysis, inv, what, target, root, src);
        if (result == CycleSolver.SolveResult.OVERFLOW) {
            return this.missingRoot(cc, what, target);
        }
        if (result != CycleSolver.SolveResult.SUCCESS) {
            return null;
        }
        // 运行时安全守护:环外子合成若触及环键(消耗/产出任何环内物品),
        // 线性系统的种子记账即失效(请求交错时必然死锁),该方案必须拒绝.
        // (与 1.20.1 的 G11 语义对齐:θ 缺中间物种子时,2 键环 + 环外子合成
        // 偷吃 root 键的"可行解"在执行层不可行.)
        if (this.hasUnsafeExternalSubcraft(root, analysis)) {
            SpecialLog.info("[特殊配方] 环求解回落:环外子合成触及环键,运行时语义不安全");
            return null;
        }

        SpecialLog.info("[特殊配方] 循环链求解成功: {}×{}", what, target);

        // used 返利:各环键的 used 回填为计划种子量(多消费者键 = max(前缀种子,每轮消耗))
        List<IAEItemStack> keys = analysis.keys();
        long[] times = analysis.timesPerRound();
        long[] seeds = analysis.seedsPerKey();
        long[] batchSeeds = analysis.batchSeedPerKey();
        Map<IAEItemStack, Long> plannedSeeds = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            plannedSeeds.put(keys.get(i), batchSeeds[i] > 0 ? Math.max(seeds[i], batchSeeds[i]) : seeds[i]);
        }
        this.rebateUsed(root, plannedSeeds);
        return root;
    }

    /**
     * used 返利:贷款法模拟中,计划键的 gross 提取经 availableCheck 被网络库存量钳制,
     * 但计划语义要求 used = 计划种子量(与 1.20.1 一致)——多余库存不应被 CPU 提取.
     * 全树遍历收集计划键的 used 条目,清零后把首个条目回填为计划种子量.
     * <p>安全性:种子校验已保证库存 ≥ 计划种子,且 gross 提取总量 ≥ 计划种子
     * （种子 ≤ 每轮消耗,gross = 轮次 × 消耗）,回填不会突破实际可用量.</p>
     */
    private void rebateUsed(CraftingTreeNode root, Map<IAEItemStack, Long> plannedSeeds) {
        TreeUsedRebate.rebate(root, plannedSeeds);
    }

    /**
     * 环外子合成安全守护:遍历合成树,任何非环步骤的 process(环外子合成)
     * 其输入/输出若触及环键,返回 true(方案不安全).
     */
    private boolean hasUnsafeExternalSubcraft(CraftingTreeNode root, CycleAnalyzer.Analysis analysis) {
        java.util.Set<ICraftingPatternDetails> stepPatterns = new java.util.HashSet<>();
        for (CycleAnalyzer.CycleStep step : analysis.steps()) {
            stepPatterns.add(step.pattern());
        }
        java.util.List<IAEItemStack> keys = analysis.keys();
        return this.treeTouchesCycleKeys(root, stepPatterns, keys);
    }

    private boolean treeTouchesCycleKeys(CraftingTreeNode node,
            java.util.Set<ICraftingPatternDetails> stepPatterns, java.util.List<IAEItemStack> keys) {
        for (CraftingTreeProcess pro : Ae2CraftingReflect.getNodeProcesses(node)) {
            // 只检查实际执行过的 process:addNode() 会为所有候选样板建 process 节点
            // (crafts == 0,从未 request),它们不是计划的组成部分,不能算作环外子合成
            if (Ae2CraftingReflect.getProcessCrafts(pro) <= 0) {
                continue;
            }
            ICraftingPatternDetails details = Ae2CraftingReflect.getProcessDetails(pro);
            if (!stepPatterns.contains(details)) {
                for (IAEItemStack input : details.getCondensedInputs()) {
                    if (input != null && keys.contains(RecursiveCraftingHelper.canon(input))) {
                        return true;
                    }
                }
                for (IAEItemStack output : details.getCondensedOutputs()) {
                    if (output != null && keys.contains(RecursiveCraftingHelper.canon(output))) {
                        return true;
                    }
                }
            }
            for (CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pro).keySet()) {
                if (this.treeTouchesCycleKeys(child, stepPatterns, keys)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 构造 O(1) 缺料失败计划（天文数字订单/催化剂型,避免原生逐份模拟卡死）.
     * 根节点 missing 字段直接记账,populatePlan 经原生 getPlan 输出缺料条目.
     */
    private CraftingTreeNode missingRoot(ICraftingGrid cc, IAEItemStack what, long target) {
        CraftingTreeNode root = new CraftingTreeNode(cc, this, what.copy(), null, -1, 0);
        Ae2CraftingReflect.setNodeMissing(root, target);
        Ae2CraftingReflect.setSimulate(this, true);
        return root;
    }

    /**
     * 显示信息（测试与展示层共用）:统一从合成树自恢复,覆盖特殊/DAG/普通计划.
     */
    public SpecialPlanInfo getPlanInfo() {
        return SpecialPlanInfo.compute(this);
    }

    /**
     * 求解成功后向发起玩家发送显示信息（机器发起的请求无 GUI,由钩子内部跳过）.
     */
    private void sendPlanInfo(IActionSource src) {
        SpecialPlanDisplayHook.sendPlanInfo(this);
    }
}
