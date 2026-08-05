package com.github.aeddddd.ae2enhanced.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import appeng.api.storage.IStorageMounts;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;

/**
 * {@link HyperdimensionalControllerBlockEntity} 单元测试.
 * <p>离线环境无真实服务端与 AE2 网格,存储对象（HyperdimensionalStorage）无法初始化,
 * 因此此处覆盖：初始状态、Nexus UUID、成形/投影状态机、NBT 与客户端同步标签往返、
 * 渲染包围盒的朝向旋转、mountInventories 的离线分支,以及 tick 的早退分支.</p>
 */
class HyperdimensionalControllerBlockEntityTest {

    private static final BlockPos POS = new BlockPos(10, 64, -10);

    @BeforeAll
    static void bootstrap() {
        BlockEntityTestSupport.bootstrap();
    }

    private static HyperdimensionalControllerBlockEntity newController() {
        return new HyperdimensionalControllerBlockEntity(POS, Blocks.STONE.defaultBlockState());
    }

    /** 初始默认状态. */
    @Test
    void testInitialState() {
        HyperdimensionalControllerBlockEntity be = newController();
        assertFalse(be.isFormed());
        assertFalse(be.isShowingStructureProjection());
        assertFalse(be.isNetworkActive());
        assertFalse(be.isNetworkPowered());
        assertFalse(be.isSafeMode());
        assertEquals(0, be.getStorageTypes());
        assertEquals(0L, be.getStorageTotal());
        assertEquals("0", be.getStorageTotalRaw());
        assertNull(be.getNexusId());
        assertEquals(POS, be.getControllerPos());
        assertNull(be.getStructure());
    }

    /** 线缆连接类型恒为 SMART. */
    @Test
    void testCableConnectionType() {
        HyperdimensionalControllerBlockEntity be = newController();
        for (Direction dir : Direction.values()) {
            assertEquals(AECableType.SMART, be.getCableConnectionType(dir));
        }
    }

    /** Nexus UUID 设置与读取. */
    @Test
    void testNexusIdAccessors() {
        HyperdimensionalControllerBlockEntity be = newController();
        UUID id = UUID.randomUUID();
        be.setNexusId(id);
        assertEquals(id, be.getNexusId());
    }

    /** 成形状态机：assemble/disassemble 幂等；离线 assemble 不分配 Nexus UUID（initStorage 需要服务端 level）. */
    @Test
    void testAssembleDisassemble() {
        HyperdimensionalControllerBlockEntity be = newController();
        be.disassemble();
        assertFalse(be.isFormed());

        be.assemble();
        assertTrue(be.isFormed());
        // level 为 null,initStorage 提前返回,不生成 nexusId
        assertNull(be.getNexusId());

        be.assemble();
        assertTrue(be.isFormed());

        be.disassemble();
        assertFalse(be.isFormed());
    }

    /** 结构投影切换：未成形翻转,成形强制关闭. */
    @Test
    void testToggleStructureProjection() {
        HyperdimensionalControllerBlockEntity be = newController();
        be.toggleStructureProjection();
        assertTrue(be.isShowingStructureProjection());
        be.toggleStructureProjection();
        assertFalse(be.isShowingStructureProjection());

        be.toggleStructureProjection();
        be.setFormed(true);
        be.toggleStructureProjection();
        assertFalse(be.isShowingStructureProjection());
    }

    /** NBT 往返：Nexus UUID、成形/投影与全部客户端统计字段. */
    @Test
    void testNbtRoundTrip() {
        HyperdimensionalControllerBlockEntity source = newController();
        UUID id = UUID.randomUUID();
        source.setNexusId(id);
        // 客户端统计字段仅经同步标签写入
        CompoundTag sync = new CompoundTag();
        sync.putBoolean("formed", true);
        sync.putBoolean("showProjection", true);
        sync.putBoolean("networkActive", true);
        sync.putBoolean("networkPowered", true);
        sync.putInt("storageTypes", 7);
        sync.putLong("storageTotal", 123456789L);
        sync.putBoolean("safeMode", true);
        source.handleUpdateTag(sync);
        // 注意 handleUpdateTag 经 super.load → loadTag 会把缺失的 nexusId 重置为 null,需在之后再设置
        source.setNexusId(id);

        CompoundTag tag = new CompoundTag();
        source.saveAdditional(tag);
        assertTrue(tag.hasUUID("nexusId"));

        HyperdimensionalControllerBlockEntity target = newController();
        target.loadTag(tag);
        assertEquals(id, target.getNexusId());
        assertTrue(target.isFormed());
        assertTrue(target.isShowingStructureProjection());
        assertTrue(target.isNetworkActive());
        assertTrue(target.isNetworkPowered());
        assertEquals(7, target.getStorageTypes());
        assertEquals(123456789L, target.getStorageTotal());
        assertEquals("123456789", target.getStorageTotalRaw());
        assertTrue(target.isSafeMode());
    }

    /** NBT 中无 Nexus UUID 时加载结果为 null. */
    @Test
    void testLoadWithoutNexusId() {
        HyperdimensionalControllerBlockEntity target = newController();
        target.setNexusId(UUID.randomUUID());
        target.loadTag(new CompoundTag());
        assertNull(target.getNexusId());
    }

