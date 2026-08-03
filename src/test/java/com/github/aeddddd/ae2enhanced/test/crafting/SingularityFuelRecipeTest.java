package com.github.aeddddd.ae2enhanced.test.crafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityFuelRecipe;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;

/**
 * {@link SingularityFuelRecipe} 单元测试:匹配、查找与配方接口默认值.
 */
class SingularityFuelRecipeTest {

    static {
        CraftingTestFixtures.init();
    }

    private static SingularityFuelRecipe newRecipe(int ticks, boolean permanent) {
        return new SingularityFuelRecipe(new ResourceLocation("ae2enhanced", "test_fuel"),
                Ingredient.of(Items.REDSTONE), ticks, permanent);
    }

    /** 访问器与构造参数一致. */
    @Test
    void testAccessors() {
        var recipe = newRecipe(12000, true);
        assertThat(recipe.getId()).isEqualTo(new ResourceLocation("ae2enhanced", "test_fuel"));
        assertThat(recipe.getTicks()).isEqualTo(12000);
        assertThat(recipe.isPermanent()).isTrue();
        assertThat(recipe.getItem().test(new ItemStack(Items.REDSTONE))).isTrue();
    }

    /** matches:匹配物品 → true;不匹配/空堆 → false. */
    @Test
    void testMatchesStack() {
        var recipe = newRecipe(100, false);
        assertThat(recipe.matches(new ItemStack(Items.REDSTONE))).isTrue();
        assertThat(recipe.matches(new ItemStack(Items.REDSTONE, 64))).isTrue();
        assertThat(recipe.matches(new ItemStack(Items.STONE))).isFalse();
        assertThat(recipe.matches(ItemStack.EMPTY)).isFalse();
    }

    /** findFor:返回第一个匹配的燃料配方;空堆/无匹配 → null. */
    @Test
    void testFindFor() {
        var recipe = newRecipe(100, false);
        var level = mock(Level.class);
        var recipeManager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(recipeManager);
        when(recipeManager.getAllRecipesFor(ModRecipes.SINGULARITY_FUEL_TYPE.get()))
                .thenReturn(List.of(recipe));

        assertThat(SingularityFuelRecipe.findFor(level, new ItemStack(Items.REDSTONE))).isSameAs(recipe);
        assertThat(SingularityFuelRecipe.findFor(level, new ItemStack(Items.STONE))).isNull();
        assertThat(SingularityFuelRecipe.findFor(level, ItemStack.EMPTY)).isNull();
    }

    /** 配方接口固定值:不用于工作台. */
    @Test
    void testRecipeInterfaceDefaults() {
        var recipe = newRecipe(100, false);
        assertThat(recipe.matches(null, null)).isFalse();
        assertThat(recipe.assemble(null, null)).isSameAs(ItemStack.EMPTY);
        assertThat(recipe.canCraftInDimensions(3, 3)).isFalse();
        assertThat(recipe.getResultItem(null)).isSameAs(ItemStack.EMPTY);
        assertThat(recipe.isSpecial()).isTrue();
        assertThat(recipe.getSerializer()).isSameAs(ModRecipes.SINGULARITY_FUEL_SERIALIZER.get());
        assertThat(recipe.getType()).isSameAs(ModRecipes.SINGULARITY_FUEL_TYPE.get());
    }
}
