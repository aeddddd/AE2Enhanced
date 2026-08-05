package com.github.aeddddd.ae2enhanced.structure;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.block.HyperdimensionalControllerBlock;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.RegistryObjectTestInjector;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HyperdimensionalStructure} 单元测试.
 * <p>静态坐标集合为纯数据,直接断言内容;验证逻辑通过注入测试方块后
 * 以 mock Level 覆盖.</p>
 */
class HyperdimensionalStructureTest {

    private static HyperdimensionalControllerBlock controller;
    private static Block core;
    private static Block casing;

    @BeforeAll
    static void setUp() {
        MinecraftTestBootstrap.bootstrap();
        controller = new HyperdimensionalControllerBlock(BlockBehaviour.Properties.of());
        core = Blocks.SEA_LANTERN;
        casing = Blocks.IRON_BLOCK;
        RegistryObjectTestInjector.inject(ModBlocks.HYPERDIMENSIONAL_CONTROLLER, controller);
        RegistryObjectTestInjector.inject(ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE, core);
        RegistryObjectTestInjector.inject(ModBlocks.HYPERDIMENSIONAL_CASING, casing);
        HyperdimensionalStructure.init();
    }

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
     * 按指定控制器朝向搭建完整结构的 世界坐标 -> 方块状态 表.
     */
    private static Map<BlockPos, BlockState> buildCompleteStates(BlockPos controllerPos, Direction facing) {
        BlockState controllerState = controller.defaultBlockState()
                .setValue(HyperdimensionalControllerBlock.FACING, facing);
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (BlockPos rel : HyperdimensionalStructure.CONTROLLER_SET) {
            states.put(controllerPos.offset(StructureUtils.rotate(rel, facing)), controllerState);
        }
        for (BlockPos rel : HyperdimensionalStructure.CORE_SET) {
            states.put(controllerPos.offset(StructureUtils.rotate(rel, facing)), core.defaultBlockState());
        }
        for (BlockPos rel : HyperdimensionalStructure.CASING_SET) {
            states.put(controllerPos.offset(StructureUtils.rotate(rel, facing)), casing.defaultBlockState());
        }
        return states;
    }

    // ---------- 静态坐标集合 ----------

    @Test
    void testStaticStructureSets() {
        assertEquals(Set.of(BlockPos.ZERO), HyperdimensionalStructure.CONTROLLER_SET);

        assertEquals(5, HyperdimensionalStructure.CORE_SET.size());
        assertTrue(HyperdimensionalStructure.CORE_SET.contains(new BlockPos(0, 0, 3)));
        assertTrue(HyperdimensionalStructure.CORE_SET.contains(new BlockPos(-1, 0, 2)));

        assertEquals(15, HyperdimensionalStructure.CASING_SET.size());
        assertTrue(HyperdimensionalStructure.CASING_SET.contains(new BlockPos(2, 0, 3)));
        assertTrue(HyperdimensionalStructure.CASING_SET.contains(new BlockPos(-2, 0, 1)));

        assertEquals(21, HyperdimensionalStructure.ALL_SET.size());
    }

    @Test
    void testStructureSetsAreDisjoint() {
        // 控制器原点不属于核心或外壳
        assertFalse(HyperdimensionalStructure.CORE_SET.contains(BlockPos.ZERO));
        assertFalse(HyperdimensionalStructure.CASING_SET.contains(BlockPos.ZERO));
        // 核心与外壳无重叠,否则 ALL_SET 规模会对不上
        Set<BlockPos> overlap = new java.util.HashSet<>(HyperdimensionalStructure.CORE_SET);
        overlap.retainAll(HyperdimensionalStructure.CASING_SET);
        assertTrue(overlap.isEmpty());
    }