    /** getUpdateTag/handleUpdateTag 客户端同步往返. */
    @Test
    void testUpdateTagRoundTrip() {
        HyperdimensionalControllerBlockEntity source = newController();
        CompoundTag sync = new CompoundTag();
        sync.putBoolean("formed", true);
        sync.putInt("storageTypes", 3);
        sync.putLong("storageTotal", 42L);
        sync.putBoolean("safeMode", true);
        source.handleUpdateTag(sync);

        CompoundTag updateTag = source.getUpdateTag();
        assertTrue(updateTag.getBoolean("formed"));
        assertEquals(3, updateTag.getInt("storageTypes"));
        assertEquals(42L, updateTag.getLong("storageTotal"));
        assertTrue(updateTag.getBoolean("safeMode"));

        HyperdimensionalControllerBlockEntity target = newController();
        target.handleUpdateTag(updateTag);
        assertTrue(target.isFormed());
        assertEquals(3, target.getStorageTypes());
        assertEquals(42L, target.getStorageTotal());
        assertTrue(target.isSafeMode());
        assertFalse(target.isNetworkActive());
    }

    /** 渲染包围盒：level 为 null 时按 NORTH 朝向计算（结构中心上方,半径 5.5）. */
    @Test
    void testRenderBoundingBoxDefaultFacing() {
        HyperdimensionalControllerBlockEntity be = newController();
        AABB box = be.getRenderBoundingBox();
        // NORTH: 局部中心 (0,3.5,2) 不旋转 → 世界中心 (x+0.5, y+4.0, z+2.5)
        assertEquals(POS.getX() + 0.5, (box.minX + box.maxX) / 2, 1e-6);
        assertEquals(POS.getY() + 4.0, (box.minY + box.maxY) / 2, 1e-6);
        assertEquals(POS.getZ() + 2.5, (box.minZ + box.maxZ) / 2, 1e-6);
        assertEquals(5.5, (box.maxX - box.minX) / 2, 1e-6);
    }

    /** 渲染包围盒随 FACING 旋转：覆盖 SOUTH/EAST/WEST 旋转分支. */
    @Test
    void testRenderBoundingBoxRotatesWithFacing() {
        HyperdimensionalControllerBlockEntity be = newController();
        Level level = mock(Level.class);
        be.setLevel(level);

        // EAST: (x,y,z) -> (-z,y,x),局部 (0,3.5,2) -> (-2,3.5,0)
        when(level.getBlockState(POS)).thenReturn(
                Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        AABB east = be.getRenderBoundingBox();
        assertEquals(POS.getX() - 1.5, (east.minX + east.maxX) / 2, 1e-6);
        assertEquals(POS.getZ() + 0.5, (east.minZ + east.maxZ) / 2, 1e-6);

        // SOUTH: (x,y,z) -> (-x,y,-z),局部 (0,3.5,2) -> (0,3.5,-2)
        when(level.getBlockState(POS)).thenReturn(
                Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        AABB south = be.getRenderBoundingBox();
        assertEquals(POS.getX() + 0.5, (south.minX + south.maxX) / 2, 1e-6);
        assertEquals(POS.getZ() - 1.5, (south.minZ + south.maxZ) / 2, 1e-6);

        // WEST: (x,y,z) -> (z,y,-x),局部 (0,3.5,2) -> (2,3.5,0)
        when(level.getBlockState(POS)).thenReturn(
                Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        AABB west = be.getRenderBoundingBox();
        assertEquals(POS.getX() + 2.5, (west.minX + west.maxX) / 2, 1e-6);
        assertEquals(POS.getZ() + 0.5, (west.minZ + west.maxZ) / 2, 1e-6);
    }

    /** mountInventories：未成形或存储未初始化（离线）时不挂载任何东西. */
    @Test
    void testMountInventoriesOffline() {
        HyperdimensionalControllerBlockEntity be = newController();
        IStorageMounts mounts = mock(IStorageMounts.class);

        be.mountInventories(mounts);
        // 成形但 meStorage 为 null（离线无服务端,initStorage 未执行）
        be.setFormed(true);
        be.mountInventories(mounts);

        verify(mounts, never()).mount(any(MEStorage.class), anyInt());
        verify(mounts, never()).mount(any(MEStorage.class));
    }

    /** serverTick 离线安全：level 为 null 直接返回；mock 服务端 level 下未成形走完全部节流分支无异常. */
    @Test
    void testServerTickOffline() {
        HyperdimensionalControllerBlockEntity be = newController();
        be.serverTick();
        assertFalse(be.isFormed());

        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        be.setLevel(level);

        be.serverTick();
        be.serverTick();
        assertFalse(be.isFormed());
        assertFalse(be.isNetworkActive());
        assertEquals(0, be.getStorageTypes());
    }

    /** clientTick：未成形或未激活时早退,不生成粒子. */
    @Test
    void testClientTickEarlyReturn() {
        HyperdimensionalControllerBlockEntity be = newController();
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        be.setLevel(level);

        // 未成形 → 早退（level.random 在 mock 上为 null,走到粒子分支会 NPE）
        be.clientTick();
        verify(level, never()).addParticle(any(), any(Double.class), any(Double.class), any(Double.class),
                any(Double.class), any(Double.class), any(Double.class));
    }

    /** setRemoved/onChunkUnloaded 离线安全（未成形、无存储）. */
    @Test
    void testRemovalOffline() {
        HyperdimensionalControllerBlockEntity be = newController();
        be.setRemoved();

        HyperdimensionalControllerBlockEntity be2 = newController();
        be2.onChunkUnloaded();
        assertFalse(be2.isFormed());
    }
}
