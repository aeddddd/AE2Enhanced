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

import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 装配枢纽结构定义与验证。
 */
public class AssemblyStructure {

    public static final Set<BlockPos> ALL_SET;
    public static final Map<Block, Set<BlockPos>> BLOCK_SETS;
    public static final AbstractMultiblockStructure INSTANCE;

    private static final Map<String, Block> BLOCK_MAP = new LinkedHashMap<>();

    static {
        BLOCK_MAP.put("casing_1", ModBlocks.ASSEMBLY_CASING_1.get());
        BLOCK_MAP.put("casing_2", ModBlocks.ASSEMBLY_CASING_2.get());
        BLOCK_MAP.put("casing_3", ModBlocks.ASSEMBLY_CASING_3.get());
        BLOCK_MAP.put("casing_4", ModBlocks.ASSEMBLY_CASING_4.get());
        BLOCK_MAP.put("controller", ModBlocks.ASSEMBLY_CONTROLLER.get());

        Map<Block, Set<BlockPos>> blockSets = new HashMap<>();
        Set<BlockPos> all = new HashSet<>();

        InputStream stream = AssemblyStructure.class.getResourceAsStream("/data/ae2enhanced/assembly_structure/assembly_new.json");
        if (stream != null) {
            try (InputStreamReader reader = new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray blocks = root.getAsJsonArray("blocks");
                for (JsonElement e : blocks) {
                    JsonObject obj = e.getAsJsonObject();
                    int x = obj.get("x").getAsInt();
                    int y = obj.get("y").getAsInt();
                    int z = obj.get("z").getAsInt();
                    String name = obj.get("block").getAsString();
                    Block block = BLOCK_MAP.get(name);
                    if (block == null) {
                        continue;
                    }
                    BlockPos rel = new BlockPos(x, y, z);
                    all.add(rel);
                    blockSets.computeIfAbsent(block, k -> new HashSet<>()).add(rel);
                }
            } catch (Exception ex) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error("[AE2E] Failed to load assembly_new.json", ex);
            }
        } else {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error("[AE2E] assembly_new.json not found in resources");
        }

        Map<Block, Set<BlockPos>> unmodifiableSets = new HashMap<>();
        for (Map.Entry<Block, Set<BlockPos>> entry : blockSets.entrySet()) {
            unmodifiableSets.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        BLOCK_SETS = Collections.unmodifiableMap(unmodifiableSets);
        ALL_SET = Collections.unmodifiableSet(all);

        INSTANCE = new Impl(StructureDefinition.of(BLOCK_SETS, null));
    }

    public static BlockPos getOriginFromController(BlockPos controllerPos, Direction facing) {
        // 新结构以控制器本身为原点，结构向面朝方向的反方向延伸。
        return controllerPos;
    }

    public static Set<BlockPos> getAllSet() {
        return ALL_SET;
    }

    public static Direction getControllerFacing(Level level, BlockPos controllerPos) {
        return INSTANCE.getRotation(level, controllerPos);
    }

    public static Set<Map.Entry<BlockPos, Block>> getExpectedBlocks(Level level, BlockPos controllerPos) {
        return INSTANCE.getExpectedBlocks(level, controllerPos);
    }

    public static boolean validate(Level level, BlockPos controllerPos) {
        return INSTANCE.validate(level, controllerPos);
    }

    public static ValidationResult validateDetailed(Level level, BlockPos controllerPos) {
        return INSTANCE.validateDetailed(level, controllerPos);
    }

    public static Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos) {
        return INSTANCE.getMissingMap(level, controllerPos);
    }

    public static void assemble(Level level, BlockPos controllerPos) {
        INSTANCE.assemble(level, controllerPos);
    }

    public static void disassemble(Level level, BlockPos controllerPos) {
        INSTANCE.disassemble(level, controllerPos);
    }

    public static void placeMissingBlocks(Level level, BlockPos controllerPos, Player player) {
        INSTANCE.placeMissingBlocks(level, controllerPos, player);
    }

    public static boolean tryConsumeAndPlace(Level level, BlockPos controllerPos, Player player) {
        return INSTANCE.tryConsumeAndPlace(level, controllerPos, player);
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

        @Override
        public void assemble(Level level, BlockPos controllerPos) {
            if (level.isClientSide()) {
                return;
            }
            if (level.getBlockEntity(controllerPos) instanceof AssemblyControllerBlockEntity tile) {
                tile.assemble();
            }
        }

        @Override
        public void disassemble(Level level, BlockPos controllerPos) {
            if (level.isClientSide()) {
                return;
            }
            if (level.getBlockEntity(controllerPos) instanceof AssemblyControllerBlockEntity tile) {
                tile.disassemble();
            }
        }
    }
}
