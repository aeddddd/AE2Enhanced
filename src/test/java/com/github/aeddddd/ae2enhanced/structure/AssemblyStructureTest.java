package com.github.aeddddd.ae2enhanced.structure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.RegistryObjectTestInjector;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AssemblyStructure} 单元测试.
 * <p>覆盖结构 JSON 的加载/解析（含负向用例,通过 {@link StructureClassReloader}
 * 以独立 ClassLoader 重载类触发静态块）、init 装配与基于 mock Level 的验证逻辑.</p>
 */
class AssemblyStructureTest {

    private static final String RESOURCE_PATH = "data/ae2enhanced/assembly_structure/assembly_new.json";

    private static Block casing;
    private static Block frame;
    private static Block innerWall;
    private static Block stabilizer;
    private static AssemblyControllerBlock controller;

    @BeforeAll
    static void setUp() {
        MinecraftTestBootstrap.bootstrap();
        casing = Blocks.STONE_BRICKS;
        frame = Blocks.IRON_BLOCK;
        innerWall = Blocks.OBSIDIAN;
        stabilizer = Blocks.GLOWSTONE;
        controller = new AssemblyControllerBlock(BlockBehaviour.Properties.of());
        RegistryObjectTestInjector.inject(ModBlocks.ASSEMBLY_CASING, casing);
        RegistryObjectTestInjector.inject(ModBlocks.ASSEMBLY_FRAME, frame);
        RegistryObjectTestInjector.inject(ModBlocks.ASSEMBLY_INNER_WALL, innerWall);
        RegistryObjectTestInjector.inject(ModBlocks.ASSEMBLY_STABILIZER, stabilizer);
        RegistryObjectTestInjector.inject(ModBlocks.ASSEMBLY_CONTROLLER, controller);
        AssemblyStructure.init();
    }

