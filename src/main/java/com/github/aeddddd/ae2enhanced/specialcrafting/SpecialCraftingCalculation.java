package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.hooks.ticking.TickHandler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.RecursiveCraftingHelper;

/**
 * 特殊配方合成计算器（路由模式,阶段 1:净产出自引用）.
 * <p>继承原生 {@link CraftingCalculation}：detector 未命中时 {@link #run()} 直接
 * {@code super.run()},行为与原生逐字节一致；命中时走"种子保留 + 贷款法闭式解"路径,
 * 复刻 {@code MixinCraftingTreeNode} 已验证的贷款语义为普通代码,并放宽其
 * "唯一候选样板"限制（多分支场景同样可用自引用样板增殖）.</p>
 * <p>特殊路径任何失败/异常都回落到原生 {@code computePlan},保证最坏情况行为
 * 退化为原版（通常报缺料）,绝不产生错误计划.</p>
 * <p>注意：本类须同时运行于游戏与纯 JUnit 环境,访问 AE2 包私有成员一律经
 * {@link Ae2CraftingReflect}（不使用 mixin accessor,详见该类的说明）.</p>
 */
public class SpecialCraftingCalculation extends CraftingCalculation {

    private final Level level;
    private final ICraftingService craftingService;
    private final GenericStack outputStack;

    public SpecialCraftingCalculation(Level level, IGrid grid, ICraftingSimulationRequester simRequester,
            GenericStack output, CalculationStrategy strategy) {
        super(level, grid, simRequester, output, strategy);
        this.level = level;
        this.craftingService = grid.getCraftingService();
        this.outputStack = output;
    }

