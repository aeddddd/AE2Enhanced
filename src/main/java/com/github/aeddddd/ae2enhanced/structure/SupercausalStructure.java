package com.github.aeddddd.ae2enhanced.structure;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.computation.block.ComputationControllerBlock;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 超因果计算核心的多方块结构验证系统.
 * <p>结构为 11x11x11 空心立方体外壳,从
 * {@code data/ae2enhanced/computation_structure/cpu_new.json} 加载.
 * 规范坐标系以控制器为原点 (0,0,0)（控制器替代面心因果锚定核心）,
 * 结构向 +Z（控制器背面）延伸,几何中心位于 (0,0,5).</p>
 * <p>为避免在方块注册完成前访问 {@link RegistryObject#get()},
 * 完整初始化推迟到 {@link #init()}.</p>
 */
public class SupercausalStructure {

    private static Set<BlockPos> ALL_SET;
    private static Map<Block, Set<BlockPos>> BLOCK_SETS;
    private static AbstractMultiblockStructure INSTANCE;
    private static boolean initialized = false;

    // 名称 -> RegistryObject,仅在 init() 中解析为 Block
    private static final Map<String, RegistryObject<Block>> BLOCK_REGISTRY_MAP = new LinkedHashMap<>();
    // 名称 -> 相对坐标集合,在静态块中从 JSON 读取
    private static final Map<String, Set<BlockPos>> RAW_POSITIONS = new LinkedHashMap<>();

    static {
        BLOCK_REGISTRY_MAP.put("tensor_casing", ModBlocks.CONSTANT_TENSOR_FIELD_CASING);
        BLOCK_REGISTRY_MAP.put("spinor_casing", ModBlocks.CONSTANT_SPINOR_FIELD_CASING);
        BLOCK_REGISTRY_MAP.put("casing_glass", ModBlocks.CASING_GLASS);
        BLOCK_REGISTRY_MAP.put("causal_anchor", ModBlocks.CAUSAL_ANCHOR_CORE);
        BLOCK_REGISTRY_MAP.put("controller", ModBlocks.COMPUTATION_CONTROLLER);

        loadRawPositions();
    }

    private static void loadRawPositions() {
        InputStream stream = SupercausalStructure.class
                .getResourceAsStream("/data/ae2enhanced/computation_structure/cpu_new.json");
        if (stream == null) {
            AE2Enhanced.LOGGER.error("[AE2E] cpu_new.json not found in resources");
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray blocks = root.getAsJsonArray("blocks");
            for (JsonElement e : blocks) {
                JsonObject obj = e.getAsJsonObject();
                int x = obj.get("x").getAsInt();
                int y = obj.get("y").getAsInt();
                int z = obj.get("z").getAsInt();
                String name = obj.get("block").getAsString();
                if (!BLOCK_REGISTRY_MAP.containsKey(name)) {
                    continue;
                }
                // 源 JSON 即以控制器为原点、NORTH 为基准朝向（结构向 +Z 延伸）,无需额外旋转
                RAW_POSITIONS.computeIfAbsent(name, k -> new HashSet<>()).add(new BlockPos(x, y, z));
            }
        } catch (Exception ex) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to load cpu_new.json", ex);
        }
    }

    /**
     * 在方块注册完成后调用,完成结构解析.
     * <p>通常在 {@code FMLCommonSetupEvent} 中执行.</p>
     */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        Map<Block, Set<BlockPos>> blockSets = new HashMap<>();
        Set<BlockPos> all = new HashSet<>();

        for (Map.Entry<String, Set<BlockPos>> entry : RAW_POSITIONS.entrySet()) {
            RegistryObject<Block> obj = BLOCK_REGISTRY_MAP.get(entry.getKey());
            if (obj == null || !obj.isPresent()) {
                AE2Enhanced.LOGGER.error("[AE2E] Computation block not registered: {}", entry.getKey());
                continue;
            }
            Block block = obj.get();
            Set<BlockPos> positions = entry.getValue();
            all.addAll(positions);
            blockSets.put(block, positions);
        }

        Map<Block, Set<BlockPos>> unmodifiableSets = new HashMap<>();
        for (Map.Entry<Block, Set<BlockPos>> entry : blockSets.entrySet()) {
            unmodifiableSets.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        BLOCK_SETS = Collections.unmodifiableMap(unmodifiableSets);
        ALL_SET = Collections.unmodifiableSet(all);
        // 计算核心采用任意结构方块接入网络方案,不使用通用 ME 接口
        INSTANCE = new Impl(StructureDefinition.of(BLOCK_SETS, null));
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "SupercausalStructure has not been initialized. Call init() during FMLCommonSetupEvent.");
        }
    }

    public static AbstractMultiblockStructure getInstance() {
        ensureInitialized();
        return INSTANCE;
    }

    public static ValidationResult validate(Level level, BlockPos controllerPos) {
        return getInstance().validateDetailed(level, controllerPos);
    }

    public static ValidationResult validateDetailed(Level level, BlockPos controllerPos) {
        return getInstance().validateDetailed(level, controllerPos);
    }

    public static Set<BlockPos> getAllSet() {
        return ALL_SET;
    }

    public static Direction getControllerFacing(Level level, BlockPos controllerPos) {
        return getInstance().getRotation(level, controllerPos);
    }

    public static Set<Map.Entry<BlockPos, Block>> getExpectedBlocks(Level level, BlockPos controllerPos) {
        return getInstance().getExpectedBlocks(level, controllerPos);
    }

    public static Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos) {
        return getInstance().getMissingMap(level, controllerPos);
    }

    public static void assemble(Level level, BlockPos controllerPos) {
        getInstance().assemble(level, controllerPos);
    }

    public static void disassemble(Level level, BlockPos controllerPos) {
        getInstance().disassemble(level, controllerPos);
    }

    public static void placeMissingBlocks(Level level, BlockPos controllerPos, Player player) {
        getInstance().placeMissingBlocks(level, controllerPos, player);
    }

    public static boolean tryConsumeAndPlace(Level level, BlockPos controllerPos, Player player) {
        return getInstance().tryConsumeAndPlace(level, controllerPos, player);
    }

    /**
     * 返回每个虚拟 CPU 的并行合成上限（由配置决定）.
     */
    public static int computeParallel() {
        return AE2EnhancedConfig.COMMON.computationMaxParallel.get();
    }

    private static class Impl extends AbstractMultiblockStructure {

        private Impl(StructureDefinition definition) {
            super(definition);
        }

        /**
         * 取控制器朝向的反方向作为结构旋转方向,使结构向控制器背面延伸,
         * 控制器正面保持朝外可交互.
         */
        @Override
        public Direction getRotation(Level level, BlockPos controllerPos) {
            BlockState state = level.getBlockState(controllerPos);
            if (state.getBlock() instanceof ComputationControllerBlock) {
                return state.getValue(ComputationControllerBlock.FACING).getOpposite();
            }
            return Direction.NORTH;
        }

        @Override
        public ValidationResult validateDetailed(Level level, BlockPos controllerPos) {
            Map<Block, Integer> missing = new LinkedHashMap<>();
            boolean allChunksLoaded = true;
            int causalCount = 0;
            Direction facing = getRotation(level, controllerPos);

            for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
                BlockPos rel = entry.getKey();
                Block expected = entry.getValue();
                BlockPos actual = controllerPos.offset(StructureUtils.rotate(rel, facing));
                if (!level.isLoaded(actual)) {
                    allChunksLoaded = false;
                    missing.merge(expected, 1, Integer::sum);
                    continue;
                }
                if (level.getBlockState(actual).getBlock() != expected) {
                    missing.merge(expected, 1, Integer::sum);
                } else if (expected == ModBlocks.CAUSAL_ANCHOR_CORE.get()) {
                    causalCount++;
                }
            }

            boolean passed = missing.isEmpty() && allChunksLoaded;
            int parallel = passed ? computeParallel() : 0;
            return new ValidationResult(passed, missing, allChunksLoaded, causalCount, parallel);
        }

        @Override
        public void placeMissingBlocks(Level level, BlockPos controllerPos, Player player) {
            if (level.isClientSide()) {
                return;
            }
            Direction facing = getRotation(level, controllerPos);
            for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
                BlockPos rel = entry.getKey();
                Block block = entry.getValue();
                BlockPos pos = controllerPos.offset(StructureUtils.rotate(rel, facing));
                if (level.getBlockState(pos).getBlock() == block) {
                    continue;
                }
                if (player != null && pos.equals(player.blockPosition())) {
                    movePlayerToSafety(level, controllerPos, player);
                }
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            }
            assemble(level, controllerPos);
        }

        /**
         * 立方体外壳会封死内部空腔,一键放置前把腔内玩家移到控制器上方安全位置.
         */
        private static void movePlayerToSafety(Level level, BlockPos controllerPos, Player player) {
            BlockPos safe = controllerPos.above(2);
            for (int dy = 2; dy < 10; dy++) {
                BlockPos candidate = controllerPos.above(dy);
                if (level.isEmptyBlock(candidate) && level.isEmptyBlock(candidate.above())) {
                    safe = candidate;
                    break;
                }
            }
            player.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        }
    }
}
