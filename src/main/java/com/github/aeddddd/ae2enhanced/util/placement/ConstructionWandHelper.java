package com.github.aeddddd.ae2enhanced.util.placement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 完全复刻 Construction Wand 的批量放置位置计算。
 *
 * Construction Wand 规则（Construction core + 方向锁）：
 * 1. 右键点击已存在方块的一个面。
 * 2. 在该面法向方向上，把与该方块同类型的连续区域整体向外延伸一层。
 * 3. 仅对空气或可替换方块进行填充。
 * 4. 最大 512 个方块。
 * 5. 方向锁（Horizontal/Vertical/N-S/E-W/No lock）限制在点击面平面上的扩展方向。
 */
public final class ConstructionWandHelper {

    public static final int MAX_BLOCKS = PlacementConfig.BULK_MAX_BLOCKS;

    private ConstructionWandHelper() {}

    /**
     * 计算批量放置位置。
     *
     * @param level        世界
     * @param clickedPos   被点击方块位置
     * @param side         被点击面
     * @param restriction  方向锁
     * @return 可放置位置列表（按铺设顺序）
     */
    public static List<BlockPos> calculatePositions(Level level, BlockPos clickedPos, Direction side,
            PlacementRestriction restriction) {
        List<BlockPos> result = new ArrayList<>();
        if (!level.isLoaded(clickedPos)) return result;

        BlockState anchorState = level.getBlockState(clickedPos);
        Block anchorBlock = anchorState.getBlock();
        if (anchorState.isAir()) return result;

        Set<BlockPos> faceRegion = findFaceRegion(level, clickedPos, side, anchorBlock, restriction);
        if (faceRegion.isEmpty()) return result;

        for (BlockPos source : faceRegion) {
            BlockPos target = source.relative(side);
            if (canPlaceBlockAt(level, target)) {
                result.add(target);
                if (result.size() >= MAX_BLOCKS) break;
            }
        }

        return result;
    }

    /**
     * 在点击面所在的平面上，找到与锚点方块类型相同且连续的所有方块位置。
     */
    private static Set<BlockPos> findFaceRegion(Level level, BlockPos clickedPos, Direction faceNormal,
            Block anchorBlock, PlacementRestriction restriction) {
        Set<BlockPos> region = new LinkedHashSet<>();
        Set<BlockPos> visited = new LinkedHashSet<>();
        java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();

        queue.offer(clickedPos);
        visited.add(clickedPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!isMatchingBlock(level, current, anchorBlock)) continue;
            region.add(current);

            for (Direction dir : getPlaneDirections(faceNormal, restriction)) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        return region;
    }

    private static boolean isMatchingBlock(Level level, BlockPos pos, Block anchorBlock) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() == anchorBlock;
    }

    private static boolean canPlaceBlockAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    private static Direction[] getPlaneDirections(Direction normal, PlacementRestriction restriction) {
        Direction[] plane;
        switch (normal.getAxis()) {
            case Y:
                plane = new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
                break;
            case X:
                plane = new Direction[] { Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH };
                break;
            case Z:
            default:
                plane = new Direction[] { Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST };
                break;
        }
        List<Direction> filtered = new ArrayList<>();
        for (Direction dir : plane) {
            if (restriction.allows(dir)) {
                filtered.add(dir);
            }
        }
        return filtered.toArray(new Direction[0]);
    }
}
