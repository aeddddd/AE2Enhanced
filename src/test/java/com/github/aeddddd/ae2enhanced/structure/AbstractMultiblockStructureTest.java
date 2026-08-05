package com.github.aeddddd.ae2enhanced.structure;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.multiblock.IMultiblockController;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * {@link AbstractMultiblockStructure} 单元测试.
 * <p>通过固定朝向的测试子类与 mock 的 {@link Level} 覆盖验证、缺失统计、
 * 旋转映射、装配/拆解通知与一键放置逻辑.</p>
 */
class AbstractMultiblockStructureTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private static final BlockPos CONTROLLER = new BlockPos(10, 64, -10);

    /**
     * 固定朝向的测试用结构实现,把旋转输入与 Level 解耦.
     */
    private static class TestStructure extends AbstractMultiblockStructure {

        private final Direction rotation;

        TestStructure(StructureDefinition definition, Direction rotation) {
            super(definition);
            this.rotation = rotation;
        }

        @Override
        public Direction getRotation(Level level, BlockPos controllerPos) {
            return rotation;
        }

        /**
         * 暴露 protected 辅助方法以便直接测试.
         */
        static Direction exposeGetBlockFacing(Level level, BlockPos pos, Block expectedBlock) {
            return getBlockFacing(level, pos, expectedBlock);
        }
    }

    /**
     * 构造按查表返回方块状态的 mock Level,未登记的位置一律为空气,区块一律已加载.
     */
    private static Level mockLevel(Map<BlockPos, BlockState> states) {
        Level level = mock(Level.class);
        when(level.getBlockState(any())).thenAnswer(inv -> {
            BlockPos pos = inv.getArgument(0);
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        });
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.isClientSide()).thenReturn(false);
        return level;
    }

    /**
     * 构造同时实现 {@link IMultiblockController} 的方块实体 mock.
     */
    private static BlockEntity mockControllerBlockEntity(Level level) {
        BlockEntity be = mock(BlockEntity.class, withSettings().extraInterfaces(IMultiblockController.class));
        when(level.getBlockEntity(CONTROLLER)).thenReturn(be);
        return be;
    }

    /**
     * 测试用定义:石头 (0,0,0)/(1,0,0),泥土 (0,1,0).
     */
    private static StructureDefinition sampleDefinition() {
        return StructureDefinition.builder()
                .addAll(Blocks.STONE, Set.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)))
                .add(Blocks.DIRT, new BlockPos(0, 1, 0))
                .build();
    }

    @Test
    void testValidatePassesWhenComplete() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        Level level = mockLevel(Map.of(
                CONTROLLER, Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(1, 0, 0), Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(0, 1, 0), Blocks.DIRT.defaultBlockState()));

        ValidationResult result = structure.validateDetailed(level, CONTROLLER);
        assertTrue(result.passed());
        assertTrue(result.missing.isEmpty());
        assertTrue(result.allChunksLoaded());
        assertTrue(structure.validate(level, CONTROLLER));
    }

    @Test
    void testValidateCountsMissingByBlock() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        // 一个石头位置为空气,泥土位置放错方块
        Level level = mockLevel(Map.of(
                CONTROLLER, Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(0, 1, 0), Blocks.STONE.defaultBlockState()));

        ValidationResult result = structure.validateDetailed(level, CONTROLLER);
        assertFalse(result.passed());
        assertTrue(result.allChunksLoaded());
        assertEquals(Map.of(Blocks.STONE, 1, Blocks.DIRT, 1), result.missing);
        assertFalse(structure.validate(level, CONTROLLER));
    }

    @Test
    void testValidateUnloadedChunkFailsAndCountsMissing() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        Level level = mockLevel(Map.of(
                CONTROLLER, Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(1, 0, 0), Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(0, 1, 0), Blocks.DIRT.defaultBlockState()));
        // 泥土所在区块未加载:视为缺失且 allChunksLoaded 为 false
        when(level.isLoaded(CONTROLLER.offset(0, 1, 0))).thenReturn(false);

        ValidationResult result = structure.validateDetailed(level, CONTROLLER);
        assertFalse(result.passed());
        assertFalse(result.allChunksLoaded());
        assertEquals(Map.of(Blocks.DIRT, 1), result.missing);
    }

    @Test
    void testValidateAppliesRotation() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.EAST);
        // EAST 旋转: (x,y,z) -> (-z,y,x),即 (1,0,0) -> (0,0,1),垂直位置不变
        Level rotated = mockLevel(Map.of(
                CONTROLLER, Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(0, 0, 1), Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(0, 1, 0), Blocks.DIRT.defaultBlockState()));
        assertTrue(structure.validate(rotated, CONTROLLER));

        // 未按朝向摆放时验证失败
        Level unrotated = mockLevel(Map.of(
                CONTROLLER, Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(1, 0, 0), Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(0, 1, 0), Blocks.DIRT.defaultBlockState()));
        assertFalse(structure.validate(unrotated, CONTROLLER));
    }

    @Test
    void testGetMissingMapSkipsUnloadedPositions() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        Level level = mockLevel(Map.of(
                CONTROLLER, Blocks.STONE.defaultBlockState()));
        // (1,0,0) 放错计入缺失,(0,1,0) 未加载则跳过
        when(level.isLoaded(CONTROLLER.offset(0, 1, 0))).thenReturn(false);

        Map<Block, Integer> missing = structure.getMissingMap(level, CONTROLLER);
        assertEquals(Map.of(Blocks.STONE, 1), missing);
    }

    @Test
    void testGetRequiredMaterials() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        assertEquals(Map.of(Blocks.STONE, 2, Blocks.DIRT, 1), structure.getRequiredMaterials());
    }

    @Test
    void testGetAllPositionsDelegatesToDefinition() {
        StructureDefinition def = sampleDefinition();
        TestStructure structure = new TestStructure(def, Direction.NORTH);
        assertEquals(def.getAllPositions(), structure.getAllPositions());
    }

    @Test
    void testGetExpectedBlocksAppliesRotation() {
        StructureDefinition def = StructureDefinition.builder()
                .add(Blocks.STONE, new BlockPos(1, 0, 0))
                .add(Blocks.DIRT, new BlockPos(0, 0, 2))
                .build();
        TestStructure structure = new TestStructure(def, Direction.SOUTH);
        // SOUTH 旋转: (x,y,z) -> (-x,y,-z)
        Set<Map.Entry<BlockPos, Block>> result = structure.getExpectedBlocks(mock(Level.class), CONTROLLER);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> e.getKey().equals(new BlockPos(-1, 0, 0))
                && e.getValue() == Blocks.STONE));
        assertTrue(result.stream().anyMatch(e -> e.getKey().equals(new BlockPos(0, 0, -2))
                && e.getValue() == Blocks.DIRT));
    }

    @Test
    void testAssembleAndDisassembleNotifyController() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        Level level = mockLevel(Map.of());
        BlockEntity be = mockControllerBlockEntity(level);

        structure.assemble(level, CONTROLLER);
        verify((IMultiblockController) be).assemble();

        structure.disassemble(level, CONTROLLER);
        verify((IMultiblockController) be).disassemble();
    }

    @Test
    void testAssembleIgnoresNonControllerBlockEntity() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        Level level = mockLevel(Map.of());
        // getBlockEntity 默认返回 null,不应抛异常
        structure.assemble(level, CONTROLLER);
        structure.disassemble(level, CONTROLLER);
    }

    @Test
    void testPlaceMissingBlocksPlacesOnlyMissingAndAssembles() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        // (0,1,0) 泥土已就位,其余缺失
        Level level = mockLevel(Map.of(
                CONTROLLER.offset(0, 1, 0), Blocks.DIRT.defaultBlockState()));
        BlockEntity be = mockControllerBlockEntity(level);

        structure.placeMissingBlocks(level, CONTROLLER, null);

        verify(level).setBlock(CONTROLLER, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        verify(level).setBlock(CONTROLLER.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        verify(level, never()).setBlock(eq(CONTROLLER.offset(0, 1, 0)), any(), anyInt());
        verify((IMultiblockController) be).assemble();
    }

    @Test
    void testTryConsumeAndPlaceAssemblesDirectlyWhenComplete() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        Level level = mockLevel(Map.of(
                CONTROLLER, Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(1, 0, 0), Blocks.STONE.defaultBlockState(),
                CONTROLLER.offset(0, 1, 0), Blocks.DIRT.defaultBlockState()));
        BlockEntity be = mockControllerBlockEntity(level);
        Player player = mock(Player.class);

        assertTrue(structure.tryConsumeAndPlace(level, CONTROLLER, player));
        verify((IMultiblockController) be).assemble();
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test
    void testTryConsumeAndPlaceConsumesInventoryItem() {
        StructureDefinition def = StructureDefinition.builder()
                .add(Blocks.STONE, new BlockPos(1, 0, 0))
                .build();
        TestStructure structure = new TestStructure(def, Direction.NORTH);
        Level level = mockLevel(Map.of());
        Player player = mock(Player.class);
        Inventory inv = new Inventory(player);
        inv.setItem(0, new ItemStack(Blocks.STONE, 1));
        when(player.getInventory()).thenReturn(inv);

        assertTrue(structure.tryConsumeAndPlace(level, CONTROLLER, player));
        assertTrue(inv.getItem(0).isEmpty());
        verify(level).setBlock(CONTROLLER.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Test
    void testTryConsumeAndPlaceFailsWithoutMaterials() {
        StructureDefinition def = StructureDefinition.builder()
                .add(Blocks.STONE, new BlockPos(1, 0, 0))
                .build();
        TestStructure structure = new TestStructure(def, Direction.NORTH);
        Level level = mockLevel(Map.of());
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(new Inventory(player));

        assertFalse(structure.tryConsumeAndPlace(level, CONTROLLER, player));
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test
    void testClientSideSkipsMutatingOperations() {
        TestStructure structure = new TestStructure(sampleDefinition(), Direction.NORTH);
        Level level = mockLevel(Map.of());
        when(level.isClientSide()).thenReturn(true);

        structure.placeMissingBlocks(level, CONTROLLER, null);
        verify(level, never()).setBlock(any(), any(), anyInt());

        assertFalse(structure.tryConsumeAndPlace(level, CONTROLLER, mock(Player.class)));

        structure.assemble(level, CONTROLLER);
        structure.disassemble(level, CONTROLLER);
        verify(level, never()).getBlockEntity(any());
    }

    @Test
    void testGetBlockFacing() {
        Level level = mock(Level.class);
        BlockPos pos = BlockPos.ZERO;
        when(level.getBlockState(pos)).thenReturn(
                Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));

        // 方块匹配时读取 HORIZONTAL_FACING
        assertEquals(Direction.EAST, TestStructure.exposeGetBlockFacing(level, pos, Blocks.FURNACE));
        // 方块不匹配时回退 NORTH
        assertEquals(Direction.NORTH, TestStructure.exposeGetBlockFacing(level, pos, Blocks.STONE));
    }
}
