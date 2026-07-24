package com.github.aeddddd.ae2enhanced.util;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 多方块结构相关的通用工具方法.
 */
public final class StructureUtils {

    private StructureUtils() {
    }

    /**
     * 计算给定相对坐标集合在指定朝向下的包围盒.
     *
     * @param relPositions 结构相对坐标集合
     * @param facing 控制器朝向
     * @return float[6] {minX, minY, minZ, maxX, maxY, maxZ}
     */
    public static float[] computeBounds(Set<BlockPos> relPositions, Direction facing) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos rel : relPositions) {
            BlockPos rot = rotate(rel, facing);
            minX = Math.min(minX, rot.getX());
            minY = Math.min(minY, rot.getY());
            minZ = Math.min(minZ, rot.getZ());
            maxX = Math.max(maxX, rot.getX());
            maxY = Math.max(maxY, rot.getY());
            maxZ = Math.max(maxZ, rot.getZ());
        }

        return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    /**
     * 将相对坐标按指定水平朝向旋转.
     * <p>以 NORTH 为基准,向南、东、西旋转时分别做 180°、90°、-90° 水平旋转.</p>
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
