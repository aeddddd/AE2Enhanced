package com.github.aeddddd.ae2enhanced.blockentity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * {@link VirtualCraftingCpuBlockEntity} 单元测试.
 * <p>离线环境主节点不会 ready,CraftingCPUCluster 无法真正创建（rebuildCluster 提前返回）,
 * 此处覆盖 serverTick 自愈路径的离线安全分支与 setRemoved 的集群清理路径.</p>
 */
class VirtualCraftingCpuBlockEntityTest {

    private static final BlockPos POS = new BlockPos(4, 66, 8);

    @BeforeAll
    static void bootstrap() {
        BlockEntityTestSupport.bootstrap();
    }

    private static VirtualCraftingCpuBlockEntity newEntity() {
        return new VirtualCraftingCpuBlockEntity(POS, Blocks.STONE.defaultBlockState());
    }

    /** level 为 null 时 serverTick 直接返回. */
    @Test
    void testServerTickWithoutLevel() {
        VirtualCraftingCpuBlockEntity be = newEntity();
        assertDoesNotThrow(be::serverTick);
    }

    /** 集群缺失时自愈重建：节点未就绪则保持无集群,重复 tick 不抛异常. */
    @Test
    void testServerTickRebuildSkippedWhenNodeNotReady() {
        VirtualCraftingCpuBlockEntity be = newEntity();
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        be.setLevel(level);

        // 集群为 null → 触发 rebuildCluster → 主节点未 ready → 提前返回
        assertDoesNotThrow(be::serverTick);
        assertDoesNotThrow(be::serverTick);
    }

    /** 客户端 level 下 serverTick 早退. */
    @Test
    void testServerTickSkipsOnClientSide() {
        VirtualCraftingCpuBlockEntity be = newEntity();
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        be.setLevel(level);
        assertDoesNotThrow(be::serverTick);
    }

    /** setRemoved 离线安全：无集群时仅销毁节点. */
    @Test
    void testSetRemovedOffline() {
        VirtualCraftingCpuBlockEntity be = newEntity();
        assertDoesNotThrow(be::setRemoved);
    }
}
