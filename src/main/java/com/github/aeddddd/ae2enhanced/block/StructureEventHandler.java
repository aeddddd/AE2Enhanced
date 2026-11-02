package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.structureapi.StructureDeferral;
import com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult;
import com.github.aeddddd.ae2enhanced.tile.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public class StructureEventHandler {

    private static final Map<Integer, Map<BlockPos, Integer>> pendingChecks = new HashMap<>();
    private static final int MAX_STRUCTURE_RADIUS = 16;

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        if (!world.isRemote) checkSurroundingControllers(world, event.getPos());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        BlockPos pos = event.getPos();
        Block brokenBlock = world.getBlockState(pos).getBlock();
        if (brokenBlock == BlockRegistry.ASSEMBLY_CONTROLLER) {
            AssemblyStructure.disassemble(world, pos);
        } else if (brokenBlock == BlockRegistry.HYPERDIMENSIONAL_CONTROLLER) {
            HyperdimensionalStructure.disassemble(world, pos);
        } else if (brokenBlock == BlockRegistry.COMPUTATION_CORE) {
            SupercausalStructure.disassemble(world, pos);
        }
        checkSurroundingControllers(world, pos);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        World world = event.world;
        int dimId = world.provider.getDimension();
        Map<BlockPos, Integer> dimChecks = pendingChecks.get(dimId);
        if (dimChecks == null || dimChecks.isEmpty()) return;

        List<BlockPos> toValidate = new ArrayList<>();
        for (Map.Entry<BlockPos, Integer> entry : dimChecks.entrySet()) {
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) toValidate.add(entry.getKey());
            else entry.setValue(ticks);
        }
        for (BlockPos pos : toValidate) {
            dimChecks.remove(pos);
            validateAndUpdate(world, pos);
        }
        if (dimChecks.isEmpty()) pendingChecks.remove(dimId);
    }

    @SubscribeEvent
    public static void onChunkLoad(net.minecraftforge.event.world.ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        ChunkPos chunk = event.getChunk().getPos();
        int dimId = world.provider.getDimension();

        ControllerIndex index = ControllerIndex.get(world);
        if (index != null) {
            for (BlockPos controllerPos : index.getInChunk(world, chunk, BlockRegistry.ASSEMBLY_CONTROLLER)) {
                scheduleCheck(dimId, controllerPos);
            }
            for (BlockPos controllerPos : index.getInChunk(world, chunk, BlockRegistry.HYPERDIMENSIONAL_CONTROLLER)) {
                scheduleCheck(dimId, controllerPos);
            }
        }
        ComputationCoreIndex compIndex = ComputationCoreIndex.get(world);
        if (compIndex != null) {
            for (BlockPos controllerPos : compIndex.getInChunk(world, chunk, BlockRegistry.COMPUTATION_CORE)) {
                scheduleCheck(dimId, controllerPos);
            }
        }

        Set<BlockPos> deferred = StructureDeferral.getDeferred(dimId);
        if (deferred != null) {
            for (BlockPos controllerPos : new HashSet<>(deferred)) {
                BlockPos chunkOrigin = chunk.getBlock(0, controllerPos.getY(), 0);
                if (chebyshevDistance(chunkOrigin, controllerPos) <= MAX_STRUCTURE_RADIUS + 16) {
                    scheduleCheck(dimId, controllerPos);
                }
            }
        }
    }

    private static void checkSurroundingControllers(World world, BlockPos changedPos) {
        ControllerIndex index = ControllerIndex.get(world);
        if (index != null) {
            for (BlockPos controllerPos : new HashSet<>(index.getAll())) {
                if (chebyshevDistance(changedPos, controllerPos) > MAX_STRUCTURE_RADIUS
                        || !world.isBlockLoaded(controllerPos)) continue;
                IBlockState state = world.getBlockState(controllerPos);
                Block block = state.getBlock();
                EnumFacing facing;
                if (block instanceof BlockAssemblyController) {
                    facing = state.getValue(BlockAssemblyController.FACING);
                    BlockPos origin = AssemblyStructure.getOriginFromController(controllerPos, facing);
                    BlockPos rel = AssemblyStructure.toCanonical(changedPos.subtract(origin), facing);
                    if (AssemblyStructure.ALL_SET.contains(rel)) scheduleCheck(world.provider.getDimension(), controllerPos);
                } else if (block instanceof BlockHyperdimensionalController) {
                    facing = state.getValue(BlockHyperdimensionalController.FACING);
                    BlockPos rotatedRel = HyperdimensionalStructure.toCanonical(changedPos.subtract(controllerPos), facing);
                    if (HyperdimensionalStructure.ALL_SET.contains(rotatedRel)) scheduleCheck(world.provider.getDimension(), controllerPos);
                } else {
                    index.remove(controllerPos);
                }
            }
        }

        ComputationCoreIndex compIndex = ComputationCoreIndex.get(world);
        if (compIndex != null) {
            for (BlockPos controllerPos : new HashSet<>(compIndex.getAll())) {
                if (chebyshevDistance(changedPos, controllerPos) > MAX_STRUCTURE_RADIUS
                        || !world.isBlockLoaded(controllerPos)) continue;
                IBlockState state = world.getBlockState(controllerPos);
                if (state.getBlock() instanceof BlockComputationCore) {
                    EnumFacing facing = state.getValue(BlockComputationCore.FACING);
                    BlockPos rotatedRel = SupercausalStructure.toCanonical(changedPos.subtract(controllerPos), facing.getOpposite());
                    if (SupercausalStructure.ALL_STRUCTURE_SET.contains(rotatedRel)) {
                        scheduleCheck(world.provider.getDimension(), controllerPos);
                    }
                } else {
                    compIndex.remove(controllerPos);
                }
            }
        }
    }

    private static void scheduleCheck(int dimId, BlockPos controllerPos) {
        StructureDeferral.scheduleCheck(dimId, controllerPos);
        pendingChecks.computeIfAbsent(dimId, key -> new HashMap<>()).put(controllerPos, 20);
    }

    public static void defer(World world, BlockPos controllerPos) {
        StructureDeferral.defer(world, controllerPos);
    }

    @SubscribeEvent
    public static void onWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        World world = event.getWorld();
        if (!world.isRemote) {
            int dimId = world.provider.getDimension();
            pendingChecks.remove(dimId);
            StructureDeferral.clearDimension(dimId);
        }
    }

    private static int chebyshevDistance(BlockPos a, BlockPos b) {
        return Math.max(Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())),
                Math.abs(a.getZ() - b.getZ()));
    }

    public static void validateAndUpdate(World world, BlockPos controllerPos) {
        if (!world.isBlockLoaded(controllerPos)) {
            defer(world, controllerPos);
            return;
        }
        Block controllerBlock = world.getBlockState(controllerPos).getBlock();
        if (controllerBlock == BlockRegistry.ASSEMBLY_CONTROLLER) {
            StructureValidationResult result = AssemblyStructure.validate(world, controllerPos);
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof TileAssemblyController) {
                TileAssemblyController tile = (TileAssemblyController) te;
                if (result == StructureValidationResult.VALID && !tile.isFormed()) {
                    AssemblyStructure.assemble(world, controllerPos);
                } else if (result == StructureValidationResult.INVALID && tile.isFormed()) {
                    AssemblyStructure.disassemble(world, controllerPos);
                } else if (result == StructureValidationResult.DEFER_UNLOADED) {
                    defer(world, controllerPos);
                }
            }
        } else if (controllerBlock == BlockRegistry.HYPERDIMENSIONAL_CONTROLLER) {
            StructureValidationResult result = HyperdimensionalStructure.validate(world, controllerPos);
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof TileHyperdimensionalController) {
                TileHyperdimensionalController tile = (TileHyperdimensionalController) te;
                if (result == StructureValidationResult.VALID && !tile.isFormed()) {
                    HyperdimensionalStructure.assemble(world, controllerPos);
                } else if (result == StructureValidationResult.INVALID && tile.isFormed()) {
                    HyperdimensionalStructure.disassemble(world, controllerPos);
                } else if (result == StructureValidationResult.DEFER_UNLOADED) {
                    defer(world, controllerPos);
                }
            }
        } else if (controllerBlock == BlockRegistry.COMPUTATION_CORE) {
            SupercausalStructure.ValidationResult result = SupercausalStructure.validate(world, controllerPos);
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof TileComputationCore) {
                TileComputationCore tile = (TileComputationCore) te;
                if (result.status == StructureValidationResult.VALID && !tile.isFormed()) {
                    SupercausalStructure.assemble(world, controllerPos);
                } else if (result.status == StructureValidationResult.INVALID && tile.isFormed()) {
                    SupercausalStructure.disassemble(world, controllerPos);
                } else if (result.status == StructureValidationResult.DEFER_UNLOADED) {
                    defer(world, controllerPos);
                }
            }
        } else {
            ControllerIndex index = ControllerIndex.get(world);
            if (index != null) index.remove(controllerPos);
            ComputationCoreIndex compIndex = ComputationCoreIndex.get(world);
            if (compIndex != null) compIndex.remove(controllerPos);
        }
    }
}
