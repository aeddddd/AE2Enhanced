package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingTreeNode;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;

import java.util.*;

/** LP 整数解的不可变提交契约；原生树只是它的物化产物。 */
public final class ResolvedPlan {

    private final IAEItemStack rootDemand;
    private final List<ExecutionRecord> executions;
    private final Map<IAEItemStack, Long> initialExtractions;
    private final long initialExtractedBytes;
    private final Map<IAEItemStack, Long> emissionObligations;
    private final Map<IAEItemStack, Long> missing;
    private final int unresolvedUnits;
    private final boolean cycleBoundary;
    private ResolvedPlan(IAEItemStack rootDemand, List<ExecutionRecord> executions,
            Map<IAEItemStack, Long> initialExtractions, long initialExtractedBytes,
            Map<IAEItemStack, Long> emissionObligations, Map<IAEItemStack, Long> missing,
            int unresolvedUnits, boolean cycleBoundary) {
        this.rootDemand = rootDemand.copy();
        this.executions = Collections.unmodifiableList(new ArrayList<>(executions));
        this.initialExtractions = immutableAmounts(initialExtractions);
        this.initialExtractedBytes = initialExtractedBytes;
        this.emissionObligations = immutableAmounts(emissionObligations);
        this.missing = immutableAmounts(missing);
        this.unresolvedUnits = unresolvedUnits;
        this.cycleBoundary = cycleBoundary;
    }

    static ResolvedPlan of(IAEItemStack rootDemand, CondensationPlanner.LpPlanOutcome outcome,
            FlowReconciler.Reconciled reconciled, CraftingTreeNode extractionRoot,
            NetworkPatternIndex index) {
        List<ExecutionRecord> executions = new ArrayList<>();
        boolean cycleBoundary = false;
        for (Execution execution : reconciled.executions) {
            long count = reconciled.times.getOrDefault(execution, 0L);
            if (count <= 0) {
                continue;
            }
            executions.add(new ExecutionRecord(execution, count));
            cycleBoundary |= index.isCycleStep(execution.pattern);
        }
        Map<IAEItemStack, Long> extracted = new LinkedHashMap<>();
        for (IAEItemStack stack : Ae2CraftingReflect.getNodeUsed(extractionRoot)) {
            if (stack != null && stack.getStackSize() > 0) {
                extracted.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(),
                        SaturatedMath::add);
            }
        }
        return new ResolvedPlan(rootDemand, executions, extracted, reconciled.totalExtracted,
                reconciled.emissions, reconciled.missing, outcome.degradedUnits, cycleBoundary);
    }

    private static Map<IAEItemStack, Long> immutableAmounts(Map<IAEItemStack, Long> source) {
        return Collections.unmodifiableMap(copyAmounts(source));
    }

    private static Map<IAEItemStack, Long> copyAmounts(Map<IAEItemStack, Long> source) {
        Map<IAEItemStack, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<IAEItemStack, Long> entry : source.entrySet()) {
            IAEItemStack key = RecursiveCraftingHelper.canon(entry.getKey()).copy();
            key.setStackSize(1L);
            copy.put(key, entry.getValue());
        }
        return copy;
    }

    public IAEItemStack rootDemand() {
        return this.rootDemand.copy();
    }

    public List<ExecutionRecord> executions() {
        return this.executions;
    }

    public Map<IAEItemStack, Long> initialExtractions() {
        return Collections.unmodifiableMap(copyAmounts(this.initialExtractions));
    }

    public long initialExtractedBytes() {
        return this.initialExtractedBytes;
    }

    public Map<IAEItemStack, Long> emissionObligations() {
        return Collections.unmodifiableMap(copyAmounts(this.emissionObligations));
    }

    public Map<IAEItemStack, Long> missing() {
        return Collections.unmodifiableMap(copyAmounts(this.missing));
    }

    public int unresolvedUnits() {
        return this.unresolvedUnits;
    }

    public boolean hasCycleBoundary() {
        return this.cycleBoundary;
    }

    Map<IAEItemStack, Long> initialExtractionsView() {
        return this.initialExtractions;
    }

    Map<IAEItemStack, Long> emissionObligationsView() {
        return this.emissionObligations;
    }

    Map<IAEItemStack, Long> missingView() {
        return this.missing;
    }

    /** 一条正计数执行；不再把求解器可变/实数产物传给物化层。 */
    public static final class ExecutionRecord {
        private final ICraftingPatternDetails pattern;
        private final Map<IAEItemStack, Long> variantInputs;
        private final long count;

        private ExecutionRecord(Execution execution, long count) {
            this.pattern = execution.pattern;
            this.variantInputs = immutableAmounts(execution.variantInputs);
            this.count = count;
        }

        public ICraftingPatternDetails pattern() {
            return this.pattern;
        }

        public Map<IAEItemStack, Long> variantInputs() {
            return Collections.unmodifiableMap(copyAmounts(this.variantInputs));
        }

        public long count() {
            return this.count;
        }

        Map<IAEItemStack, Long> variantInputsView() {
            return this.variantInputs;
        }
    }
}