    /**
     * 直接解析类路径中的结构 JSON,按加载时的约定 (x,y,z) -> (z,y,-x) 变换,
     * 得到 名称 -> 期望相对坐标集合.
     */
    private static Map<String, Set<BlockPos>> expectedPositionsFromJson() throws IOException {
        Map<String, Set<BlockPos>> expected = new HashMap<>();
        try (InputStream in = AssemblyStructureTest.class.getResourceAsStream("/" + RESOURCE_PATH)) {
            assertNotNull(in);
            JsonObject root = JsonParser
                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("blocks")) {
                JsonObject obj = e.getAsJsonObject();
                int x = obj.get("x").getAsInt();
                int y = obj.get("y").getAsInt();
                int z = obj.get("z").getAsInt();
                expected.computeIfAbsent(obj.get("block").getAsString(), k -> new HashSet<>())
                        .add(new BlockPos(z, y, -x));
            }
        }
        return expected;
    }

    /**
     * 名称 -> 注入的测试方块.
     */
    private static Map<String, Block> blocksByName() {
        Map<String, Block> map = new HashMap<>();
        map.put("casing", casing);
        map.put("frame", frame);
        map.put("inner_wall", innerWall);
        map.put("stabilizer", stabilizer);
        map.put("controller", controller);
        return map;
    }

    /**
     * 读取真实资源字节.
     */
    private static byte[] realResourceBytes() throws IOException {
        try (InputStream in = AssemblyStructureTest.class.getResourceAsStream("/" + RESOURCE_PATH)) {
            assertNotNull(in);
            return in.readAllBytes();
        }
    }

    /**
     * 按 JSON 期望坐标生成完整结构的 世界坐标 -> 方块状态 表（控制器朝向 NORTH,即恒等旋转）.
     */
    private static Map<BlockPos, BlockState> buildCompleteStates(BlockPos controllerPos) throws IOException {
        Map<BlockPos, BlockState> states = new HashMap<>();
        Map<String, Block> byName = blocksByName();
        for (Map.Entry<String, Set<BlockPos>> entry : expectedPositionsFromJson().entrySet()) {
            Block block = byName.get(entry.getKey());
            for (BlockPos rel : entry.getValue()) {
                BlockState state = block == controller ? controller.defaultBlockState() : block.defaultBlockState();
                states.put(controllerPos.offset(rel), state);
            }
        }
        return states;
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

    // ---------- init 与结构定义 ----------

    @Test
    void testInitCreatesStructureInstance() {
        assertNotNull(AssemblyStructure.getInstance());
        assertNotNull(AssemblyStructure.getAllSet());
        assertEquals(5, AssemblyStructure.getBlockSets().size());
    }

    @Test
    void testInitIsIdempotent() {
        AbstractMultiblockStructure first = AssemblyStructure.getInstance();
        AssemblyStructure.init();
        assertSame(first, AssemblyStructure.getInstance());
    }

    @Test
    void testBlockSetsMatchTransformedJsonPositions() throws IOException {
        Map<String, Set<BlockPos>> expected = expectedPositionsFromJson();
        Map<String, Block> byName = blocksByName();
        Map<Block, Set<BlockPos>> blockSets = AssemblyStructure.getBlockSets();

        Set<BlockPos> union = new HashSet<>();
        for (Map.Entry<String, Set<BlockPos>> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), blockSets.get(byName.get(entry.getKey())),
                    "方块 " + entry.getKey() + " 的坐标集合与 JSON 不一致");
            union.addAll(entry.getValue());
        }
        assertEquals(union, AssemblyStructure.getAllSet());
        // 控制器位于结构原点
        assertEquals(Set.of(BlockPos.ZERO), blockSets.get(controller));
    }

    @Test
    void testGetOriginFromControllerReturnsControllerPos() {
        BlockPos pos = new BlockPos(3, 70, -8);
        assertEquals(pos, AssemblyStructure.getOriginFromController(pos, Direction.NORTH));
        assertEquals(pos, AssemblyStructure.getOriginFromController(pos, Direction.WEST));
    }

    @Test
    void testUninitializedAccessThrows() throws Exception {
        // 以独立 ClassLoader 重载类（未调用 init）,getBlockSets 应抛出 IllegalStateException
        Class<?> reloaded = StructureClassReloader.reload(AssemblyStructure.class, RESOURCE_PATH,
                realResourceBytes());
        Method getBlockSets = reloaded.getMethod("getBlockSets");
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> getBlockSets.invoke(null));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void testGetAllSetBeforeInitReturnsNull() throws Exception {
        // getAllSet 注释声称坐标在静态块中已初始化,但 ALL_SET 实际在 init() 中赋值,
        // 未 init 时返回 null.本用例记录该现状（注释与实现不一致,详见问题报告）.
        Class<?> reloaded = StructureClassReloader.reload(AssemblyStructure.class, RESOURCE_PATH,
                realResourceBytes());
        Method getAllSet = reloaded.getMethod("getAllSet");
        assertNull(getAllSet.invoke(null));
    }

    // ---------- JSON 加载/解析（独立 ClassLoader 重载） ----------

    @Test
    void testLoadValidJsonTransformsCoordinates() throws Exception {
        Class<?> reloaded = StructureClassReloader.reload(AssemblyStructure.class, RESOURCE_PATH,
                realResourceBytes());
        assertEquals(expectedPositionsFromJson(), StructureClassReloader.readRawPositions(reloaded));
    }

    @Test
    void testLoadMalformedJsonYieldsEmptyPositions() throws Exception {
        Class<?> reloaded = StructureClassReloader.reload(AssemblyStructure.class, RESOURCE_PATH,
                "{ 这不是合法 JSON !!!".getBytes(StandardCharsets.UTF_8));
        assertTrue(StructureClassReloader.readRawPositions(reloaded).isEmpty());
    }

    @Test
    void testLoadMissingBlocksFieldYieldsEmptyPositions() throws Exception {
        Class<?> reloaded = StructureClassReloader.reload(AssemblyStructure.class, RESOURCE_PATH,
                "{\"originIsController\":true}".getBytes(StandardCharsets.UTF_8));
        assertTrue(StructureClassReloader.readRawPositions(reloaded).isEmpty());
    }

    @Test
    void testLoadMissingResourceYieldsEmptyPositions() throws Exception {
        // 资源缺失（流为 null）时仅记录错误,坐标为空
        Class<?> reloaded = StructureClassReloader.reload(AssemblyStructure.class, RESOURCE_PATH, null);
        assertTrue(StructureClassReloader.readRawPositions(reloaded).isEmpty());
    }

    @Test
    void testLoadSkipsUnknownBlockNames() throws Exception {
        String json = "{\"blocks\":["
                + "{\"x\":1,\"y\":2,\"z\":3,\"block\":\"casing\"},"
                + "{\"x\":4,\"y\":5,\"z\":6,\"block\":\"unknown_block\"}]}";
        Class<?> reloaded = StructureClassReloader.reload(AssemblyStructure.class, RESOURCE_PATH,
                json.getBytes(StandardCharsets.UTF_8));
        // 未知名称被跳过,已知名称按 (x,y,z) -> (z,y,-x) 变换
        assertEquals(Map.of("casing", Set.of(new BlockPos(3, 2, -1))),
                StructureClassReloader.readRawPositions(reloaded));
    }

    @Test
    void testLoadEntryMissingFieldAbortsRemainingEntries() throws Exception {
        // 第二条目缺少 block 字段导致解析异常,已加载的第一条保留,后续条目丢弃
        String json = "{\"blocks\":["
                + "{\"x\":1,\"y\":2,\"z\":3,\"block\":\"casing\"},"
                + "{\"x\":4,\"y\":5,\"z\":6},"
                + "{\"x\":7,\"y\":8,\"z\":9,\"block\":\"frame\"}]}";
        Class<?> reloaded = StructureClassReloader.reload(AssemblyStructure.class, RESOURCE_PATH,
                json.getBytes(StandardCharsets.UTF_8));
        assertEquals(Map.of("casing", Set.of(new BlockPos(3, 2, -1))),
                StructureClassReloader.readRawPositions(reloaded));
    }

    // ---------- 基于 mock Level 的验证 ----------

    @Test
    void testGetControllerFacing() {
        Level level = mock(Level.class);
        BlockPos pos = new BlockPos(1, 64, 1);
        when(level.getBlockState(pos))
                .thenReturn(controller.defaultBlockState().setValue(AssemblyControllerBlock.FACING, Direction.EAST));
        assertEquals(Direction.EAST, AssemblyStructure.getControllerFacing(level, pos));

        // 非控制器方块时回退 NORTH
        when(level.getBlockState(pos)).thenReturn(Blocks.STONE.defaultBlockState());
        assertEquals(Direction.NORTH, AssemblyStructure.getControllerFacing(level, pos));
    }

    @Test
    void testValidateCompleteStructure() throws IOException {
        BlockPos controllerPos = new BlockPos(100, 64, -200);
        Level level = mockLevel(buildCompleteStates(controllerPos));

        ValidationResult result = AssemblyStructure.validateDetailed(level, controllerPos);
        assertTrue(result.passed());
        assertTrue(result.missing.isEmpty());
        assertTrue(result.allChunksLoaded());
        assertTrue(AssemblyStructure.validate(level, controllerPos));
    }

    @Test
    void testValidateReportsMissingBlock() throws IOException {
        BlockPos controllerPos = new BlockPos(100, 64, -200);
        Map<BlockPos, BlockState> states = buildCompleteStates(controllerPos);
        // 取一个 casing 位置放错方块
        BlockPos wrongPos = controllerPos
                .offset(AssemblyStructure.getBlockSets().get(casing).iterator().next());
        states.put(wrongPos, Blocks.DIRT.defaultBlockState());
        Level level = mockLevel(states);

        ValidationResult result = AssemblyStructure.validateDetailed(level, controllerPos);
        assertFalse(result.passed());
        assertEquals(Map.of(casing, 1), result.missing);
        assertFalse(AssemblyStructure.validate(level, controllerPos));
    }

    @Test
    void testGetBlackHoleCenterWithinStructureBounds() throws IOException {
        BlockPos controllerPos = new BlockPos(0, 80, 0);
        Level level = mock(Level.class);
        when(level.getBlockState(controllerPos)).thenReturn(controller.defaultBlockState());

        Vec3 center = AssemblyStructure.getBlackHoleCenter(level, controllerPos);
        // 多次调用结果一致
        Vec3 again = AssemblyStructure.getBlackHoleCenter(level, controllerPos);
        assertEquals(center.x, again.x);
        assertEquals(center.y, again.y);
        assertEquals(center.z, again.z);

        // 中心位于结构包围盒内（含向控制器偏移一格后仍不越界）
        float[] b = StructureUtils.computeBounds(AssemblyStructure.getAllSet(), Direction.NORTH);
        assertTrue(center.x >= controllerPos.getX() + b[0] && center.x <= controllerPos.getX() + b[3]);
        assertTrue(center.y >= controllerPos.getY() + b[1] && center.y <= controllerPos.getY() + b[4]);
        assertTrue(center.z >= controllerPos.getZ() + b[2] && center.z <= controllerPos.getZ() + b[5]);
    }
}

