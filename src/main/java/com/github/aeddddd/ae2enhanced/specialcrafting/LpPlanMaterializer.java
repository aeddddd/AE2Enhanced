package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.github.aeddddd.ae2enhanced.specialcrafting.ResolvedPlan.ExecutionRecord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将不可变 LP 整数计划物化为原生树，并给出可机器检查的提交契约。 */
public final class LpPlanMaterializer {

    private LpPlanMaterializer() {
    }

    public static Report attach(ICraftingGrid cc, CraftingJob job, CraftingTreeNode rootNode,
            ResolvedPlan plan) {
        List<String> reasons = new ArrayList<>();
        Map<CraftingTreeProcess, ExecutionRecord> expectedProcesses = new IdentityHashMap<>();
        try {
            attachProcesses(cc, job, rootNode, plan, expectedProcesses, reasons);
        } catch (RuntimeException e) {
            reasons.add("物化异常: " + e.getMessage());
        }
        List<String> attachmentReasons = new ArrayList<>(reasons);
        verify(plan, rootNode, expectedProcesses, reasons);
        return new Report(plan, reasons, attachmentReasons, expectedProcesses);
    }

    private static void attachProcesses(ICraftingGrid cc, CraftingJob job, CraftingTreeNode rootNode,
            ResolvedPlan plan, Map<CraftingTreeProcess, ExecutionRecord> expectedProcesses,
            List<String> reasons) {
        Map<IAEItemStack, List<ExecutionRecord>> producers = new HashMap<>();
        for (ExecutionRecord exec : plan.executions()) {
            for (IAEItemStack out : exec.pattern().getCondensedOutputs()) {
                if (out != null) {
                    producers.computeIfAbsent(RecursiveCraftingHelper.canon(out), k -> new ArrayList<>())
                            .add(exec);
                }
            }
        }

        Map<IAEItemStack, Long> unusedStock = new HashMap<>();
        for (IAEItemStack used : Ae2CraftingReflect.getNodeUsed(rootNode)) {
            unusedStock.merge(RecursiveCraftingHelper.canon(used), used.getStackSize(), SaturatedMath::add);
        }

        Set<IAEItemStack> attached = new HashSet<>();
        Set<ExecutionRecord> attachedExecs = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<CraftingTreeNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<CraftingTreeNode, Long> requests = new IdentityHashMap<>();
        Map<IAEItemStack, CraftingTreeNode> firstSlotByKey = new HashMap<>();
        ArrayDeque<CraftingTreeNode> queue = new ArrayDeque<>();
        queue.add(rootNode);
        IAEItemStack rootDemand = plan.rootDemand();
        requests.put(rootNode, rootDemand.getStackSize());
        IAEItemStack rootKey = RecursiveCraftingHelper.canon(rootDemand);
        while (!queue.isEmpty()) {
            CraftingTreeNode node = queue.poll();
            if (!visited.add(node)) {
                continue;
            }
            IAEItemStack nodeWhat = Ae2CraftingReflect.getNodeWhat(node);
            IAEItemStack key = RecursiveCraftingHelper.canon(nodeWhat);
            long requested = requests.getOrDefault(node, 0L);
            if (cc.canEmitFor(nodeWhat)) {
                long fromStock = node == rootNode ? 0 : Math.min(requested, unusedStock.getOrDefault(key, 0L));
                Ae2CraftingReflect.setNodeEmitted(node, requested - fromStock);
                if (fromStock > 0) {
                    unusedStock.put(key, unusedStock.get(key) - fromStock);
                }
            } else if (attached.add(key)) {
                for (ExecutionRecord exec : producers.getOrDefault(key, Collections.emptyList())) {
                    if (!attachedExecs.add(exec)) {
                        continue;
                    }
                    CraftingTreeProcess process = new CraftingTreeProcess(cc, job, exec.pattern(), node, 1);
                    addProcessInputs(cc, job, process, exec);
                    Ae2CraftingReflect.addProcessToNode(node, process);
                    Ae2CraftingReflect.setProcessCrafts(process, exec.count());
                    expectedProcesses.put(process, exec);
                    for (CraftingTreeNode child : Ae2CraftingReflect.sortedProcessNodes(process)) {
                        IAEItemStack childWhat = Ae2CraftingReflect.getNodeWhat(child);
                        if (childWhat == null) {
                            continue;
                        }
                        IAEItemStack childKey = RecursiveCraftingHelper.canon(childWhat);
                        firstSlotByKey.putIfAbsent(childKey, child);
                        long perCraft = Ae2CraftingReflect.getProcessNodes(process).getLong(child);
                        requests.put(child, SaturatedMath.multiply(perCraft, exec.count()));
                        queue.add(child);
                    }
                }
            }
        }

        for (Map.Entry<IAEItemStack, Long> entry : plan.missingView().entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            CraftingTreeNode slot = entry.getKey().equals(rootKey) ? rootNode : firstSlotByKey.get(entry.getKey());
            if (slot == null) {
                reasons.add("缺料键无挂载节点: " + entry.getValue() + "×" + entry.getKey());
            } else {
                Ae2CraftingReflect.setNodeMissing(slot,
                        Ae2CraftingReflect.getNodeMissing(slot) + entry.getValue());
            }
        }
        Ae2CraftingReflect.setNodeBytes(rootNode, plan.initialExtractedBytes());
    }

