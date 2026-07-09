package com.github.aeddddd.ae2enhanced.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.block.HyperdimensionalControllerBlock;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.computation.block.ComputationControllerBlock;
import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.ComputationCoreIndex;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structure.IMultiblockStructure;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;
import com.github.aeddddd.ae2enhanced.structure.ValidationResult;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;

/**
 * 多方块结构全局事件驱动验证器。
 * <p>移植自 master 的 {@code StructureEventHandler}，负责在方块变化、chunk 加载等事件后
 * 延迟验证结构完整性，并自动组装/解体。</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StructureEventHandler {

    // 按维度分离的待验证控制器位置 -> 剩余 tick
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> pendingChecks = new HashMap<>();

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        checkSurroundingControllers(serverLevel, event.getPos());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block brokenBlock = state.getBlock();

        // 如果是控制器本身被破坏，立即解散
        if (brokenBlock instanceof HyperdimensionalControllerBlock) {
            HyperdimensionalStructure.disassemble(level, pos);
            ControllerIndex.get(serverLevel).remove(pos);
        } else if (brokenBlock instanceof AssemblyControllerBlock) {
            AssemblyStructure.disassemble(level, pos);
            ControllerIndex.get(serverLevel).remove(pos);
        } else if (brokenBlock instanceof ComputationControllerBlock) {
            SupercausalStructure.disassemble(level, pos);
            ComputationCoreIndex.get(serverLevel).remove(pos);
        }

        checkSurroundingControllers(serverLevel, pos);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) event.level;
        ResourceKey<Level> dim = level.dimension();
        Map<BlockPos, Integer> dimChecks = pendingChecks.get(dim);
        if (dimChecks == null || dimChecks.isEmpty()) {
            return;
        }

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
            validateAndUpdate(level, pos);
        }
        if (dimChecks.isEmpty()) {
            pendingChecks.remove(dim);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        ControllerIndex index = ControllerIndex.get(serverLevel);
        for (BlockPos controllerPos : index.getAll()) {
            if (areAllChunksLoadedForController(level, controllerPos)) {
                scheduleCheck(serverLevel, controllerPos);
            }
        }

        ComputationCoreIndex compIndex = ComputationCoreIndex.get(serverLevel);
        for (BlockPos controllerPos : compIndex.getAll()) {
            if (areAllChunksLoadedForController(level, controllerPos)) {
                scheduleCheck(serverLevel, controllerPos);
            }
        }
    }

    /**
     * 检查指定控制器对应的所有结构方块是否都已加载。
     * 未完全加载时返回 false，防止 validate 误判导致解体。
     */
    private static boolean areAllChunksLoadedForController(Level level, BlockPos controllerPos) {
        IMultiblockStructure structure = getStructureForController(level, controllerPos);
        if (structure == null) {
            return true;
        }
        Direction rotation = structure.getRotation(level, controllerPos);
        for (BlockPos rel : structure.getAllPositions()) {
            BlockPos actual = controllerPos.offset(StructureUtils.rotate(rel, rotation));
            if (!level.isLoaded(actual)) {
                return false;
            }
        }
        return true;
    }

    private static void checkSurroundingControllers(ServerLevel level, BlockPos changedPos) {
        ControllerIndex index = ControllerIndex.get(level);
        Set<BlockPos> controllers = index.getAll();
        for (BlockPos controllerPos : controllers) {
            BlockState state = level.getBlockState(controllerPos);
            Block block = state.getBlock();
            if (block instanceof AssemblyControllerBlock) {
                checkControllerChanged(level, changedPos, controllerPos, AssemblyStructure.INSTANCE);
            } else if (block instanceof HyperdimensionalControllerBlock) {
                checkControllerChanged(level, changedPos, controllerPos, HyperdimensionalStructure.INSTANCE);
            }
        }

        ComputationCoreIndex compIndex = ComputationCoreIndex.get(level);
        for (BlockPos controllerPos : compIndex.getAll()) {
            BlockState state = level.getBlockState(controllerPos);
            Block block = state.getBlock();
            if (block instanceof ComputationControllerBlock) {
                checkControllerChanged(level, changedPos, controllerPos, SupercausalStructure.INSTANCE);
            }
        }
    }

    private static void checkControllerChanged(ServerLevel level, BlockPos changedPos, BlockPos controllerPos, IMultiblockStructure structure) {
        Direction rotation = structure.getRotation(level, controllerPos);
        BlockPos rel = changedPos.subtract(controllerPos);
        BlockPos rotatedRel = StructureUtils.rotate(rel, rotation.getOpposite());
        if (structure.getAllPositions().contains(rotatedRel)) {
            scheduleCheck(level, controllerPos);
        }
    }

    private static void scheduleCheck(ServerLevel level, BlockPos controllerPos) {
        pendingChecks.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(controllerPos.immutable(), 20);
    }

    private static void validateAndUpdate(ServerLevel level, BlockPos controllerPos) {
        // 双重保护：若 chunk 未全部加载则重新安排，避免误判
        if (!areAllChunksLoadedForController(level, controllerPos)) {
            scheduleCheck(level, controllerPos);
            return;
        }

        BlockState state = level.getBlockState(controllerPos);
        Block block = state.getBlock();
        if (block instanceof HyperdimensionalControllerBlock) {
            ValidationResult result = HyperdimensionalStructure.validateDetailed(level, controllerPos);
            if (level.getBlockEntity(controllerPos) instanceof HyperdimensionalControllerBlockEntity tile) {
                if (result.passed() && !tile.isFormed()) {
                    HyperdimensionalStructure.assemble(level, controllerPos);
                } else if (!result.passed() && tile.isFormed()) {
                    HyperdimensionalStructure.disassemble(level, controllerPos);
                }
            }
        } else if (block instanceof AssemblyControllerBlock) {
            ValidationResult result = AssemblyStructure.validateDetailed(level, controllerPos);
            if (level.getBlockEntity(controllerPos) instanceof AssemblyControllerBlockEntity tile) {
                if (result.passed() && !tile.isFormed()) {
                    AssemblyStructure.assemble(level, controllerPos);
                } else if (!result.passed() && tile.isFormed()) {
                    AssemblyStructure.disassemble(level, controllerPos);
                }
            }
        } else if (block instanceof ComputationControllerBlock) {
            ValidationResult result = SupercausalStructure.validateDetailed(level, controllerPos);
            if (level.getBlockEntity(controllerPos) instanceof ComputationCoreBlockEntity tile) {
                if (result.passed() && !tile.isFormed()) {
                    SupercausalStructure.assemble(level, controllerPos);
                } else if (!result.passed() && tile.isFormed()) {
                    SupercausalStructure.disassemble(level, controllerPos);
                }
            }
        }
    }

    private static IMultiblockStructure getStructureForController(Level level, BlockPos controllerPos) {
        BlockState state = level.getBlockState(controllerPos);
        Block block = state.getBlock();
        if (block instanceof AssemblyControllerBlock) {
            return AssemblyStructure.INSTANCE;
        } else if (block instanceof HyperdimensionalControllerBlock) {
            return HyperdimensionalStructure.INSTANCE;
        } else if (block instanceof ComputationControllerBlock) {
            return SupercausalStructure.INSTANCE;
        }
        return null;
    }
}
