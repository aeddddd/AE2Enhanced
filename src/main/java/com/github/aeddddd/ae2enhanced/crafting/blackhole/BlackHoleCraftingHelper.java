package com.github.aeddddd.ae2enhanced.crafting.blackhole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;

/**
 * 黑洞合成辅助类.
 * 扫描黑洞中心周围 3×3×3 区域内的物品实体,累加匹配配方后消耗/产出.
 */
public class BlackHoleCraftingHelper {

    /**
     * 尝试执行一次黑洞合成.
     *
     * @param level 世界
     * @param pos 扫描中心坐标
     * @param outputPos 产物掉落坐标
     * @param destroyOnMismatch 配方不匹配时是否销毁区域内的所有物品.
     *                          正式黑洞自动吸入时应为 true；
     *                          微型奇点玩家主动触发时应为 false,避免误销毁未配齐的材料.
     * @return 是否成功匹配并执行了至少一个配方
     */
    public static boolean tryCraft(Level level, BlockPos pos, BlockPos outputPos, boolean destroyOnMismatch) {
        if (!AE2EnhancedConfig.COMMON.enableBlackHole.get()) {
            return false;
        }

        AABB area = new AABB(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area);
        if (items.isEmpty()) {
            return false;
        }

        // 累加物品数量（区分 NBT）
        Map<String, Integer> found = new HashMap<>();
        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            found.merge(BlackHoleRecipe.keyOf(stack), stack.getCount(), Integer::sum);
        }

        // 匹配配方
        BlackHoleRecipe recipe = findMatching(level, found);
        if (recipe != null) {
            // 消耗材料
            Map<String, Integer> remaining = new HashMap<>(recipe.getInputs());
            for (ItemEntity itemEntity : items) {
                ItemStack stack = itemEntity.getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                String key = BlackHoleRecipe.keyOf(stack);
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
            // 生成产物（从指定位置喷出）
            ItemEntity result = new ItemEntity(level,
                    outputPos.getX() + 0.5, outputPos.getY() + 0.5, outputPos.getZ() + 0.5,
                    recipe.getOutput().copy());
            result.setPickUpDelay(0);
            level.addFreshEntity(result);
            return true;
        } else if (destroyOnMismatch) {
            // 不匹配任何配方：黑洞销毁所有物品
            for (ItemEntity itemEntity : items) {
                itemEntity.discard();
            }
        }
        // 若 destroyOnMismatch == false 且配方不匹配,保留所有物品,什么都不做
        return false;
    }

