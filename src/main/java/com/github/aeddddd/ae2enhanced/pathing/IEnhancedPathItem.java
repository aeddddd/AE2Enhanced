package com.github.aeddddd.ae2enhanced.pathing;

import javax.annotation.Nullable;

import appeng.me.GridNode;

/**
 * 扩展 {@link appeng.me.pathfinding.IPathItem}，为 PR #8285 的快速频道路径算法提供额外状态和方法。
 *
 * <p>由 {@link com.github.aeddddd.ae2enhanced.mixin.late.ae2.MixinGridNode} 与
 * {@link com.github.aeddddd.ae2enhanced.mixin.late.ae2.MixinGridConnection} 实现。</p>
 */
public interface IEnhancedPathItem {

    /**
     * AdHoc 网络下直接设置本图元上通过的频道数。
     */
    void ae2enhanced$setAdHocChannels(int channels);

    /**
     * 获取当前节点在到 Controller 路径上的“最高同类祖先”。
     * 用于跳过中间节点，直接检查容量瓶颈。
     *
     * @return 祖先节点；若父节点即 Controller 则返回 null
     */
    @Nullable
    GridNode ae2enhanced$getHighestSimilarAncestor();

    /**
     * 当前子树是否允许承载致密（压缩）频道。
     */
    boolean ae2enhanced$getSubtreeAllowsCompressedChannels();

    /**
     * DFS 阶段把子树的已用频道数汇总到本节点。
     *
     * @param consumesChannel 本节点自身是否消费一个频道
     * @return 汇总后的已用频道数
     */
    int ae2enhanced$propagateChannelsUpwards(boolean consumesChannel);

    /**
     * 获取以本节点为根的子树中，到 Controller 路径上的最小最大频道数。
     */
    int ae2enhanced$getSubtreeMaxChannels();

    /**
     * 获取本图元最大可承载频道数。
     */
    int ae2enhanced$getMaxChannels();

    /**
     * 获取当前路径计算阶段的工作态已用频道数。
     */
    int ae2enhanced$getUsedChannels();
}
