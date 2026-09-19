package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;

import java.util.*;

/**
 * LP 计划物化器:整数化计数 → 原生合成树.
 * <p>BFS 从根展开,每个被生产的键挂载其 LP 生产者 process(crafts = 整数化次数,
 * 构造后立即展开输入子节点).同键只挂载一次(首个父槽位),其余父槽位保持空叶子,
 * dive/setJob 不重复计数;无生产者的键(库存/发射台/缺料)为空叶子,与原生树同构.
 * 缺料回填到该键首个父槽位子节点(根请求键回填根节点),供
 * {@code LpCraftingJob.populatePlan} 的 missing 收集通道复用.</p>
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

        // BFS 挂载:每键一次(共享生产);执行记录也只挂一次——多输出样板的执行
        // 挂在首个产出键节点上,其余产出键节点保持空叶子
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
            } else {
                // 无挂载节点的缺料键(降级/截断单元):计划已置模拟态(不可提交),告警提示
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER
                        .warn("[LP计划] 缺料键无挂载节点,计划显示不完整: {}×{}",
                                entry.getValue(), entry.getKey());
            }
        }

        // bytes 近似(初始提取总量口径)
        Ae2CraftingReflect.setNodeBytes(rootNode, totalExtracted);
    }
}
