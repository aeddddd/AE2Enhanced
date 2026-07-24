package com.github.aeddddd.ae2enhanced.api.dimension;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 个人维度地板样式(世界生成样式)对外 API 接口.
 *
 * <p>实现按世界坐标返回地板方块状态;生成器按单元尺寸对全图平铺,实现无需关心边界.
 * 通过 {@link PersonalDimensionApi#registerFloorPreset} 注册命名样式后,
 * 整合包/用户可在 config 的 {@code personalDimension.presetPath} 中以
 * {@code "namespace:path"} 形式引用该样式.</p>
 */
public interface IFloorPreset {

    /**
     * 平铺单元宽度(X 方向,格),必须大于 0.
     */
    int getWidth();

    /**
     * 平铺单元深度(Z 方向,格),必须大于 0.
     */
    int getDepth();

    /**
     * 获取世界坐标处的地板方块状态.
     *
     * @param worldX 世界 X 坐标
     * @param worldZ 世界 Z 坐标
     * @return 地板方块状态;返回 {@code null} 时由生成器回退为基岩
     */
    @Nullable
    BlockState getState(int worldX, int worldZ);
}
