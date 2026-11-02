package com.github.aeddddd.ae2enhanced.tile;

import com.github.aeddddd.ae2enhanced.structureapi.StructureDeferral;
import com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult;
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

    public static BlockPos toCanonical(BlockPos rel, EnumFacing facing) {
        switch (facing) {
            case SOUTH: return rotate(rel, EnumFacing.SOUTH);
            case EAST: return rotate(rel, EnumFacing.WEST);
            case WEST: return rotate(rel, EnumFacing.EAST);
            default: return rel;
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
        if (TileBlockStates.is(state, TileBlockStates.ASSEMBLY_CONTROLLER)) {
            return TileBlockStates.facing(state);
        }
        return EnumFacing.NORTH;
    }

    /**
     * 验证结构完整性(优先检查 core 与 part1)
     */
    public static StructureValidationResult validate(World world, BlockPos controllerPos) {
        if (!world.isBlockLoaded(controllerPos)) {
            return StructureValidationResult.DEFER_UNLOADED;
        }
        EnumFacing facing = getControllerFacing(world, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        boolean deferred = false;

        StructureValidationResult core = checkBlock(world, origin, CORE_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_CONTROLLER), facing);
        if (core == StructureValidationResult.INVALID) return core;
        deferred |= core == StructureValidationResult.DEFER_UNLOADED;

        StructureValidationResult part1 = checkBlock(world, origin, PART1_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_ME_INTERFACE), facing);
        if (part1 == StructureValidationResult.INVALID) return part1;
        deferred |= part1 == StructureValidationResult.DEFER_UNLOADED;

        StructureValidationResult part2 = checkBlock(world, origin, PART2_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_CASING), facing);
        if (part2 == StructureValidationResult.INVALID) return part2;
        deferred |= part2 == StructureValidationResult.DEFER_UNLOADED;
        StructureValidationResult part3 = checkBlock(world, origin, PART3_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_INNER_WALL), facing);
        if (part3 == StructureValidationResult.INVALID) return part3;
        deferred |= part3 == StructureValidationResult.DEFER_UNLOADED;
        StructureValidationResult part4 = checkBlock(world, origin, PART4_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_STABILIZER), facing);
        if (part4 == StructureValidationResult.INVALID) return part4;
        deferred |= part4 == StructureValidationResult.DEFER_UNLOADED;

        return deferred ? StructureValidationResult.DEFER_UNLOADED : StructureValidationResult.VALID;
    }

    private static StructureValidationResult checkBlock(World world, BlockPos origin, Set<BlockPos> relativeSet, Block expected, EnumFacing facing) {
        boolean deferred = false;
        for (BlockPos rel : relativeSet) {
            BlockPos actual = origin.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) {
                deferred = true;
                continue;
            }
            if (world.getBlockState(actual).getBlock() != expected) {
                return StructureValidationResult.INVALID;
            }
        }
        return deferred ? StructureValidationResult.DEFER_UNLOADED : StructureValidationResult.VALID;
    }

    /**
     * 获取缺失方块清单(用于未组装 GUI 显示)
     */
    public static Map<Block, Integer> getMissingMap(World world, BlockPos controllerPos) {
        EnumFacing facing = getControllerFacing(world, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        Map<Block, Integer> missing = new LinkedHashMap<>();

        countMissing(world, origin, PART1_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_ME_INTERFACE), missing, facing);
        countMissing(world, origin, PART2_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_CASING), missing, facing);
        countMissing(world, origin, PART3_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_INNER_WALL), missing, facing);
        countMissing(world, origin, PART4_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_STABILIZER), missing, facing);

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
        StructureValidationResult validation = validate(world, controllerPos);
        if (validation != StructureValidationResult.VALID) {
            if (validation == StructureValidationResult.DEFER_UNLOADED) StructureDeferral.defer(world, controllerPos);
            return;
        }
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
        IBlockState state = TileBlockStates.formed(TileBlockStates.block(TileBlockStates.ASSEMBLY_ME_INTERFACE).getDefaultState(), formed);
        for (BlockPos rel : PART1_SET) {
            BlockPos pos = origin.add(rotate(rel, facing));
            if (world.getBlockState(pos).getBlock() == TileBlockStates.block(TileBlockStates.ASSEMBLY_ME_INTERFACE)) {
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

    /** 创造模式：在完整占用预检后一次放置，失败恢复全部快照。 */
    public static void placeMissingBlocks(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return;
        com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.Plan plan = preparePlacement(world, controllerPos);
        if (plan == null) return;
        if (com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.execute(plan,
                () -> validate(world, controllerPos) == StructureValidationResult.VALID)) {
            assemble(world, controllerPos);
        }
    }

    private static com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.Plan preparePlacement(
            World world, BlockPos controllerPos) {
        EnumFacing facing = getControllerFacing(world, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        Map<BlockPos, Block> targets = new LinkedHashMap<>();
        addTargets(targets, origin, PART1_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_ME_INTERFACE), facing);
        addTargets(targets, origin, PART2_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_CASING), facing);
        addTargets(targets, origin, PART3_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_INNER_WALL), facing);
        addTargets(targets, origin, PART4_SET, TileBlockStates.block(TileBlockStates.ASSEMBLY_STABILIZER), facing);
        return com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.prepare(world, targets);
    }

    private static void addTargets(Map<BlockPos, Block> targets, BlockPos origin, Set<BlockPos> set,
            Block block, EnumFacing facing) {
        for (BlockPos rel : set) targets.put(origin.add(rotate(rel, facing)), block);
    }

    /** 生存模式：完整预检、一次扣料、放置复验，任一步失败均回滚。 */
    public static boolean tryConsumeAndPlace(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return false;
        com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.Plan plan = preparePlacement(world, controllerPos);
        if (plan == null) return false;
        boolean success = com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.consume(player.inventory, plan,
                () -> validate(world, controllerPos) == StructureValidationResult.VALID);
        if (success) assemble(world, controllerPos);
        return success;
    }
}
