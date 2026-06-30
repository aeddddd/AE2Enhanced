package com.github.aeddddd.ae2enhanced.structure;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;

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
    public static BlockPos rotate(BlockPos rel, Direction facing) {
        if (facing == Direction.NORTH) return rel;
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
    public static BlockPos getOriginFromController(BlockPos controllerPos, Direction facing) {
        return controllerPos.offset(rotate(new BlockPos(0, 0, 7), facing));
    }

    private static Direction getControllerFacing(Level level, BlockPos controllerPos) {
        BlockState state = level.getBlockState(controllerPos);
        if (state.getBlock() instanceof AssemblyControllerBlock) {
            return state.getValue(AssemblyControllerBlock.FACING);
        }
        return Direction.NORTH;
    }

    /**
     * 验证结构完整性(优先检查 core 与 part1)
     */
    public static boolean validate(Level level, BlockPos controllerPos) {
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);

        // 优先检查 core
        if (!checkBlock(level, origin, CORE_SET, ModBlocks.ASSEMBLY_CONTROLLER.get(), facing)) {
            return false;
        }

        // 优先检查 part1(ME 接口,结构完整性关键)
        if (!checkBlock(level, origin, PART1_SET, ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), facing)) {
            return false;
        }

        if (!checkBlock(level, origin, PART2_SET, ModBlocks.ASSEMBLY_CASING.get(), facing)) {
            return false;
        }
        if (!checkBlock(level, origin, PART3_SET, ModBlocks.ASSEMBLY_INNER_WALL.get(), facing)) {
            return false;
        }
        if (!checkBlock(level, origin, PART4_SET, ModBlocks.ASSEMBLY_STABILIZER.get(), facing)) {
            return false;
        }

        return true;
    }

    private static boolean checkBlock(Level level, BlockPos origin, Set<BlockPos> relativeSet, Block expected, Direction facing) {
        for (BlockPos rel : relativeSet) {
            BlockPos actual = origin.offset(rotate(rel, facing));
            if (!level.isLoaded(actual)) {
                continue; // chunk 未加载,保持当前状态,不判定为缺失
            }
            if (level.getBlockState(actual).getBlock() != expected) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取缺失方块清单(用于未组装 GUI 显示)
     */
    public static Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos) {
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        Map<Block, Integer> missing = new LinkedHashMap<>();

        countMissing(level, origin, PART1_SET, ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), missing, facing);
        countMissing(level, origin, PART2_SET, ModBlocks.ASSEMBLY_CASING.get(), missing, facing);
        countMissing(level, origin, PART3_SET, ModBlocks.ASSEMBLY_INNER_WALL.get(), missing, facing);
        countMissing(level, origin, PART4_SET, ModBlocks.ASSEMBLY_STABILIZER.get(), missing, facing);

        return missing;
    }

    private static void countMissing(Level level, BlockPos origin, Set<BlockPos> relativeSet, Block expected, Map<Block, Integer> missing, Direction facing) {
        for (BlockPos rel : relativeSet) {
            BlockPos actual = origin.offset(rotate(rel, facing));
            if (!level.isLoaded(actual)) {
                continue; // chunk 未加载,不计入缺失
            }
            if (level.getBlockState(actual).getBlock() != expected) {
                missing.put(expected, missing.getOrDefault(expected, 0) + 1);
            }
        }
    }

    /**
     * 组装：通知 TileEntity 进入已组装状态,并更新 ME 接口 blockstate
     */
    public static void assemble(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) return;
        AssemblyControllerBlockEntity tile = getControllerTile(level, controllerPos);
        if (tile != null) {
            tile.setFormed(true);
        }
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        updateMeInterfaceState(level, origin, true, controllerPos, facing);
    }

    /**
     * 解散：通知 TileEntity 进入未组装状态,并更新 ME 接口 blockstate
     */
    public static void disassemble(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) return;
        AssemblyControllerBlockEntity tile = getControllerTile(level, controllerPos);
        if (tile != null) {
            tile.setFormed(false);
        }
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        updateMeInterfaceState(level, origin, false, controllerPos, facing);
    }

    private static void updateMeInterfaceState(Level level, BlockPos origin, boolean formed, BlockPos controllerPos, Direction facing) {
        BlockState state = ModBlocks.MULTIBLOCK_ME_INTERFACE.get().defaultBlockState()
            .setValue(com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlock.FORMED, formed);
        for (BlockPos rel : PART1_SET) {
            BlockPos pos = origin.offset(rotate(rel, facing));
            if (level.getBlockState(pos).getBlock() == ModBlocks.MULTIBLOCK_ME_INTERFACE.get()) {
                level.setBlock(pos, state, Block.UPDATE_ALL);
                net.minecraft.world.level.block.entity.BlockEntity te = level.getBlockEntity(pos);
                if (te instanceof com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity) {
                    com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity me = (com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity) te;
                    me.setControllerPos(formed ? controllerPos : null);
                }
            }
        }
    }

    private static AssemblyControllerBlockEntity getControllerTile(Level level, BlockPos pos) {
        // 注意：控制器在 origin + (0,0,-7),也就是 controllerPos 参数本身就是控制器位置
        net.minecraft.world.level.block.entity.BlockEntity te = level.getBlockEntity(pos);
        return te instanceof AssemblyControllerBlockEntity ? (AssemblyControllerBlockEntity) te : null;
    }

    /**
     * 创造模式：一键生成所有缺失方块
     */
    public static void placeMissingBlocks(Level level, BlockPos controllerPos, net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide()) return;
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);

        placeBlocks(level, origin, PART1_SET, ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), facing);
        placeBlocks(level, origin, PART2_SET, ModBlocks.ASSEMBLY_CASING.get(), facing);
        placeBlocks(level, origin, PART3_SET, ModBlocks.ASSEMBLY_INNER_WALL.get(), facing);
        placeBlocks(level, origin, PART4_SET, ModBlocks.ASSEMBLY_STABILIZER.get(), facing);

        // 立即触发组装(跳过 20 tick 等待)
        assemble(level, controllerPos);
    }

    private static void placeBlocks(Level level, BlockPos origin, Set<BlockPos> set, Block block, Direction facing) {
        for (BlockPos rel : set) {
            BlockPos pos = origin.offset(rotate(rel, facing));
            if (level.getBlockState(pos).getBlock() != block) {
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * 生存模式：检查背包材料,足够则扣除并放置
     * @return 是否成功
     */
    public static boolean tryConsumeAndPlace(Level level, BlockPos controllerPos, net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide()) return false;
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);

        Map<Block, Integer> missing = new LinkedHashMap<>();
        countMissing(level, origin, PART1_SET, ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), missing, facing);
        countMissing(level, origin, PART2_SET, ModBlocks.ASSEMBLY_CASING.get(), missing, facing);
        countMissing(level, origin, PART3_SET, ModBlocks.ASSEMBLY_INNER_WALL.get(), missing, facing);
        countMissing(level, origin, PART4_SET, ModBlocks.ASSEMBLY_STABILIZER.get(), missing, facing);

        if (missing.isEmpty()) {
            assemble(level, controllerPos);
            return true;
        }

        // 检查背包是否有足够材料
        Inventory inv = player.getInventory();
        Map<Block, Integer> needed = new LinkedHashMap<>(missing);

        for (ItemStack stack : inv.items) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                Block block = entry.getKey();
                if (stack.getItem() == block.asItem()) {
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
            Item item = block.asItem();
            for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
                ItemStack stack = inv.items.get(i);
                if (stack.getItem() == item) {
                    int take = Math.min(stack.getCount(), remaining);
                    int removed = inv.removeItem(i, take).getCount();
                    remaining -= removed;
                }
            }
        }

        // 放置方块
        placeMissingBlocks(level, controllerPos, player);
        return true;
    }
}