    /**
     * 并行批量合成：扫描 3×3×3 区域内的物品实体,对每个匹配的配方
     * 一次性按最大批次数消耗材料并产出,循环直到没有任何配方可匹配.
     * 不匹配配方的物品保持不变.
     *
     * @return 实际执行的合成批次数
     */
    public static int craftAllAvailable(Level level, BlockPos pos, BlockPos outputPos) {
        if (!AE2EnhancedConfig.COMMON.enableBlackHole.get()) {
            return 0;
        }
        int totalBatches = 0;
        for (int iter = 0; iter < 100; iter++) {
            AABB area = new AABB(
                    pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                    pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area);
            if (items.isEmpty()) {
                break;
            }

            Map<String, Integer> found = new HashMap<>();
            for (ItemEntity itemEntity : items) {
                ItemStack stack = itemEntity.getItem();
                if (!stack.isEmpty()) {
                    found.merge(BlackHoleRecipe.keyOf(stack), stack.getCount(), Integer::sum);
                }
            }

            boolean anyCrafted = false;
            for (BlackHoleRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.BLACK_HOLE_TYPE.get())) {
                int batches = recipe.maxBatches(found);
                if (batches <= 0) {
                    continue;
                }
                // 消耗 batches 倍材料
                Map<String, Integer> remaining = new HashMap<>();
                for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
                    remaining.put(entry.getKey(), entry.getValue() * batches);
                }
                for (ItemEntity itemEntity : items) {
                    ItemStack stack = itemEntity.getItem();
                    if (stack.isEmpty()) {
                        continue;
                    }
                    String key = BlackHoleRecipe.keyOf(stack);
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
                // 同步 found,供后续配方计算批次
                for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
                    found.merge(entry.getKey(), -entry.getValue() * batches, Integer::sum);
                }
                // 产出：按最大堆叠拆分为多个物品实体
                spawnOutputs(level, outputPos, recipe.getOutput(), batches);
                totalBatches += batches;
                anyCrafted = true;
            }
            if (!anyCrafted) {
                break;
            }
        }
        return totalBatches;
    }

    /**
     * 生成 batches 份产物,按物品最大堆叠拆分为多个物品实体从指定位置喷出.
     */
    private static void spawnOutputs(Level level, BlockPos outputPos, ItemStack output, int batches) {
        int remaining = output.getCount() * batches;
        int maxStack = output.getMaxStackSize();
        while (remaining > 0) {
            int count = Math.min(remaining, maxStack);
            ItemStack stack = output.copy();
            stack.setCount(count);
            ItemEntity result = new ItemEntity(level,
                    outputPos.getX() + 0.5, outputPos.getY() + 0.5, outputPos.getZ() + 0.5, stack);
            result.setPickUpDelay(0);
            level.addFreshEntity(result);
            remaining -= count;
        }
    }

    /**
     * 微型奇点：吸入 5×5×5 范围内可参与黑洞合成的物品实体.
     * 不匹配任何配方输入的物品原地不动,避免杂物堆积与误吞.
     */
    public static void suckMatchingItems(Level level, BlockPos center) {
        AABB area = new AABB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area);
        Vec3 target = Vec3.atCenterOf(center);
        for (ItemEntity item : items) {
            if (!isCraftingInput(level, item.getItem())) {
                continue;
            }
            Vec3 motion = target.subtract(item.position()).normalize().scale(0.25);
            item.setDeltaMovement(motion);
            item.hasImpulse = true;
        }
    }

    /**
     * 判断物品是否可作为任一黑洞配方的输入（按物品注册名匹配,忽略 NBT 后缀）.
     */
    public static boolean isCraftingInput(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (BlackHoleRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.BLACK_HOLE_TYPE.get())) {
            for (String inputKey : recipe.getInputs().keySet()) {
                if (inputKey.equals(itemId) || inputKey.startsWith(itemId + "#")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 装配枢纽黑洞：吸入 5×5×5 范围内的物品实体.
     */
    public static void suckItems(Level level, BlockPos center) {
        AABB area = new AABB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area);
        Vec3 target = Vec3.atCenterOf(center);
        for (ItemEntity item : items) {
            Vec3 motion = target.subtract(item.position()).normalize().scale(0.25);
            item.setDeltaMovement(motion);
            item.hasImpulse = true;
        }
    }

    /**
     * 装配枢纽黑洞：击杀 5×5×5 范围内的生物.
     */
    public static void killLivingEntities(Level level, BlockPos center) {
        AABB area = new AABB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area);
        for (LivingEntity living : entities) {
            if (!living.isAlive()) {
                continue;
            }
            if (living instanceof Player player && player.isCreative()) {
                continue;
            }
            living.hurt(level.damageSources().generic(), Float.MAX_VALUE);
        }
    }

    /**
     * 装配枢纽黑洞：过载爆炸效果.
     */
    public static void explode(Level level, BlockPos center) {
        if (level.isClientSide()) {
            return;
        }
        Vec3 c = Vec3.atCenterOf(center);
        ((ServerLevel) level).sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 4, 0.5, 0.5, 0.5, 0);
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0f, 0.5f);
    }

    /**
     * 在配方管理器中查找第一个匹配的配方.
     */
    private static BlackHoleRecipe findMatching(Level level, Map<String, Integer> found) {
        var recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.BLACK_HOLE_TYPE.get());
        for (BlackHoleRecipe recipe : recipes) {
            if (recipe.matches(found)) {
                return recipe;
            }
        }
        return null;
    }
}
