package com.github.aeddddd.ae2enhanced.pathing;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.util.AEPartLocation;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import appeng.tile.networking.TileController;

/**
 * 从 AE2 PR #8285 移植的快速频道路径计算。
 *
 * <p>算法分为两阶段：</p>
 * <ol>
 *   <li>分层 BFS：从 Controller 出发建立到所有图元的父节点树，并尝试为需要频道的节点分配频道。
 *      利用 {@link IEnhancedPathItem#ae2enhanced$getHighestSimilarAncestor()} 跳过中间容量检查。</li>
 *   <li>迭代 DFS：汇总子树频道数到每个节点/连接。</li>
 * </ol>
 */
public class EnhancedPathingCalculation {

    private static final Object SUBTREE_END = new Object();

    private final IGrid grid;
    private final Set<GridNode> multiblocksWithChannel = new HashSet<>();
    @SuppressWarnings("unchecked")
    private final Queue<IPathItem>[] queues = new Queue[] {
            new ArrayDeque<>(), // 0: dense cable
            new ArrayDeque<>(), // 1: normal cable
            new ArrayDeque<>()  // 2: device/other
    };
    private final Set<IPathItem> visited = new HashSet<>();
    private final Map<IPathItem, Integer> channelBottlenecks = new IdentityHashMap<>();
    private final Set<IPathItem> channelNodes = new HashSet<>();

    private int channelsInUse = 0;
    private int channelsByBlocks = 0;

    public EnhancedPathingCalculation(IGrid grid) {
        this.grid = grid;

        for (IGridNode controllerNode : grid.getMachines(TileController.class)) {
            if (controllerNode == null) {
                continue;
            }
            IPathItem controllerPathItem = (IPathItem) controllerNode;
            this.visited.add(controllerPathItem);

            for (IGridConnection gcc : controllerNode.getConnections()) {
                GridConnection gc = (GridConnection) gcc;
                IGridNode otherSide = gc.getOtherSide(controllerNode);
                if (otherSide.getMachine() instanceof TileController) {
                    continue;
                }
                this.enqueue(gc, 0);
                gc.setControllerRoute((GridNode) controllerNode, true);
            }
        }
    }

    private void enqueue(IPathItem pathItem, int queueIndex) {
        this.visited.add(pathItem);

        int possibleIndex;
        if (pathItem instanceof GridConnection) {
            possibleIndex = 0;
        } else {
            EnumSet<GridFlags> flags = pathItem.getFlags();
            if (flags.contains(GridFlags.DENSE_CAPACITY)) {
                possibleIndex = 0;
            } else if (flags.contains(GridFlags.PREFERRED)) {
                possibleIndex = 1;
            } else {
                possibleIndex = 2;
            }
        }

        int index = Math.max(possibleIndex, queueIndex);
        this.queues[index].add(pathItem);
    }

    public void compute() {
        // BFS pass
        for (int i = 0; i < this.queues.length; i++) {
            this.processQueue(this.queues[i], i);
        }

        // DFS pass
        this.propagateAssignments();
    }

    private void processQueue(Queue<IPathItem> oldOpen, int queueIndex) {
        while (!oldOpen.isEmpty()) {
            IPathItem current = oldOpen.poll();
            for (IPathItem pi : current.getPossibleOptions()) {
                if (this.visited.contains(pi)) {
                    continue;
                }

                // 设置 BFS 父节点。
                pi.setControllerRoute(current, true);

                EnumSet<GridFlags> flags = pi.getFlags();
                if (flags.contains(GridFlags.REQUIRE_CHANNEL)) {
                    if (!this.multiblocksWithChannel.contains(pi)) {
                        boolean worked = this.tryUseChannel((GridNode) pi);

                        if (worked && flags.contains(GridFlags.MULTIBLOCK)) {
                            IGridMultiblock multiblock = (IGridMultiblock) ((IGridNode) pi).getGridBlock();
                            if (multiblock != null) {
                                Iterator<IGridNode> it = multiblock.getMultiblockNodes();
                                while (it.hasNext()) {
                                    IGridNode other = it.next();
                                    if (other != pi) {
                                        this.multiblocksWithChannel.add((GridNode) other);
                                    }
                                }
                            }
                        }
                    }
                }

                this.enqueue(pi, queueIndex);
            }
        }
    }

    private boolean tryUseChannel(GridNode start) {
        IEnhancedPathItem enhancedStart = (IEnhancedPathItem) start;
        if (start.getFlags().contains(GridFlags.COMPRESSED_CHANNEL)
                && !enhancedStart.ae2enhanced$getSubtreeAllowsCompressedChannels()) {
            return false;
        }

        // 检查路径容量。
        GridNode pi = start;
        while (pi != null) {
            int used = this.channelBottlenecks.getOrDefault(pi, 0);
            if (used >= ((IEnhancedPathItem) pi).ae2enhanced$getMaxChannels()) {
                return false;
            }
            pi = ((IEnhancedPathItem) pi).ae2enhanced$getHighestSimilarAncestor();
        }

        // 分配频道。
        pi = start;
        while (pi != null) {
            this.channelBottlenecks.merge(pi, 1, Integer::sum);
            pi = ((IEnhancedPathItem) pi).ae2enhanced$getHighestSimilarAncestor();
        }

        this.channelNodes.add(start);
        return true;
    }

    private void propagateAssignments() {
        ArrayDeque<Object> stack = new ArrayDeque<>();
        Set<IPathItem> controllerNodes = new HashSet<>();

        for (IGridNode controllerNode : this.grid.getMachines(TileController.class)) {
            if (controllerNode == null) {
                continue;
            }
            IPathItem controllerPathItem = (IPathItem) controllerNode;
            controllerNodes.add(controllerPathItem);

            for (IGridConnection gcc : controllerNode.getConnections()) {
                GridConnection gc = (GridConnection) gcc;
                IGridNode otherSide = gc.getOtherSide(controllerNode);
                if (!(otherSide.getMachine() instanceof TileController)) {
                    stack.addLast(gc);
                }
            }
        }

        while (!stack.isEmpty()) {
            Object current = stack.peekLast();
            if (current == SUBTREE_END) {
                stack.removeLast();
                IPathItem item = (IPathItem) stack.removeLast();

                if (item instanceof GridNode) {
                    boolean hasChannel = this.channelNodes.contains(item);
                    int propagated = ((IEnhancedPathItem) item).ae2enhanced$propagateChannelsUpwards(hasChannel);
                    this.channelsByBlocks += propagated;
                    if (hasChannel) {
                        this.channelsInUse++;
                    }
                } else {
                    this.channelsByBlocks += ((IEnhancedPathItem) item).ae2enhanced$propagateChannelsUpwards(false);
                }
            } else {
                stack.addLast(SUBTREE_END);
                IPathItem item = (IPathItem) current;
                for (IPathItem pi : item.getPossibleOptions()) {
                    if (!controllerNodes.contains(pi) && pi.getControllerRoute() == item) {
                        stack.addLast(pi);
                    }
                }
            }
        }

        // 给 multiblock 中已获得过频道的其他节点也加 1。
        for (GridNode multiblockNode : this.multiblocksWithChannel) {
            ((IPathItem) multiblockNode).incrementChannelCount(1);
        }
    }

    public int getChannelsInUse() {
        return this.channelsInUse;
    }

    public int getChannelsByBlocks() {
        return this.channelsByBlocks;
    }
}
