package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;

import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;

/**
 * LP 计划物化器（方案 L §10.3.5,M5）:整数化计数 → 原生合成树.
 * <p>挂载语义与 {@code DagExecutor} 阶段 1 完全一致:</p>
 * <ul>
 * <li>BFS 从根展开:每个被生产的键挂载其全部 LP 生产者 process
 * （crafts = 整数化次数;构造后 {@code processAddProcess} 立即展开输入子节点）;</li>
 * <li>共享生产:同键只挂载一次(首个父槽位),其余父槽位保持空叶子——
 * dive/setJob 不重复计数,执行层经 CPU 库存池自然衔接;</li>
 * <li>无生产者的键(库存/发射台/缺料)= 空叶子,与原生树同构;</li>
 * <li>缺料回填到该键首个父槽位子节点(根请求键回填根节点)——
 * {@code DagCraftingJob.populatePlan} 的 missing 收集通道直接复用.</li>
 * </ul>
 * 层级合法性由构造保证:子树节点 what 恒等于某输入键,而输入键就是父样板的产出
 * 需求——与 DAG 物化相同的 dive/getAmountCrafted 不变量.
 */
public final class LpPlanMaterializer {

    private LpPlanMaterializer() {
    }

    /**
     * 物化树结构并回填 crafts/missing/bytes.
     *
     * @param times 逐执行记录整数次数({@link FlowReconciler} 产物)
     * @param missing 缺料(canon 键 → 数量)
     * @param totalExtracted 初始提取总量(bytes 近似)
     */
    public static void attach(ICraftingGrid cc, CraftingJob job, CraftingTreeNode rootNode,
            IAEItemStack what, LpPlanOutcome outcome, Map<Execution, Long> times,
            Map<IAEItemStack, Long> missing, long totalExtracted) {
        // 生产者索引:canon 键 → LP 执行记录(仅整数次数 > 0)
        Map<IAEItemStack, List<Execution>> producers = new HashMap<>();
        for (Execution exec : outcome.executions) {
            Long t = times.get(exec);
            if (t == null || t <= 0) {
                continue;
            }
            for (IAEItemStack out : exec.pattern.getCondensedOutputs()) {
                if (out != null) {
                    producers.computeIfAbsent(RecursiveCraftingHelper.canon(out), k -> new ArrayList<>())
                            .add(exec);
                }
            }
        }

        // BFS 挂载:每键一次(共享生产);执行记录亦只挂一次——多输出样板的产出键
        // 都会索引到它,首个产出键节点挂载后,其余产出键保持空叶子(产出已在运行)
        Set<IAEItemStack> attached = new java.util.HashSet<>();
        Set<Execution> attachedExecs = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<IAEItemStack, CraftingTreeNode> firstSlotByKey = new HashMap<>();
        ArrayDeque<CraftingTreeNode> queue = new ArrayDeque<>();
        queue.add(rootNode);
        IAEItemStack rootKey = RecursiveCraftingHelper.canon(what);
        while (!queue.isEmpty()) {
            CraftingTreeNode node = queue.poll();
            IAEItemStack key = RecursiveCraftingHelper.canon(Ae2CraftingReflect.getNodeWhat(node));
            if (!attached.add(key)) {
                continue; // 已挂载:本槽位保持空叶子
            }
            List<Execution> procs = producers.getOrDefault(key, Collections.emptyList());
            for (Execution exec : procs) {
                if (!attachedExecs.add(exec)) {
                    continue; // 已在首个产出键节点挂载
                }
                CraftingTreeProcess pro = new CraftingTreeProcess(cc, job, exec.pattern, node, 1);
                Ae2CraftingReflect.processAddProcess(pro); // 立即展开输入子节点
                Ae2CraftingReflect.addProcessToNode(node, pro);
                Ae2CraftingReflect.setProcessCrafts(pro, times.get(exec));
                for (CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pro).keySet()) {
                    IAEItemStack childWhat = Ae2CraftingReflect.getNodeWhat(child);
                    if (childWhat == null) {
                        continue;
                    }
                    IAEItemStack childKey = RecursiveCraftingHelper.canon(childWhat);
                    firstSlotByKey.putIfAbsent(childKey, child);
                    if (producers.containsKey(childKey) && !attached.contains(childKey)) {
                        queue.add(child);
                    }
                }
            }
        }

        // 缺料回填(根键 → 根节点;其余 → 首个父槽位子节点)
        for (Map.Entry<IAEItemStack, Long> entry : missing.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            CraftingTreeNode slot = entry.getKey().equals(rootKey) ? rootNode
                    : firstSlotByKey.get(entry.getKey());
            if (slot != null) {
                Ae2CraftingReflect.setNodeMissing(slot,
                        Ae2CraftingReflect.getNodeMissing(slot) + entry.getValue());
            }
        }

        // bytes 近似(同 DagExecutor 口径:初始提取总量)
        Ae2CraftingReflect.setNodeBytes(rootNode, totalExtracted);
    }
}
