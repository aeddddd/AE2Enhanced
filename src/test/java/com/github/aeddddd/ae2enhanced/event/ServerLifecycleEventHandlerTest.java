package com.github.aeddddd.ae2enhanced.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.event.server.ServerStoppingEvent;

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;

/**
 * {@link ServerLifecycleEventHandler} 单元测试.
 * <p>关服时遍历各维度的控制器索引,对超维度控制器方块实体执行 flushStorage.
 * {@code HyperdimensionalStorageFile.shutdown()} 的异步执行器在测试 JVM 中本就空闲,
 * 调用安全(后续使用方会惰性重建).</p>
 */
class ServerLifecycleEventHandlerTest {

    private static final BlockPos CONTROLLER_POS = new BlockPos(1, 64, 1);

    /** 构造一个 ControllerIndex 内容为 index 的 mock 维度. */
    private static ServerLevel levelWithIndex(ControllerIndex index) {
        ServerLevel level = mock(ServerLevel.class);
        DimensionDataStorage storage = mock(DimensionDataStorage.class);
        when(level.getDataStorage()).thenReturn(storage);
        doReturn(index).when(storage).computeIfAbsent(any(), any(), anyString());
        return level;
    }

    /** 索引内的超维度控制器在关服时收到 flushStorage. */
    @Test
    void testFlushesHyperdimensionalControllers() {
        ControllerIndex index = new ControllerIndex();
        index.add(CONTROLLER_POS);
        ServerLevel level = levelWithIndex(index);
        HyperdimensionalControllerBlockEntity controller = mock(HyperdimensionalControllerBlockEntity.class);
        when(level.getBlockEntity(CONTROLLER_POS)).thenReturn(controller);
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getAllLevels()).thenReturn(List.of(level));

        ServerLifecycleEventHandler.onServerStopping(new ServerStoppingEvent(server));

        verify(controller).flushStorage();
    }

    /** 索引位置上的非超维度方块实体被跳过. */
    @Test
    void testSkipsNonHyperdimensionalBlockEntity() {
        ControllerIndex index = new ControllerIndex();
        index.add(CONTROLLER_POS);
        ServerLevel level = levelWithIndex(index);
        when(level.getBlockEntity(CONTROLLER_POS)).thenReturn(mock(BlockEntity.class));
        HyperdimensionalControllerBlockEntity controller = mock(HyperdimensionalControllerBlockEntity.class);
        // 另一个维度中不存在该控制器
        ServerLevel emptyLevel = levelWithIndex(new ControllerIndex());
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getAllLevels()).thenReturn(List.of(level, emptyLevel));

        ServerLifecycleEventHandler.onServerStopping(new ServerStoppingEvent(server));

        verify(controller, never()).flushStorage();
        verify(emptyLevel, never()).getBlockEntity(any(BlockPos.class));
    }

    /** 空索引维度不访问任何方块实体. */
    @Test
    void testEmptyIndexDoesNothing() {
        ServerLevel level = levelWithIndex(new ControllerIndex());
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getAllLevels()).thenReturn(List.of(level));

        ServerLifecycleEventHandler.onServerStopping(new ServerStoppingEvent(server));

        verify(level, never()).getBlockEntity(any(BlockPos.class));
    }
}
