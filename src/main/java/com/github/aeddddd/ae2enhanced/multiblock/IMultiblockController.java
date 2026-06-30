package com.github.aeddddd.ae2enhanced.multiblock;

import net.minecraft.core.BlockPos;

/**
 * 多方块控制器标记接口。
 * <p>所有控制器方块实体（超维度、装配、计算核心）均实现此接口，供通用 ME 接口查找与委托。</p>
 */
public interface IMultiblockController {

    /**
     * @return 多方块是否已成形。
     */
    boolean isFormed();

    /**
     * @return 控制器所在位置。
     */
    BlockPos getControllerPos();

    /**
     * 结构装配时调用，记录某个接口位置属于本控制器。
     */
    void attachInterface(BlockPos interfacePos);

    /**
     * 结构拆解时调用，移除接口位置记录。
     */
    void detachInterface(BlockPos interfacePos);
}
