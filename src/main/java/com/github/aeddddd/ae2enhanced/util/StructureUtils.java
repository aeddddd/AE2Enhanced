package com.github.aeddddd.ae2enhanced.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 多方块结构相关的通用工具方法。
 */
public final class StructureUtils {

    private StructureUtils() {
    }

    /**
     * 将相对坐标按指定水平朝向旋转。
     * <p>以 NORTH 为基准，向南、东、西旋转时分别做 180°、90°、-90° 水平旋转。</p>
     *
     * @param rel    相对坐标
     * @param facing 水平朝向
     * @return 旋转后的相对坐标
     */
    public static BlockPos rotate(BlockPos rel, Direction facing) {
        if (facing == Direction.NORTH) {
            return rel;
        }
        int x = rel.getX();
        int y = rel.getY();
        int z = rel.getZ();
        return switch (facing) {
            case SOUTH -> new BlockPos(-x, y, -z);
            case EAST -> new BlockPos(-z, y, x);
            case WEST -> new BlockPos(z, y, -x);
            default -> rel;
        };
    }
}
