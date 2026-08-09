package com.github.aeddddd.ae2enhanced.crafting;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑洞合成辅助类.
 * 扫描黑洞中心周围 3×3×3 区域内的物品实体,累加匹配配方后消耗/产出.
 */
public class BlackHoleCraftingHelper {

    /**
     * 尝试执行一次黑洞合成.
     * 产物生成在扫描范围外(y+2),默认行为：配方不匹配时销毁所有物品.
     *
     * @return 是否成功匹配并执行了至少一个配方
     */
    public static boolean tryCraft(World world, BlockPos pos) {
        return tryCraft(world, pos, pos.add(0, 2, 0), true);
    }

    /**
     * 尝试执行一次黑洞合成.
     *
     * @param world 世界
     * @param pos 扫描中心坐标
     * @param outputPos 产物掉落坐标
     * @param destroyOnMismatch 配方不匹配时是否销毁区域内的所有物品.
     *                          正式黑洞自动吸入时应为 true；
     *                          微型奇点玩家主动触发时应为 false,避免误销毁未配齐的材料.
     * @return 是否成功匹配并执行了至少一个配方
     */
    public static boolean tryCraft(World world, BlockPos pos, BlockPos outputPos, boolean destroyOnMismatch) {
        AxisAlignedBB area = new AxisAlignedBB(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2
        );
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, area);
        if (items.isEmpty()) return false;

        // 累加物品数量(区分 metadata)
        Map<String, Integer> found = new HashMap<>();
        for (EntityItem entityItem : items) {
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;
            found.merge(BlackHoleRecipe.keyOf(stack), stack.getCount(), Integer::sum);
        }

