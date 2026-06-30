package com.github.aeddddd.ae2enhanced.multiblock;

import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

/**
 * 可向 AE2 网络提供并行放大样板的控制器标记接口。
 */
public interface IPatternProviderHost extends IMultiblockController {

    /**
     * @return 当前可向网络提供的所有样板详情。
     */
    List<IPatternDetails> getAvailablePatterns();

    /**
     * 执行一个样板任务。
     *
     * @param pattern 被推送的样板（通常已被并行放大）
     * @param inputs  输入材料计数
     * @return 是否成功执行
     */
    boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs);

    /**
     * @return 当前是否忙碌。虚拟合成通常瞬时完成，返回 false。
     */
    boolean isBusy();

    /**
     * 当样板库存或并行上限变化时，通知接口节点刷新网络中的样板列表。
     */
    default void onPatternsChanged() {
    }
}
