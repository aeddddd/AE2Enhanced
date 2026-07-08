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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AssemblyStructure {

    public static final Set<BlockPos> ALL_SET;
    public static final Map<Block, Set<BlockPos>> BLOCK_SETS;

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
    }

    public static BlockPos rotate(BlockPos rel, Direction facing) {
        if (facing == Direction.NORTH) return rel;
        int x = rel.getX();
        int y = rel.getY();
        int z = rel.getZ();
        switch (facing) {
            case SOUTH: return new BlockPos(-x, y, -z);
            case EAST:  return new BlockPos(-z, y, x);
            case WEST:  return new BlockPos(z, y, -x);
            default:    return rel;
        }
    }

    public static BlockPos getOriginFromController(BlockPos controllerPos, Direction facing) {
        // 新结构以控制器本身为原点，结构向面朝方向的反方向延伸。
        return controllerPos;
    }

    private static Direction getControllerFacing(Level level, BlockPos controllerPos) {
        BlockState state = level.getBlockState(controllerPos);
        if (state.getBlock() instanceof AssemblyControllerBlock) {
            return state.getValue(AssemblyControllerBlock.FACING);
        }
        return Direction.NORTH;
    }

    public static boolean validate(Level level, BlockPos controllerPos) {
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        for (Map.Entry<BlockPos, Block> entry : getExpectedBlocks()) {
            BlockPos rel = entry.getKey();
            Block expected = entry.getValue();
            BlockPos actual = origin.offset(rotate(rel, facing));
            if (!level.isLoaded(actual)) {
                continue;
            }
            if (level.getBlockState(actual).getBlock() != expected) {
                return false;
            }
        }
        return true;
    }

    private static Set<Map.Entry<BlockPos, Block>> getExpectedBlocks() {
        Set<Map.Entry<BlockPos, Block>> result = new HashSet<>();
        for (Map.Entry<Block, Set<BlockPos>> blockEntry : BLOCK_SETS.entrySet()) {
            Block block = blockEntry.getKey();
            for (BlockPos rel : blockEntry.getValue()) {
                result.add(new java.util.AbstractMap.SimpleEntry<>(rel, block));
            }
        }
        return result;
    }

    public static Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos) {
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        Map<Block, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Block> entry : getExpectedBlocks()) {
            BlockPos rel = entry.getKey();
            Block expected = entry.getValue();
            BlockPos actual = origin.offset(rotate(rel, facing));
            if (!level.isLoaded(actual)) {
                continue;
            }
            if (level.getBlockState(actual).getBlock() != expected) {
                missing.put(expected, missing.getOrDefault(expected, 0) + 1);
            }
        }
        return missing;
    }

    public static void assemble(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) return;
        AssemblyControllerBlockEntity tile = getControllerTile(level, controllerPos);
        if (tile != null) {
            tile.assemble();
        }
    }

    public static void disassemble(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) return;
        AssemblyControllerBlockEntity tile = getControllerTile(level, controllerPos);
        if (tile != null) {
            tile.disassemble();
        }
    }

    private static AssemblyControllerBlockEntity getControllerTile(Level level, BlockPos pos) {
        net.minecraft.world.level.block.entity.BlockEntity te = level.getBlockEntity(pos);
        return te instanceof AssemblyControllerBlockEntity ? (AssemblyControllerBlockEntity) te : null;
    }

    public static void placeMissingBlocks(Level level, BlockPos controllerPos, net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide()) return;
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);
        for (Map.Entry<BlockPos, Block> entry : getExpectedBlocks()) {
            BlockPos rel = entry.getKey();
            Block block = entry.getValue();
            BlockPos pos = origin.offset(rotate(rel, facing));
            if (level.getBlockState(pos).getBlock() != block) {
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        assemble(level, controllerPos);
    }

    public static boolean tryConsumeAndPlace(Level level, BlockPos controllerPos, net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide()) return false;
        Direction facing = getControllerFacing(level, controllerPos);
        BlockPos origin = getOriginFromController(controllerPos, facing);

        Map<Block, Integer> missing = getMissingMap(level, controllerPos);
        if (missing.isEmpty()) {
            assemble(level, controllerPos);
            return true;
        }

        Inventory inv = player.getInventory();
        Map<Block, Integer> needed = new LinkedHashMap<>(missing);

        for (ItemStack stack : inv.items) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                Block block = entry.getKey();
                if (stack.getItem() == block.asItem()) {
                    int need = entry.getValue();
                    int have = stack.getCount();
                    if (have >= need) {
                        entry.setValue(0);
                    } else {
                        entry.setValue(need - have);
                    }
                    break;
                }
            }
        }

        for (int count : needed.values()) {
            if (count > 0) return false;
        }

        for (Map.Entry<Block, Integer> entry : missing.entrySet()) {
            Block block = entry.getKey();
            int remaining = entry.getValue();
            Item item = block.asItem();
            for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
                ItemStack stack = inv.items.get(i);
                if (stack.getItem() == item) {
                    int take = Math.min(stack.getCount(), remaining);
                    int removed = inv.removeItem(i, take).getCount();
                    remaining -= removed;
                }
            }
        }

        placeMissingBlocks(level, controllerPos, player);
        return true;
    }
}
