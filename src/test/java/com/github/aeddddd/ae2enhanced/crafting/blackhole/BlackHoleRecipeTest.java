package com.github.aeddddd.ae2enhanced.crafting.blackhole;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BlackHoleRecipe} 单元测试。
 */
class BlackHoleRecipeTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private BlackHoleRecipe newRecipe() {
        Map<String, Integer> inputs = new HashMap<>();
        inputs.put("minecraft:stone", 3);
        inputs.put("minecraft:dirt", 2);
        return new BlackHoleRecipe(new ResourceLocation("ae2enhanced", "test_recipe"), inputs,
                new ItemStack(Items.DIAMOND, 1));
    }

    @Test
    void testMatchesWithExactInputs() {
        BlackHoleRecipe recipe = newRecipe();
        Map<String, Integer> found = Map.of("minecraft:stone", 3, "minecraft:dirt", 2);
        assertTrue(recipe.matches(found));
    }

    @Test
    void testMatchesWithSurplusInputs() {
        BlackHoleRecipe recipe = newRecipe();
        // 区域内物品多于配方需求时同样匹配
        Map<String, Integer> found = Map.of(
                "minecraft:stone", 10,
                "minecraft:dirt", 5,
                "minecraft:gravel", 99);
        assertTrue(recipe.matches(found));
    }

    @Test
    void testMatchesFailsOnInsufficientCount() {
        BlackHoleRecipe recipe = newRecipe();
        Map<String, Integer> found = Map.of("minecraft:stone", 2, "minecraft:dirt", 2);
        assertFalse(recipe.matches(found));
    }

    @Test
    void testMatchesFailsOnMissingItem() {
        BlackHoleRecipe recipe = newRecipe();
        Map<String, Integer> found = Map.of("minecraft:stone", 3);
        assertFalse(recipe.matches(found));
    }

    @Test
    void testMatchesWithEmptyFound() {
        BlackHoleRecipe recipe = newRecipe();
        assertFalse(recipe.matches(Map.of()));
    }

    @Test
    void testKeyOfWithoutNbt() {
        assertEquals("minecraft:stone", BlackHoleRecipe.keyOf(new ItemStack(Items.STONE)));
        assertEquals("minecraft:dirt", BlackHoleRecipe.keyOf(new ItemStack(Items.DIRT)));
    }

    @Test
    void testKeyOfDamageableItemCarriesDamageTag() {
        // Forge 会为可损坏物品初始化 {Damage:0} 标签，因此其键附带 NBT 后缀
        String key = BlackHoleRecipe.keyOf(new ItemStack(Items.DIAMOND_SWORD));
        assertTrue(key.startsWith("minecraft:diamond_sword#"));
    }

    @Test
    void testKeyOfWithNbt() {
        ItemStack stack = new ItemStack(Items.STONE);
        CompoundTag tag = new CompoundTag();
        tag.putInt("custom", 1);
        stack.setTag(tag);

        String key = BlackHoleRecipe.keyOf(stack);
        // 带 NBT 时键为 注册名#NBT 形式
        assertTrue(key.startsWith("minecraft:stone#"));
        assertTrue(key.contains("custom"));
    }

    @Test
    void testGetIdAndStringId() {
        BlackHoleRecipe recipe = newRecipe();
        assertEquals(new ResourceLocation("ae2enhanced", "test_recipe"), recipe.getId());
        assertEquals("ae2enhanced:test_recipe", recipe.getStringId());
    }

    @Test
    void testGetInputsReturnsDefensiveCopy() {
        BlackHoleRecipe recipe = newRecipe();
        Map<String, Integer> inputs = recipe.getInputs();
        inputs.clear();
        // 修改返回的映射不影响配方内部数据
        assertEquals(2, recipe.getInputs().size());
        assertEquals(3, recipe.getInputs().get("minecraft:stone"));
    }

    @Test
    void testGetOutputReturnsCopy() {
        BlackHoleRecipe recipe = newRecipe();
        ItemStack output = recipe.getOutput();
        output.setCount(99);
        // 修改返回的物品堆不影响配方输出
        assertEquals(1, recipe.getOutput().getCount());
        assertEquals(Items.DIAMOND, recipe.getOutput().getItem());
    }

    @Test
    void testAssembleReturnsCopy() {
        BlackHoleRecipe recipe = newRecipe();
        ItemStack assembled = recipe.assemble(null, null);
        assertNotSame(recipe.getOutput(), assembled);
        assertEquals(Items.DIAMOND, assembled.getItem());
    }

    @Test
    void testRecipeInterfaceDefaults() {
        BlackHoleRecipe recipe = newRecipe();
        // 该配方不用于工作台，相关接口返回固定值
        assertTrue(recipe.isSpecial());
        assertFalse(recipe.canCraftInDimensions(3, 3));
        assertFalse(recipe.matches(null, null));
        assertEquals(Items.DIAMOND, recipe.getResultItem(null).getItem());
    }
}
