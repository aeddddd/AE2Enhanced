package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalMeInterface;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class HyperdimensionalStructure {

    // 相对于控制器 (0,0,0) 的坐标
    public static final Set<BlockPos> CONTROLLER_SET;
    public static final Set<BlockPos> ME_INTERFACE_SET;
    public static final Set<BlockPos> CASING_SET;
    public static final Set<BlockPos> CORE_SET; // singularity core
    public static final Set<BlockPos> ALL_SET;

    static {
        Set<BlockPos> controller = new HashSet<>();
        controller.add(new BlockPos(0, 0, 0));
        CONTROLLER_SET = Collections.unmodifiableSet(controller);

        Set<BlockPos> meInterface = new HashSet<>();
        meInterface.add(new BlockPos(0, 0, 4));
        ME_INTERFACE_SET = Collections.unmodifiableSet(meInterface);

        Set<BlockPos> core = new HashSet<>();
        core.add(new BlockPos(0, 0, 3));
        core.add(new BlockPos(-1, 0, 2));
        core.add(new BlockPos(1, 0, 2));
        core.add(new BlockPos(0, 0, 2));
        core.add(new BlockPos(0, 0, 1));
        CORE_SET = Collections.unmodifiableSet(core);

        Set<BlockPos> casing = new HashSet<>();
        casing.add(new BlockPos(-1, 0, 3));
        casing.add(new BlockPos(-2, 0, 3));
        casing.add(new BlockPos(1, 0, 4));
        casing.add(new BlockPos(1, 0, 3));
        casing.add(new BlockPos(-2, 0, 1));
        casing.add(new BlockPos(-1, 0, 1));
        casing.add(new BlockPos(-2, 0, 2));
        casing.add(new BlockPos(-1, 0, 0));
        casing.add(new BlockPos(1, 0, 1));
        casing.add(new BlockPos(1, 0, 0));
        casing.add(new BlockPos(-1, 0, 4));
        casing.add(new BlockPos(2, 0, 2));
        casing.add(new BlockPos(2, 0, 1));
        casing.add(new BlockPos(2, 0, 3));
        CASING_SET = Collections.unmodifiableSet(casing);

        Set<BlockPos> all = new HashSet<>();
        all.addAll(CONTROLLER_SET);
        all.addAll(ME_INTERFACE_SET);
        all.addAll(CORE_SET);
        all.addAll(CASING_SET);
        ALL_SET = Collections.unmodifiableSet(all);
    }

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
     * 世界相对坐标 → 基准相对坐标的逆旋转（rotate 的逆映射）.
     * NORTH 为恒等,SOUTH 旋转 180°（自逆）,EAST/WEST 互为逆.
     */
    public static BlockPos toCanonical(BlockPos rel, EnumFacing facing) {
        switch (facing) {
            case SOUTH: return rotate(rel, EnumFacing.SOUTH);
            case EAST:  return rotate(rel, EnumFacing.WEST);
            case WEST:  return rotate(rel, EnumFacing.EAST);
            default:    return rel;
        }
    }

    public static StructureValidationResult validate(World world, BlockPos controllerPos) {
        if (!world.isBlockLoaded(controllerPos)) {
            return StructureValidationResult.DEFER_UNLOADED;
        }
        EnumFacing facing = getControllerFacing(world, controllerPos);
        boolean deferred = false;
        StructureValidationResult controller = checkBlock(world, controllerPos, CONTROLLER_SET, BlockRegistry.HYPERDIMENSIONAL_CONTROLLER, facing);
        if (controller == StructureValidationResult.INVALID) return controller;
        deferred |= controller == StructureValidationResult.DEFER_UNLOADED;
        StructureValidationResult me = checkBlock(world, controllerPos, ME_INTERFACE_SET, BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE, facing);
        if (me == StructureValidationResult.INVALID) return me;
        deferred |= me == StructureValidationResult.DEFER_UNLOADED;
        StructureValidationResult core = checkBlock(world, controllerPos, CORE_SET, BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE, facing);
        if (core == StructureValidationResult.INVALID) return core;
        deferred |= core == StructureValidationResult.DEFER_UNLOADED;
        StructureValidationResult casing = checkBlock(world, controllerPos, CASING_SET, BlockRegistry.HYPERDIMENSIONAL_CASING, facing);
        if (casing == StructureValidationResult.INVALID) return casing;
        deferred |= casing == StructureValidationResult.DEFER_UNLOADED;
        return deferred ? StructureValidationResult.DEFER_UNLOADED : StructureValidationResult.VALID;
    }

    private static StructureValidationResult checkBlock(World world, BlockPos controllerPos, Set<BlockPos> relativeSet, Block expected, EnumFacing facing) {
        boolean deferred = false;
        for (BlockPos rel : relativeSet) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
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

    public static Map<Block, Integer> getMissingMap(World world, BlockPos controllerPos) {
        EnumFacing facing = getControllerFacing(world, controllerPos);
        Map<Block, Integer> missing = new LinkedHashMap<>();
        countMissing(world, controllerPos, ME_INTERFACE_SET, BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE, missing, facing);
        countMissing(world, controllerPos, CORE_SET, BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE, missing, facing);
        countMissing(world, controllerPos, CASING_SET, BlockRegistry.HYPERDIMENSIONAL_CASING, missing, facing);
        return missing;
    }

    private static void countMissing(World world, BlockPos controllerPos, Set<BlockPos> relativeSet, Block expected, Map<Block, Integer> missing, EnumFacing facing) {
        for (BlockPos rel : relativeSet) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) {
                continue;
            }
            if (world.getBlockState(actual).getBlock() != expected) {
                missing.put(expected, missing.getOrDefault(expected, 0) + 1);
            }
        }
    }

    public static void assemble(World world, BlockPos controllerPos) {
        if (world.isRemote) return;
        StructureValidationResult validation = validate(world, controllerPos);
        if (validation != StructureValidationResult.VALID) {
            if (validation == StructureValidationResult.DEFER_UNLOADED) com.github.aeddddd.ae2enhanced.structureapi.StructureDeferral.defer(world, controllerPos);
            return;
        }
        TileHyperdimensionalController tile = getControllerTile(world, controllerPos);
        if (tile != null) {
            tile.assemble();
        }
        EnumFacing facing = getControllerFacing(world, controllerPos);
        updateMeInterfaceState(world, controllerPos, true, facing);
    }

    public static void disassemble(World world, BlockPos controllerPos) {
        if (world.isRemote) return;
        TileHyperdimensionalController tile = getControllerTile(world, controllerPos);
        if (tile != null) {
            tile.disassemble();
        }
        EnumFacing facing = getControllerFacing(world, controllerPos);
        updateMeInterfaceState(world, controllerPos, false, facing);
    }

    private static void updateMeInterfaceState(World world, BlockPos controllerPos, boolean formed, EnumFacing facing) {
        IBlockState state = BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE.getDefaultState()
            .withProperty(com.github.aeddddd.ae2enhanced.block.BlockHyperdimensionalMeInterface.FORMED, formed);
        for (BlockPos rel : ME_INTERFACE_SET) {
            BlockPos pos = controllerPos.add(rotate(rel, facing));
            if (world.getBlockState(pos).getBlock() == BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE) {
                world.setBlockState(pos, state);
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
                if (te instanceof TileHyperdimensionalMeInterface) {
                    ((TileHyperdimensionalMeInterface) te).setControllerPos(formed ? controllerPos : null);
                }
            }
        }
    }

    private static TileHyperdimensionalController getControllerTile(World world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        return te instanceof TileHyperdimensionalController ? (TileHyperdimensionalController) te : null;
    }

    private static EnumFacing getControllerFacing(World world, BlockPos controllerPos) {
        IBlockState state = world.getBlockState(controllerPos);
        if (state.getBlock() instanceof BlockHyperdimensionalController) {
            return state.getValue(BlockHyperdimensionalController.FACING);
        }
        return EnumFacing.NORTH;
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
        Map<BlockPos, Block> targets = new LinkedHashMap<>();
        addTargets(targets, controllerPos, ME_INTERFACE_SET, BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE, facing);
        addTargets(targets, controllerPos, CORE_SET, BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE, facing);
        addTargets(targets, controllerPos, CASING_SET, BlockRegistry.HYPERDIMENSIONAL_CASING, facing);
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
