package com.github.aeddddd.ae2enhanced.structure;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.block.ComputationControllerBlock;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.RegistryObjectTestInjector;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SupercausalStructure} 单元测试.
 * <p>覆盖 cpu_new.json 的加载/解析（含负向用例,通过 {@link StructureClassReloader}
 * 重载类触发静态块）、init 装配、因果锚定计数与并行上限、反向朝向旋转,
 * 以及一键放置时的玩家安全转移逻辑.</p>
 */
class SupercausalStructureTest {

    private static final String RESOURCE_PATH = "data/ae2enhanced/computation_structure/cpu_new.json";

    private static Block tensorCasing;
    private static Block spinorCasing;
    private static Block casingGlass;
    private static Block causalAnchor;
    private static ComputationControllerBlock controller;

    @BeforeAll
    static void setUp() {
        MinecraftTestBootstrap.bootstrap();
        // computeParallel 依赖 COMMON 配置,以默认值加载
        ConfigTestBootstrap.loadDefaults();
        tensorCasing = Blocks.IRON_BLOCK;
        spinorCasing = Blocks.GOLD_BLOCK;
        casingGlass = Blocks.GLASS;
        causalAnchor = Blocks.OBSIDIAN;
        controller = new ComputationControllerBlock(BlockBehaviour.Properties.of());
        RegistryObjectTestInjector.inject(ModBlocks.CONSTANT_TENSOR_FIELD_CASING, tensorCasing);
        RegistryObjectTestInjector.inject(ModBlocks.CONSTANT_SPINOR_FIELD_CASING, spinorCasing);
        RegistryObjectTestInjector.inject(ModBlocks.CASING_GLASS, casingGlass);
        RegistryObjectTestInjector.inject(ModBlocks.CAUSAL_ANCHOR_CORE, causalAnchor);
        RegistryObjectTestInjector.inject(ModBlocks.COMPUTATION_CONTROLLER, controller);
        SupercausalStructure.init();
    }

