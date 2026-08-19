package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CycleAnalyzer;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecipeRemainingResolver;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;

/**
 * DAG 编译器:从样板索引把请求展开为计划图（1.12.2 移植）.
 * <ul>
 * <li>节点按 key 合并——重复子树只编译一次(相对原生递归树的核心提速点);</li>
 * <li>两遍编译:第一遍 DFS 探环(回边目标记入边界集合),第二遍把边界 key
 * 当 CYCLE 叶子正式编译;出现边界外的新环 → 回落;</li>
 * <li>只接管"干净"样板:每个输入槽无替代候选({@code getSubstituteInputs} 为空);
 * 存在任一不干净输入 → 换下一个候选样板,全部不干净 → 整单回落;</li>
 * <li>选定样板本身是环步骤(含经副产物闭合的催化环)→ 节点收缩为循环边界,
 * 由 CycleBoundarySolver 联立求解;</li>
 * <li>预算:节点数上限,超限即回落(防病态网络卡死计算线程).</li>
 * </ul>
 */
public final class DagCompiler {

    /** 单图节点数上限(病态深度/广度保护). */
    public static final int MAX_NODES = 100_000;

    private static final int WHITE = 0;
    private static final int GRAY = 1;
    private static final int BLACK = 2;

    private final ICraftingGrid cc;
    private final World world;
    private final Map<IAEItemStack, DagGraph.DagNode> nodes = new HashMap<>();
    private final Map<IAEItemStack, Integer> colors = new HashMap<>();
    private final List<DagGraph.DagNode> postOrder = new ArrayList<>();
    /** 探环遍历时发现的边界 key(回边目标);第二遍编译把它们当叶子. */
    private final Set<IAEItemStack> boundaryKeys;
    /** true = 第一遍(只探环,容忍回落);false = 第二遍(正式编译). */
    private final boolean detectOnly;
    /** 两遍编译共享的生产者索引(成环检测的副产物倒排只建一次;SCC 快路径经其共享索引). */
    private final CycleAnalyzer.ProducerIndex producerIndex;
    /** 本趟编译是否产出了多样板节点(传导到 DagGraph.hasMultiBranch). */
    private boolean sawMultiBranch;

    private DagCompiler(ICraftingGrid cc, World world, Set<IAEItemStack> boundaryKeys, boolean detectOnly,
            CycleAnalyzer.ProducerIndex producerIndex) {
        this.cc = cc;
        this.world = world;
        this.boundaryKeys = boundaryKeys;
        this.detectOnly = detectOnly;
        this.producerIndex = producerIndex;
    }

    /**
     * 两遍编译:第一遍 DFS 探环(回边目标记入边界集合,自身容错),
     * 第二遍把边界 key 当 CYCLE 叶子正式编译;出现边界外的新环 → 回落.
     */
    public static DagGraph compile(ICraftingGrid cc, IAEItemStack root, World world) throws DagFallback {
        try {
            IAEItemStack rootKey = RecursiveCraftingHelper.canon(root);
            Set<IAEItemStack> boundaryKeys = new HashSet<>();
            CycleAnalyzer.ProducerIndex producerIndex = new CycleAnalyzer.ProducerIndex(cc, world);
            try {
                new DagCompiler(cc, world, boundaryKeys, true, producerIndex).visit(rootKey);
            } catch (DagFallback ignored) {
                // 第一遍只负责发现边界;分支编译失败不影响(第二遍做真正的校验)
            }
            DagCompiler compiler = new DagCompiler(cc, world, boundaryKeys, false, producerIndex);
            DagGraph.DagNode rootNode = compiler.visit(rootKey);
            DagGraph graph = new DagGraph(rootNode);
            graph.hasMultiBranch = compiler.sawMultiBranch;
            // 逆后序:父节点(需求方)先于子节点(原料方)
            for (int i = compiler.postOrder.size() - 1; i >= 0; i--) {
                graph.topoOrder.add(compiler.postOrder.get(i));
            }
            return graph;
        } catch (StackOverflowError e) {
            throw new DagFallback("compile_stack_overflow");
        }
    }

    private DagGraph.DagNode visit(IAEItemStack key) throws DagFallback {
        DagGraph.DagNode existing = this.nodes.get(key);
        if (existing != null) {
            if (this.colors.get(key) == GRAY) {
                // 回边:有向环——记录/确认边界;第二遍中边界外的新环是编译缺陷
                if (this.detectOnly) {
                    this.boundaryKeys.add(key);
                    return existing;
                }
                throw new DagFallback("cycle_in_dag:" + key);
            }
            return existing; // BLACK:已编译,直接共享
        }
        if (this.nodes.size() >= MAX_NODES) {
            throw new DagFallback("budget_nodes_exceeded");
        }
        if (!this.detectOnly && this.boundaryKeys.contains(key)) {
            // 循环边界:叶子节点,输入遍历委托 CycleBoundarySolver
            DagGraph.DagNode boundary = new DagGraph.DagNode(DagGraph.Kind.CYCLE, key, 0, null);
            this.nodes.put(key, boundary);
            this.colors.put(key, BLACK);
            this.postOrder.add(boundary);
            return boundary;
        }

        this.colors.put(key, GRAY);
        DagGraph.DagNode node = this.buildNode(key);
        this.nodes.put(key, node);
        if (node.kind == DagGraph.Kind.NORMAL) {
            this.visitInputs(node.pattern, node.edges);
            // 多样板接管:额外分支的输入同样展开(分支序 = 原生尝试序)
            for (DagGraph.Branch branch : node.extraBranches) {
                this.visitInputs(branch.pattern, branch.edges);
            }
        }
        this.colors.put(key, BLACK);
        this.postOrder.add(node);
        return node;
    }

