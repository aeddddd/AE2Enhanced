package com.github.aeddddd.ae2enhanced.pathing;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridVisitor;
import appeng.me.GridNode;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;

import java.util.HashSet;
import java.util.Set;

/**
 * 网格分裂检测批处理器。
 *
 * <p>原版 {@link GridNode#destroy()} 在逐条销毁连接时，每条连接都会触发
 * 两侧节点的 {@code validateGrid()}（各一次全图 BFS）。拆除一台有 N 条连接的设备
 * 最多产生 2N 次全图 BFS，在大型网络中是明显的卡顿源。</p>
 *
 * <p>本类在 GridNode.destroy() 期间推迟所有 validateGrid 调用：连接全部销毁后，
 * 从网格 pivot 做一次 BFS 收集主连通分量，仅对不在主分量中的前邻居节点执行
 * validateGrid（让分裂出的连通分量各自建立新 Grid）。无分裂的常见情况下，
 * 全套操作只有 1 次全图 BFS。</p>
 *
 * <p>仅在服务器主线程使用，无需同步。</p>
 */
public final class GridValidationBatcher implements AutoCloseable {

    private int depth;
    private Set<GridNode> pending;

    public GridValidationBatcher() {
    }

    private static GridValidationBatcher instance() {
        com.github.aeddddd.ae2enhanced.runtime.ServerScope scope = RuntimeScopes.currentServer().orElse(null);
        if (scope == null || !scope.acceptingWork()) {
            return null;
        }
        return scope.service(GridValidationBatcher.class, GridValidationBatcher::new);
    }

    public static void begin() {
        GridValidationBatcher batcher = instance();
        if (batcher != null) {
            batcher.beginBatch();
        }
    }

    public static boolean isBatching() {
        GridValidationBatcher batcher = instance();
        return batcher != null && batcher.batching();
    }

    public static void defer(GridNode node) {
        GridValidationBatcher batcher = instance();
        if (batcher == null) {
            throw new IllegalStateException("Cannot defer grid validation without a server scope");
        }
        batcher.deferNode(node);
    }

    public static void end(GridNode destroyedNode) {
        GridValidationBatcher batcher = instance();
        if (batcher != null) {
            batcher.endBatch(destroyedNode);
        }
    }

    private static void validateAll(Set<GridNode> nodes) {
        for (GridNode n : nodes) {
            ((GridNodeValidationAccess) n).ae2e$validateGrid();
        }
    }

    public void beginBatch() {
        depth++;
    }

    public boolean batching() {
        return depth > 0;
    }

    public void deferNode(GridNode node) {
        if (!batching()) {
            throw new IllegalStateException("Cannot defer grid validation outside a batch");
        }
        if (pending == null) {
            pending = new HashSet<>();
        }
        pending.add(node);
    }

    /**
     * 结束一层批处理；仅在最外层执行延迟的分裂检测。
     *
     * @param destroyedNode 正在被销毁的节点本身（无需验证，原版会为它临时创建
     *                      单节点网格再移除，属于无意义开销）
     */
    public void endBatch(GridNode destroyedNode) {
        if (depth <= 0) {
            depth = 0;
            pending = null;
            throw new IllegalStateException("Grid validation batch is not active");
        }
        if (--depth > 0) {
            return;
        }
        try {
            flush(destroyedNode);
        } finally {
            pending = null;
        }
    }

    @Override
    public void close() {
        depth = 0;
        pending = null;
    }

    public int batchDepth() {
        return depth;
    }

    public int pendingNodeCount() {
        return pending == null ? 0 : pending.size();
    }

    private void flush(GridNode destroyedNode) {
        Set<GridNode> nodes = pending;
        if (nodes == null) {
            return;
        }
        nodes.remove(destroyedNode);
        if (nodes.isEmpty()) {
            return;
        }

        IGrid grid = null;
        for (GridNode n : nodes) {
            grid = n.getGrid();
            if (grid != null) {
                break;
            }
        }
        if (grid == null) {
            validateAll(nodes);
            return;
        }

        // pivot 可能已被移出网格（如指向一个已分裂走的孤立叶子节点），此时退化为逐节点验证。
        IGridNode pivot = grid.getPivot();
        if (pivot == null || pivot.getGrid() != grid) {
            validateAll(nodes);
            return;
        }

        // 从 pivot 单次 BFS 收集主连通分量。
        final Set<IGridNode> visited = new HashSet<>();
        pivot.beginVisit(new IGridVisitor() {
            @Override
            public boolean visitNode(IGridNode n) {
                visited.add(n);
                return true;
            }
        });

        // 不在主分量中的前邻居：各自所在分量需要分裂为新网格。
        for (GridNode n : nodes) {
            if (!visited.contains(n)) {
                ((GridNodeValidationAccess) n).ae2e$validateGrid();
            }
        }
    }
}
