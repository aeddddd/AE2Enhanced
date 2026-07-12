package com.github.aeddddd.ae2enhanced.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.block.HyperdimensionalControllerBlock;
import com.github.aeddddd.ae2enhanced.computation.block.ComputationControllerBlock;
import com.github.aeddddd.ae2enhanced.multiblock.IMultiblockController;
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
 * <p>负责在方块变化、chunk 加载等事件后延迟验证结构完整性，并自动组装/解体。
 * 具体 Block 到 {@link IMultiblockStructure} 的映射由 {@link #STRUCTURES} 集中管理。</p>
 * <p>该类不在 {@code @Mod.EventBusSubscriber} 中自动注册，而是延迟到
 * {@code FMLCommonSetupEvent} 中手动注册，以避免在方块注册完成前加载结构类。</p>
 */
public class StructureEventHandler {

    // Block 类型 -> 对应的多方块结构定义 Supplier（延迟获取）
    private static final Map<Class<? extends Block>, Supplier<IMultiblockStructure>> STRUCTURES = Map.of(
            AssemblyControllerBlock.class, AssemblyStructure::getInstance,
            HyperdimensionalControllerBlock.class, HyperdimensionalStructure::getInstance,
            ComputationControllerBlock.class, SupercausalStructure::getInstance);

    // Block 类型 -> 从对应维度索引移除控制器位置的操作
    private static final Map<Class<? extends Block>, BiConsumer<ServerLevel, BlockPos>> INDEX_REMOVERS = Map.of(
            AssemblyControllerBlock.class, (level, pos) -> ControllerIndex.get(level).remove(pos),
            HyperdimensionalControllerBlock.class, (level, pos) -> ControllerIndex.get(level).remove(pos),
            ComputationControllerBlock.class, (level, pos) -> ComputationCoreIndex.get(level).remove(pos));

    // Block 类型 -> 获取所有已注册控制器位置的 Supplier
    private static final Map<Class<? extends Block>, Function<ServerLevel, Set<BlockPos>>> INDEX_PROVIDERS = Map.of(
            AssemblyControllerBlock.class, level -> ControllerIndex.get(level).getAll(),
            HyperdimensionalControllerBlock.class, level -> ControllerIndex.get(level).getAll(),
            ComputationControllerBlock.class, level -> ComputationCoreIndex.get(level).getAll());

    // 按维度分离的待验证控制器位置 -> 剩余 tick
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> pendingChecks = new HashMap<>();

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null) {
            return;
        }
        checkSurroundingControllers(level, event.getPos());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null) {
            return;
        }

        BlockPos pos = event.getPos();
        Block brokenBlock = level.getBlockState(pos).getBlock();

        Supplier<IMultiblockStructure> supplier = STRUCTURES.get(brokenBlock.getClass());
        IMultiblockStructure structure = supplier != null ? supplier.get() : null;
        if (structure != null) {
            structure.disassemble(level, pos);
            INDEX_REMOVERS.get(brokenBlock.getClass()).accept(level, pos);
        }

        checkSurroundingControllers(level, pos);
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
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null) {
            return;
        }

        for (Function<ServerLevel, Set<BlockPos>> provider : INDEX_PROVIDERS.values()) {
            for (BlockPos controllerPos : provider.apply(level)) {
                if (areAllChunksLoadedForController(level, controllerPos)) {
                    scheduleCheck(level, controllerPos);
                }
            }
        }
    }

    @Nullable
    private static ServerLevel asServerLevel(LevelAccessor levelAccessor) {
        return levelAccessor instanceof ServerLevel serverLevel ? serverLevel : null;
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
        for (Function<ServerLevel, Set<BlockPos>> provider : INDEX_PROVIDERS.values()) {
            for (BlockPos controllerPos : provider.apply(level)) {
                IMultiblockStructure structure = getStructureForController(level, controllerPos);
                if (structure != null) {
                    checkControllerChanged(level, changedPos, controllerPos, structure);
                }
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

        IMultiblockStructure structure = getStructureForController(level, controllerPos);
        if (structure == null) {
            return;
        }

        ValidationResult result = structure.validateDetailed(level, controllerPos);
        if (level.getBlockEntity(controllerPos) instanceof IMultiblockController tile) {
            if (result.passed() && !tile.isFormed()) {
                structure.assemble(level, controllerPos);
            } else if (!result.passed() && tile.isFormed()) {
                structure.disassemble(level, controllerPos);
            }
        }
    }

    private static IMultiblockStructure getStructureForController(Level level, BlockPos controllerPos) {
        BlockState state = level.getBlockState(controllerPos);
        Supplier<IMultiblockStructure> supplier = STRUCTURES.get(state.getBlock().getClass());
        return supplier != null ? supplier.get() : null;
    }
}
