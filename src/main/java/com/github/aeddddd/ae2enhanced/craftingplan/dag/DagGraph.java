package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * DAG 计划图:编译产物(纯结构,不含库存).
 * <p>节点按 key 合并(重复子树共享同一节点);{@link #topoOrder} 保证
 * 父节点(需求方)先于子节点(原料方),执行器按此序单趟扫描.</p>
 */
public final class DagGraph {

    public enum Kind {
        /** 可由"干净"样板合成(输入唯一候选). */
        NORMAL,
        /** 网络发射台提供(level emitter),零成本. */
        EMITTER,
        /** 无任何样板且不可发射 → 缺料. */
        TERMINAL,
        /** 循环边界(SCC 收缩点):深层自引用/循环链,执行时委托 CycleBoundarySolver. */
        CYCLE
    }

    /** 一条输入边:每执行一次父样板,消耗子 key {@link #perCraft} 份. */
    public static final class Edge {
        private final DagNode child;
        private final long perCraft;
        /**
         * 该输入槽的替代候选(矿词等价物等,canon 键,不含编码输入自身;空 = 精确输入).
         * 仅父样板可合成且启用替代标志时非空;执行层 canCraft 对启用替代的样板
         * 逐槽接受候选(已实证),故含候选的计划在执行层有效.
         */
        private final List<IAEItemStack> substitutes;

        public Edge(DagNode child, long perCraft) {
            this(child, perCraft, java.util.Collections.emptyList());
        }

        public Edge(DagNode child, long perCraft, List<IAEItemStack> substitutes) {
            this.child = child;
            this.perCraft = perCraft;
            this.substitutes = substitutes;
        }

        public DagNode child() {
            return this.child;
        }

        public long perCraft() {
            return this.perCraft;
        }

        public List<IAEItemStack> substitutes() {
            return this.substitutes;
        }
    }

    /**
     * 一个候选分支(多样板接管):同 key 的第 N 个干净样板及其输入边.
     * 分支顺序 = {@code getCraftingFor} 返回序,与原生多分支
     * "分支 1 尽力→分支 2"的尝试顺序一致.
     */
    public static final class Branch {
        public final ICraftingPatternDetails pattern;
        public final long outPer;
        public final List<Edge> edges = new ArrayList<>();

        Branch(ICraftingPatternDetails pattern, long outPer) {
            this.pattern = pattern;
            this.outPer = outPer;
        }
    }

    public static final class DagNode {
        public final Kind kind;
        public final IAEItemStack key;
        /** 每次执行产出本 key 的数量(NORMAL 有效). */
        public final long outputPerCraft;
        @Nullable
        public final ICraftingPatternDetails pattern;
        public final List<Edge> edges = new ArrayList<>();
        /**
         * 额外候选分支(多样板接管,仅 NORMAL):pattern/edges/outputPerCraft
         * 为主分支(分支 0),本列表为分支 1..N;为空 = 单一样板节点.
         * 编译规则:成环样板不参与常规分支(全部成环才收缩为循环边界).
         */
        public final List<Branch> extraBranches = new ArrayList<>();
        /**
         * 环盲降级切的"纯库存"别名叶子(仅 TERMINAL):提取只见网络真实库存,
         * 不得吃祖先样板注入的合成侧产出/返还余额——对齐原生 notRecursive
         * 时序语义(子请求发生时祖先样板尚未产出入账),否则自环键会被
         * 祖先产出双重计数,缺料凭空消失.
         */
        public boolean cycleBlindAlias;

        DagNode(Kind kind, IAEItemStack key, long outputPerCraft, @Nullable ICraftingPatternDetails pattern) {
            this.kind = kind;
            this.key = key;
            this.outputPerCraft = outputPerCraft;
            this.pattern = pattern;
        }
    }

    public final List<DagNode> topoOrder = new ArrayList<>();
    public final DagNode root;
    /**
     * 图中是否含多样板节点:含时缺料计划须以"模拟语义"第二趟重算
     * (首分支不封顶,对齐原生失败重试),见 DagCraftingJob.
     */
    public boolean hasMultiBranch;

    DagGraph(DagNode root) {
        this.root = root;
    }
}
