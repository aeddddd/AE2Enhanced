package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.inv.CraftingSimulationState;

import com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect;
import com.github.aeddddd.ae2enhanced.specialcrafting.CycleBoundarySolver;

/**
 * DAG 拓扑执行器:对编译产物做单趟扫描,把计划记账进模拟库存.
 * <p>记账序列与原生 {@code CraftingTreeNode.request} 对齐(已按 15.4.10 源码核对):</p>
 * <ul>
 * <li>库存提取:模糊模板逐个提取(与 {@code getValidItemTemplates} 同语义,
 * 覆盖受损工具等 NBT 变体库存);</li>
 * <li>发射台:记账 emittedItems,零库存零样板;</li>
 * <li>缺料:记录 missing(REPORT_MISSING_ITEMS 语义),继续扫描其余分支;</li>
 * <li>合成:次数 = ⌈缺口/单次产出⌉,子需求沿边饱和累加,
 * <b>全部</b>产出(含副产物)按次回插库存,addCrafting/addBytes 记账;</li>
 * </ul>
 * 根节点的库存语义由调用方先行 {@code ignore(output)} 保证(镜像原生:
 * 请求物自身库存不参与计划扣除).
 */
public final class DagExecutor {

    private DagExecutor() {
    }

    /**
     * @param graph 编译产物
     * @param target 根请求量
     * @param inv 模拟库存(调用方已 ignore 请求物)
     * @param calculation 缺料记账宿主
     * @param craftingService 循环边界求解用
     */
    public static void execute(DagGraph graph, long target, CraftingSimulationState inv,
            CraftingCalculation calculation,
            appeng.api.networking.crafting.ICraftingService craftingService) throws DagFallback {
        Map<DagGraph.DagNode, Long> requests = new IdentityHashMap<>();
        requests.put(graph.root, target);
        // 容器钳制用的乐观容器池(跨键复用:桶式容器可资助下游合成,见 supplyCap)
        Map<AEKey, Long> containerCredits = new java.util.HashMap<>();
        Map<DagGraph.DagNode, Long> supplyMemo = new IdentityHashMap<>();
        // 切边终端(④)的基线库存额度:计划启动前的真实库存,同 key 多节点共享;
        // 切边终端不得消耗计划内产出(否则循环自我供养、产出虚假可行计划)
        Map<AEKey, Long> baselineLeft = new java.util.HashMap<>();
        for (var n : graph.topoOrder) {
            if (n.cutTerminal) {
                baselineLeft.computeIfAbsent(n.key,
                        k -> inv.extract(k, Long.MAX_VALUE, Actionable.SIMULATE));
            }
        }

        for (var node : graph.topoOrder) {
            long need = requests.getOrDefault(node, 0L);
            if (need <= 0) {
                continue;
            }
            // 循环边界:库存/种子语义由边界求解器全权处理,不做预提取
            if (node.kind == DagGraph.Kind.CYCLE) {
                try {
                    if (!CycleBoundarySolver.solveInto(craftingService, calculation, node.key, need, inv)) {
                        throw new DagFallback("cycle_boundary_unsolvable:" + node.key);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DagFallback("cycle_boundary_interrupted");
                }
                continue;
            }

            long taken;
            if (node.cutTerminal) {
                long allowance = baselineLeft.getOrDefault(node.key, 0L);
                taken = extractViaTemplates(inv, node, calculation, Math.min(need, allowance));
                baselineLeft.merge(node.key, -taken, SaturatedMath::add);
            } else {
                taken = extractViaTemplates(inv, node, calculation, need);
            }
            long remaining = need - taken;
            if (remaining <= 0) {
                continue;
            }

            switch (node.kind) {
                case EMITTER -> inv.emitItems(node.key, remaining);
                case TERMINAL -> Ae2CraftingReflect.addMissing(calculation, node.key, remaining);
                case NORMAL -> {
                    if (!node.extraBranches.isEmpty()) {
                        executeMultiBranch(node, remaining, inv, calculation, requests,
                                containerCredits);
                        continue;
                    }
                    long times = SaturatedMath.ceilDiv(remaining, node.outputPerCraft);
                    // 供给感知(②):容器样板按原生 limitQty 语义截断——任一输入的
                    // 供给上限(库存+可产,含容器池预贷)决定可执行次数;
                    // 自返还边(容器==输入键,催化剂式)豁免:净消耗为 0,种子已由
                    // (times-1) 回记规则保证;截断部分记缺料(原生同为首败迭代截断)
                    boolean hasContainer = false;
                    for (var input : node.pattern.getInputs()) {
                        var possible = input.getPossibleInputs();
                        if (input.getRemainingKey(possible[0].what()) != null) {
                            hasContainer = true;
                            // 跨键容器按期望次数乐观预贷(钳制只会减少,方向安全)
                            var container = input.getRemainingKey(possible[0].what());
                            long creditPer = SaturatedMath.multiply(possible[0].amount(),
                                    input.getMultiplier());
                            containerCredits.merge(container, SaturatedMath.multiply(creditPer, times),
                                    SaturatedMath::add);
                        }
                    }
                    if (hasContainer) {
                        for (int i = 0; i < node.edges.size(); i++) {
                            var edge = node.edges.get(i);
                            var input = node.pattern.getInputs()[i];
                            var possible = input.getPossibleInputs();
                            var container = input.getRemainingKey(possible[0].what());
                            if (container != null && container.equals(edge.child().key)) {
                                continue; // 自返还(催化剂):净消耗 0,不钳制
                            }
                            times = Math.min(times,
                                    supplyCap(edge.child(), inv, containerCredits, supplyMemo)
                                            / edge.perCraft());
                        }
                        if (times < 0) {
                            times = 0;
                        }
                        long unmet = remaining - SaturatedMath.multiply(times, node.outputPerCraft);
                        if (unmet > 0) {
                            Ae2CraftingReflect.addMissing(calculation, node.key, unmet);
                        }
                    }
                    for (var edge : node.edges) {
                        long childRequest = SaturatedMath.multiply(edge.perCraft(), times);
                        requests.merge(edge.child(), childRequest, SaturatedMath::add);
                    }
                    // 容器物返还:消耗 N 份输入回记 N 份容器,但**首个循环不预贷**
                    // (按 times-1 计)——物理上第一份容器必须先消耗才返还,全额预贷会把
                    // 催化剂类输入的 usedItems 抹成 0,CPU 不提取种子、执行卡死
                    // (原生逐次循环的高水位 = 每样板 1 份种子,与之对齐);
                    // 容器被下游复用时,拓扑序保证本子节点先回记、下游节点后提取
                    for (var input : node.pattern.getInputs()) {
                        var possible = input.getPossibleInputs();
                        var container = input.getRemainingKey(possible[0].what());
                        if (container != null && times > 1) {
                            long containerAmount = SaturatedMath.multiply(
                                    SaturatedMath.multiply(possible[0].amount(), input.getMultiplier()),
                                    times - 1);
                            inv.insert(container, containerAmount, Actionable.MODULATE);
                        }
                    }
                    for (var output : node.pattern.getOutputs()) {
                        inv.insert(output.what(), SaturatedMath.multiply(output.amount(), times),
                                Actionable.MODULATE);
                    }
                    inv.addCrafting(node.pattern, times);
                    inv.addBytes(times);
                }
                default -> throw new DagFallback("unexpected_node_kind");
            }
        }
    }

    /**
     * 多样板节点执行(修复"多样板 key × 极大数量"的 O(数量) 下单陷阱):
     * 镜像原生多分支语义——按 {@code getCraftingFor} 顺序"分支 1 尽力 → 分支 2",
     * 但把逐次 {@code request(child,1)} 收敛为<b>按供给容量整批</b>:
     * 分支可执行次数 = min(⌈剩余/单次产出⌉, min_e (supplyCap(子) − 已提交需求)/perCraft).
     * <p>容量方向保守(只可能低估):低估只会让更多量落到后续分支,
     * 不产生虚假缺料;全部分支尽力后仍不足 → 记缺料(与原生缺料语义一致).</p>
     */
    private static void executeMultiBranch(DagGraph.DagNode node, long remaining,
            CraftingSimulationState inv, CraftingCalculation calculation,
            Map<DagGraph.DagNode, Long> requests, Map<AEKey, Long> containerCredits) {
        // 本节点本次评估专用 memo:supplyCap 读取的是当前库存,多节点间不复用
        // (库存随执行变化,陈旧 memo 会高估容量、把缺料错误地压在前序分支)
        Map<DagGraph.DagNode, Long> multiMemo = new IdentityHashMap<>();
        // 分支 0 = 节点主分支,1..N = extraBranches(顺序与原生尝试序一致)
        for (int b = -1; b < node.extraBranches.size(); b++) {
            if (remaining <= 0) {
                break;
            }
            var pattern = b < 0 ? node.pattern : node.extraBranches.get(b).pattern();
            long outPer = b < 0 ? node.outputPerCraft : node.extraBranches.get(b).outPer();
            var edges = b < 0 ? node.edges : node.extraBranches.get(b).edges();
            long times = SaturatedMath.ceilDiv(remaining, outPer);
            // 容量截断仅在非模拟尝试启用(真实"分支尽力"语义);
            // 模拟尝试镜像原生"乐观幻影生产":pro.request 在 simulate 下永不失败,
            // 分支 1 包揽全部剩余,缺料沿其原料子树在终端浮现(分支 2 不参与)
            if (!calculation.isSimulation()) {
                for (var edge : edges) {
                    long available = SaturatedMath.add(
                            supplyCap(edge.child(), inv, containerCredits, multiMemo),
                            -requests.getOrDefault(edge.child(), 0L));
                    times = Math.min(times, Math.max(0, available / edge.perCraft()));
                }
            }
            if (times <= 0) {
                continue;
            }
            for (var edge : edges) {
                requests.merge(edge.child(), SaturatedMath.multiply(edge.perCraft(), times),
                        SaturatedMath::add);
            }
            for (var output : pattern.getOutputs()) {
                inv.insert(output.what(), SaturatedMath.multiply(output.amount(), times),
                        Actionable.MODULATE);
            }
            inv.addCrafting(pattern, times);
            inv.addBytes(times);
            remaining -= SaturatedMath.multiply(times, outPer);
        }
        if (remaining > 0) {
            Ae2CraftingReflect.addMissing(calculation, node.key, remaining);
        }
    }

    /**
     * 节点供给上限(② 容器钳制用;③ 多样板分支容量用):当前池库存(含容器池预贷)
     * + 各输入供给上限递归推出的可产次数×单次产出(多样板节点为各分支之和);
     * 发射台/循环边界按乐观无界.
     * 方向保证:只可能高估(预贷乐观),高估导致的超计划在终端以缺料形式浮现,
     * 不会产出"看似可行实际缺料"的计划.
     */
    private static long supplyCap(DagGraph.DagNode node, CraftingSimulationState inv,
            Map<AEKey, Long> containerCredits, Map<DagGraph.DagNode, Long> memo) {
        var cached = memo.get(node);
        if (cached != null) {
            return cached;
        }
        long cap;
        switch (node.kind) {
            case EMITTER, CYCLE -> cap = Long.MAX_VALUE / 4;
            case TERMINAL -> {
                long stock = inv.extract(node.key, Long.MAX_VALUE, Actionable.SIMULATE);
                cap = SaturatedMath.add(stock, containerCredits.getOrDefault(node.key, 0L));
            }
            case NORMAL -> {
                long stock = inv.extract(node.key, Long.MAX_VALUE, Actionable.SIMULATE);
                stock = SaturatedMath.add(stock, containerCredits.getOrDefault(node.key, 0L));
                long producible = branchCap(node.outputPerCraft, node.edges, inv,
                        containerCredits, memo);
                for (var branch : node.extraBranches) {
                    producible = SaturatedMath.add(producible,
                            branchCap(branch.outPer(), branch.edges(), inv,
                                    containerCredits, memo));
                }
                cap = SaturatedMath.add(stock, producible);
            }
            default -> cap = 0;
        }
        memo.put(node, cap);
        return cap;
    }

    /** 单分支可产量:min_e supplyCap(子)/perCraft × 单次产出. */
    private static long branchCap(long outPer, List<DagGraph.Edge> edges, CraftingSimulationState inv,
            Map<AEKey, Long> containerCredits, Map<DagGraph.DagNode, Long> memo) {
        long crafts = Long.MAX_VALUE / 4;
        for (var edge : edges) {
            crafts = Math.min(crafts,
                    supplyCap(edge.child(), inv, containerCredits, memo) / edge.perCraft());
        }
        return SaturatedMath.multiply(crafts, outPer);
    }

    /**
     * 经模糊模板提取库存(与原生 {@code getValidItemTemplates} 同语义):
     * 按请求输入槽的 {@code isValid} 过滤后逐个模板提取——精确输入只认
     * NBT 精确相等,受损工具等模糊输入才允许变体;根节点(无父输入)仅精确键.
     * <p><b>回归教训</b>:曾缺少 isValid 过滤,含同基底物品 NBT 变体库存的
     * 大图上会把别的物品误当本节点库存提取(少合成、计划失真).</p>
     *
     * @return 实际提取总量
     */
    private static long extractViaTemplates(CraftingSimulationState inv, DagGraph.DagNode node,
            CraftingCalculation calculation, long amount) {
        long extracted = 0;
        var level = Ae2CraftingReflect.getLevel(calculation);
        for (var template : inv.findFuzzyTemplates(node.key)) {
            if (extracted >= amount) {
                break;
            }
            if (node.requestInput == null ? !template.equals(node.key)
                    : !node.requestInput.isValid(template, level)) {
                continue;
            }
            extracted += inv.extract(template, amount - extracted, Actionable.MODULATE);
        }
        return extracted;
    }
}
