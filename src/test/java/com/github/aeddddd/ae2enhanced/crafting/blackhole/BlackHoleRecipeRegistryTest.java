package com.github.aeddddd.ae2enhanced.crafting.blackhole;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BlackHoleRecipeRegistry} 单元测试。
 * <p>注册表为静态状态，每个测试结束后统一清理，避免相互污染。</p>
 */
class BlackHoleRecipeRegistryTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        BlackHoleRecipeRegistry.RECIPES.clear();
        BlackHoleRecipeRegistry.PENDING_REMOVALS.clear();
    }

    private BlackHoleRecipe newRecipe(String id, String inputKey, int count) {
        return new BlackHoleRecipe(new ResourceLocation(id), Map.of(inputKey, count), new ItemStack(Items.DIAMOND));
    }

    @Test
    void testRegisterAndFindMatching() {
        BlackHoleRecipe recipe = newRecipe("ae2enhanced:r1", "minecraft:stone", 3);
        BlackHoleRecipeRegistry.register(recipe);

        BlackHoleRecipe found = BlackHoleRecipeRegistry.findMatching(Map.of("minecraft:stone", 5));
        assertSame(recipe, found);
    }

    @Test
    void testFindMatchingReturnsNullWhenNoMatch() {
        BlackHoleRecipeRegistry.register(newRecipe("ae2enhanced:r1", "minecraft:stone", 3));

        assertNull(BlackHoleRecipeRegistry.findMatching(Map.of("minecraft:stone", 2)));
        assertNull(BlackHoleRecipeRegistry.findMatching(Map.of()));
    }

    @Test
    void testFindMatchingReturnsFirstMatch() {
        BlackHoleRecipe first = newRecipe("ae2enhanced:r1", "minecraft:stone", 2);
        BlackHoleRecipe second = newRecipe("ae2enhanced:r2", "minecraft:stone", 3);
        BlackHoleRecipeRegistry.register(first);
        BlackHoleRecipeRegistry.register(second);

        // 同一输入同时满足两个配方时返回先注册者
        BlackHoleRecipe found = BlackHoleRecipeRegistry.findMatching(Map.of("minecraft:stone", 10));
        assertSame(first, found);
    }

    @Test
    void testRemoveById() {
        BlackHoleRecipe keep = newRecipe("ae2enhanced:keep", "minecraft:stone", 1);
        BlackHoleRecipe drop = newRecipe("ae2enhanced:drop", "minecraft:dirt", 1);
        BlackHoleRecipeRegistry.register(keep);
        BlackHoleRecipeRegistry.register(drop);

        BlackHoleRecipeRegistry.removeById("ae2enhanced:drop");

        assertEquals(1, BlackHoleRecipeRegistry.RECIPES.size());
        assertSame(keep, BlackHoleRecipeRegistry.RECIPES.get(0));
        assertNull(BlackHoleRecipeRegistry.findMatching(Map.of("minecraft:dirt", 1)));
    }

    @Test
    void testRemoveByIdAlsoClearsPendingFlag() {
        BlackHoleRecipeRegistry.register(newRecipe("ae2enhanced:r1", "minecraft:stone", 1));
        BlackHoleRecipeRegistry.queueRemoval("ae2enhanced:r1");

        BlackHoleRecipeRegistry.removeById("ae2enhanced:r1");

        assertTrue(BlackHoleRecipeRegistry.PENDING_REMOVALS.isEmpty());
        assertTrue(BlackHoleRecipeRegistry.RECIPES.isEmpty());
    }

    @Test
    void testQueueRemovalDoesNotRemoveImmediately() {
        BlackHoleRecipe recipe = newRecipe("ae2enhanced:r1", "minecraft:stone", 1);
        BlackHoleRecipeRegistry.register(recipe);

        BlackHoleRecipeRegistry.queueRemoval("ae2enhanced:r1");

        // 排队期间配方仍可匹配
        assertSame(recipe, BlackHoleRecipeRegistry.findMatching(Map.of("minecraft:stone", 1)));
        assertTrue(BlackHoleRecipeRegistry.PENDING_REMOVALS.contains("ae2enhanced:r1"));
    }

    @Test
    void testApplyPendingRemovals() {
        BlackHoleRecipeRegistry.register(newRecipe("ae2enhanced:r1", "minecraft:stone", 1));
        BlackHoleRecipeRegistry.register(newRecipe("ae2enhanced:r2", "minecraft:dirt", 1));
        BlackHoleRecipeRegistry.queueRemoval("ae2enhanced:r1");

        BlackHoleRecipeRegistry.applyPendingRemovals();

        assertEquals(1, BlackHoleRecipeRegistry.RECIPES.size());
        assertTrue(BlackHoleRecipeRegistry.PENDING_REMOVALS.isEmpty());
        assertNull(BlackHoleRecipeRegistry.findMatching(Map.of("minecraft:stone", 1)));
    }

    @Test
    void testApplyPendingRemovalsWithEmptyQueueIsNoOp() {
        BlackHoleRecipeRegistry.register(newRecipe("ae2enhanced:r1", "minecraft:stone", 1));

        BlackHoleRecipeRegistry.applyPendingRemovals();

        assertEquals(1, BlackHoleRecipeRegistry.RECIPES.size());
    }
}