/**
 * 结构类重载工具:以独立 ClassLoader 重新加载结构类并替换结构 JSON 资源,
 * 使静态块中的 JSON 解析逻辑可在 malformed JSON、缺失字段、资源缺失等负向场景下被测试.
 * <p>重载仅针对目标类本身做 child-first 加载,其余类仍委派父加载器,
 * 因此 {@code ModBlocks} 等依赖与主类加载器共享.</p>
 */
final class StructureClassReloader {

    private StructureClassReloader() {
    }

    /**
     * 重新加载指定类并触发其静态初始化.
     *
     * @param template 目标类（以其 Class 定位 .class 字节码）
     * @param resourcePath 要替换的资源路径（ClassLoader 层级,不带前导斜杠）
     * @param resourceBytes 替换后的资源内容;为 null 表示资源缺失
     * @return 重载后的 Class 对象（与 template 不是同一 Class）
     */
    static Class<?> reload(Class<?> template, String resourcePath, @Nullable byte[] resourceBytes) throws Exception {
        ClassLoader parent = template.getClassLoader();
        String className = template.getName();
        ClassLoader loader = new ClassLoader(parent) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (name.equals(resourcePath)) {
                    return resourceBytes == null ? null : new ByteArrayInputStream(resourceBytes);
                }
                return super.getResourceAsStream(name);
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(className)) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> c = findLoadedClass(name);
                        if (c == null) {
                            try (InputStream in = getParent()
                                    .getResourceAsStream(name.replace('.', '/') + ".class")) {
                                if (in == null) {
                                    throw new ClassNotFoundException(name);
                                }
                                byte[] bytes = in.readAllBytes();
                                c = defineClass(name, bytes, 0, bytes.length);
                            } catch (IOException e) {
                                throw new ClassNotFoundException(name, e);
                            }
                        }
                        if (resolve) {
                            resolveClass(c);
                        }
                        return c;
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
        return Class.forName(className, true, loader);
    }

    /**
     * 读取重载类静态块解析得到的 名称 -> 相对坐标集合（私有字段 RAW_POSITIONS）.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Set<BlockPos>> readRawPositions(Class<?> reloaded) throws Exception {
        Field field = reloaded.getDeclaredField("RAW_POSITIONS");
        field.setAccessible(true);
        return (Map<String, Set<BlockPos>>) field.get(null);
    }
}
