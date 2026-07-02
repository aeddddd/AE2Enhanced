package com.github.aeddddd.ae2enhanced.crafting.blackhole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.registry.ModRecipes;

/**
 * 黑洞事件视界合成辅助类。
 * <p>负责击杀实体、吸引物品、匹配配方与销毁残留。</p>
 */
public class BlackHoleCraftingHelper {

    private static final int SUCK_RADIUS = 2; // 5x5x5 吸引范围
    private static final int CRAFT_RADIUS = 1; // 3x3x3 合成扫描范围

    /**
     * 扫描 3x3x3 中心区域，尝试执行一次黑洞配方。
     *
     * @return 是否成功匹配并产出
     */
    public static boolean tryCraft(Level level, BlockPos center, BlockPos outputPos, boolean destroyOnMismatch) {
        if (level.isClientSide()) {
            return false;
        }

        AABB craftBox = new AABB(
                center.getX() - CRAFT_RADIUS, center.getY() - CRAFT_RADIUS, center.getZ() - CRAFT_RADIUS,
                center.getX() + CRAFT_RADIUS, center.getY() + CRAFT_RADIUS, center.getZ() + CRAFT_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, craftBox);

        Map<String, Integer> found = new HashMap<>();
        Map<String, List<ItemEntity>> entitiesByKey = new HashMap<>();
        for (ItemEntity entity : items) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String key = BlackHoleRecipe.keyOf(stack);
            found.merge(key, stack.getCount(), Integer::sum);
            entitiesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
        }

        if (found.isEmpty()) {
            return false;
        }

        BlackHoleRecipe recipe = BlackHoleRecipeRegistry.findMatching(found);
        if (recipe == null) {
            recipe = findMatchingFromRecipeManager(level, found);
        }

        if (recipe != null) {
            // 扣除输入物品
            for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
                String key = entry.getKey();
                int needed = entry.getValue();
                List<ItemEntity> entityList = entitiesByKey.getOrDefault(key, new ArrayList<>());
                for (ItemEntity entity : entityList) {
                    if (needed <= 0) {
                        break;
                    }
                    ItemStack stack = entity.getItem();
                    int shrink = Math.min(needed, stack.getCount());
                    stack.shrink(shrink);
                    needed -= shrink;
                    if (stack.isEmpty()) {
                        entity.discard();
                    }
                }
            }
            // 在输出位置生成产物
            ItemStack output = recipe.getOutput().copy();
            ItemEntity outputEntity = new ItemEntity(
                    level,
                    outputPos.getX() + 0.5, outputPos.getY() + 0.5, outputPos.getZ() + 0.5,
                    output);
            outputEntity.setNoPickUpDelay();
            level.addFreshEntity(outputEntity);
            return true;
        }

        if (destroyOnMismatch) {
            for (ItemEntity entity : items) {
                entity.discard();
            }
        }
        return false;
    }

    private static BlackHoleRecipe findMatchingFromRecipeManager(Level level, Map<String, Integer> found) {
        var recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.BLACK_HOLE_TYPE.get());
        for (var recipe : recipes) {
            if (recipe.matches(found)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 重复尝试合成，直到没有匹配或达到最大迭代次数。
     */
    public static int tryCraftAll(Level level, BlockPos center, BlockPos outputPos, boolean destroyOnMismatch, int maxIterations) {
        int crafted = 0;
        for (int i = 0; i < maxIterations; i++) {
            if (!tryCraft(level, center, outputPos, destroyOnMismatch)) {
                break;
            }
            crafted++;
        }
        return crafted;
    }

    /**
     * 击杀 5x5x5 中心区域内的所有活实体。
     */
    public static void killLivingEntities(Level level, BlockPos center) {
        if (level.isClientSide()) {
            return;
        }
        AABB killBox = new AABB(
                center.getX() - SUCK_RADIUS, center.getY() - SUCK_RADIUS, center.getZ() - SUCK_RADIUS,
                center.getX() + SUCK_RADIUS, center.getY() + SUCK_RADIUS, center.getZ() + SUCK_RADIUS);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, killBox);
        for (LivingEntity entity : entities) {
            entity.kill();
        }
    }

    /**
     * 将 5x5x5 范围内的物品实体吸向中心。
     */
    public static void suckItems(Level level, BlockPos center) {
        if (level.isClientSide()) {
            return;
        }
        AABB suckBox = new AABB(
                center.getX() - SUCK_RADIUS, center.getY() - SUCK_RADIUS, center.getZ() - SUCK_RADIUS,
                center.getX() + SUCK_RADIUS, center.getY() + SUCK_RADIUS, center.getZ() + SUCK_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, suckBox);
        Vec3 centerVec = new Vec3(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
        for (ItemEntity item : items) {
            Vec3 pos = item.position();
            Vec3 motion = centerVec.subtract(pos).normalize().scale(0.25);
            item.setDeltaMovement(motion);
            item.hasImpulse = true;
        }
    }

    /**
     * 在中心位置引发爆炸，用于过载惩罚。
     */
    public static void explode(Level level, BlockPos center) {
        if (level.isClientSide()) {
            return;
        }
        level.explode(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                4.0f, Level.ExplosionInteraction.BLOCK);
    }
}
