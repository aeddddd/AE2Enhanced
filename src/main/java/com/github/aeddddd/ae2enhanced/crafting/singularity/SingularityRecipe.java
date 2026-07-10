package com.github.aeddddd.ae2enhanced.crafting.singularity;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.github.aeddddd.ae2enhanced.blackhole.blockentity.MicroSingularityBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;

/**
 * 微型奇点仪式配方。
 * 以目标方块为中心，扫描周围 5×5×5 区域内的物品实体，累加数量后匹配输入。
 * 输入支持三类：丢在世界中的物品、右键手持物品、右键目标方块。
 * 输出为微型奇点方块，可配置存在时间。
 */
public class SingularityRecipe {

    private final String id;
    private final List<ItemStack> droppedInputs;
    private final ItemStack heldItem;
    private final Block targetBlock;
    private final int lifetimeTicks;

    public SingularityRecipe(String id, List<ItemStack> droppedInputs,
            ItemStack heldItem, Block targetBlock, int lifetimeTicks) {
        this.id = id;
        this.droppedInputs = droppedInputs != null ? droppedInputs : Collections.emptyList();
        this.heldItem = heldItem != null ? heldItem : ItemStack.EMPTY;
        this.targetBlock = targetBlock;
        this.lifetimeTicks = lifetimeTicks > 0 ? lifetimeTicks : MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS;
    }

    public SingularityRecipe(String id, List<ItemStack> droppedInputs) {
        this(id, droppedInputs, ItemStack.EMPTY, null, MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS);
    }

    public String getId() {
        return id;
    }

    public List<ItemStack> getInputs() {
        return droppedInputs;
    }

    public ItemStack getHeldItem() {
        return heldItem;
    }

    public Block getTargetBlock() {
        return targetBlock;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    private static String stackKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.toString();
    }

    /**
     * 检测是否匹配：先检查目标方块和手持物品，再检查 droppedInputs。
     */
    public boolean matches(Level level, BlockPos center, ItemStack held) {
        // 检查目标方块
        if (targetBlock != null) {
            BlockState actualState = level.getBlockState(center);
            if (!actualState.is(targetBlock)) {
                return false;
            }
        }

        // 检查手持物品（忽略数量，只要类型匹配且数量>=1）
        if (!heldItem.isEmpty()) {
            if (held == null || held.isEmpty()) {
                return false;
            }
            if (held.getItem() != heldItem.getItem()) {
                return false;
            }
        }

        // 检查 droppedInputs
        AABB area = new AABB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3);
        Map<String, Integer> found = new HashMap<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            found.merge(stackKey(stack), stack.getCount(), Integer::sum);
        }
        for (ItemStack need : droppedInputs) {
            if (found.getOrDefault(stackKey(need), 0) < need.getCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行仪式：消耗区域内材料，在中心位置生成微型奇点方块。
     */
    public void craft(Level level, BlockPos center, ItemStack held) {
        // 消耗 droppedInputs
        AABB area = new AABB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3);
        Map<String, Integer> remaining = new HashMap<>();
        for (ItemStack need : droppedInputs) {
            remaining.put(stackKey(need), need.getCount());
        }
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String key = stackKey(stack);
            int needed = remaining.getOrDefault(key, 0);
            if (needed > 0) {
                int consume = Math.min(needed, stack.getCount());
                stack.shrink(consume);
                remaining.put(key, needed - consume);
                if (stack.isEmpty()) {
                    itemEntity.discard();
                }
            }
        }

        // 生成微型奇点方块
        level.setBlockAndUpdate(center, ModBlocks.MICRO_SINGULARITY.get().defaultBlockState());
        if (level.getBlockEntity(center) instanceof MicroSingularityBlockEntity microSingularity) {
            microSingularity.setLifetimeTicks(lifetimeTicks);
        }
    }
}
