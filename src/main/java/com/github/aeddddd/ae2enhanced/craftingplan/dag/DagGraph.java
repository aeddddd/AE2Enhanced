package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

/**
 * DAG 计划图:编译产物(纯结构,不含库存).
 * <p>节点按 key 合并(重复子树共享同一节点);{@link #topoOrder} 保证
 * 父节点(需求方)先于子节点(原料方),执行器按此序单趟扫描.</p>
 */
public final class DagGraph {

    public enum Kind {
        /** 可由"干净"样板合成(输入唯一候选、无容器物). */
        NORMAL,
        /** 网络发射台提供(level emitter),零成本. */
        EMITTER,
        /** 无任何样板且不可发射 → 缺料. */
        TERMINAL,
        /** 循环边界(SCC 收缩点):深层自引用/循环链,执行时委托 CycleBoundarySolver. */
        CYCLE
    }

    /** 一条输入边:每执行一次父样板,消耗子 key {@link #perCraft} 份. */
    public record Edge(DagNode child, long perCraft) {
    }

    public static final class DagNode {
        public final Kind kind;
        public final AEKey key;
        /** 每次执行产出本 key 的数量(NORMAL 有效). */
        public final long outputPerCraft;
        @Nullable
        public final IPatternDetails pattern;
        public final List<Edge> edges = new ArrayList<>();
        /**
         * 切边终端(④):回边被剪断生成的独立 TERMINAL 节点——只允许消耗
         * 计划启动前的基线库存,不得使用计划内产出(否则循环自我供养,
         * 产出"无中生有"的虚假可行计划).
         */
        public boolean cutTerminal;
        /**
         * 首个请求本节点的父输入槽(编译建边时写入,多父共享时先到先得):
         * 库存模板提取的 {@code isValid} 过滤依据,镜像原生
         * {@code CraftingTreeNode.parentInput}——精确输入只认 NBT 精确相等,
         * 受损工具等模糊输入才允许变体;{@code null}(根节点)= 仅精确键.
         */
        @Nullable
        public IPatternDetails.IInput requestInput;

        DagNode(Kind kind, AEKey key, long outputPerCraft, @Nullable IPatternDetails pattern) {
            this.kind = kind;
            this.key = key;
            this.outputPerCraft = outputPerCraft;
            this.pattern = pattern;
        }
    }

    public final List<DagNode> topoOrder = new ArrayList<>();
    public final DagNode root;

    DagGraph(DagNode root) {
        this.root = root;
    }
}
