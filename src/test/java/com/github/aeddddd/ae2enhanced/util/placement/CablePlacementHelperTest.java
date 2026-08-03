package com.github.aeddddd.ae2enhanced.util.placement;

import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CablePlacementHelper} 单元测试.
 * <p>placeCable 依赖 ME 网络与 PartHelper 运行时,无法单元测试；
 * 此处只覆盖纯几何逻辑 {@link CablePlacementHelper#calculatePath}.</p>
 */
class CablePlacementHelperTest {

    @Test
    void samePositionReturnsSinglePoint() {
        BlockPos pos = new BlockPos(3, 64, -7);
        assertThat(CablePlacementHelper.calculatePath(pos, pos)).containsExactly(pos);
    }

    @Test
    void pureXAxisPath() {
        List<BlockPos> path = CablePlacementHelper.calculatePath(
                new BlockPos(0, 0, 0), new BlockPos(3, 0, 0));
        assertThat(path).containsExactly(
                new BlockPos(1, 0, 0),
                new BlockPos(2, 0, 0),
                new BlockPos(3, 0, 0));
    }

    @Test
    void negativeDirectionPath() {
        List<BlockPos> path = CablePlacementHelper.calculatePath(
                new BlockPos(0, 0, 0), new BlockPos(-2, 0, 0));
        assertThat(path).containsExactly(
                new BlockPos(-1, 0, 0),
                new BlockPos(-2, 0, 0));
    }

    @Test
    void pathOrderIsXThenYThenZ() {
        // 曼哈顿路径按 X → Y → Z 顺序展开
        List<BlockPos> path = CablePlacementHelper.calculatePath(
                new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));
        assertThat(path).containsExactly(
                new BlockPos(1, 0, 0),
                new BlockPos(1, 1, 0),
                new BlockPos(1, 1, 1));
    }

    @Test
    void pathOrderWithNegativeComponents() {
        List<BlockPos> path = CablePlacementHelper.calculatePath(
                new BlockPos(0, 0, 0), new BlockPos(-1, -2, -1));
        assertThat(path).containsExactly(
                new BlockPos(-1, 0, 0),
                new BlockPos(-1, -1, 0),
                new BlockPos(-1, -2, 0),
                new BlockPos(-1, -2, -1));
    }

    @Test
    void pathLengthIsManhattanDistance() {
        BlockPos start = new BlockPos(1, 2, 3);
        BlockPos end = new BlockPos(-4, 8, 0);
        List<BlockPos> path = CablePlacementHelper.calculatePath(start, end);
        assertThat(path).hasSize(5 + 6 + 3);
        // 路径终点即 end,且不含起点
        assertThat(path.get(path.size() - 1)).isEqualTo(end);
        assertThat(path).doesNotContain(start);
        // 相邻步进差为 1（连续路径）
        BlockPos prev = start;
        for (BlockPos step : path) {
            assertThat(step.distManhattan(prev)).isEqualTo(1);
            prev = step;
        }
    }
}
