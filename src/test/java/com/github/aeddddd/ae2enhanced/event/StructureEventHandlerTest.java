package com.github.aeddddd.ae2enhanced.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.LogicalSide;

import com.github.aeddddd.ae2enhanced.structure.ComputationCoreIndex;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link StructureEventHandler} 单元测试.
 * <p>覆盖范围:延迟验证队列({@code pendingChecks})的 tick 倒计时语义与各事件的
 * 非服务端早期返回分支.完整结构验证(validate/assemble/disassemble)需要真实已注册
 * 控制器方块与已加载区块,超出单测环境,由 GameTest 覆盖.</p>
 */
class StructureEventHandlerTest {

    private static final BlockPos CONTROLLER_POS = new BlockPos(5, 64, 5);

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceKey<Level>, Map<BlockPos, Integer>> pendingChecks() {
        try {
            Field field = StructureEventHandler.class.getDeclaredField("pendingChecks");
            field.setAccessible(true);
            return (Map<ResourceKey<Level>, Map<BlockPos, Integer>>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法访问 pendingChecks 字段", e);
        }
    }

    @BeforeEach
    void clearPending() {
        pendingChecks().clear();
    }

    private static ServerLevel overworldMock() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        return level;
    }

    private static TickEvent.LevelTickEvent tickEvent(Level level, TickEvent.Phase phase) {
        return new TickEvent.LevelTickEvent(LogicalSide.SERVER, phase, level, () -> true);
    }

    /** END 阶段倒计时递减,未到 0 时不触发验证. */
    @Test
    void testTickDecrementsCountdown() {
        ServerLevel level = overworldMock();
        pendingChecks().computeIfAbsent(Level.OVERWORLD, k -> new HashMap<>()).put(CONTROLLER_POS, 2);

        StructureEventHandler.onLevelTick(tickEvent(level, TickEvent.Phase.END));

        assertEquals(1, pendingChecks().get(Level.OVERWORLD).get(CONTROLLER_POS));
        // 倒计时未耗尽,不应访问区块加载状态(即未进入 validate)
        verify(level, never()).isLoaded(any(BlockPos.class));
    }

    /** START 阶段与客户端 level 不处理队列. */
    @Test
    void testTickGuardBranches() {
        ServerLevel serverLevel = overworldMock();
        pendingChecks().computeIfAbsent(Level.OVERWORLD, k -> new HashMap<>()).put(CONTROLLER_POS, 2);

        StructureEventHandler.onLevelTick(tickEvent(serverLevel, TickEvent.Phase.START));
        assertEquals(2, pendingChecks().get(Level.OVERWORLD).get(CONTROLLER_POS));

        Level clientLevel = mock(Level.class);
        when(clientLevel.isClientSide()).thenReturn(true);
        StructureEventHandler.onLevelTick(tickEvent(clientLevel, TickEvent.Phase.END));
        assertEquals(2, pendingChecks().get(Level.OVERWORLD).get(CONTROLLER_POS));
    }

    /** 倒计时耗尽但控制器区块未加载:重新安排 20 tick,防止误判. */
    @Test
    void testTickReschedulesWhenChunkNotLoaded() {
        ServerLevel level = overworldMock();
        // mock 默认 isLoaded 返回 false,模拟区块未加载
        pendingChecks().computeIfAbsent(Level.OVERWORLD, k -> new HashMap<>()).put(CONTROLLER_POS, 1);

        StructureEventHandler.onLevelTick(tickEvent(level, TickEvent.Phase.END));

        assertEquals(20, pendingChecks().get(Level.OVERWORLD).get(CONTROLLER_POS));
    }

    /** 维度队列为空时早返回,不做任何处理(空表保留,仅经 validate 路径清空的表才会被移除). */
    @Test
    void testTickEarlyReturnOnEmptyQueue() {
        ServerLevel level = overworldMock();
        pendingChecks().put(Level.OVERWORLD, new HashMap<>());

        StructureEventHandler.onLevelTick(tickEvent(level, TickEvent.Phase.END));

        assertTrue(pendingChecks().containsKey(Level.OVERWORLD));
        verify(level, never()).isLoaded(any(BlockPos.class));
    }

    /** 区块加载事件:位于刚加载区块内的已登记控制器被调度验证. */
    @Test
    void testChunkLoadSchedulesControllersInLoadedChunk() {
        ServerLevel level = overworldMock();
        ControllerIndex controllerIndex = new ControllerIndex();
        controllerIndex.add(CONTROLLER_POS); // 位于区块 (0,0)
        ComputationCoreIndex coreIndex = new ComputationCoreIndex();
        DimensionDataStorage storage = mock(DimensionDataStorage.class);
        when(level.getDataStorage()).thenReturn(storage);
        doReturn(controllerIndex).when(storage).computeIfAbsent(any(), any(),
                eq("ae2enhanced_controller_index"));
        doReturn(coreIndex).when(storage).computeIfAbsent(any(), any(),
                eq("ae2enhanced_computation_core_index"));
        ChunkAccess chunk = mock(ChunkAccess.class);
        when(chunk.getPos()).thenReturn(new ChunkPos(0, 0));
        when(chunk.getWorldForge()).thenReturn(level);

        StructureEventHandler.onChunkLoad(new ChunkEvent.Load(chunk, true));

        assertEquals(20, pendingChecks().get(Level.OVERWORLD).get(CONTROLLER_POS));
    }

    /** 区块加载事件:其他区块内的控制器不被调度,避免触发同步区块加载死锁. */
    @Test
    void testChunkLoadSkipsControllersOutsideLoadedChunk() {
        ServerLevel level = overworldMock();
        ControllerIndex controllerIndex = new ControllerIndex();
        controllerIndex.add(CONTROLLER_POS); // 区块 (0,0)
        DimensionDataStorage storage = mock(DimensionDataStorage.class);
        when(level.getDataStorage()).thenReturn(storage);
        doReturn(controllerIndex).when(storage).computeIfAbsent(any(), any(),
                eq("ae2enhanced_controller_index"));
        doReturn(new ComputationCoreIndex()).when(storage).computeIfAbsent(any(), any(),
                eq("ae2enhanced_computation_core_index"));
        ChunkAccess chunk = mock(ChunkAccess.class);
        when(chunk.getPos()).thenReturn(new ChunkPos(3, 3));
        when(chunk.getWorldForge()).thenReturn(level);

        StructureEventHandler.onChunkLoad(new ChunkEvent.Load(chunk, true));

        assertTrue(pendingChecks().isEmpty());
    }

    /** 非服务端 level 的邻居/破坏/区块事件均为无操作. */
    @Test
    void testNonServerLevelEventsAreNoOp() {
        Level level = mock(Level.class);

        StructureEventHandler.onNeighborNotify(new BlockEvent.NeighborNotifyEvent(level, CONTROLLER_POS,
                Blocks.STONE.defaultBlockState(), EnumSet.noneOf(Direction.class), false));
        StructureEventHandler.onBlockBreak(new BlockEvent.BreakEvent(level, CONTROLLER_POS,
                Blocks.STONE.defaultBlockState(), mock(net.minecraft.world.entity.player.Player.class)));
        // 单参构造经 ChunkAccess#getWorldForge 取 level,mock 返回 null 时同样命中非服务端分支
        StructureEventHandler.onChunkLoad(new ChunkEvent.Load(mock(ChunkAccess.class), true));

        assertTrue(pendingChecks().isEmpty());
    }
}
