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
import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 装配枢纽结构定义与验证。
 * <p>为避免在方块注册完成前访问 {@link RegistryObject#get()}，
 * 本类的完整静态初始化被推迟到 {@link #init()}。</p>
 */
public class AssemblyStructure {

    private static Set<BlockPos> ALL_SET;
    private static Map<Block, Set<BlockPos>> BLOCK_SETS;
    private static AbstractMultiblockStructure INSTANCE;
    private static boolean initialized = false;

    // 名称 -> RegistryObject，仅在 init() 中解析为 Block
    private static final Map<String, RegistryObject<Block>> BLOCK_REGISTRY_MAP = new LinkedHashMap<>();
    // 名称 -> 相对坐标集合，在静态块中从 JSON 读取
    private static final Map<String, Set<BlockPos>> RAW_POSITIONS = new LinkedHashMap<>();

    static {
        BLOCK_REGISTRY_MAP.put("casing_1", ModBlocks.ASSEMBLY_CASING_1);
        BLOCK_REGISTRY_MAP.put("casing_2", ModBlocks.ASSEMBLY_CASING_2);
        BLOCK_REGISTRY_MAP.put("casing_3", ModBlocks.ASSEMBLY_CASING_3);
        BLOCK_REGISTRY_MAP.put("casing_4", ModBlocks.ASSEMBLY_CASING_4);
        BLOCK_REGISTRY_MAP.put("controller", ModBlocks.ASSEMBLY_CONTROLLER);

        loadRawPositions();
    }

    private static void loadRawPositions() {
        InputStream stream = AssemblyStructure.class
                .getResourceAsStream("/data/ae2enhanced/assembly_structure/assembly_new.json");
        if (stream == null) {
            AE2Enhanced.LOGGER.error("[AE2E] assembly_new.json not found in resources");
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
                RAW_POSITIONS.computeIfAbsent(name, k -> new HashSet<>()).add(new BlockPos(x, y, z));
            }
        } catch (Exception ex) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to load assembly_new.json", ex);
        }
    }

    /**
     * 在方块注册完成后调用，完成结构解析。
     * <p>通常在 {@code FMLCommonSetupEvent} 中执行。</p>
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
                AE2Enhanced.LOGGER.error("[AE2E] Assembly block not registered: {}", entry.getKey());
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
        INSTANCE = new Impl(StructureDefinition.of(BLOCK_SETS, null));
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("AssemblyStructure has not been initialized. Call init() during FMLCommonSetupEvent.");
        }
    }

    public static Set<BlockPos> getAllSet() {
        // ALL_SET 仅包含坐标，在静态块中已初始化，无需等待 init()
        return ALL_SET;
    }

    public static Map<Block, Set<BlockPos>> getBlockSets() {
        ensureInitialized();
        return BLOCK_SETS;
    }

    public static AbstractMultiblockStructure getInstance() {
        ensureInitialized();
        return INSTANCE;
    }

    public static BlockPos getOriginFromController(BlockPos controllerPos, Direction facing) {
        // 新结构以控制器本身为原点，结构向面朝方向的反方向延伸。
        return controllerPos;
    }

    public static Direction getControllerFacing(Level level, BlockPos controllerPos) {
        return getInstance().getRotation(level, controllerPos);
    }

    public static Set<Map.Entry<BlockPos, Block>> getExpectedBlocks(Level level, BlockPos controllerPos) {
        return getInstance().getExpectedBlocks(level, controllerPos);
    }

    public static boolean validate(Level level, BlockPos controllerPos) {
        return getInstance().validate(level, controllerPos);
    }

    public static ValidationResult validateDetailed(Level level, BlockPos controllerPos) {
        return getInstance().validateDetailed(level, controllerPos);
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

    private static class Impl extends AbstractMultiblockStructure {

        private Impl(StructureDefinition definition) {
            super(definition);
        }

        @Override
        public Direction getRotation(Level level, BlockPos controllerPos) {
            BlockState state = level.getBlockState(controllerPos);
            if (state.getBlock() instanceof AssemblyControllerBlock) {
                return state.getValue(AssemblyControllerBlock.FACING);
            }
            return Direction.NORTH;
        }
    }
}
