package com.github.aeddddd.ae2enhanced.api.dimension;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 个人维度地板样式的区块单元(世界生成样式的最小单元).
 *
 * <p>一个单元固定为 16×16 格(一个区块的水平范围),样式由若干单元按区块网格拼合而成.
 * 通过 {@link PersonalDimensionApi#registerFloorTile} 注册命名单元后,可用于构建组合样式.</p>
 */
public interface IFloorTile {

    /** 单元边长(格),恒为一个区块的水平边长. */
    int SIZE = 16;

    /**
     * 获取单元内局部坐标处的方块状态.
     *
     * @param localX 单元内 X 坐标(0-15)
     * @param localZ 单元内 Z 坐标(0-15)
     * @return 方块状态;返回 {@code null} 时由生成器回退为基岩
     */
    @Nullable
    BlockState getState(int localX, int localZ);
}
