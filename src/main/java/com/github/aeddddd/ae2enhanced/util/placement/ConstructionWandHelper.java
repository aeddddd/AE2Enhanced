package com.github.aeddddd.ae2enhanced.util.placement;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
     * @param world        世界
     * @param clickedPos   被点击方块位置
     * @param side         被点击面
     * @param restriction  方向锁
     * @return 可放置位置列表（按铺设顺序）
     */
    public static List<BlockPos> calculatePositions(World world, BlockPos clickedPos, EnumFacing side, PlacementRestriction restriction) {
        List<BlockPos> result = new ArrayList<>();
        if (!world.isBlockLoaded(clickedPos)) return result;

        IBlockState anchorState = world.getBlockState(clickedPos);
        Block anchorBlock = anchorState.getBlock();
        if (anchorBlock.isAir(anchorState, world, clickedPos)) return result;

        Set<BlockPos> faceRegion = findFaceRegion(world, clickedPos, side, anchorBlock, restriction);
        if (faceRegion.isEmpty()) return result;

        for (BlockPos source : faceRegion) {
            BlockPos target = source.offset(side);
            if (canPlaceBlockAt(world, target)) {
                result.add(target);
                if (result.size() >= MAX_BLOCKS) break;
            }
        }

        return result;
    }

    /**
     * 在点击面所在的平面上，找到与锚点方块类型相同且连续的所有方块位置。
     */
    private static Set<BlockPos> findFaceRegion(World world, BlockPos clickedPos, EnumFacing faceNormal, Block anchorBlock, PlacementRestriction restriction) {
        Set<BlockPos> region = new LinkedHashSet<>();
        Set<BlockPos> visited = new LinkedHashSet<>();
        java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();

        queue.offer(clickedPos);
        visited.add(clickedPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!isMatchingBlock(world, current, anchorBlock)) continue;
            region.add(current);

            for (EnumFacing dir : getPlaneDirections(faceNormal, restriction)) {
                BlockPos neighbor = current.offset(dir);
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        return region;
    }

    private static boolean isMatchingBlock(World world, BlockPos pos, Block anchorBlock) {
        if (!world.isBlockLoaded(pos)) return false;
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() == anchorBlock;
    }

    private static boolean canPlaceBlockAt(World world, BlockPos pos) {
        return world.isAirBlock(pos) || world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }

    private static EnumFacing[] getPlaneDirections(EnumFacing normal, PlacementRestriction restriction) {
        EnumFacing[] plane;
        switch (normal.getAxis()) {
            case Y:
                plane = new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};
                break;
            case X:
                plane = new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH};
                break;
            case Z:
            default:
                plane = new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN, EnumFacing.EAST, EnumFacing.WEST};
                break;
        }
        java.util.List<EnumFacing> filtered = new java.util.ArrayList<>();
        for (EnumFacing dir : plane) {
            if (restriction.allows(dir)) {
                filtered.add(dir);
            }
        }
        return filtered.toArray(new EnumFacing[0]);
    }
}