    @Override
    public ICraftingPlan run() {
        // 廉价能力检查:无净产出自引用候选 → 100% 原生路径
        if (!SpecialRecipeDetector.mayInvolveSpecialRecipes(craftingService, getOutput())) {
            return super.run();
        }
        try {
            // 复制原生 run() 骨架:注册时间片调度 → 让出 → 计算 → 收尾
            TickHandler.instance().registerCraftingSimulation(this.level, this);
            Ae2CraftingReflect.handlePausing(this);

            ICraftingPlan plan = computeSpecialPlan();
            if (plan == null) {
                // 特殊求解不适用（无种子/溢出等）→ 原生兜底(含 CRAFT_LESS 与缺料模拟)
                plan = Ae2CraftingReflect.computePlan(this);
            }
            return plan;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (Exception e) {
            // 特殊路径异常:共享状态未被污染(子模拟库存随弃,网络快照只读),安全回落原生
            AE2Enhanced.LOGGER.warn("特殊配方求解异常,回落原生计算: {}", e.toString());
            try {
                return Ae2CraftingReflect.computePlan(this);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } finally {
            Ae2CraftingReflect.finish(this);
        }
    }

    /**
     * 特殊求解:净产出自引用闭式解.
     *
     * @return 成功且已标记为特殊计划的结果;不适用时返回 null（调用方回落原生）.
     */
    @Nullable
    private ICraftingPlan computeSpecialPlan() throws InterruptedException {
        AEKey what = getOutput();
        long target = this.outputStack.amount();

        IPatternDetails selfRef = null;
        List<IPatternDetails> selfRefAnyCandidates = new ArrayList<>();
        int candidateCount = 0;
        for (var pattern : craftingService.getCraftingFor(what)) {
            candidateCount++;
            if (selfRef == null && RecursiveCraftingHelper.isNetPositiveSelfRef(pattern, what)) {
                selfRef = pattern;
            }
            if (RecursiveCraftingHelper.findSelfRefKey(pattern) != null) {
                selfRefAnyCandidates.add(pattern);
            }
        }
        if (selfRef == null) {
            if (!selfRefAnyCandidates.isEmpty()) {
                AE2Enhanced.LOGGER.info("[特殊配方] 广义自引用路径: {}×{},{} 个候选样板", what, target,
                        selfRefAnyCandidates.size());
            }
            // 广义自引用:自引用 key ≠ 请求 key 的候选迭代求解(不同候选的种子需求
            // 可能不同);催化剂型(X==Y 进出等量)无法净增殖,留待最后统一报缺料,
            // 不阻塞后续 X≠Y 候选.
            boolean sawCatalystSelf = false;
            for (var candidate : selfRefAnyCandidates) {
                AEKey selfKey = RecursiveCraftingHelper.findSelfRefKey(candidate);
                if (selfKey != null && selfKey.equals(what)) {
                    sawCatalystSelf = true;
                    continue;
                }
                var plan = computeGeneralSelfRefPlan(candidate, what, target, candidateCount);
                if (plan != null) {
                    return plan; // 含 O(1) 缺料计划(天文数字),不可解时终止迭代
                }
            }
            if (sawCatalystSelf) {
                // 催化剂型(X==Y,gain=0):请求物无法增殖;而"库存直接交付"在执行模型
                // 上行不通(无样板任务的计划永远无法完成,产出又必须喂给下一份合成,
                // 结算时库存只剩种子)→ O(1) 缺料计划,与原生失败语义一致但不挂起
                return missingPlan(what, target, candidateCount > 1);
            }
            // 阶段 2:跨样板循环链
            return computeCyclePlan(what, target);
        }

        long inPer = RecursiveCraftingHelper.selfInputPerCraft(selfRef, what);
        long outPer = RecursiveCraftingHelper.selfOutputPerCraft(selfRef, what);
        long gain = outPer - inPer;
        if (gain <= 0 || inPer <= 0) {
            return null;
        }

        ChildCraftingSimulationState inv = new ChildCraftingSimulationState(
                Ae2CraftingReflect.getNetworkInv(this));
        // 关键差异:不执行 ignore(what),保留网络库存中的种子

        // 1) 种子校验:网络库存至少要有 inPer 个请求物作为增殖种子
        long stock = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        if (stock < inPer) {
            return null; // 无种子 → 原生兜底(报缺料),保证不凭空增殖
        }

        // 注意:不做"库存直接交付"(fromStock)——AE2 执行模型只认样板产出作为交付来源,
        // 无样板任务的计划会让 CPU 提取材料后永远无法完成(游戏内验证发现).
        // 交付量一律由样板产出:crafts 覆盖全额,种子保留,余量执行结束返回网络.
        long remaining = target;

        if (remaining > 0) {
            long crafts = (remaining + gain - 1) / gain;
            if (crafts <= 0 || crafts > Long.MAX_VALUE / Math.max(1, inPer)) {
                // 天文数字订单（贷款量溢出）:直接构造缺料失败计划(与旧 mixin 的
                // failShortage 语义一致,O(1));若回落原生,其逐份展开会在超大单下卡死
                return missingPlan(what, target, candidateCount > 1);
            }

            // 3) 贷款法:借入 (crafts-1)×inPer 使整批 request 通过,产出后立即归还.
            // 非自输入/容器物/产出/字节记账全部由原生 CraftingTreeProcess.request 处理.
            CraftingTreeNode rootNode = new CraftingTreeNode(craftingService, this, what, 1, null, -1);
            CraftingTreeProcess pro = new CraftingTreeProcess(craftingService, this, selfRef, rootNode);

            long loan = inPer * (crafts - 1);
            if (loan > 0) {
                inv.insert(what, loan, Actionable.MODULATE);
            }
            try {
                Ae2CraftingReflect.treeProcessRequest(pro, inv, crafts);
            } catch (CraftBranchFailure failure) {
                return null; // 非自输入不足 → 原生兜底(缺料报告)
            } finally {
                if (loan > 0) {
                    inv.extract(what, loan, Actionable.MODULATE);
                }
            }

            // 4) 结算:模拟库存 = 种子 + crafts×gain,取走交付量,种子保留（执行结束返回网络）
            long avail = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
            long keep = avail > remaining ? inPer : 0;
            long drain = inv.extract(what, Math.min(remaining, Math.max(0, avail - keep)),
                    Actionable.MODULATE);
            remaining -= drain;
            if (remaining > 0) {
                return null; // 理论不可达,保险起见回落原生
            }
        }

        // 5) 产出原生 CraftingPlan:根节点字节按原生模型补记
        inv.addBytes(8);
        CraftingPlan base = CraftingSimulationState.buildCraftingPlan(inv, this, target);
        ICraftingPlan plan = candidateCount > 1
                // 多候选时照原生语义标记 multiplePaths（GUI 备选路线提示）
                ? new CraftingPlan(base.finalOutput(), base.bytes(), base.simulation(), true,
                        base.usedItems(), base.emittedItems(), base.missingItems(), base.patternTimes())
                : base;
        SpecialPlanMarker.mark(plan);
        return plan;
    }

    /**
     * 阶段 2 特殊求解:跨样板增殖环闭式解.
     *
     * @return 成功且已标记为特殊计划的结果;不适用时返回 null（调用方回落原生）.
     */
    @Nullable
    private ICraftingPlan computeCyclePlan(AEKey what, long target) throws InterruptedException {
        // 枚举候选环（长环优先,键集更完整）,迭代求解直到成功——不同候选环的
        // 种子需求/环外输入可能不同,第一个增殖环求解失败时应尝试下一个.
        var cycles = CycleAnalyzer.findCyclesThrough(craftingService, what);
        AE2Enhanced.LOGGER.info("[特殊配方] 循环链求解: {}×{},找到 {} 个候选环", what, target, cycles.size());
        // θ 形共享结构(多个环共享同一中间样板,如赛特斯石英循环)逐环分析会互相把
        // 对方的中间物当环外输入而双双失败 → 先尝试候选环并集联立求解
        var union = CycleAnalyzer.analyzeUnion(cycles);
        if (union != null && union.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE) {
            var plan = tryCycleAnalysis(union, what, target);
            if (plan != null) {
                return plan;
            }
        }
        for (var cycle : cycles) {
            var analysis = CycleAnalyzer.analyze(cycle);
            if (analysis == null) {
                AE2Enhanced.LOGGER.info("[特殊配方] 候选环({} 步)不可解(秩不足/无正整数解/超 long),跳过",
                        cycle.size());
                continue;
            }
            if (analysis.rateClass() != CycleAnalyzer.RateClass.PRODUCTIVE) {
                AE2Enhanced.LOGGER.info("[特殊配方] 候选环({} 步)为 {},不接管", cycle.size(), analysis.rateClass());
                continue;
            }
            var plan = tryCycleAnalysis(analysis, what, target);
            if (plan != null) {
                return plan;
            }
        }
        // 所有候选环均不适用 → 原生兜底(原生对环剪枝,快速失败,无回归)
        AE2Enhanced.LOGGER.info("[特殊配方] 无可用候选环,回落原生计算: {}×{}", what, target);
        return null;
    }

    /**
     * 对单个增殖环分析结果尝试求解并构建计划.
     *
     * @return 成功且已标记的计划;失败（种子/环外输入不足）返回 null,溢出返回缺料计划.
     */
    @Nullable
    private ICraftingPlan tryCycleAnalysis(CycleAnalyzer.Analysis analysis, AEKey what, long target)
            throws InterruptedException {
        AE2Enhanced.LOGGER.info("[特殊配方] 尝试增殖环({} 步):净产 {}/轮,种子 {},全批次种子 {}",
                analysis.steps().size(), analysis.netGain(),
                java.util.Arrays.toString(analysis.seedsPerKey()),
                java.util.Arrays.toString(analysis.batchSeedPerKey()));

        ChildCraftingSimulationState inv = new ChildCraftingSimulationState(
                Ae2CraftingReflect.getNetworkInv(this));
        // 关键差异:不执行 ignore(what),保留网络库存中的种子
        var result = CycleSolver.trySolve(craftingService, this, analysis, inv, what, target);
        if (result == CycleSolver.SolveResult.OVERFLOW) {
            return missingPlan(what, target, true);
        }
        if (result != CycleSolver.SolveResult.SUCCESS) {
            return null;
        }

        AE2Enhanced.LOGGER.info("[特殊配方] 循环链求解成功: {}×{}", what, target);
        inv.addBytes(8);
        CraftingPlan base = CraftingSimulationState.buildCraftingPlan(inv, this, target);
        // 环计划保守标记 multiplePaths（环外可能仍有其他候选路线）
        ICraftingPlan plan = new CraftingPlan(base.finalOutput(), base.bytes(), base.simulation(), true,
                base.usedItems(), base.emittedItems(), base.missingItems(), base.patternTimes());
        SpecialPlanMarker.mark(plan);
        return plan;
    }

    /**
     * 广义自引用求解（解决原生 limitQty 逐份展开在超大订单下挂起的问题）.
     * <ul>
     * <li>自引用 key == 请求 key 且进出等量（催化剂型）:请求物无法增殖,
     * 直接从库存交付,不足部分报缺料,O(1).</li>
     * <li>自引用 key X ≠ 请求 key Y(如请求 B,样板 A→A+B):X 只需 inPer 份种子,
     * 贷款法整批模拟,O(1).</li>
     * </ul>
     */
    @Nullable
    private ICraftingPlan computeGeneralSelfRefPlan(IPatternDetails pattern, AEKey what, long target,
            int candidateCount) throws InterruptedException {
        AEKey selfKey = RecursiveCraftingHelper.findSelfRefKey(pattern);
        if (selfKey == null) {
            return null;
        }
        long inX = RecursiveCraftingHelper.selfInputPerCraft(pattern, selfKey);
        long outY = RecursiveCraftingHelper.selfOutputPerCraft(pattern, what);
        if (inX <= 0 || outY <= 0) {
            return null;
        }

        ChildCraftingSimulationState inv = new ChildCraftingSimulationState(
                Ae2CraftingReflect.getNetworkInv(this));
        // 关键差异:不执行 ignore(what),保留网络库存中的种子/库存

        if (selfKey.equals(what)) {
            // 催化剂型由调用方统一报缺料(见 computeSpecialPlan),此处防御性拒绝:
            // gain=0 时产出必须喂给下一份合成,结算只剩种子,计划期"成功"是假象
            return null;
        }

        // X ≠ Y:种子 = inX 份 X(执行结束随返还回网络),贷款覆盖整批消耗
        long stockX = inv.extract(selfKey, Long.MAX_VALUE, Actionable.SIMULATE);
        if (stockX < inX) {
            return null; // 无种子 → 原生兜底(首份即缺,快速失败)
        }
        // 注意:不使用请求物库存直接交付(同阶段 1 的执行模型约束),交付全部来自样板产出
        long remaining = target;

        if (remaining > 0) {
            long crafts = (remaining + outY - 1) / outY;
            if (crafts <= 0 || crafts > Long.MAX_VALUE / inX) {
                return missingPlan(what, target, candidateCount > 1);
            }
            CraftingTreeNode rootNode = new CraftingTreeNode(craftingService, this, what, 1, null, -1);
            CraftingTreeProcess pro = new CraftingTreeProcess(craftingService, this, pattern, rootNode);

            long loan = inX * (crafts - 1);
            if (loan > 0) {
                inv.insert(selfKey, loan, Actionable.MODULATE);
            }
            try {
                Ae2CraftingReflect.treeProcessRequest(pro, inv, crafts);
            } catch (CraftBranchFailure failure) {
                return null; // 其他输入不足 → 原生兜底(缺料报告)
            } finally {
                if (loan > 0) {
                    inv.extract(selfKey, loan, Actionable.MODULATE);
                }
            }

            long avail = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
            long drain = inv.extract(what, Math.min(remaining, avail), Actionable.MODULATE);
            remaining -= drain;
            if (remaining > 0) {
                return null; // 理论不可达,保险起见回落
            }
        }

        inv.addBytes(8);
        CraftingPlan base = CraftingSimulationState.buildCraftingPlan(inv, this, target);
        ICraftingPlan plan = candidateCount > 1
                ? new CraftingPlan(base.finalOutput(), base.bytes(), base.simulation(), true,
                        base.usedItems(), base.emittedItems(), base.missingItems(), base.patternTimes())
                : base;
        SpecialPlanMarker.mark(plan);
        return plan;
    }

    /**
     * 构造 O(1) 缺料失败计划（天文数字订单,避免原生逐份模拟卡死）.
     */
    private ICraftingPlan missingPlan(AEKey what, long target, boolean multiplePaths) {
        Ae2CraftingReflect.addMissing(this, what, target);
        return new CraftingPlan(new GenericStack(what, target), 8, true, multiplePaths,
                new appeng.api.stacks.KeyCounter(), new appeng.api.stacks.KeyCounter(),
                this.getMissingItems(), java.util.Map.of());
    }
}
