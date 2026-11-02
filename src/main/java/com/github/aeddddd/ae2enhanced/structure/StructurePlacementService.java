package com.github.aeddddd.ae2enhanced.structure;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** 三类结构共用的占用预检、材料提交和失败回滚边界。 */
public final class StructurePlacementService {
    private StructurePlacementService() {
    }

    public static Plan prepare(World world, Map<BlockPos, Block> targets) {
        Map<BlockPos, Block> pending = new LinkedHashMap<>();
        Map<BlockPos, IBlockState> snapshots = new LinkedHashMap<>();
        Map<Block, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Block> entry : targets.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!world.isBlockLoaded(pos)) return null;
            IBlockState current = world.getBlockState(pos);
            snapshots.put(pos, current);
            if (current.getBlock() == entry.getValue()) continue;
            if (!world.isAirBlock(pos) && !current.getBlock().isReplaceable(world, pos)) return null;
            pending.put(pos, entry.getValue());
            missing.put(entry.getValue(), missing.getOrDefault(entry.getValue(), 0) + 1);
        }
        return new Plan(world, pending, snapshots, missing);
    }

    public static boolean execute(Plan plan, BooleanSupplier verify) {
        boolean success = false;
        try {
            for (Map.Entry<BlockPos, Block> entry : plan.targets.entrySet()) {
                if (!plan.world.setBlockState(entry.getKey(), entry.getValue().getDefaultState())) return false;
            }
            success = verify.getAsBoolean();
            return success;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            if (!success) restore(plan);
        }
    }

    private static void restore(Plan plan) {
        List<BlockPos> positions = new ArrayList<>(plan.snapshots.keySet());
        for (int i = positions.size() - 1; i >= 0; i--) {
            BlockPos pos = positions.get(i);
            plan.world.setBlockState(pos, plan.snapshots.get(pos));
        }
    }

    public static boolean consume(InventoryPlayer inventory, Plan plan, BooleanSupplier verify) {
        Map<Block, Integer> needed = new LinkedHashMap<>(plan.missing);
        for (ItemStack stack : inventory.mainInventory) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                if (stack.getItem() == Item.getItemFromBlock(entry.getKey())) {
                    entry.setValue(Math.max(0, entry.getValue() - stack.getCount()));
                }
            }
        }
        for (int count : needed.values()) {
            if (count > 0) return false;
        }

        List<ItemStack> removed = new ArrayList<>();
        for (Map.Entry<Block, Integer> entry : plan.missing.entrySet()) {
            int remaining = entry.getValue();
            Item item = Item.getItemFromBlock(entry.getKey());
            for (int slot = 0; slot < inventory.mainInventory.size() && remaining > 0; slot++) {
                ItemStack stack = inventory.mainInventory.get(slot);
                if (stack.getItem() != item) continue;
                int take = Math.min(stack.getCount(), remaining);
                ItemStack removedStack = inventory.decrStackSize(slot, take);
                removed.add(removedStack);
                remaining -= removedStack.getCount();
            }
            if (remaining > 0) {
                for (ItemStack stack : removed) inventory.addItemStackToInventory(stack);
                return false;
            }
        }
        if (execute(plan, verify)) return true;
        for (ItemStack stack : removed) inventory.addItemStackToInventory(stack);
        return false;
    }

    public static final class Plan {
        final World world;
        final Map<BlockPos, Block> targets;
        final Map<BlockPos, IBlockState> snapshots;
        final Map<Block, Integer> missing;

        private Plan(World world, Map<BlockPos, Block> targets, Map<BlockPos, IBlockState> snapshots,
                Map<Block, Integer> missing) {
            this.world = world;
            this.targets = targets;
            this.snapshots = snapshots;
            this.missing = missing;
        }

        public boolean isEmpty() {
            return targets.isEmpty();
        }

        public boolean occupies(BlockPos pos) {
            return targets.containsKey(pos);
        }
    }
}