        // 匹配配方
        BlackHoleRecipe recipe = BlackHoleRecipeRegistry.findMatching(found);
        if (recipe != null) {
            // 消耗材料
            Map<String, Integer> remaining = new HashMap<>(recipe.getInputs());
            for (EntityItem entityItem : items) {
                ItemStack stack = entityItem.getItem();
                if (stack.isEmpty()) continue;
                String key = BlackHoleRecipe.keyOf(stack);
                int needed = remaining.getOrDefault(key, 0);
                if (needed > 0) {
                    int consume = Math.min(needed, stack.getCount());
                    stack.shrink(consume);
                    remaining.put(key, needed - consume);
                    if (stack.isEmpty()) {
                        entityItem.setDead();
                    }
                }
            }
            // 生成产物(从指定位置喷出)
            EntityItem result = new EntityItem(world,
                    outputPos.getX() + 0.5, outputPos.getY() + 0.5, outputPos.getZ() + 0.5,
                    recipe.getOutput().copy());
            result.setNoPickupDelay();
            world.spawnEntity(result);
            return true;
        } else if (destroyOnMismatch) {
            // 不匹配任何配方：黑洞销毁所有物品
            for (EntityItem entityItem : items) {
                entityItem.setDead();
            }
        }
        // 若 destroyOnMismatch == false 且配方不匹配,保留所有物品,什么都不做
        return false;
    }

    /**
     * 循环执行黑洞合成,直到区域内的物品不再匹配任何配方.
     * 用于微型奇点右键时一次性处理所有可合成配方.
     *
     * @param maxIterations 最大循环次数,防止意外死循环
     */
    public static void tryCraftAll(World world, BlockPos pos, BlockPos outputPos, boolean destroyOnMismatch, int maxIterations) {
        for (int i = 0; i < maxIterations; i++) {
            if (!tryCraft(world, pos, outputPos, destroyOnMismatch)) {
                break;
            }
        }
    }

    /**
     * 并行批量合成：扫描 3×3×3 区域内的物品实体,对每个匹配的配方
     * 一次性按最大批次数消耗材料并产出,循环直到没有任何配方可匹配.
     * 不匹配配方的物品保持不变.
     *
     * @return 实际执行的合成批次数
     */
    public static int craftAllAvailable(World world, BlockPos pos, BlockPos outputPos) {
        int totalBatches = 0;
        for (int iter = 0; iter < 100; iter++) {
            AxisAlignedBB area = new AxisAlignedBB(
                    pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                    pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2
            );
            List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, area);
            if (items.isEmpty()) break;

            Map<String, Integer> found = new HashMap<>();
            for (EntityItem entityItem : items) {
                ItemStack stack = entityItem.getItem();
                if (!stack.isEmpty()) {
                    found.merge(BlackHoleRecipe.keyOf(stack), stack.getCount(), Integer::sum);
                }
            }

            boolean anyCrafted = false;
            for (BlackHoleRecipe recipe : BlackHoleRecipeRegistry.getRecipes()) {
                int batches = recipe.maxBatches(found);
                if (batches <= 0) continue;

                // 消耗 batches 倍材料
                Map<String, Integer> remaining = new HashMap<>();
                for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
                    remaining.put(entry.getKey(), entry.getValue() * batches);
                }
                for (EntityItem entityItem : items) {
                    ItemStack stack = entityItem.getItem();
                    if (stack.isEmpty()) continue;
                    String key = BlackHoleRecipe.keyOf(stack);
                    int needed = remaining.getOrDefault(key, 0);
                    if (needed > 0) {
                        int consume = Math.min(needed, stack.getCount());
                        stack.shrink(consume);
                        remaining.put(key, needed - consume);
                        if (stack.isEmpty()) {
                            entityItem.setDead();
                        }
                    }
                }
                // 同步 found,供后续配方计算批次
                for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
                    found.merge(entry.getKey(), -entry.getValue() * batches, Integer::sum);
                }
                spawnOutputs(world, outputPos, recipe.getOutput(), batches);
                totalBatches += batches;
                anyCrafted = true;
            }
            if (!anyCrafted) break;
        }
        return totalBatches;
    }

    /**
     * 生成 batches 份产物,按物品最大堆叠拆分为多个物品实体从指定位置喷出.
     */
    private static void spawnOutputs(World world, BlockPos outputPos, ItemStack output, int batches) {
        long remaining = (long) output.getCount() * batches;
        int maxStack = output.getMaxStackSize();
        while (remaining > 0) {
            int count = (int) Math.min(remaining, maxStack);
            ItemStack stack = output.copy();
            stack.setCount(count);
            EntityItem result = new EntityItem(world,
                    outputPos.getX() + 0.5, outputPos.getY() + 0.5, outputPos.getZ() + 0.5, stack);
            result.setNoPickupDelay();
            world.spawnEntity(result);
            remaining -= count;
        }
    }

    /**
     * 微型奇点：吸入 5×5×5 范围内可参与黑洞合成的物品实体.
     * 不匹配任何配方输入的物品原地不动,避免杂物堆积与误吞.
     */
    public static void suckMatchingItems(World world, BlockPos center) {
        AxisAlignedBB area = new AxisAlignedBB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3
        );
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, area);
        double tx = center.getX() + 0.5;
        double ty = center.getY() + 0.5;
        double tz = center.getZ() + 0.5;
        for (EntityItem item : items) {
            if (!isCraftingInput(item.getItem())) continue;
            double dx = tx - item.posX;
            double dy = ty - item.posY;
            double dz = tz - item.posZ;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-4) continue;
            item.setVelocity(dx / len * 0.25, dy / len * 0.25, dz / len * 0.25);
            item.velocityChanged = true;
        }
    }

    /**
     * 判断物品是否可作为任一黑洞配方的输入（精确匹配 "registryName:meta",或带 NBT 后缀的 key）.
     * keyOf 格式为 "registryName:meta[+NBT字符串]",NBT 以 '{' 起始；
     * 不能用裸前缀匹配,否则 meta 1 会误配 meta 10.
     */
    public static boolean isCraftingInput(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) return false;
        String itemId = stack.getItem().getRegistryName().toString() + ":" + stack.getMetadata();
        for (BlackHoleRecipe recipe : BlackHoleRecipeRegistry.getRecipes()) {
            for (String inputKey : recipe.getInputs().keySet()) {
                if (inputKey.equals(itemId) || inputKey.startsWith(itemId + "{")) {
                    return true;
                }
            }
        }
        return false;
    }
}
