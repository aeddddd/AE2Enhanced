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
    /**
     * true = 切边模式(④):回边不再收缩为循环边界,而是生成独立终端节点
     * (库存/缺料满足)——用于边界求解失败后的重试编译,产出诚实的缺料计划
     * 而非整单回落(压缩/解压对等中性转换环).
     */
    private final boolean cutCycles;
    /**
     * "被产生"索引(一次 compile 的两趟编译共享):isCycleStep 的高频全网络扫描
     * 在此收敛为一次性预扫 + 查表,避免 O(N³) 编译退化.
     */
    private final CycleAnalyzer.ProducerIndex producerIndex;

    private DagCompiler(ICraftingService craftingService, Set<AEKey> boundaryKeys, boolean detectOnly,
            boolean cutCycles, CycleAnalyzer.ProducerIndex producerIndex) {
        this.craftingService = craftingService;
        this.boundaryKeys = boundaryKeys;
        this.detectOnly = detectOnly;
        this.cutCycles = cutCycles;
        this.producerIndex = producerIndex;
    }

    /**
     * 两遍编译:第一遍 DFS 探环(回边目标记入边界集合,自身容错),
     * 第二遍把边界 key 当 CYCLE 叶子正式编译;出现边界外的新环 → 回落.
     */
    public static DagGraph compile(ICraftingService craftingService, AEKey root) throws DagFallback {
        return compile(craftingService, root, false);
    }

    /**
     * @param cutCycles true = 切边模式:回边生成独立终端节点(库存/缺料满足),
     *        图必然无环;用于边界求解失败后的重试(见 DagPlanAttempt)
     */
    public static DagGraph compile(ICraftingService craftingService, AEKey root, boolean cutCycles)
            throws DagFallback {
        try {
            var producerIndex = new CycleAnalyzer.ProducerIndex(craftingService);
            if (cutCycles) {
                var compiler = new DagCompiler(craftingService, new HashSet<>(), false, true, producerIndex);
                var rootNode = compiler.visit(root);
                var graph = new DagGraph(rootNode);
                for (int i = compiler.postOrder.size() - 1; i >= 0; i--) {
                    graph.topoOrder.add(compiler.postOrder.get(i));
                }
                return graph;
            }
            Set<AEKey> boundaryKeys = new HashSet<>();
            try {
                new DagCompiler(craftingService, boundaryKeys, true, false, producerIndex).visit(root);
            } catch (DagFallback ignored) {
                // 第一遍只负责发现边界;分支编译失败不影响(第二遍做真正的校验)
            }
            var compiler = new DagCompiler(craftingService, boundaryKeys, false, false, producerIndex);
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
                if (cutCycles) {
                    // 切边(④):生成独立终端节点(不进合并表),仅基线库存/缺料满足,
                    // 循环被剪断、图保持无环;多次回边各自独立、共享同一基线额度
                    var cut = new DagGraph.DagNode(DagGraph.Kind.TERMINAL, key, 0, null);
                    cut.cutTerminal = true;
                    postOrder.add(cut);
                    return cut;
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
            visitInputs(node.pattern.getInputs(), node.edges);
            // 多样板接管:额外分支的输入同样展开(分支序 = 原生尝试序)
            for (var branch : node.extraBranches) {
                visitInputs(branch.pattern().getInputs(), branch.edges());
            }
        }
        colors.put(key, BLACK);
        postOrder.add(node);
        return node;
    }

    /**
     * 展开一个分支的输入边(逆输入序 DFS:postOrder 整体反转后,兄弟分支在
     * topoOrder 中的相对顺序恰好还原为样板输入槽位顺序,与原生
     * CraftingTreeNode 的逐槽位处理一致——否则"副产物供兄弟分支"场景会
     * 在 DAG 下误报缺料);edges 仍按输入序落盘.
     */
    private void visitInputs(IPatternDetails.IInput[] inputs, List<DagGraph.Edge> edges)
            throws DagFallback {
        var built = new DagGraph.Edge[inputs.length];
        for (int i = inputs.length - 1; i >= 0; i--) {
            var input = inputs[i];
            var possible = input.getPossibleInputs();
            long perCraft = SaturatedMath.multiply(possible[0].amount(), input.getMultiplier());
            var child = visit(possible[0].what());
            // 记录首个请求输入槽:执行器库存模板提取的 isValid 过滤依据
            if (child.requestInput == null) {
                child.requestInput = input;
            }
            built[i] = new DagGraph.Edge(child, perCraft);
        }
        java.util.Collections.addAll(edges, built);
    }

    private DagGraph.DagNode buildNode(AEKey key) throws DagFallback {
        List<IPatternDetails> clean = new ArrayList<>();
        boolean sawAny = false;
        for (var pattern : craftingService.getCraftingFor(key)) {
            sawAny = true;
            if (isClean(pattern)) {
                clean.add(pattern);
            }
        }
        if (clean.isEmpty()) {
            if (sawAny) {
                // 有样板但全部含替代/tag/容器输入:本版本不接管
                throw new DagFallback("unclean_inputs:" + key);
            }
            if (craftingService.canEmitFor(key)) {
                return new DagGraph.DagNode(DagGraph.Kind.EMITTER, key, 0, null);
            }
            return new DagGraph.DagNode(DagGraph.Kind.TERMINAL, key, 0, null);
        }
        var chosen = clean.get(0);
        // 选定样板本身是环步骤(含经副产物闭合的催化环)→ 本节点收缩为循环边界,
        // 由 CycleBoundarySolver 联立求解(否则边界会错位落到环键上而不可解);
        // 切边模式禁用此标记——切边的目的就是剪断环,不能再收缩回求解器
        if (!cutCycles && CycleAnalyzer.isCycleStep(craftingService, chosen, producerIndex)) {
            return new DagGraph.DagNode(DagGraph.Kind.CYCLE, key, 0, null);
        }
        var node = new DagGraph.DagNode(DagGraph.Kind.NORMAL, key, outPerOf(chosen, key), chosen);
        if (clean.size() > 1) {
            // 多样板接管(修复"多样板 key × 极大数量"的 O(数量) 下单陷阱):
            // 任一分支含容器输入或为环步骤 → 语义过繁,整单回落原生(保守)
            for (int i = 1; i < clean.size(); i++) {
                var branch = clean.get(i);
                if (hasContainerInput(branch)) {
                    throw new DagFallback("container_multi:" + key);
                }
                if (!cutCycles && CycleAnalyzer.isCycleStep(craftingService, branch, producerIndex)) {
                    throw new DagFallback("cycle_multi:" + key);
                }
                node.extraBranches.add(new DagGraph.Branch(branch, outPerOf(branch, key),
                        new ArrayList<>()));
            }
            // 主分支同样不允许容器输入(多分支节点的容器语义不予接管)
            if (hasContainerInput(chosen)) {
                throw new DagFallback("container_multi:" + key);
            }
        }
        return node;
    }

    /** 样板对本 key 的单次产出(累计全部输出槽);无产出即编译失败. */
    private static long outPerOf(IPatternDetails pattern, AEKey key) throws DagFallback {
        long outPer = 0;
        for (var output : pattern.getOutputs()) {
            if (output.what().equals(key)) {
                outPer = SaturatedMath.add(outPer, output.amount());
            }
        }
        if (outPer <= 0) {
            throw new DagFallback("pattern_without_output:" + key);
        }
        return outPer;
    }

    /** 任一输入带容器返还(getRemainingKey 非空). */
    private static boolean hasContainerInput(IPatternDetails pattern) {
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length == 1 && input.getRemainingKey(possible[0].what()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 干净样板:每个输入单一候选(无 tag/替代展开).
     * <p>容器物返还不再是限制(1.1.0):原生对容器样板逐次(times=1)循环,
     * 但"消耗输入→回记容器"在批量记账下完全等价(消耗 N 份、回记 N 份容器,
     * 下游复用经拓扑序自然衔接),执行器统一回记,见 DagExecutor.</p>
     */
    private static boolean isClean(IPatternDetails pattern) {
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length != 1) {
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
