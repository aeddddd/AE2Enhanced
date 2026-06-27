package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.block.BlockAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class AssemblyStructure {

    // 坐标相对于几何中心 (0,0,0)
    public static final Set<BlockPos> CORE_SET;
    public static final Set<BlockPos> PART1_SET;
    public static final Set<BlockPos> PART2_SET;
    public static final Set<BlockPos> PART3_SET;
    public static final Set<BlockPos> PART4_SET;
    public static final Set<BlockPos> ALL_SET;

    static {
        Set<BlockPos> core = new HashSet<>();
        core.add(new BlockPos(0, 0, -7));
        CORE_SET = Collections.unmodifiableSet(core);

        Set<BlockPos> part1 = new HashSet<>();
        part1.add(new BlockPos(-7, 0, 0));
        part1.add(new BlockPos(7, 0, 0));
        part1.add(new BlockPos(0, 0, 7));
        PART1_SET = Collections.unmodifiableSet(part1);

        Set<BlockPos> part2 = new HashSet<>();
        part2.add(new BlockPos(-4, -1, -6));
        part2.add(new BlockPos(-4, -1, 6));
        part2.add(new BlockPos(-4, 0, -6));
        part2.add(new BlockPos(-4, 0, 6));
        part2.add(new BlockPos(-4, 1, -6));
        part2.add(new BlockPos(-4, 1, 6));
        part2.add(new BlockPos(-3, -1, -6));
        part2.add(new BlockPos(-3, -1, 6));
        part2.add(new BlockPos(-3, 0, -6));
        part2.add(new BlockPos(-3, 0, 6));
        part2.add(new BlockPos(-3, 1, -6));
        part2.add(new BlockPos(-3, 1, 6));
        part2.add(new BlockPos(-2, -1, -6));
        part2.add(new BlockPos(-2, -1, 6));
        part2.add(new BlockPos(-2, 0, -6));
        part2.add(new BlockPos(-2, 0, 6));
        part2.add(new BlockPos(-2, 1, -6));
        part2.add(new BlockPos(-2, 1, 6));
        part2.add(new BlockPos(2, -1, -6));
        part2.add(new BlockPos(2, -1, 6));
        part2.add(new BlockPos(2, 0, -6));
        part2.add(new BlockPos(2, 0, 6));
        part2.add(new BlockPos(2, 1, -6));
        part2.add(new BlockPos(2, 1, 6));
        part2.add(new BlockPos(3, -1, -6));
        part2.add(new BlockPos(3, -1, 6));
        part2.add(new BlockPos(3, 0, -6));
        part2.add(new BlockPos(3, 0, 6));
        part2.add(new BlockPos(3, 1, -6));
        part2.add(new BlockPos(3, 1, 6));
        part2.add(new BlockPos(4, -1, -6));
        part2.add(new BlockPos(4, -1, 6));
        part2.add(new BlockPos(4, 0, -6));
        part2.add(new BlockPos(4, 0, 6));
        part2.add(new BlockPos(4, 1, -6));
        part2.add(new BlockPos(4, 1, 6));
        part2.add(new BlockPos(-6, -1, -4));
        part2.add(new BlockPos(-6, -1, -3));
        part2.add(new BlockPos(-6, -1, -2));
        part2.add(new BlockPos(-6, -1, 2));
        part2.add(new BlockPos(-6, -1, 3));
        part2.add(new BlockPos(-6, -1, 4));
        part2.add(new BlockPos(-6, 0, -4));
        part2.add(new BlockPos(-6, 0, -3));
        part2.add(new BlockPos(-6, 0, -2));
        part2.add(new BlockPos(-6, 0, 2));
        part2.add(new BlockPos(-6, 0, 3));
        part2.add(new BlockPos(-6, 0, 4));
        part2.add(new BlockPos(-6, 1, -4));
        part2.add(new BlockPos(-6, 1, -3));
        part2.add(new BlockPos(-6, 1, -2));
        part2.add(new BlockPos(-6, 1, 2));
        part2.add(new BlockPos(-6, 1, 3));
        part2.add(new BlockPos(-6, 1, 4));
        part2.add(new BlockPos(6, -1, -4));
        part2.add(new BlockPos(6, -1, -3));
        part2.add(new BlockPos(6, -1, -2));
        part2.add(new BlockPos(6, -1, 2));
        part2.add(new BlockPos(6, -1, 3));
        part2.add(new BlockPos(6, -1, 4));
        part2.add(new BlockPos(6, 0, -4));
        part2.add(new BlockPos(6, 0, -3));
        part2.add(new BlockPos(6, 0, -2));
        part2.add(new BlockPos(6, 0, 2));
        part2.add(new BlockPos(6, 0, 3));
        part2.add(new BlockPos(6, 0, 4));
        part2.add(new BlockPos(6, 1, -4));
        part2.add(new BlockPos(6, 1, -3));
        part2.add(new BlockPos(6, 1, -2));
        part2.add(new BlockPos(6, 1, 2));
        part2.add(new BlockPos(6, 1, 3));
        part2.add(new BlockPos(6, 1, 4));
        part2.add(new BlockPos(-5, -1, -5));
        part2.add(new BlockPos(-5, -1, -4));
        part2.add(new BlockPos(-5, -1, 4));
        part2.add(new BlockPos(-5, -1, 5));
        part2.add(new BlockPos(-5, 0, -5));
        part2.add(new BlockPos(-5, 0, -4));
        part2.add(new BlockPos(-5, 0, 4));
        part2.add(new BlockPos(-5, 0, 5));
        part2.add(new BlockPos(-5, 1, -5));
        part2.add(new BlockPos(-5, 1, -4));
        part2.add(new BlockPos(-5, 1, 4));
        part2.add(new BlockPos(-5, 1, 5));
        part2.add(new BlockPos(5, -1, -5));
        part2.add(new BlockPos(5, -1, -4));
        part2.add(new BlockPos(5, -1, 4));
        part2.add(new BlockPos(5, -1, 5));
        part2.add(new BlockPos(5, 0, -5));
        part2.add(new BlockPos(5, 0, -4));
        part2.add(new BlockPos(5, 0, 4));
        part2.add(new BlockPos(5, 0, 5));
        part2.add(new BlockPos(5, 1, -5));
        part2.add(new BlockPos(5, 1, -4));
        part2.add(new BlockPos(5, 1, 4));
        part2.add(new BlockPos(5, 1, 5));
        part2.add(new BlockPos(-7, -1, -2));
        part2.add(new BlockPos(-7, -1, -1));
        part2.add(new BlockPos(-7, -1, 1));
        part2.add(new BlockPos(-7, -1, 2));
        part2.add(new BlockPos(-7, 0, -2));
        part2.add(new BlockPos(-7, 0, -1));
        part2.add(new BlockPos(-7, 0, 1));
        part2.add(new BlockPos(-7, 0, 2));
        part2.add(new BlockPos(-7, 1, -2));
        part2.add(new BlockPos(-7, 1, -1));
        part2.add(new BlockPos(-7, 1, 1));
        part2.add(new BlockPos(-7, 1, 2));
        part2.add(new BlockPos(7, -1, -2));
        part2.add(new BlockPos(7, -1, -1));
        part2.add(new BlockPos(7, -1, 1));
        part2.add(new BlockPos(7, -1, 2));
        part2.add(new BlockPos(7, 0, -2));
        part2.add(new BlockPos(7, 0, -1));
        part2.add(new BlockPos(7, 0, 1));
        part2.add(new BlockPos(7, 0, 2));
        part2.add(new BlockPos(7, 1, -2));
        part2.add(new BlockPos(7, 1, -1));
        part2.add(new BlockPos(7, 1, 1));
        part2.add(new BlockPos(7, 1, 2));
        part2.add(new BlockPos(-2, -1, -7));
        part2.add(new BlockPos(-2, -1, 7));
        part2.add(new BlockPos(-2, 1, -7));
        part2.add(new BlockPos(-2, 1, 7));
        part2.add(new BlockPos(-1, -1, -7));
        part2.add(new BlockPos(-1, -1, 7));
        part2.add(new BlockPos(-1, 1, -7));
        part2.add(new BlockPos(-1, 1, 7));
        part2.add(new BlockPos(0, -1, -7));
        part2.add(new BlockPos(0, -1, 7));
        part2.add(new BlockPos(0, 1, -7));
        part2.add(new BlockPos(0, 1, 7));
        part2.add(new BlockPos(1, -1, -7));
        part2.add(new BlockPos(1, -1, 7));
        part2.add(new BlockPos(1, 1, -7));
        part2.add(new BlockPos(1, 1, 7));
        part2.add(new BlockPos(2, -1, -7));
        part2.add(new BlockPos(2, -1, 7));
        part2.add(new BlockPos(2, 1, -7));
        part2.add(new BlockPos(2, 1, 7));
        part2.add(new BlockPos(-4, -1, -5));
        part2.add(new BlockPos(-4, -1, 5));
        part2.add(new BlockPos(-4, 0, -5));
        part2.add(new BlockPos(-4, 0, 5));
        part2.add(new BlockPos(-4, 1, -5));
        part2.add(new BlockPos(-4, 1, 5));
        part2.add(new BlockPos(4, -1, -5));
        part2.add(new BlockPos(4, -1, 5));
        part2.add(new BlockPos(4, 0, -5));
        part2.add(new BlockPos(4, 0, 5));
        part2.add(new BlockPos(4, 1, -5));
        part2.add(new BlockPos(4, 1, 5));
        part2.add(new BlockPos(-3, 0, -7));
        part2.add(new BlockPos(-3, 0, 7));
        part2.add(new BlockPos(-2, 0, -7));
        part2.add(new BlockPos(-2, 0, 7));
        part2.add(new BlockPos(-1, 0, -7));
        part2.add(new BlockPos(-1, 0, 7));
        part2.add(new BlockPos(1, 0, -7));
        part2.add(new BlockPos(1, 0, 7));
        part2.add(new BlockPos(2, 0, -7));
        part2.add(new BlockPos(2, 0, 7));
        part2.add(new BlockPos(3, 0, -7));
        part2.add(new BlockPos(3, 0, 7));
        part2.add(new BlockPos(-5, 0, -6));
        part2.add(new BlockPos(-5, 0, 6));
        part2.add(new BlockPos(5, 0, -6));
        part2.add(new BlockPos(5, 0, 6));
        part2.add(new BlockPos(-6, 0, -5));
        part2.add(new BlockPos(-6, 0, 5));
        part2.add(new BlockPos(6, 0, -5));
        part2.add(new BlockPos(6, 0, 5));
        part2.add(new BlockPos(-7, -1, 0));
        part2.add(new BlockPos(-7, 1, 0));
        part2.add(new BlockPos(7, -1, 0));
        part2.add(new BlockPos(7, 1, 0));
        part2.add(new BlockPos(-7, 0, -3));
        part2.add(new BlockPos(-7, 0, 3));
        part2.add(new BlockPos(7, 0, -3));
        part2.add(new BlockPos(7, 0, 3));
        PART2_SET = Collections.unmodifiableSet(part2);

        Set<BlockPos> part3 = new HashSet<>();
        part3.add(new BlockPos(-3, -2, -5));
        part3.add(new BlockPos(-3, -2, 5));
        part3.add(new BlockPos(-3, -1, -5));
        part3.add(new BlockPos(-3, -1, 5));
        part3.add(new BlockPos(-3, 1, -5));
        part3.add(new BlockPos(-3, 1, 5));
        part3.add(new BlockPos(-3, 2, -5));
        part3.add(new BlockPos(-3, 2, 5));
        part3.add(new BlockPos(-2, -2, -5));
        part3.add(new BlockPos(-2, -2, 5));
        part3.add(new BlockPos(-2, -1, -5));
        part3.add(new BlockPos(-2, -1, 5));
        part3.add(new BlockPos(-2, 1, -5));
        part3.add(new BlockPos(-2, 1, 5));
        part3.add(new BlockPos(-2, 2, -5));
        part3.add(new BlockPos(-2, 2, 5));
        part3.add(new BlockPos(2, -2, -5));
        part3.add(new BlockPos(2, -2, 5));
        part3.add(new BlockPos(2, -1, -5));
        part3.add(new BlockPos(2, -1, 5));
        part3.add(new BlockPos(2, 1, -5));
        part3.add(new BlockPos(2, 1, 5));
        part3.add(new BlockPos(2, 2, -5));
        part3.add(new BlockPos(2, 2, 5));
        part3.add(new BlockPos(3, -2, -5));
        part3.add(new BlockPos(3, -2, 5));
        part3.add(new BlockPos(3, -1, -5));
        part3.add(new BlockPos(3, -1, 5));
        part3.add(new BlockPos(3, 1, -5));
        part3.add(new BlockPos(3, 1, 5));
        part3.add(new BlockPos(3, 2, -5));
        part3.add(new BlockPos(3, 2, 5));
        part3.add(new BlockPos(-5, -2, -3));
        part3.add(new BlockPos(-5, -2, -2));
        part3.add(new BlockPos(-5, -2, 2));
        part3.add(new BlockPos(-5, -2, 3));
        part3.add(new BlockPos(-5, -1, -3));
        part3.add(new BlockPos(-5, -1, -2));
        part3.add(new BlockPos(-5, -1, 2));
        part3.add(new BlockPos(-5, -1, 3));
        part3.add(new BlockPos(-5, 1, -3));
        part3.add(new BlockPos(-5, 1, -2));
        part3.add(new BlockPos(-5, 1, 2));
        part3.add(new BlockPos(-5, 1, 3));
        part3.add(new BlockPos(-5, 2, -3));
        part3.add(new BlockPos(-5, 2, -2));
        part3.add(new BlockPos(-5, 2, 2));
        part3.add(new BlockPos(-5, 2, 3));
        part3.add(new BlockPos(5, -2, -3));
        part3.add(new BlockPos(5, -2, -2));
        part3.add(new BlockPos(5, -2, 2));
        part3.add(new BlockPos(5, -2, 3));
        part3.add(new BlockPos(5, -1, -3));
        part3.add(new BlockPos(5, -1, -2));
        part3.add(new BlockPos(5, -1, 2));
        part3.add(new BlockPos(5, -1, 3));
        part3.add(new BlockPos(5, 1, -3));
        part3.add(new BlockPos(5, 1, -2));
        part3.add(new BlockPos(5, 1, 2));
        part3.add(new BlockPos(5, 1, 3));
        part3.add(new BlockPos(5, 2, -3));
        part3.add(new BlockPos(5, 2, -2));
        part3.add(new BlockPos(5, 2, 2));
        part3.add(new BlockPos(5, 2, 3));
        part3.add(new BlockPos(-1, -2, -6));
        part3.add(new BlockPos(-1, -2, 6));
        part3.add(new BlockPos(-1, -1, -6));
        part3.add(new BlockPos(-1, -1, 6));
        part3.add(new BlockPos(-1, 1, -6));
        part3.add(new BlockPos(-1, 1, 6));
        part3.add(new BlockPos(-1, 2, -6));
        part3.add(new BlockPos(-1, 2, 6));
        part3.add(new BlockPos(0, -2, -6));
        part3.add(new BlockPos(0, -2, 6));
        part3.add(new BlockPos(0, -1, -6));
        part3.add(new BlockPos(0, -1, 6));
        part3.add(new BlockPos(0, 1, -6));
        part3.add(new BlockPos(0, 1, 6));
        part3.add(new BlockPos(0, 2, -6));
        part3.add(new BlockPos(0, 2, 6));
        part3.add(new BlockPos(1, -2, -6));
        part3.add(new BlockPos(1, -2, 6));
        part3.add(new BlockPos(1, -1, -6));
        part3.add(new BlockPos(1, -1, 6));
        part3.add(new BlockPos(1, 1, -6));
        part3.add(new BlockPos(1, 1, 6));
        part3.add(new BlockPos(1, 2, -6));
        part3.add(new BlockPos(1, 2, 6));
        part3.add(new BlockPos(-6, -2, -1));
        part3.add(new BlockPos(-6, -2, 0));
        part3.add(new BlockPos(-6, -2, 1));
        part3.add(new BlockPos(-6, -1, -1));
        part3.add(new BlockPos(-6, -1, 0));
        part3.add(new BlockPos(-6, -1, 1));
        part3.add(new BlockPos(-6, 1, -1));
        part3.add(new BlockPos(-6, 1, 0));
        part3.add(new BlockPos(-6, 1, 1));
        part3.add(new BlockPos(-6, 2, -1));
        part3.add(new BlockPos(-6, 2, 0));
        part3.add(new BlockPos(-6, 2, 1));
        part3.add(new BlockPos(6, -2, -1));
        part3.add(new BlockPos(6, -2, 0));
        part3.add(new BlockPos(6, -2, 1));
        part3.add(new BlockPos(6, -1, -1));
        part3.add(new BlockPos(6, -1, 0));
        part3.add(new BlockPos(6, -1, 1));
        part3.add(new BlockPos(6, 1, -1));
        part3.add(new BlockPos(6, 1, 0));
        part3.add(new BlockPos(6, 1, 1));
        part3.add(new BlockPos(6, 2, -1));
        part3.add(new BlockPos(6, 2, 0));
        part3.add(new BlockPos(6, 2, 1));
        part3.add(new BlockPos(-4, -2, -4));
        part3.add(new BlockPos(-4, -2, 4));
        part3.add(new BlockPos(-4, -1, -4));
        part3.add(new BlockPos(-4, -1, 4));
        part3.add(new BlockPos(-4, 1, -4));
        part3.add(new BlockPos(-4, 1, 4));
        part3.add(new BlockPos(-4, 2, -4));
        part3.add(new BlockPos(-4, 2, 4));
        part3.add(new BlockPos(4, -2, -4));
        part3.add(new BlockPos(4, -2, 4));
        part3.add(new BlockPos(4, -1, -4));
        part3.add(new BlockPos(4, -1, 4));
        part3.add(new BlockPos(4, 1, -4));
        part3.add(new BlockPos(4, 1, 4));
        part3.add(new BlockPos(4, 2, -4));
        part3.add(new BlockPos(4, 2, 4));
        PART3_SET = Collections.unmodifiableSet(part3);

        Set<BlockPos> part4 = new HashSet<>();
        part4.add(new BlockPos(-5, 0, -3));
        part4.add(new BlockPos(-5, 0, -2));
        part4.add(new BlockPos(-5, 0, 2));
        part4.add(new BlockPos(-5, 0, 3));
        part4.add(new BlockPos(5, 0, -3));
        part4.add(new BlockPos(5, 0, -2));
        part4.add(new BlockPos(5, 0, 2));
        part4.add(new BlockPos(5, 0, 3));
        part4.add(new BlockPos(-3, 0, -5));
        part4.add(new BlockPos(-3, 0, 5));
        part4.add(new BlockPos(-2, 0, -5));
        part4.add(new BlockPos(-2, 0, 5));
        part4.add(new BlockPos(2, 0, -5));
        part4.add(new BlockPos(2, 0, 5));
        part4.add(new BlockPos(3, 0, -5));
        part4.add(new BlockPos(3, 0, 5));
        part4.add(new BlockPos(-1, 0, -6));
        part4.add(new BlockPos(-1, 0, 6));
        part4.add(new BlockPos(0, 0, -6));
        part4.add(new BlockPos(0, 0, 6));
        part4.add(new BlockPos(1, 0, -6));
        part4.add(new BlockPos(1, 0, 6));
        part4.add(new BlockPos(-6, 0, -1));
        part4.add(new BlockPos(-6, 0, 0));
        part4.add(new BlockPos(-6, 0, 1));
        part4.add(new BlockPos(6, 0, -1));
        part4.add(new BlockPos(6, 0, 0));
        part4.add(new BlockPos(6, 0, 1));
        part4.add(new BlockPos(-4, 0, -4));
        part4.add(new BlockPos(-4, 0, 4));
        part4.add(new BlockPos(4, 0, -4));
        part4.add(new BlockPos(4, 0, 4));
        PART4_SET = Collections.unmodifiableSet(part4);

        Set<BlockPos> all = new HashSet<>();
        all.addAll(CORE_SET);
        all.addAll(PART1_SET);
        all.addAll(PART2_SET);
        all.addAll(PART3_SET);
        all.addAll(PART4_SET);
        ALL_SET = Collections.unmodifiableSet(all);
    }

    /**
     * 将默认朝向(北)的相对坐标按控制器朝向旋转.
     */
    public static BlockPos rotate(BlockPos rel, EnumFacing facing) {
        if (facing == EnumFacing.NORTH) return rel;
        int x = rel.getX();
        int y = rel.getY();
        int z = rel.getZ();
        switch (facing) {
            case SOUTH: return new BlockPos(-x, y, -z);
            case EAST:  return new BlockPos(-z, y, x);
            case WEST:  return new BlockPos(z, y, -x);
            default:    return rel;
        }
    }

    /**
     * 由控制器位置获取几何中心原点
     */
    public static BlockPos getOriginFromController(BlockPos controllerPos, EnumFacing facing) {
        return controllerPos.add(rotate(new BlockPos(0, 0, 7), facing));
    }

    private static EnumFacing getControllerFacing(World world, BlockPos controllerPos) {
        IBlockState state = world.getBlockState(controllerPos);
        if (state.getBlock() instanceof BlockAssemblyController) {
            return state.getValue(BlockAssemblyController.FACING);
        }
        return EnumFacing.NORTH;
    }

    /**
     * 验证结构完整性(优先检查 core 与 part1)
     */
    public static boolean validate(World world, BlockPos controllerPos) {
        EnumFacing facing = getControllerFacing(world, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);

        // 优先检查 core
        if (!checkBlock(world, origin, CORE_SET, BlockRegistry.ASSEMBLY_CONTROLLER, facing)) {
            return false;
        }

        // 优先检查 part1(ME 接口,结构完整性关键)
        if (!checkBlock(world, origin, PART1_SET, BlockRegistry.ASSEMBLY_ME_INTERFACE, facing)) {
            return false;
        }

        if (!checkBlock(world, origin, PART2_SET, BlockRegistry.ASSEMBLY_CASING, facing)) {
            return false;
        }
        if (!checkBlock(world, origin, PART3_SET, BlockRegistry.ASSEMBLY_INNER_WALL, facing)) {
            return false;
        }
        if (!checkBlock(world, origin, PART4_SET, BlockRegistry.ASSEMBLY_STABILIZER, facing)) {
            return false;
        }

        return true;
    }

    private static boolean checkBlock(World world, BlockPos origin, Set<BlockPos> relativeSet, Block expected, EnumFacing facing) {
        for (BlockPos rel : relativeSet) {
            BlockPos actual = origin.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) {
                continue; // chunk 未加载,保持当前状态,不判定为缺失
            }
            if (world.getBlockState(actual).getBlock() != expected) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取缺失方块清单(用于未组装 GUI 显示)
     */
    public static Map<Block, Integer> getMissingMap(World world, BlockPos controllerPos) {
        EnumFacing facing = getControllerFacing(world, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        Map<Block, Integer> missing = new LinkedHashMap<>();

        countMissing(world, origin, PART1_SET, BlockRegistry.ASSEMBLY_ME_INTERFACE, missing, facing);
        countMissing(world, origin, PART2_SET, BlockRegistry.ASSEMBLY_CASING, missing, facing);
        countMissing(world, origin, PART3_SET, BlockRegistry.ASSEMBLY_INNER_WALL, missing, facing);
        countMissing(world, origin, PART4_SET, BlockRegistry.ASSEMBLY_STABILIZER, missing, facing);

        return missing;
    }

    private static void countMissing(World world, BlockPos origin, Set<BlockPos> relativeSet, Block expected, Map<Block, Integer> missing, EnumFacing facing) {
        for (BlockPos rel : relativeSet) {
            BlockPos actual = origin.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) {
                continue; // chunk 未加载,不计入缺失
            }
            if (world.getBlockState(actual).getBlock() != expected) {
                missing.put(expected, missing.getOrDefault(expected, 0) + 1);
            }
        }
    }

    /**
     * 组装：通知 TileEntity 进入已组装状态,并更新 ME 接口 blockstate
     */
    public static void assemble(World world, BlockPos controllerPos) {
        if (world.isRemote) return;
        TileAssemblyController tile = getControllerTile(world, controllerPos);
        if (tile != null) {
            tile.assemble();
        }
        EnumFacing facing = getControllerFacing(world, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        updateMeInterfaceState(world, origin, true, controllerPos, facing);
    }

    /**
     * 解散：通知 TileEntity 进入未组装状态,并更新 ME 接口 blockstate
     */
    public static void disassemble(World world, BlockPos controllerPos) {
        if (world.isRemote) return;
        TileAssemblyController tile = getControllerTile(world, controllerPos);
        if (tile != null) {
            tile.disassemble();
        }
        EnumFacing facing = getControllerFacing(world, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        updateMeInterfaceState(world, origin, false, controllerPos, facing);
    }

    private static void updateMeInterfaceState(World world, BlockPos origin, boolean formed, BlockPos controllerPos, EnumFacing facing) {
        IBlockState state = BlockRegistry.ASSEMBLY_ME_INTERFACE.getDefaultState()
            .withProperty(com.github.aeddddd.ae2enhanced.block.BlockAssemblyMeInterface.FORMED, formed);
        for (BlockPos rel : PART1_SET) {
            BlockPos pos = origin.add(rotate(rel, facing));
            if (world.getBlockState(pos).getBlock() == BlockRegistry.ASSEMBLY_ME_INTERFACE) {
                world.setBlockState(pos, state);
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
                if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface) {
                    com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface me = (com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface) te;
                    me.setControllerPos(formed ? controllerPos : null);
                }
            }
        }
    }

    private static TileAssemblyController getControllerTile(World world, BlockPos pos) {
        // 注意：控制器在 origin + (0,0,-7),也就是 controllerPos 参数本身就是控制器位置
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        return te instanceof TileAssemblyController ? (TileAssemblyController) te : null;
    }

    /**
     * 创造模式：一键生成所有缺失方块
     */
    public static void placeMissingBlocks(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return;
        EnumFacing facing = getControllerFacing(world, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);

        placeBlocks(world, origin, PART1_SET, BlockRegistry.ASSEMBLY_ME_INTERFACE, facing);
        placeBlocks(world, origin, PART2_SET, BlockRegistry.ASSEMBLY_CASING, facing);
        placeBlocks(world, origin, PART3_SET, BlockRegistry.ASSEMBLY_INNER_WALL, facing);
        placeBlocks(world, origin, PART4_SET, BlockRegistry.ASSEMBLY_STABILIZER, facing);

        // 立即触发组装(跳过 20 tick 等待)
        assemble(world, controllerPos);
    }

    private static void placeBlocks(World world, BlockPos origin, Set<BlockPos> set, Block block, EnumFacing facing) {
        for (BlockPos rel : set) {
            BlockPos pos = origin.add(rotate(rel, facing));
            if (world.getBlockState(pos).getBlock() != block) {
                world.setBlockState(pos, block.getDefaultState());
            }
        }
    }

    /**
     * 生存模式：检查背包材料,足够则扣除并放置
     * @return 是否成功
     */
    public static boolean tryConsumeAndPlace(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return false;
        EnumFacing facing = getControllerFacing(world, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);

        Map<Block, Integer> missing = new LinkedHashMap<>();
        countMissing(world, origin, PART1_SET, BlockRegistry.ASSEMBLY_ME_INTERFACE, missing, facing);
        countMissing(world, origin, PART2_SET, BlockRegistry.ASSEMBLY_CASING, missing, facing);
        countMissing(world, origin, PART3_SET, BlockRegistry.ASSEMBLY_INNER_WALL, missing, facing);
        countMissing(world, origin, PART4_SET, BlockRegistry.ASSEMBLY_STABILIZER, missing, facing);

        if (missing.isEmpty()) {
            assemble(world, controllerPos);
            return true;
        }

        // 检查背包是否有足够材料
        net.minecraft.entity.player.InventoryPlayer inv = player.inventory;
        Map<Block, Integer> needed = new LinkedHashMap<>(missing);

        for (net.minecraft.item.ItemStack stack : inv.mainInventory) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                Block block = entry.getKey();
                if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(block)) {
                    int need = entry.getValue();
                    int have = stack.getCount();
                    if (have >= need) {
                        entry.setValue(0);
                    } else {
                        entry.setValue(need - have);
                    }
                    break;
                }
            }
        }

        for (int count : needed.values()) {
            if (count > 0) return false; // 材料不足
        }

        // 扣除材料
        for (Map.Entry<Block, Integer> entry : missing.entrySet()) {
            Block block = entry.getKey();
            int remaining = entry.getValue();
            net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
            for (int i = 0; i < inv.mainInventory.size() && remaining > 0; i++) {
                net.minecraft.item.ItemStack stack = inv.mainInventory.get(i);
                if (stack.getItem() == item) {
                    int take = Math.min(stack.getCount(), remaining);
                    int removed = inv.decrStackSize(i, take).getCount();
                    remaining -= removed;
                }
            }
        }

        // 放置方块
        placeMissingBlocks(world, controllerPos, player);
        return true;
    }
}
