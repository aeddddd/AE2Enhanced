package com.github.aeddddd.ae2enhanced.util;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link StructureUtils} 单元测试。
 */
class StructureUtilsTest {

    private static final BlockPos POS = new BlockPos(1, 2, 3);

    @Test
    void testRotateNorthIsIdentity() {
        // NORTH 为基准方向，坐标不变
        assertEquals(POS, StructureUtils.rotate(POS, Direction.NORTH));
    }

    @Test
    void testRotateSouth() {
        // SOUTH：180° 水平旋转，x/z 同时取反
        assertEquals(new BlockPos(-1, 2, -3), StructureUtils.rotate(POS, Direction.SOUTH));
    }

    @Test
    void testRotateEast() {
        // EAST：(x, y, z) -> (-z, y, x)
        assertEquals(new BlockPos(-3, 2, 1), StructureUtils.rotate(POS, Direction.EAST));
    }

    @Test
    void testRotateWest() {
        // WEST：(x, y, z) -> (z, y, -x)
        assertEquals(new BlockPos(3, 2, -1), StructureUtils.rotate(POS, Direction.WEST));
    }

    @Test
    void testRotateVerticalDirectionsReturnOriginal() {
        // UP/DOWN 不是水平朝向，按原样返回
        assertEquals(POS, StructureUtils.rotate(POS, Direction.UP));
        assertEquals(POS, StructureUtils.rotate(POS, Direction.DOWN));
    }

    @Test
    void testRotatePreservesY() {
        BlockPos pos = new BlockPos(5, -17, 9);
        for (Direction direction : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST,
                Direction.WEST }) {
            assertEquals(-17, StructureUtils.rotate(pos, direction).getY());
        }
    }

    @Test
    void testRotateComposition() {
        // EAST 旋转两次等价于 SOUTH 一次
        BlockPos eastTwice = StructureUtils.rotate(StructureUtils.rotate(POS, Direction.EAST), Direction.EAST);
        assertEquals(StructureUtils.rotate(POS, Direction.SOUTH), eastTwice);

        // WEST 旋转两次同样等价于 SOUTH 一次
        BlockPos westTwice = StructureUtils.rotate(StructureUtils.rotate(POS, Direction.WEST), Direction.WEST);
        assertEquals(StructureUtils.rotate(POS, Direction.SOUTH), westTwice);

        // EAST + WEST 相互抵消
        BlockPos eastThenWest = StructureUtils.rotate(StructureUtils.rotate(POS, Direction.EAST), Direction.WEST);
        assertEquals(POS, eastThenWest);
    }

    @Test
    void testRotateFullCircleReturnsOriginal() {
        // 同一方向旋转四次回到原点
        BlockPos rotated = POS;
        for (int i = 0; i < 4; i++) {
            rotated = StructureUtils.rotate(rotated, Direction.EAST);
        }
        assertEquals(POS, rotated);
    }

    @Test
    void testRotateZeroPosition() {
        BlockPos zero = BlockPos.ZERO;
        for (Direction direction : Direction.values()) {
            assertEquals(zero, StructureUtils.rotate(zero, direction));
        }
    }
}
