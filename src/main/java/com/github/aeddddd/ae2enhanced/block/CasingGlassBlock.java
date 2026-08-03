package com.github.aeddddd.ae2enhanced.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 外壳玻璃.
 * <p>超因果计算核心外壳的透明观察窗,主体完全透明、仅边框可见,
 * 使用连接纹理合并相邻玻璃的边框.同样作为网格节点参与 ME 网络.</p>
 */
public class CasingGlassBlock extends ComputationCasingBlock {

    public CasingGlassBlock(Properties properties) {
        super(properties);
    }

    /**
     * 剔除同类玻璃之间的相邻面（同原版玻璃）,
     * 否则透明面会透出内部面的边框,形成接缝.
     */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return adjacentState.getBlock() instanceof CasingGlassBlock
                || super.skipRendering(state, adjacentState, side);
    }
}