    @Test
    void testStructureSetsAreUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> HyperdimensionalStructure.CONTROLLER_SET.add(new BlockPos(1, 0, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> HyperdimensionalStructure.CORE_SET.add(new BlockPos(1, 0, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> HyperdimensionalStructure.CASING_SET.add(new BlockPos(1, 0, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> HyperdimensionalStructure.ALL_SET.add(new BlockPos(1, 0, 0)));
    }

    // ---------- init ----------

    @Test
    void testInitCreatesStructureInstance() {
        assertNotNull(HyperdimensionalStructure.getInstance());
        assertSame(HyperdimensionalStructure.getAllSet(), HyperdimensionalStructure.ALL_SET);
    }

    @Test
    void testInitIsIdempotent() {
        AbstractMultiblockStructure first = HyperdimensionalStructure.getInstance();
        HyperdimensionalStructure.init();
        assertSame(first, HyperdimensionalStructure.getInstance());
    }

    @Test
    void testUninitializedAccessThrows() throws Exception {
        // 以独立 ClassLoader 重载类（未调用 init）,getInstance 应抛出 IllegalStateException
        Class<?> reloaded = StructureClassReloader.reload(HyperdimensionalStructure.class, "__no_such_resource__",
                null);
        Method getInstance = reloaded.getMethod("getInstance");
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> getInstance.invoke(null));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    // ---------- 基于 mock Level 的验证 ----------

    @Test
    void testGetControllerFacing() {
        Level level = mock(Level.class);
        BlockPos pos = new BlockPos(1, 64, 1);
        when(level.getBlockState(pos)).thenReturn(
                controller.defaultBlockState().setValue(HyperdimensionalControllerBlock.FACING, Direction.EAST));
        assertEquals(Direction.EAST, HyperdimensionalStructure.getControllerFacing(level, pos));

        // 非控制器方块时回退 NORTH
        when(level.getBlockState(pos)).thenReturn(Blocks.STONE.defaultBlockState());
        assertEquals(Direction.NORTH, HyperdimensionalStructure.getControllerFacing(level, pos));
    }

    @Test
    void testValidateCompleteStructureNorth() {
        BlockPos controllerPos = new BlockPos(-30, 70, 12);
        Level level = mockLevel(buildCompleteStates(controllerPos, Direction.NORTH));

        ValidationResult result = HyperdimensionalStructure.validateDetailed(level, controllerPos);
        assertTrue(result.passed());
        assertTrue(result.missing.isEmpty());
        assertTrue(result.allChunksLoaded());
        assertTrue(HyperdimensionalStructure.validate(level, controllerPos));
    }

    @Test
    void testValidateCompleteStructureEast() {
        BlockPos controllerPos = new BlockPos(-30, 70, 12);
        Level level = mockLevel(buildCompleteStates(controllerPos, Direction.EAST));
        assertTrue(HyperdimensionalStructure.validate(level, controllerPos));

        // 朝向 EAST 时按 NORTH 摆放不能通过
        Level wrong = mockLevel(buildCompleteStates(controllerPos, Direction.NORTH));
        // 将控制器方块替换为 EAST 朝向
        Level wrongFacing = mock(Level.class);
        when(wrongFacing.getBlockState(any())).thenAnswer(inv -> {
            BlockPos pos = inv.getArgument(0);
            if (pos.equals(controllerPos)) {
                return controller.defaultBlockState()
                        .setValue(HyperdimensionalControllerBlock.FACING, Direction.EAST);
            }
            return wrong.getBlockState(pos);
        });
        when(wrongFacing.isLoaded(any(BlockPos.class))).thenReturn(true);
        assertFalse(HyperdimensionalStructure.validate(wrongFacing, controllerPos));
    }

    @Test
    void testValidateReportsMissingBlock() {
        BlockPos controllerPos = new BlockPos(-30, 70, 12);
        Map<BlockPos, BlockState> states = buildCompleteStates(controllerPos, Direction.NORTH);
        // 拆掉一个外壳方块
        states.put(controllerPos.offset(2, 0, 3), Blocks.AIR.defaultBlockState());
        Level level = mockLevel(states);

        ValidationResult result = HyperdimensionalStructure.validateDetailed(level, controllerPos);
        assertFalse(result.passed());
        assertEquals(Map.of(casing, 1), result.missing);
    }

    @Test
    void testValidateUnloadedChunk() {
        BlockPos controllerPos = new BlockPos(-30, 70, 12);
        Level level = mockLevel(buildCompleteStates(controllerPos, Direction.NORTH));
        // 核心方块所在区块未加载
        when(level.isLoaded(controllerPos.offset(0, 0, 3))).thenReturn(false);

        ValidationResult result = HyperdimensionalStructure.validateDetailed(level, controllerPos);
        assertFalse(result.passed());
        assertFalse(result.allChunksLoaded());
        assertEquals(Map.of(core, 1), result.missing);
    }

    @Test
    void testGetMissingMapSkipsUnloadedPositions() {
        BlockPos controllerPos = new BlockPos(-30, 70, 12);
        Map<BlockPos, BlockState> states = buildCompleteStates(controllerPos, Direction.NORTH);
        // (2,0,3) 外壳缺失计入,核心 (0,0,3) 未加载跳过
        states.put(controllerPos.offset(2, 0, 3), Blocks.AIR.defaultBlockState());
        Level level = mockLevel(states);
        when(level.isLoaded(controllerPos.offset(0, 0, 3))).thenReturn(false);

        Map<Block, Integer> missing = HyperdimensionalStructure.getMissingMap(level, controllerPos);
        assertEquals(Map.of(casing, 1), missing);
    }

    @Test
    void testGetExpectedBlocksAppliesRotation() {
        BlockPos controllerPos = new BlockPos(-30, 70, 12);
        Level level = mock(Level.class);
        when(level.getBlockState(controllerPos)).thenReturn(
                controller.defaultBlockState().setValue(HyperdimensionalControllerBlock.FACING, Direction.EAST));

        Set<Map.Entry<BlockPos, Block>> expected = HyperdimensionalStructure.getExpectedBlocks(level, controllerPos);
        assertEquals(21, expected.size());
        // EAST 旋转: (x,y,z) -> (-z,y,x),核心 (0,0,1) -> (-1,0,0)
        assertTrue(expected.stream().anyMatch(e -> e.getKey().equals(new BlockPos(-1, 0, 0))
                && e.getValue() == core));
        assertTrue(expected.stream().anyMatch(e -> e.getKey().equals(BlockPos.ZERO)
                && e.getValue() == controller));
    }
}
