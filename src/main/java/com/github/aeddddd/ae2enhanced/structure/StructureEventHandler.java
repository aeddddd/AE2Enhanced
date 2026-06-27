package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        // 如果是控制器本身被破坏,立即解散(不用等 tick)
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
            if (ticks <= 0) {
                toValidate.add(entry.getKey());
            } else {
                entry.setValue(ticks);
            }
        }
        for (BlockPos pos : toValidate) {
            dimChecks.remove(pos);
            validateAndUpdate(world, pos);
        }
        if (dimChecks.isEmpty()) {
            pendingChecks.remove(dimId);
        }
    }

    /**
     * Chunk 加载时：只有当控制器对应的全部结构方块所在 chunk 都已加载时,才安排验证.
     * 这防止了因部分结构 chunk 未加载而导致 validate() 误判为非法、进而错误解散结构的问题.
     */
    @SubscribeEvent
    public static void onChunkLoad(net.minecraftforge.event.world.ChunkEvent.Load event) {
        if (event.getWorld().isRemote) return;
        World world = event.getWorld();

        ControllerIndex index = ControllerIndex.get(world);
        if (index != null) {
            for (BlockPos controllerPos : index.getAll()) {
                if (world.provider.getDimension() != event.getWorld().provider.getDimension()) continue;
                if (areAllChunksLoadedForController(world, controllerPos)) {
                    scheduleCheck(world.provider.getDimension(), controllerPos);
                }
            }
        }

        ComputationCoreIndex compIndex = ComputationCoreIndex.get(world);
        if (compIndex != null) {
            for (BlockPos controllerPos : compIndex.getAll()) {
                if (world.provider.getDimension() != event.getWorld().provider.getDimension()) continue;
                if (areAllChunksLoadedForController(world, controllerPos)) {
                    scheduleCheck(world.provider.getDimension(), controllerPos);
                }
            }
        }
    }

    /**
     * 检查指定控制器对应的所有结构方块是否都已加载.
     * 未完全加载时返回 false,防止 validate 误判导致 disassemble.
     */
    private static boolean areAllChunksLoadedForController(World world, BlockPos controllerPos) {
        IBlockState state = world.getBlockState(controllerPos);
        Block block = state.getBlock();
        if (block instanceof BlockAssemblyController) {
            EnumFacing facing = state.getValue(BlockAssemblyController.FACING);
            BlockPos origin = AssemblyStructure.getOriginFromController(controllerPos, facing);
            for (BlockPos rel : AssemblyStructure.ALL_SET) {
                if (!world.isBlockLoaded(origin.add(rel))) return false;
            }
            return true;
        } else if (block instanceof BlockHyperdimensionalController) {
            EnumFacing facing = state.getValue(BlockHyperdimensionalController.FACING);
            for (BlockPos rel : HyperdimensionalStructure.ALL_SET) {
                BlockPos actual = controllerPos.add(HyperdimensionalStructure.rotate(rel, facing));
                if (!world.isBlockLoaded(actual)) return false;
            }
            return true;
        } else if (block instanceof BlockComputationCore) {
            EnumFacing facing = state.getValue(BlockComputationCore.FACING).getOpposite();
            for (BlockPos rel : SupercausalStructure.ALL_STRUCTURE_SET) {
                BlockPos actual = controllerPos.add(SupercausalStructure.rotate(rel, facing));
                if (!world.isBlockLoaded(actual)) return false;
            }
            return true;
        }
        return true;
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
        // 双重保护：即使因 tick 调度延迟导致 chunk 被卸载,也不应在此状态下解散结构
        if (!areAllChunksLoadedForController(world, controllerPos)) {
            scheduleCheck(world.provider.getDimension(), controllerPos);
            return;
        }

        Block controllerBlock = world.getBlockState(controllerPos).getBlock();
        if (controllerBlock == BlockRegistry.ASSEMBLY_CONTROLLER) {
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
        } else if (controllerBlock == BlockRegistry.HYPERDIMENSIONAL_CONTROLLER) {
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
        } else if (controllerBlock == BlockRegistry.COMPUTATION_CORE) {
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
