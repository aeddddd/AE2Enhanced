package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.HashSet;
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
 * 跨样板增殖环求解器（阶段 2,泛化版,移植自 1.20.1）.
 * 对 {@link CycleAnalyzer.Analysis} 判定为增殖环的环键集求闭式解:
 * 各环内物品按前缀分析得出的种子保留 + 贷款法整批模拟——沿环执行顺序依次以
 * {@link BatchSubcraft} 整批执行各样板(复刻原生语义但消除逐单位循环爆炸),
 * 环内物品的消耗/产出在模拟库存内闭合（非 root 键每超轮净变化为零）,仅请求物有净增益.
 * 环外输入（辅材等）由子节点批量解析（库存/外部子合成,不足即分支失败回落）.</p>
 */
public final class CycleSolver {

    /**
     * 求解结果.
     */
    public enum SolveResult {
        /** 成功,模拟库存与树已记账（调用方 dive 聚合计划）. */
        SUCCESS,
        /** 不适用（种子不足/输入不足等）,调用方应回落原生. */
        FALLBACK,
        /** 数值溢出（天文数字订单）,调用方应产出 O(1) 缺料计划. */
        OVERFLOW
    }

    private CycleSolver() {
    }

    /**
     * 蛛网子树预检的 SCC 规模阈值:环外输入属于巨型 SCC(整合包整张 progression
     * 网,实测 5077 键)时,树模型在该子树上的构建<b>无节点共享、广度与需求量
     * 无关</b>(实测 3 个单位的需求也烧 1.5s+ 看门狗)——批量化只能消除逐单位
     * 循环,消除不了蛛网扇出的指数膨胀.这类子树一律转环盲降级,由 DAG 引擎
     * (有节点共享与预算封顶)批量展开;局部小规模 SCC 不受影响.
     */
    private static final int GUARD_WEB_SCC_SIZE = 256;

