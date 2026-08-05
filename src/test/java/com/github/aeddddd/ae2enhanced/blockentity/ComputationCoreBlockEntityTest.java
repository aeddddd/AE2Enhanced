package com.github.aeddddd.ae2enhanced.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import appeng.api.util.AECableType;

/**
 * {@link ComputationCoreBlockEntity} 单元测试.
 * <p>离线环境主节点不会 ready,虚拟 CPU 池无法真正创建（createCpu 提前返回 null）,
 * 因此此处覆盖：初始状态、成形/投影状态机、虚拟 CPU 可用性查询、spawnSubCpu 的
 * 离线分支、NBT 与客户端同步标签往返,以及 serverTick 的离线安全分支（含池自愈路径）.</p>
 */
class ComputationCoreBlockEntityTest {

    private static final BlockPos POS = new BlockPos(10, 64, -10);

    @BeforeAll
    static void bootstrap() {
        BlockEntityTestSupport.bootstrap();
    }

    private static ComputationCoreBlockEntity newController() {
        return new ComputationCoreBlockEntity(POS, Blocks.STONE.defaultBlockState());
    }

    /** 初始默认状态. */
    @Test
    void testInitialState() {
        ComputationCoreBlockEntity be = newController();
        assertFalse(be.isFormed());
        assertFalse(be.isShowingStructureProjection());
        assertEquals(0, be.getParallelLimit());
        assertEquals(0, be.getActiveJobs());
        assertEquals(0, be.getClientPoolSize());
        assertEquals(0, be.getClientActiveJobs());
        assertFalse(be.isClientNetworkActive());
        assertEquals(POS, be.getControllerPos());
        assertNull(be.getStructure());
        // 主节点已创建但未入世界,底层 IGridNode 尚不存在
        assertNull(be.getActionSourceNode());
    }

    /** 线缆连接类型恒为 SMART. */
    @Test
    void testCableConnectionType() {
        ComputationCoreBlockEntity be = newController();
        for (Direction dir : Direction.values()) {
            assertEquals(AECableType.SMART, be.getCableConnectionType(dir));
        }
    }

    /** 成形状态机与虚拟 CPU 可用性：离线 assemble 拿不到结构,并行上限保持 0. */
    @Test
    void testAssembleDisassemble() {
        ComputationCoreBlockEntity be = newController();
        assertFalse(be.isVirtualCpuAvailable());
        assertEquals(0, be.getVirtualCpuParallelLimit());

        be.disassemble();
        assertFalse(be.isFormed());

        be.assemble();
        assertTrue(be.isFormed());
        assertTrue(be.isVirtualCpuAvailable());
        // level 为 null,onAssemble 无法校验结构,parallelLimit 不被赋值
        assertEquals(0, be.getParallelLimit());
        assertEquals(0, be.getVirtualCpuParallelLimit());

        be.assemble();
        assertTrue(be.isFormed());

        be.disassemble();
        assertFalse(be.isFormed());
        assertFalse(be.isVirtualCpuAvailable());
    }

    /** 结构投影切换：未成形翻转,成形强制关闭. */
    @Test
    void testToggleStructureProjection() {
        ComputationCoreBlockEntity be = newController();
        be.toggleStructureProjection();
        assertTrue(be.isShowingStructureProjection());
        be.toggleStructureProjection();
        assertFalse(be.isShowingStructureProjection());

        be.toggleStructureProjection();
        be.setFormed(true);
        be.toggleStructureProjection();
        assertFalse(be.isShowingStructureProjection());
    }

    /** spawnSubCpu：未成形直接拒绝；成形但节点未就绪（离线）返回 null. */
    @Test
    void testSpawnSubCpuOffline() {
        ComputationCoreBlockEntity be = newController();
        assertNull(be.spawnSubCpu());

        be.setFormed(true);
        // level 为 null → createCpu 返回 null
        assertNull(be.spawnSubCpu());

        // mock 服务端 level → 主节点未 ready,仍返回 null
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        be.setLevel(level);
        assertNull(be.spawnSubCpu());
        assertEquals(0, be.getUpdateTag().getInt("clientPoolSize"));
    }

    /** NBT 往返：成形/投影/并行上限持久化,池大小仅作记录. */
    @Test
    void testNbtRoundTrip() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putBoolean("showProjection", true);
        tag.putInt("parallelLimit", 16384);
        tag.putInt("poolSize", 4);

        ComputationCoreBlockEntity be = newController();
        be.loadTag(tag);
        assertTrue(be.isFormed());
        assertTrue(be.isShowingStructureProjection());
        assertEquals(16384, be.getParallelLimit());
        // 池不随 NBT 恢复,等待 serverTick 重建
        assertEquals(0, be.getActiveJobs());

        CompoundTag saved = new CompoundTag();
        be.saveAdditional(saved);
        assertTrue(saved.getBoolean("formed"));
        assertTrue(saved.getBoolean("showProjection"));
        assertEquals(16384, saved.getInt("parallelLimit"));
        assertEquals(0, saved.getInt("poolSize"));
    }

    /** getUpdateTag/handleUpdateTag 客户端显示数据往返. */
    @Test
    void testUpdateTagRoundTrip() {
        ComputationCoreBlockEntity source = newController();
        source.setFormed(true);

        CompoundTag updateTag = source.getUpdateTag();
        assertTrue(updateTag.getBoolean("formed"));
        assertEquals(0, updateTag.getInt("clientPoolSize"));
        assertEquals(0, updateTag.getInt("clientActiveJobs"));
        // 节点离线不活跃
        assertFalse(updateTag.getBoolean("clientNetworkActive"));

        ComputationCoreBlockEntity target = newController();
        target.handleUpdateTag(updateTag);
        assertTrue(target.isFormed());
        assertEquals(0, target.getClientPoolSize());
        assertEquals(0, target.getClientActiveJobs());
        assertFalse(target.isClientNetworkActive());
    }

    /** handleUpdateTag 仅更新出现的键（客户端显示字段）. */
    @Test
    void testHandleUpdateTagPartialKeys() {
        ComputationCoreBlockEntity be = newController();
        CompoundTag tag = new CompoundTag();
        tag.putInt("clientPoolSize", 9);
        tag.putInt("clientActiveJobs", 4);
        tag.putBoolean("clientNetworkActive", true);
        be.handleUpdateTag(tag);
        assertEquals(9, be.getClientPoolSize());
        assertEquals(4, be.getClientActiveJobs());
        assertTrue(be.isClientNetworkActive());
    }

    /** serverTick 离线安全：level 为 null 直接返回；成形 + mock level 走池自愈路径,节点未就绪时池保持为空. */
    @Test
    void testServerTickOffline() {
        ComputationCoreBlockEntity be = newController();
        be.serverTick();
        assertFalse(be.isFormed());

        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        be.setLevel(level);
        be.setFormed(true);

        // 池为空 → 自愈重建 → 节点未 ready → createCpu 返回 null,池保持为空,可重复 tick
        be.serverTick();
        be.serverTick();
        assertTrue(be.isFormed());
        assertEquals(0, be.getUpdateTag().getInt("clientPoolSize"));
    }

    /** setRemoved 离线安全：未成形且无虚拟 CPU 时直接走节点销毁. */
    @Test
    void testSetRemovedOffline() {
        ComputationCoreBlockEntity be = newController();
        be.setRemoved();
        assertFalse(be.isFormed());
    }
}
