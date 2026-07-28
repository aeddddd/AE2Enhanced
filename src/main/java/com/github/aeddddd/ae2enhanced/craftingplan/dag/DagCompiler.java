package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;

import com.github.aeddddd.ae2enhanced.specialcrafting.CycleAnalyzer;

/**
 * DAG 编译器:从样板索引把请求展开为计划图.
 * <ul>
 * <li>节点按 key 合并——重复子树只编译一次(相对原生递归树的核心提速点);</li>
 * <li>同一趟 DFS 做三色检环:任何环(含深层自引用/循环链)在本版本一律回落,
 * 深层循环的边界委托见规划文档阶段 4.3;</li>
 * <li>只接管"干净"样板:每个输入唯一候选(无 tag/替代)且无容器物
 * ({@link appeng.api.crafting.IPatternDetails.IInput#getRemainingKey} 为空);
 * 存在任一不干净输入 → 换下一个候选样板,全部不干净 → 整单回落;</li>
 * <li>预算:节点数上限,超限即回落(防病态网络卡死计算线程).</li>
 * </ul>
 */
public final class DagCompiler {

    /** 单图节点数上限(病态深度/广度保护). */
    public static final int MAX_NODES = 100_000;

    private static final int WHITE = 0;
    private static final int GRAY = 1;
    private static final int BLACK = 2;

    private final ICraftingService craftingService;
    private final Map<AEKey, DagGraph.DagNode> nodes = new HashMap<>();
    private final Map<AEKey, Integer> colors = new HashMap<>();
    private final List<DagGraph.DagNode> postOrder = new ArrayList<>();
    /** 探环遍历时发现的边界 key(回边目标);第二遍编译把它们当叶子. */
    private final Set<AEKey> boundaryKeys;
    /** true = 第一遍(只探环,容忍回落);false = 第二遍(正式编译). */
    private final boolean detectOnly;

    private DagCompiler(ICraftingService craftingService, Set<AEKey> boundaryKeys, boolean detectOnly) {
        this.craftingService = craftingService;
        this.boundaryKeys = boundaryKeys;
        this.detectOnly = detectOnly;
    }

    /**
     * 两遍编译:第一遍 DFS 探环(回边目标记入边界集合,自身容错),
     * 第二遍把边界 key 当 CYCLE 叶子正式编译;出现边界外的新环 → 回落.
     */
    public static DagGraph compile(ICraftingService craftingService, AEKey root) throws DagFallback {
        try {
            Set<AEKey> boundaryKeys = new HashSet<>();
            try {
                new DagCompiler(craftingService, boundaryKeys, true).visit(root);
            } catch (DagFallback ignored) {
                // 第一遍只负责发现边界;分支编译失败不影响(第二遍做真正的校验)
            }
            var compiler = new DagCompiler(craftingService, boundaryKeys, false);
            var rootNode = compiler.visit(root);
            var graph = new DagGraph(rootNode);
            // 逆后序:父节点(需求方)先于子节点(原料方)
            for (int i = compiler.postOrder.size() - 1; i >= 0; i--) {
                graph.topoOrder.add(compiler.postOrder.get(i));
            }
            return graph;
        } catch (StackOverflowError e) {
            throw new DagFallback("compile_stack_overflow");
        }
    }

    private DagGraph.DagNode visit(AEKey key) throws DagFallback {
        var existing = nodes.get(key);
        if (existing != null) {
            if (colors.get(key) == GRAY) {
                // 回边:有向环——记录/确认边界;第二遍中边界外的新环是编译缺陷
                if (detectOnly) {
                    boundaryKeys.add(key);
                    return existing;
                }
                throw new DagFallback("cycle_in_dag:" + key);
            }
            return existing; // BLACK:已编译,直接共享
        }
        if (nodes.size() >= MAX_NODES) {
            throw new DagFallback("budget_nodes_exceeded");
        }
        if (!detectOnly && boundaryKeys.contains(key)) {
            // 循环边界:叶子节点,输入遍历委托 CycleBoundarySolver
            var boundary = new DagGraph.DagNode(DagGraph.Kind.CYCLE, key, 0, null);
            nodes.put(key, boundary);
            colors.put(key, BLACK);
            postOrder.add(boundary);
            return boundary;
        }

        colors.put(key, GRAY);
        var node = buildNode(key);
        nodes.put(key, node);
        if (node.kind == DagGraph.Kind.NORMAL) {
            for (var input : node.pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                long perCraft = SaturatedMath.multiply(possible[0].amount(), input.getMultiplier());
                node.edges.add(new DagGraph.Edge(visit(possible[0].what()), perCraft));
            }
        }
        colors.put(key, BLACK);
        postOrder.add(node);
        return node;
    }

    private DagGraph.DagNode buildNode(AEKey key) throws DagFallback {
        IPatternDetails chosen = null;
        boolean sawAny = false;
        for (var pattern : craftingService.getCraftingFor(key)) {
            sawAny = true;
            if (isClean(pattern)) {
                chosen = pattern;
                break;
            }
        }
        if (chosen == null) {
            if (sawAny) {
                // 有样板但全部含替代/tag/容器输入:本版本不接管
                throw new DagFallback("unclean_inputs:" + key);
            }
            if (craftingService.canEmitFor(key)) {
                return new DagGraph.DagNode(DagGraph.Kind.EMITTER, key, 0, null);
            }
            return new DagGraph.DagNode(DagGraph.Kind.TERMINAL, key, 0, null);
        }
        // 选定样板本身是环步骤(含经副产物闭合的催化环)→ 本节点收缩为循环边界,
        // 由 CycleBoundarySolver 联立求解(否则边界会错位落到环键上而不可解)
        if (CycleAnalyzer.isCycleStep(craftingService, chosen)) {
            return new DagGraph.DagNode(DagGraph.Kind.CYCLE, key, 0, null);
        }
        long outPer = 0;
        for (var output : chosen.getOutputs()) {
            if (output.what().equals(key)) {
                outPer = SaturatedMath.add(outPer, output.amount());
            }
        }
        if (outPer <= 0) {
            throw new DagFallback("pattern_without_output:" + key);
        }
        return new DagGraph.DagNode(DagGraph.Kind.NORMAL, key, outPer, chosen);
    }

    /**
     * 干净样板:每个输入单一候选(无 tag/替代展开)且无容器物返还.
     */
    private static boolean isClean(IPatternDetails pattern) {
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length != 1) {
                return false;
            }
            if (input.getRemainingKey(possible[0].what()) != null) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static String describe(@Nullable DagFallback fallback) {
        return fallback == null ? null : fallback.reason;
    }
}