    private static void verify(ResolvedPlan plan, CraftingTreeNode root,
            Map<CraftingTreeProcess, ExecutionRecord> expectedProcesses, List<String> reasons) {
        if (root == null) {
            reasons.add("物化树为 null");
            return;
        }
        Set<CraftingTreeProcess> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<ExecutionRecord, Integer> executionsSeen = new IdentityHashMap<>();
        Map<IAEItemStack, Long> emitted = new LinkedHashMap<>();
        Map<IAEItemStack, Long> missing = new LinkedHashMap<>();
        collect(root, expectedProcesses, seen, executionsSeen, emitted, missing, reasons);
        for (ExecutionRecord execution : plan.executions()) {
            int count = executionsSeen.getOrDefault(execution, 0);
            if (count != 1) {
                reasons.add((count == 0 ? "活跃执行未物化: " : "活跃执行重复物化: ")
                        + execution.pattern() + "×" + execution.count());
            }
        }
        compareAmounts("发射义务", plan.emissionObligationsView(), emitted, reasons);
        compareAmounts("缺料回填", plan.missingView(), missing, reasons);
        Map<IAEItemStack, Long> extracted = new LinkedHashMap<>();
        for (IAEItemStack stack : Ae2CraftingReflect.getNodeUsed(root)) {
            if (stack != null && stack.getStackSize() > 0) {
                extracted.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(), SaturatedMath::add);
            }
        }
        compareAmounts("初始提取", plan.initialExtractionsView(), extracted, reasons);
        long bytes = Ae2CraftingReflect.getNodeBytes(root);
        long expectedBytes = Math.min(Integer.MAX_VALUE, plan.initialExtractedBytes());
        if (bytes != expectedBytes) {
            reasons.add("初始提取 bytes 不一致: plan=" + expectedBytes + ", tree=" + bytes);
        }
        if (!plan.missingView().isEmpty()) {
            reasons.add("计划存在缺料");
        }
        if (plan.unresolvedUnits() > 0) {
            reasons.add("计划存在未解出单元: " + plan.unresolvedUnits());
        }
    }

    private static void collect(CraftingTreeNode node,
            Map<CraftingTreeProcess, ExecutionRecord> expectedProcesses, Set<CraftingTreeProcess> seen,
            Map<ExecutionRecord, Integer> executionsSeen, Map<IAEItemStack, Long> emitted,
            Map<IAEItemStack, Long> missing, List<String> reasons) {
        long nodeEmitted = Ae2CraftingReflect.getNodeEmitted(node);
        if (nodeEmitted > 0) {
            emitted.merge(RecursiveCraftingHelper.canon(Ae2CraftingReflect.getNodeWhat(node)), nodeEmitted,
                    SaturatedMath::add);
        }
        long nodeMissing = Ae2CraftingReflect.getNodeMissing(node);
        if (nodeMissing > 0) {
            missing.merge(RecursiveCraftingHelper.canon(Ae2CraftingReflect.getNodeWhat(node)), nodeMissing,
                    SaturatedMath::add);
        }
        for (CraftingTreeProcess process : Ae2CraftingReflect.sortedNodeProcesses(node)) {
            long crafts = Ae2CraftingReflect.getProcessCrafts(process);
            ExecutionRecord expected = expectedProcesses.get(process);
            if (crafts > 0 && expected == null) {
                reasons.add("树包含计划外执行: " + Ae2CraftingReflect.getProcessDetails(process) + "×" + crafts);
            }
            if (expected != null) {
                if (!seen.add(process)) {
                    reasons.add("同一 process 重复出现: " + expected.pattern());
                }
                executionsSeen.merge(expected, 1, Integer::sum);
                if (crafts != expected.count()) {
                    reasons.add("执行次数不一致: " + expected.pattern() + " plan=" + expected.count()
                            + ", tree=" + crafts);
                }
            }
            for (CraftingTreeNode child : Ae2CraftingReflect.sortedProcessNodes(process)) {
                collect(child, expectedProcesses, seen, executionsSeen, emitted, missing, reasons);
            }
        }
    }

    private static void compareAmounts(String label, Map<IAEItemStack, Long> expected,
            Map<IAEItemStack, Long> actual, List<String> reasons) {
        Set<IAEItemStack> keys = new HashSet<>();
        keys.addAll(expected.keySet());
        keys.addAll(actual.keySet());
        for (IAEItemStack key : keys) {
            long expectedAmount = amountOf(expected, key);
            long actualAmount = amountOf(actual, key);
            if (expectedAmount != actualAmount) {
                reasons.add(label + "不一致: " + key + " plan=" + expectedAmount + ", tree=" + actualAmount);
            }
        }
    }

    private static long amountOf(Map<IAEItemStack, Long> amounts, IAEItemStack key) {
        for (Map.Entry<IAEItemStack, Long> entry : amounts.entrySet()) {
            if (entry.getKey().equals(key)) {
                return entry.getValue();
            }
        }
        return 0L;
    }

    private static void addProcessInputs(ICraftingGrid cc, CraftingJob job, CraftingTreeProcess process,
            ExecutionRecord exec) {
        Map<IAEItemStack, Long> variantInputs = exec.variantInputsView();
        if (variantInputs.isEmpty()) {
            Ae2CraftingReflect.processAddProcess(process);
            return;
        }
        IAEItemStack[] slots = exec.pattern().getInputs();
        IAEItemStack[] selected = new IAEItemStack[slots.length];
        Map<IAEItemStack, Long> remaining = new LinkedHashMap<>(
                com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpModelBuilder
                        .condensedInputs(exec.pattern(), variantInputs));
        if (!selectVariantInputs(exec.pattern(), slots, selected, remaining, 0)) {
            throw new IllegalStateException("LP 替代变体无法还原到原生样板槽位: " + variantInputs);
        }

        Map<IAEItemStack, IAEItemStack> merged = new LinkedHashMap<>();
        Map<IAEItemStack, Integer> firstSlots = new HashMap<>();
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] == null || slots[slot].getStackSize() <= 0) {
                continue;
            }
            IAEItemStack key = RecursiveCraftingHelper.canon(selected[slot]);
            IAEItemStack input = merged.get(key);
            if (input == null) {
                input = selected[slot].copy();
                input.setStackSize(slots[slot].getStackSize());
                merged.put(key, input);
                firstSlots.put(key, slot);
            } else {
                input.setStackSize(SaturatedMath.add(input.getStackSize(), slots[slot].getStackSize()));
            }
        }
        for (Map.Entry<IAEItemStack, IAEItemStack> entry : merged.entrySet()) {
            IAEItemStack input = entry.getValue();
            CraftingTreeNode child = new CraftingTreeNode(cc, job, input, process,
                    firstSlots.get(entry.getKey()), 2);
            Ae2CraftingReflect.getProcessNodes(process).put(child, input.getStackSize());
        }
    }

    private static boolean selectVariantInputs(ICraftingPatternDetails pattern, IAEItemStack[] slots,
            IAEItemStack[] selected, Map<IAEItemStack, Long> remaining, int slot) {
        if (slot == slots.length) {
            return true;
        }
        IAEItemStack encodedStack = slots[slot];
        if (encodedStack == null || encodedStack.getStackSize() <= 0) {
            return selectVariantInputs(pattern, slots, selected, remaining, slot + 1);
        }
        IAEItemStack encoded = RecursiveCraftingHelper.canon(encodedStack);
        Set<IAEItemStack> candidateSet = new HashSet<>();
        List<IAEItemStack> substitutes = pattern.getSubstituteInputs(slot);
        if (substitutes != null) {
            for (IAEItemStack substitute : substitutes) {
                if (substitute != null) {
                    candidateSet.add(RecursiveCraftingHelper.canon(substitute));
                }
            }
        }
        List<IAEItemStack> candidates = new ArrayList<>();
        candidates.add(encoded);
        for (IAEItemStack candidate : StableOrder.sortedKeys(candidateSet)) {
            if (!candidate.equals(encoded)) {
                candidates.add(candidate);
            }
        }
        long amount = encodedStack.getStackSize();
        for (IAEItemStack candidate : candidates) {
            long available = remaining.getOrDefault(candidate, 0L);
            if (available < amount) {
                continue;
            }
            remaining.put(candidate, available - amount);
            selected[slot] = candidate;
            if (selectVariantInputs(pattern, slots, selected, remaining, slot + 1)) {
                return true;
            }
            remaining.put(candidate, available);
            selected[slot] = null;
        }
        return false;
    }

    /** 物化结果；失败原因是提交门禁数据，不是可忽略的日志。 */
    public static final class Report {
        private final List<String> reasons;
        private final List<String> attachmentReasons;
        private final Map<CraftingTreeProcess, ExecutionRecord> expectedProcesses;
        private final ResolvedPlan plan;

        private Report(ResolvedPlan plan, List<String> reasons, List<String> attachmentReasons,
                Map<CraftingTreeProcess, ExecutionRecord> expectedProcesses) {
            this.plan = plan;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
            this.attachmentReasons = Collections.unmodifiableList(new ArrayList<>(attachmentReasons));
            this.expectedProcesses = new IdentityHashMap<>(expectedProcesses);
        }

        public boolean committable() {
            return this.reasons.isEmpty();
        }

        public List<String> reasons() {
            return this.reasons;
        }

        /** 对最终树重新校验，防止物化后或 dive 后发生静默损坏。 */
        public Report revalidate(CraftingTreeNode root) {
            List<String> reasons = new ArrayList<>(this.attachmentReasons);
            verify(this.plan, root, this.expectedProcesses, reasons);
            return new Report(this.plan, reasons, this.attachmentReasons, this.expectedProcesses);
        }
    }
}
