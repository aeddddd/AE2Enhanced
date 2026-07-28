package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import java.util.IdentityHashMap;
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

            long remaining = need - extractViaTemplates(inv, node.key, need);
            if (remaining <= 0) {
                continue;
            }

            switch (node.kind) {
                case EMITTER -> inv.emitItems(node.key, remaining);
                case TERMINAL -> Ae2CraftingReflect.addMissing(calculation, node.key, remaining);
                case NORMAL -> {
                    long times = SaturatedMath.ceilDiv(remaining, node.outputPerCraft);
                    for (var edge : node.edges) {
                        long childRequest = SaturatedMath.multiply(edge.perCraft(), times);
                        requests.merge(edge.child(), childRequest, SaturatedMath::add);
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
     * 经模糊模板提取库存(与原生 getValidItemTemplates 同语义):逐个变体提取直到满足.
     *
     * @return 实际提取总量
     */
    private static long extractViaTemplates(CraftingSimulationState inv, AEKey key, long amount) {
        long extracted = 0;
        for (var template : inv.findFuzzyTemplates(key)) {
            if (extracted >= amount) {
                break;
            }
            extracted += inv.extract(template, amount - extracted, Actionable.MODULATE);
        }
        return extracted;
    }
}