    /**
     * 蛛网子树预检:任一环步的环外输入属于巨型 SCC 即命中(与需求量无关,
     * 故不计算缺口).非蛛网输入由 {@link BatchSubcraft} 批量处理,无需预检.
     *
     * @param excludedKeys 环键与交付键(由贷款/交付语义覆盖,不参与子合成)
     * @return 触发蛛网风险的环外输入键;无风险返回 null
     */
    @javax.annotation.Nullable
    static IAEItemStack subcraftWebRisk(ICraftingGrid cc, ICraftingPatternDetails pattern,
            Set<IAEItemStack> excludedKeys) {
        NetworkPatternIndex patternIndex = NetworkPatternIndex.of(cc);
        if (patternIndex == null) {
            return null;
        }
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack key = RecursiveCraftingHelper.canon(input);
            if (excludedKeys.contains(key)) {
                continue;
            }
            if (patternIndex.sccSizeOf(key) >= GUARD_WEB_SCC_SIZE) {
                return key;
            }
        }
        return null;
    }

    /**
     * 模拟库存中某键的可用量（只读）.
     */
    static long invAmount(MECraftingInventory inv, IAEItemStack key) {
        IAEItemStack probe = key.copy();
        probe.setStackSize(Long.MAX_VALUE);
        IAEItemStack result = inv.extractItems(probe, Actionable.SIMULATE, null);
        return result == null ? 0 : result.getStackSize();
    }

    /**
     * 尝试以增殖环闭式解满足请求.
     *
     * @param inv 以网络快照为父的模拟库存（不得 ignore 请求物）
     * @param rootNode 新建的空根节点,成功的 process 会挂载到其 nodes 下
     */
    public static SolveResult trySolve(ICraftingGrid cc, CraftingJob job, CycleAnalyzer.Analysis analysis,
            MECraftingInventory inv, IAEItemStack what, long target, CraftingTreeNode rootNode,
            IActionSource src, World world) throws InterruptedException {
        return solveCore(cc, job, analysis, inv, what, target, rootNode, src, world, analysis.netGain(),
                analysis.seedsPerKey()[0]);
    }

    /**
     * 尝试以催化环闭式解满足请求:what 不在环键上,是环每超轮发射的环外副产物
     * (如 1A→1X+1B、1B→1A 请求 X).环键种子语义与增殖环一致;what 无种子、
     * 每超轮净得 {@code xPerRound}.
     */
    public static SolveResult trySolveCatalytic(ICraftingGrid cc, CraftingJob job,
            CycleAnalyzer.Analysis analysis, long xPerRound, MECraftingInventory inv, IAEItemStack what,
            long target, CraftingTreeNode rootNode, IActionSource src, World world)
            throws InterruptedException {
        if (xPerRound <= 0) {
            return SolveResult.FALLBACK;
        }
        return solveCore(cc, job, analysis, inv, what, target, rootNode, src, world, xPerRound, 0);
    }

    /**
     * 环闭式解共用内核.
     *
     * @param gainPerRound 每超轮交付键的净得数量(增殖环=环键净增益;催化环=副产物/轮)
     * @param deliverSeed 交付键的保留种子(催化环为 0)
     */
    private static SolveResult solveCore(ICraftingGrid cc, CraftingJob job, CycleAnalyzer.Analysis analysis,
            MECraftingInventory inv, IAEItemStack what, long target, CraftingTreeNode rootNode,
            IActionSource src, World world, long gainPerRound, long deliverSeed)
            throws InterruptedException {
        List<IAEItemStack> keys = analysis.keys();
        long[] seeds = analysis.seedsPerKey();
        long[] batchSeeds = analysis.batchSeedPerKey();
        long[] times = analysis.timesPerRound();

        // 注意:不做"库存直接交付"(fromStock)——AE2 执行模型只认样板产出作为交付来源,
        // 交付量一律由环运转产出,种子保留,余量执行结束返回网络.
        long remaining = target;
        if (remaining <= 0) {
            return SolveResult.SUCCESS;
        }

        // 溢出安全 ceilDiv:(remaining + gain - 1) 形式在近 Long.MAX 需求下加法回绕成负数,
        // 会被下游误判为"求解失败"而整单回落原生(大网络上即高请求计算卡死)
        long rounds = remaining / gainPerRound + (remaining % gainPerRound != 0 ? 1 : 0);
        // T_i = rounds × timesPerRound[i],任一溢出即天文数字订单
        long[] totalTimes = new long[times.length];
        for (int i = 0; i < totalTimes.length; i++) {
            if (times[i] != 0 && rounds > Long.MAX_VALUE / times[i]) {
                return SolveResult.OVERFLOW;
            }
            totalTimes[i] = rounds * times[i];
        }
        // 贷款水位预检:各环键的"每轮量级"(前缀种子与每轮总消耗取大者)×轮数同样必须
        // 可表示——否则批量模拟的库存水位本身超 long,贷款公式无法补足,模拟必欠资失败
        // (CraftBranchFailure),提前按天文数字处理,避免无效模拟与整单回落原生
        for (int i = 0; i < keys.size(); i++) {
            long perRound = Math.max(seeds[i], batchSeeds[i]);
            if (perRound > 0 && rounds > Long.MAX_VALUE / perRound) {
                return SolveResult.OVERFLOW;
            }
        }
        // IO 侧守卫:批量模拟经原生 CraftingTreeProcess.request(无饱和乘法),每步的
        // 输入/输出×总次数同样必须可表示,否则记账回绕错乱、结算必败
        List<CycleAnalyzer.CycleStep> stepsForCheck = analysis.steps();
        for (int i = 0; i < stepsForCheck.size(); i++) {
            if (totalTimes[i] <= 0) {
                continue;
            }
            ICraftingPatternDetails pattern = stepsForCheck.get(i).pattern();
            for (IAEItemStack io : pattern.getCondensedInputs()) {
                if (io != null && io.getStackSize() > 0 && totalTimes[i] > Long.MAX_VALUE / io.getStackSize()) {
                    return SolveResult.OVERFLOW;
                }
            }
            for (IAEItemStack io : pattern.getCondensedOutputs()) {
                if (io != null && io.getStackSize() > 0 && totalTimes[i] > Long.MAX_VALUE / io.getStackSize()) {
                    return SolveResult.OVERFLOW;
                }
            }
        }

        // 蛛网子树预检:任一环步的环外输入属于巨型 SCC 时,树模型在该子树
        // 无节点共享、广度与需求量无关(批量化无法消除),直接判不适用,
        // 由上层(DAG 环盲重编译/根路径原生回落)接管
        Set<IAEItemStack> guardExcluded = new HashSet<>();
        for (IAEItemStack key : keys) {
            guardExcluded.add(RecursiveCraftingHelper.canon(key));
        }
        guardExcluded.add(RecursiveCraftingHelper.canon(what));
        for (int i = 0; i < stepsForCheck.size(); i++) {
            IAEItemStack risk = subcraftWebRisk(cc, stepsForCheck.get(i).pattern(), guardExcluded);
            if (risk != null) {
                SpecialLog.info("[特殊配方] 环求解预检拦截: {} 属巨型 SCC,树模型广度爆炸转降级({}×{})",
                        risk, what, target);
                return SolveResult.FALLBACK;
            }
        }

        // 1) 各环内物品种子校验:
        // - 多消费者键(batchSeeds>0):仅需 max(前缀种子, 每轮总消耗)——运行时并发消耗
        //   由超轮配额调度器(RoundQuotaScheduler)闸在每轮以内;
        // - 单消费者键:仅需前缀启动种子.
        long[] requiredStock = new long[seeds.length];
        for (int i = 0; i < keys.size(); i++) {
            requiredStock[i] = batchSeeds[i] > 0 ? Math.max(seeds[i], batchSeeds[i]) : seeds[i];
            if (requiredStock[i] > 0) {
                long stock = invAmount(inv, keys.get(i));
                if (stock < requiredStock[i]) {
                    SpecialLog.info(
                            "[特殊配方] 环求解回落: {} 种子不足(需要 {},库存 {}{})",
                            keys.get(i), requiredStock[i], stock,
                            batchSeeds[i] > 0 ? ",多消费者键按每轮消耗记账" : "");
                    return SolveResult.FALLBACK;
                }
            }
        }

        // 2) 贷款法(计划期模拟技巧,借还精确对冲,只抬高水位)
        long[] loans = new long[seeds.length];
        for (int i = 0; i < seeds.length; i++) {
            if (seeds[i] <= 0 || rounds - 1 <= 0) {
                continue;
            }
            long dip;
            if (batchSeeds[i] > 0) {
                if (seeds[i] > Long.MAX_VALUE / rounds) {
                    return SolveResult.OVERFLOW;
                }
                dip = rounds * seeds[i] - requiredStock[i];
            } else {
                if (seeds[i] > Long.MAX_VALUE / (rounds - 1)) {
                    return SolveResult.OVERFLOW;
                }
                dip = (rounds - 1) * seeds[i];
            }
            if (dip > 0) {
                loans[i] = dip;
                IAEItemStack loan = keys.get(i).copy();
                loan.setStackSize(dip);
                inv.injectItems(loan, Actionable.MODULATE, src);
            }
        }
        // CrT 不消耗配方(配方级返还,如 .reuse() 催化剂):各环步样板按总次数
        // 预注入虚拟返还(不归还)——原生批量模拟只认 Item 容器物,环外催化剂输入的
        // gross 提取会误报缺料;环键/交付键由既有贷款语义覆盖,不参与
        Map<IAEItemStack, Long> catalystInject = new java.util.LinkedHashMap<>();
        Map<IAEItemStack, Long> catalystRebate = new java.util.LinkedHashMap<>();
        Set<IAEItemStack> catalystExcluded = new HashSet<>();
        for (IAEItemStack key : keys) {
            catalystExcluded.add(RecursiveCraftingHelper.canon(key));
        }
        catalystExcluded.add(RecursiveCraftingHelper.canon(what));
        List<CycleAnalyzer.CycleStep> steps = analysis.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (totalTimes[i] > 0) {
                CatalystReturns.collect(steps.get(i).pattern(), totalTimes[i], catalystExcluded,
                        catalystInject, catalystRebate);
            }
        }
        CatalystReturns.inject(catalystInject, inv, src);
        try {
            List<CraftingTreeProcess> pros = new java.util.ArrayList<>();
            List<CycleAnalyzer.CycleStep> proSteps = new java.util.ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                if (totalTimes[i] <= 0) {
                    continue;
                }
                CraftingTreeProcess pro = new CraftingTreeProcess(cc, job, steps.get(i).pattern(), rootNode, 1);
                Ae2CraftingReflect.addProcessToNode(rootNode, pro);
                // 慢步骤埋点(>500ms):定位病态环批量模拟中的重子树
                long stepStart = System.nanoTime();
                BatchSubcraft.requestStep(pro, cc, job, inv, totalTimes[i], src);
                long stepElapsed = System.nanoTime() - stepStart;
                if (stepElapsed > 500_000_000L) {
                    SpecialLog.info("[特殊配方] 慢环步模拟: {} 耗时 {}ms(次数 {})",
                            steps.get(i).pattern(), stepElapsed / 1_000_000L, totalTimes[i]);
                }
                pros.add(pro);
                proSteps.add(steps.get(i));
            }
            // 1.12.2 特有:dive/getAmountCrafted 要求每个 process 的父节点 what
            // 必须是其输出之一,故按"谁产出谁"把平挂的 process 重接为层级链
            reattachProcesses(rootNode, what, proSteps, pros);
        } catch (CraftBranchFailure failure) {
            SpecialLog.info("[特殊配方] 环求解回落:环外输入不足({})", failure.toString());
            return SolveResult.FALLBACK; // 环外输入不足 → 原生兜底(缺料报告)
        } finally {
            for (int i = 0; i < seeds.length; i++) {
                if (loans[i] > 0) {
                    IAEItemStack payback = keys.get(i).copy();
                    payback.setStackSize(loans[i]);
                    inv.extractItems(payback, Actionable.MODULATE, src);
                }
            }
        }

        // 催化剂 used 返利(与环键种子返利键集互斥;边界路径调用方不做环键返利,
        // 此处就地返利保证共享模拟库存余量与网络实取一致)
        if (!catalystRebate.isEmpty()) {
            TreeUsedRebate.rebate(rootNode, catalystRebate);
        }

        // 3) 结算校验(对应 1.20.1 的 settle 步骤):线性系统的守恒记账必须经得起
        // 实际模拟检验——环外子合成若偷吃了环键(如外部输入的子合成消耗请求物),
        // 最终库存将低于 请求量+种子,该方案运行时必死锁,必须回落.
        // 预期库存 = 种子 + rounds×gainPerRound ≥ 请求量 + 种子(种子保留;催化环为 0).
        long avail = invAmount(inv, what);
        long keep = avail > target ? deliverSeed : 0;
        long drainable = Math.min(target, Math.max(0, avail - keep));
        if (drainable < target) {
            SpecialLog.info("[特殊配方] 环求解回落:结算校验失败(库存 {} < 请求 {} + 种子 {})",
                    avail, target, keep);
            return SolveResult.FALLBACK;
        }
        // 结算:取走交付量(种子保留)——防止共享模拟库存(DAG 边界)时同一批产出
        // 被其他节点重复取用;根请求路径模拟库存随后即弃,无行为差异.
        if (drainable > 0) {
            IAEItemStack drainStack = RecursiveCraftingHelper.canon(what);
            drainStack.setStackSize(drainable);
            inv.extractItems(drainStack, Actionable.MODULATE, src);
        }
        return SolveResult.SUCCESS;
    }

    /**
     * 循环链层级重建（1.12.2 树语义）.
     * <p>贷款模拟阶段 process 平挂在根节点下（顺序执行以匹配贷款水位）;但
     * {@code CraftingTreeProcess.dive → getAmountCrafted} 要求父节点的 what 是
     * 该样板的输出之一,否则抛出 "Crafting Tree construction failed"。此处按
     * 产出关系重接:<b>根节点 what 是其输出之一</b>的样板留在根下（常规环 =
     * 产出请求键的样板;催化环 = 产出交付副产物的样板,如 1A→1X+1B 中的该样板）,
     * 其余样板挂到消费其 toKey 的样板的对应输入子节点下;最末端输入节点保持为叶子
     * （种子喂养）.</p>
     * <p>挂接采用 <b>BFS 生成树</b>:从根锚点样板出发,沿"消费者的输入子节点"
     * 反向扩散,保证每个 process 从根可达且不成环。直接"找首个消费者子节点"
     * 会在致密交叉环中形成不含根锚点的挂接环（如 P1→P4→P1),整段子树从根
     * 不可达,dive 丢失这些样板的调用计数;自引用样板（1A+1B→2A）还会匹配到
     * 自身的输入子节点而自挂。</p>
     */
    private static void reattachProcesses(CraftingTreeNode rootNode, IAEItemStack rootWhat,
            List<CycleAnalyzer.CycleStep> proSteps, List<CraftingTreeProcess> pros) {
        Ae2CraftingReflect.getNodeProcesses(rootNode).clear();
        int n = pros.size();
        boolean[] visited = new boolean[n];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        // 根锚点:产出 rootWhat 的样板直接挂根下
        for (int i = 0; i < n; i++) {
            if (producesKey(proSteps.get(i).pattern(), rootWhat)) {
                Ae2CraftingReflect.setProcessParent(pros.get(i), rootNode);
                Ae2CraftingReflect.addProcessToNode(rootNode, pros.get(i));
                visited[i] = true;
                queue.add(i);
            }
        }
        // BFS:已挂接样板的每个输入子节点(what=key)接纳"产出 key 且未挂接"的样板.
        // 自引用步骤已在队列中标记 visited,不会挂到自己的输入子节点下
        while (!queue.isEmpty()) {
            int parent = queue.poll();
            for (CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pros.get(parent)).keySet()) {
                IAEItemStack childWhat = Ae2CraftingReflect.getNodeWhat(child);
                for (int j = 0; j < n; j++) {
                    if (visited[j] || !proSteps.get(j).toKey().equals(childWhat)) {
                        continue;
                    }
                    Ae2CraftingReflect.setProcessParent(pros.get(j), child);
                    Ae2CraftingReflect.addProcessToNode(child, pros.get(j));
                    visited[j] = true;
                    queue.add(j);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                throw new IllegalStateException(
                        "循环链层级重建失败:找不到消费 " + proSteps.get(i).toKey() + " 的样板子节点");
            }
        }
    }

    /** 样板的任一输出（含副产物）是否为 key——dive 的 getAmountCrafted 父锚定判据. */
    private static boolean producesKey(ICraftingPatternDetails pattern, IAEItemStack key) {
        for (IAEItemStack output : pattern.getCondensedOutputs()) {
            if (output != null && key.equals(output)) {
                return true;
            }
        }
        return false;
    }
}