    /** 展开一个分支的输入边(condensed 输入序 = 原生逐槽位处理序). */
    private void visitInputs(ICraftingPatternDetails pattern, List<DagGraph.Edge> edges) throws DagFallback {
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            long perCraft = input.getStackSize();
            edges.add(new DagGraph.Edge(this.visit(RecursiveCraftingHelper.canon(input)), perCraft));
        }
    }

    private DagGraph.DagNode buildNode(IAEItemStack key) throws DagFallback {
        List<ICraftingPatternDetails> clean = new ArrayList<>();
        boolean sawAny = false;
        for (ICraftingPatternDetails pattern : this.cc.getCraftingFor(key, null, -1, this.world)) {
            sawAny = true;
            if (isClean(pattern)) {
                clean.add(pattern);
            }
        }
        if (clean.isEmpty()) {
            if (sawAny) {
                // 有样板但全部含替代输入:本版本不接管
                throw new DagFallback("unclean_inputs:" + key);
            }
            if (this.cc.canEmitFor(key)) {
                return new DagGraph.DagNode(DagGraph.Kind.EMITTER, key, 0, null);
            }
            return new DagGraph.DagNode(DagGraph.Kind.TERMINAL, key, 0, null);
        }
        ICraftingPatternDetails chosen = clean.get(0);
        // 选定样板本身是环步骤(含经副产物闭合的催化环)→ 本节点收缩为循环边界,
        // 由 CycleBoundarySolver 联立求解(否则边界会错位落到环键上而不可解)
        if (CycleAnalyzer.isCycleStep(this.cc, this.world, chosen, this.producerIndex)) {
            return new DagGraph.DagNode(DagGraph.Kind.CYCLE, key, 0, null);
        }
        DagGraph.DagNode node = new DagGraph.DagNode(DagGraph.Kind.NORMAL, key, outPerOf(chosen, key),
                chosen);
        if (clean.size() > 1) {
            // 多样板接管(修复"多样板 key × 极大数量"的 O(数量) 下单陷阱,移植自 1.20.1):
            // 任一分支含容器输入或为环步骤 → 语义过繁,整单回落原生(保守)
            this.sawMultiBranch = true;
            if (hasContainerInput(chosen)) {
                throw new DagFallback("container_multi:" + key);
            }
            for (int i = 1; i < clean.size(); i++) {
                ICraftingPatternDetails branch = clean.get(i);
                if (hasContainerInput(branch)) {
                    throw new DagFallback("container_multi:" + key);
                }
                if (CycleAnalyzer.isCycleStep(this.cc, this.world, branch, this.producerIndex)) {
                    throw new DagFallback("cycle_multi:" + key);
                }
                node.extraBranches.add(new DagGraph.Branch(branch, outPerOf(branch, key)));
            }
        }
        return node;
    }

    /** 样板对本 key 的单次产出(累计全部输出槽);无产出即编译失败. */
    private static long outPerOf(ICraftingPatternDetails pattern, IAEItemStack key) throws DagFallback {
        long outPer = 0;
        for (IAEItemStack output : pattern.getCondensedOutputs()) {
            if (output != null && key.equals(output)) {
                outPer = SaturatedMath.add(outPer, output.getStackSize());
            }
        }
        if (outPer <= 0) {
            throw new DagFallback("pattern_without_output:" + key);
        }
        return outPer;
    }

    /**
     * 任一输入带返还(多分支节点的返还语义不予接管).
     * 可合成样板以配方 getRemainingItems 判定(覆盖 CrT reuse 等不消耗实现);
     * 其余回退 Item 容器 API.
     */
    private static boolean hasContainerInput(ICraftingPatternDetails pattern) {
        Map<IAEItemStack, Long> remaining = RecipeRemainingResolver.remainingPerCraft(pattern);
        if (remaining != null) {
            return !remaining.isEmpty();
        }
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null || input.getItem() == null) {
                continue;
            }
            if (input.getItem().hasContainerItem(input.getDefinition())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 干净样板:每个输入槽无替代候选.
     * <p>容器物返还不再是限制(1.1.0):原生对容器样板逐次(times=1)循环,
     * 但"消耗输入→回记容器"在批量记账下完全等价(消耗 N 份、回记 N-1 份容器,
     * 首个循环不预贷),执行器统一回记,见 DagExecutor.</p>
     */
    private static boolean isClean(ICraftingPatternDetails pattern) {
        // processing 样板无替代输入概念,且原生 PatternHelper.getSubstituteInputs
        // 在 standardRecipe == null 时必 NPE(直接读取其配料表)——必须短路,
        // 否则任何含 processing 样板的请求都会异常回落原生并刷警告日志
        if (!pattern.isCraftable()) {
            return true;
        }
        IAEItemStack[] inputs = pattern.getInputs();
        for (int slot = 0; slot < inputs.length; slot++) {
            if (inputs[slot] == null) {
                continue;
            }
            if (!pattern.getSubstituteInputs(slot).isEmpty()) {
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