    /**
     * 直接解析类路径中的结构 JSON.cpu_new.json 以控制器为原点、NORTH 为基准,
     * 加载时坐标不做变换,得到 名称 -> 期望相对坐标集合.
     */
    private static Map<String, Set<BlockPos>> expectedPositionsFromJson() throws IOException {
        Map<String, Set<BlockPos>> expected = new HashMap<>();
        try (InputStream in = SupercausalStructureTest.class.getResourceAsStream("/" + RESOURCE_PATH)) {
            assertNotNull(in);
            JsonObject root = JsonParser
                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("blocks")) {
                JsonObject obj = e.getAsJsonObject();
                expected.computeIfAbsent(obj.get("block").getAsString(), k -> new HashSet<>())
                        .add(new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt()));
            }
        }
        return expected;
    }

    /**
     * 名称 -> 注入的测试方块.
     */
    private static Map<String, Block> blocksByName() {
        Map<String, Block> map = new HashMap<>();
        map.put("tensor_casing", tensorCasing);
        map.put("spinor_casing", spinorCasing);
        map.put("casing_glass", casingGlass);
        map.put("causal_anchor", causalAnchor);
        map.put("controller", controller);
        return map;
    }

    private static byte[] realResourceBytes() throws IOException {
        try (InputStream in = SupercausalStructureTest.class.getResourceAsStream("/" + RESOURCE_PATH)) {
            assertNotNull(in);
            return in.readAllBytes();
        }
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
     * 按指定结构旋转方向搭建完整结构的 世界坐标 -> 方块状态 表.
     *
     * @param rotation 结构旋转方向（即控制器朝向的反方向）
     */
    private static Map<BlockPos, BlockState> buildCompleteStates(BlockPos controllerPos, Direction rotation)
            throws IOException {
        BlockState controllerState = controller.defaultBlockState()
                .setValue(ComputationControllerBlock.FACING, rotation.getOpposite());
        Map<BlockPos, BlockState> states = new HashMap<>();
        Map<String, Block> byName = blocksByName();
        for (Map.Entry<String, Set<BlockPos>> entry : expectedPositionsFromJson().entrySet()) {
            Block block = byName.get(entry.getKey());
            for (BlockPos rel : entry.getValue()) {
                BlockState state = block == controller ? controllerState : block.defaultBlockState();
                states.put(controllerPos.offset(StructureUtils.rotate(rel, rotation)), state);
            }
        }
        return states;
    }

    // ---------- init 与结构定义 ----------

    @Test
    void testInitCreatesStructureInstance() {
        assertNotNull(SupercausalStructure.getInstance());
        assertNotNull(SupercausalStructure.getAllSet());
    }

    @Test
    void testInitIsIdempotent() {
        AbstractMultiblockStructure first = SupercausalStructure.getInstance();
        SupercausalStructure.init();
        assertSame(first, SupercausalStructure.getInstance());
    }

    @Test
    void testRequiredMaterialsMatchJsonCounts() throws IOException {
        Map<String, Set<BlockPos>> expected = expectedPositionsFromJson();
        Map<String, Block> byName = blocksByName();
        Map<Block, Integer> materials = SupercausalStructure.getInstance().getRequiredMaterials();

        Set<BlockPos> union = new HashSet<>();
        for (Map.Entry<String, Set<BlockPos>> entry : expected.entrySet()) {
            assertEquals(Integer.valueOf(entry.getValue().size()), materials.get(byName.get(entry.getKey())),
                    "方块 " + entry.getKey() + " 的数量与 JSON 不一致");
            union.addAll(entry.getValue());
        }
        assertEquals(union, SupercausalStructure.getAllSet());
        // 五个面心因果锚定核心,控制器位于原点
        assertEquals(5, expected.get("causal_anchor").size());
        assertEquals(Set.of(BlockPos.ZERO), expected.get("controller"));
    }

    @Test
    void testUninitializedAccessThrows() throws Exception {
        Class<?> reloaded = StructureClassReloader.reload(SupercausalStructure.class, RESOURCE_PATH,
                realResourceBytes());
        Method getInstance = reloaded.getMethod("getInstance");
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> getInstance.invoke(null));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    // ---------- JSON 加载/解析（独立 ClassLoader 重载） ----------

    @Test
    void testLoadValidJsonKeepsCoordinatesUnchanged() throws Exception {
        Class<?> reloaded = StructureClassReloader.reload(SupercausalStructure.class, RESOURCE_PATH,
                realResourceBytes());
        assertEquals(expectedPositionsFromJson(), StructureClassReloader.readRawPositions(reloaded));
    }

    @Test
    void testLoadMalformedJsonYieldsEmptyPositions() throws Exception {
        Class<?> reloaded = StructureClassReloader.reload(SupercausalStructure.class, RESOURCE_PATH,
                "{ 这不是合法 JSON !!!".getBytes(StandardCharsets.UTF_8));
        assertTrue(StructureClassReloader.readRawPositions(reloaded).isEmpty());
    }

    @Test
    void testLoadMissingBlocksFieldYieldsEmptyPositions() throws Exception {
        Class<?> reloaded = StructureClassReloader.reload(SupercausalStructure.class, RESOURCE_PATH,
                "{\"originIsController\":true}".getBytes(StandardCharsets.UTF_8));
        assertTrue(StructureClassReloader.readRawPositions(reloaded).isEmpty());
    }

    @Test
    void testLoadMissingResourceYieldsEmptyPositions() throws Exception {
        Class<?> reloaded = StructureClassReloader.reload(SupercausalStructure.class, RESOURCE_PATH, null);
        assertTrue(StructureClassReloader.readRawPositions(reloaded).isEmpty());
    }

    @Test
    void testLoadSkipsUnknownBlockNames() throws Exception {
        String json = "{\"blocks\":["
                + "{\"x\":1,\"y\":2,\"z\":3,\"block\":\"tensor_casing\"},"
                + "{\"x\":4,\"y\":5,\"z\":6,\"block\":\"unknown_block\"}]}";
        Class<?> reloaded = StructureClassReloader.reload(SupercausalStructure.class, RESOURCE_PATH,
                json.getBytes(StandardCharsets.UTF_8));
        // 未知名称被跳过,已知名称坐标保持原样
        assertEquals(Map.of("tensor_casing", Set.of(new BlockPos(1, 2, 3))),
                StructureClassReloader.readRawPositions(reloaded));
    }

    @Test
    void testLoadEntryMissingFieldAbortsRemainingEntries() throws Exception {
        // 第二条目缺少 block 字段导致解析异常,已加载的第一条保留,后续条目丢弃
        String json = "{\"blocks\":["
                + "{\"x\":1,\"y\":2,\"z\":3,\"block\":\"tensor_casing\"},"
                + "{\"x\":4,\"y\":5,\"z\":6},"
                + "{\"x\":7,\"y\":8,\"z\":9,\"block\":\"causal_anchor\"}]}";
        Class<?> reloaded = StructureClassReloader.reload(SupercausalStructure.class, RESOURCE_PATH,
                json.getBytes(StandardCharsets.UTF_8));
        assertEquals(Map.of("tensor_casing", Set.of(new BlockPos(1, 2, 3))),
                StructureClassReloader.readRawPositions(reloaded));
    }

    // ---------- 基于 mock Level 的验证 ----------

    @Test
    void testGetControllerFacingReturnsOpposite() {
        Level level = mock(Level.class);
        BlockPos pos = new BlockPos(1, 64, 1);
        // 结构旋转方向取控制器朝向的反方向,使结构向控制器背面延伸
        when(level.getBlockState(pos)).thenReturn(
                controller.defaultBlockState().setValue(ComputationControllerBlock.FACING, Direction.SOUTH));
        assertEquals(Direction.NORTH, SupercausalStructure.getControllerFacing(level, pos));

        when(level.getBlockState(pos)).thenReturn(
                controller.defaultBlockState().setValue(ComputationControllerBlock.FACING, Direction.EAST));
        assertEquals(Direction.WEST, SupercausalStructure.getControllerFacing(level, pos));

        // 非控制器方块时回退 NORTH
        when(level.getBlockState(pos)).thenReturn(Blocks.STONE.defaultBlockState());
        assertEquals(Direction.NORTH, SupercausalStructure.getControllerFacing(level, pos));
    }

    @Test
    void testValidateCompleteStructure() throws IOException {
        BlockPos controllerPos = new BlockPos(50, 70, -60);
        // 控制器 FACING=SOUTH -> 旋转 NORTH,恒等映射
        Level level = mockLevel(buildCompleteStates(controllerPos, Direction.NORTH));

        ValidationResult result = SupercausalStructure.validateDetailed(level, controllerPos);
        assertTrue(result.passed());
        assertTrue(result.missing.isEmpty());
        assertTrue(result.allChunksLoaded());
        assertEquals(5, result.causalAnchorCount());
        // 验证通过时并行上限取配置默认值
        assertEquals(16384, result.parallelLimit());
    }

    @Test
    void testValidateCompleteStructureWithRotation() throws IOException {
        BlockPos controllerPos = new BlockPos(50, 70, -60);
        // 控制器 FACING=EAST -> 旋转 WEST,按 WEST 摆放应通过
        Level level = mockLevel(buildCompleteStates(controllerPos, Direction.WEST));

        ValidationResult result = SupercausalStructure.validateDetailed(level, controllerPos);
        assertTrue(result.passed());
        assertEquals(5, result.causalAnchorCount());
    }

    @Test
    void testValidateMissingAnchorReducesCountAndDisablesParallel() throws IOException {
        BlockPos controllerPos = new BlockPos(50, 70, -60);
        Map<BlockPos, BlockState> states = buildCompleteStates(controllerPos, Direction.NORTH);
        // 拆掉一个因果锚定核心
        BlockPos anchorPos = controllerPos.offset(expectedPositionsFromJson().get("causal_anchor").iterator().next());
        states.put(anchorPos, Blocks.AIR.defaultBlockState());
        Level level = mockLevel(states);

        ValidationResult result = SupercausalStructure.validateDetailed(level, controllerPos);
        assertFalse(result.passed());
        assertEquals(Map.of(causalAnchor, 1), result.missing);
        assertEquals(4, result.causalAnchorCount());
        // 未通过时并行上限为 0
        assertEquals(0, result.parallelLimit());
    }

    @Test
    void testValidateUnloadedChunk() throws IOException {
        BlockPos controllerPos = new BlockPos(50, 70, -60);
        Level level = mockLevel(buildCompleteStates(controllerPos, Direction.NORTH));
        // 一个外壳方块所在区块未加载
        BlockPos unloaded = controllerPos.offset(new BlockPos(-4, -5, 0));
        when(level.isLoaded(unloaded)).thenReturn(false);

        ValidationResult result = SupercausalStructure.validateDetailed(level, controllerPos);
        assertFalse(result.passed());
        assertFalse(result.allChunksLoaded());
        assertEquals(Map.of(tensorCasing, 1), result.missing);
        assertEquals(0, result.parallelLimit());
    }

    @Test
    void testPlaceMissingBlocksMovesPlayerToSafety() throws IOException {
        BlockPos controllerPos = new BlockPos(0, 100, 0);
        Map<BlockPos, BlockState> states = buildCompleteStates(controllerPos, Direction.NORTH);
        // 玩家站在一个缺失的外壳位置内,放置前应被转移到控制器上方
        BlockPos missingPos = controllerPos.offset(-4, -5, 0);
        states.remove(missingPos);
        Level level = mockLevel(states);
        Player player = mock(Player.class);
        when(player.blockPosition()).thenReturn(missingPos);

        SupercausalStructure.placeMissingBlocks(level, controllerPos, player);

        verify(level).setBlock(missingPos, tensorCasing.defaultBlockState(), Block.UPDATE_ALL);
        // 控制器上方两个均非空（mock 默认）,安全位置回退为控制器上方第 2 格
        BlockPos safe = controllerPos.above(2);
        verify(player).teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
    }
}
