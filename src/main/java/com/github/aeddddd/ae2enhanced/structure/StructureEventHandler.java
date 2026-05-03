package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.block.BlockAssemblyController;
import com.github.aeddddd.ae2enhanced.block.BlockComputationCore;
import com.github.aeddddd.ae2enhanced.block.BlockHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public class StructureEventHandler {

    // 按维度分离的待验证控制器位置 -> 剩余 tick
    private static final Map<Integer, Map<BlockPos, Integer>> pendingChecks = new HashMap<>();

    @SubscribeEvent
    public static void onNeighborNotify(net.minecraftforge.event.world.BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;

        BlockPos pos = event.getPos();
        checkSurroundingControllers(world, pos);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;

        BlockPos pos = event.getPos();
        // 如果是控制器本身被破坏，立即解散（不用等 tick）
        Block brokenBlock = world.getBlockState(pos).getBlock();
        if (brokenBlock == ModBlocks.ASSEMBLY_CONTROLLER) {
            AssemblyStructure.disassemble(world, pos);
        } else if (brokenBlock == ModBlocks.HYPERDIMENSIONAL_CONTROLLER) {
            HyperdimensionalStructure.disassemble(world, pos);
        } else if (brokenBlock == ModBlocks.COMPUTATION_CORE) {
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
        if (dimChecks == null) return;

        dimChecks.entrySet().removeIf(entry -> {
            BlockPos controllerPos = entry.getKey();
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) {
                validateAndUpdate(world, controllerPos);
                return true;
            }
            entry.setValue(ticks);
            return false;
        });
    }

    /**
     * Chunk 加载时：如果该 chunk 包含 ControllerIndex 中记录的控制器，将其加入待验证队列。
     * 这保证存档重进后，已成型结构能在 chunk 加载完毕后自动重新验证，避免因加载顺序导致的误判解散。
     */
    @SubscribeEvent
    public static void onChunkLoad(net.minecraftforge.event.world.ChunkEvent.Load event) {
        if (event.getWorld().isRemote) return;
        World world = event.getWorld();
        ControllerIndex index = ControllerIndex.get(world);
        if (index == null) return;

        net.minecraft.world.chunk.Chunk chunk = event.getChunk();
        int chunkX = chunk.x;
        int chunkZ = chunk.z;

        for (BlockPos controllerPos : index.getAll()) {
            if (world.provider.getDimension() != chunk.getWorld().provider.getDimension()) continue;
            int cx = controllerPos.getX() >> 4;
            int cz = controllerPos.getZ() >> 4;
            // 控制器所在 chunk 已加载，且 8 格半径内的结构 chunk 都已加载时才验证
            if (cx == chunkX && cz == chunkZ) {
                scheduleCheck(world.provider.getDimension(), controllerPos);
            }
        }

        ComputationCoreIndex compIndex = ComputationCoreIndex.get(world);
        if (compIndex != null) {
            for (BlockPos controllerPos : compIndex.getAll()) {
                if (world.provider.getDimension() != chunk.getWorld().provider.getDimension()) continue;
                int cx = controllerPos.getX() >> 4;
                int cz = controllerPos.getZ() >> 4;
                if (cx == chunkX && cz == chunkZ) {
                    scheduleCheck(world.provider.getDimension(), controllerPos);
                }
            }
        }
    }

    private static void checkSurroundingControllers(World world, BlockPos changedPos) {
        ControllerIndex index = ControllerIndex.get(world);
        if (index != null) {
            Set<BlockPos> controllers = index.getAll();
            for (BlockPos controllerPos : controllers) {
                IBlockState state = world.getBlockState(controllerPos);
                Block block = state.getBlock();
                EnumFacing facing;
                if (block instanceof BlockAssemblyController) {
                    facing = state.getValue(BlockAssemblyController.FACING);
                    BlockPos origin = AssemblyStructure.getOriginFromController(controllerPos, facing);
                    BlockPos rel = changedPos.subtract(origin);
                    if (AssemblyStructure.ALL_SET.contains(rel)) {
                        scheduleCheck(world.provider.getDimension(), controllerPos);
                    }
                } else if (block instanceof BlockHyperdimensionalController) {
                    facing = state.getValue(BlockHyperdimensionalController.FACING);
                    BlockPos rel = changedPos.subtract(controllerPos);
                    BlockPos rotatedRel = HyperdimensionalStructure.rotate(rel, facing.getOpposite());
                    if (HyperdimensionalStructure.ALL_SET.contains(rotatedRel)) {
                        scheduleCheck(world.provider.getDimension(), controllerPos);
                    }
                }
            }
        }

        ComputationCoreIndex compIndex = ComputationCoreIndex.get(world);
        if (compIndex != null) {
            for (BlockPos controllerPos : compIndex.getAll()) {
                IBlockState state = world.getBlockState(controllerPos);
                Block block = state.getBlock();
                if (block instanceof BlockComputationCore) {
                    EnumFacing facing = state.getValue(BlockComputationCore.FACING);
                    BlockPos rel = changedPos.subtract(controllerPos);
                    BlockPos rotatedRel = SupercausalStructure.rotate(rel, facing.getOpposite());
                    if (SupercausalStructure.ALL_STRUCTURE_SET.contains(rotatedRel)) {
                        scheduleCheck(world.provider.getDimension(), controllerPos);
                    }
                }
            }
        }
    }

    private static void scheduleCheck(int dimId, BlockPos controllerPos) {
        pendingChecks.computeIfAbsent(dimId, k -> new HashMap<>()).put(controllerPos, 20);
    }

    private static void validateAndUpdate(World world, BlockPos controllerPos) {
        Block controllerBlock = world.getBlockState(controllerPos).getBlock();
        if (controllerBlock == ModBlocks.ASSEMBLY_CONTROLLER) {
            boolean valid = AssemblyStructure.validate(world, controllerPos);
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof TileAssemblyController) {
                TileAssemblyController tile = (TileAssemblyController) te;
                if (valid && !tile.isFormed()) {
                    AssemblyStructure.assemble(world, controllerPos);
                } else if (!valid && tile.isFormed()) {
                    AssemblyStructure.disassemble(world, controllerPos);
                }
            }
        } else if (controllerBlock == ModBlocks.HYPERDIMENSIONAL_CONTROLLER) {
            boolean valid = HyperdimensionalStructure.validate(world, controllerPos);
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof TileHyperdimensionalController) {
                TileHyperdimensionalController tile = (TileHyperdimensionalController) te;
                if (valid && !tile.isFormed()) {
                    HyperdimensionalStructure.assemble(world, controllerPos);
                } else if (!valid && tile.isFormed()) {
                    HyperdimensionalStructure.disassemble(world, controllerPos);
                }
            }
        } else if (controllerBlock == ModBlocks.COMPUTATION_CORE) {
            SupercausalStructure.ValidationResult result = SupercausalStructure.validate(world, controllerPos);
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof TileComputationCore) {
                TileComputationCore tile = (TileComputationCore) te;
                if (result.passed && !tile.isFormed()) {
                    SupercausalStructure.assemble(world, controllerPos);
                } else if (!result.passed && tile.isFormed()) {
                    SupercausalStructure.disassemble(world, controllerPos);
                }
            }
        }
    }
}
