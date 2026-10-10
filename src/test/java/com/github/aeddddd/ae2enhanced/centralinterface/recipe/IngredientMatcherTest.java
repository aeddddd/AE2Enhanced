package com.github.aeddddd.ae2enhanced.centralinterface.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link IngredientMatcher} 测试（重构计划 P3）。
 *
 * <p>这是"配方校验重做"的核心断言：样板声明的输入必须真的能构成配方，
 * 否则"任意输入 + 真产物样板"可以凭空刷出真产物。</p>
 */
public class IngredientMatcherTest {

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
    }

    @Test
    public void singleIngredientMatches() {
        assertThat(IngredientMatcher.matches(
                table(new ItemStack(Items.APPLE)),
                recipe(Ingredient.fromStacks(new ItemStack(Items.APPLE))))).isTrue();
    }

    /** 无序多重集：样板槽位顺序与配方 ingredient 顺序不一致也必须判为吻合。 */
    @Test
    public void orderDoesNotMatter() {
        assertThat(IngredientMatcher.matches(
                table(new ItemStack(Items.APPLE), new ItemStack(Items.COAL)),
                recipe(Ingredient.fromStacks(new ItemStack(Items.COAL)),
                        Ingredient.fromStacks(new ItemStack(Items.APPLE))))).isTrue();
    }

    /** 数量不一致：样板少声明一件（剩下一件未被计价）必须拒绝。 */
    @Test
    public void fewerInputsThanRecipeIsRejected() {
        assertThat(IngredientMatcher.matches(
                table(new ItemStack(Items.APPLE)),
                recipe(Ingredient.fromStacks(new ItemStack(Items.APPLE)),
                        Ingredient.fromStacks(new ItemStack(Items.COAL))))).isFalse();
    }

    /** 数量不一致：样板多声明一件也必须拒绝。 */
    @Test
    public void moreInputsThanRecipeIsRejected() {
        assertThat(IngredientMatcher.matches(
                table(new ItemStack(Items.APPLE), new ItemStack(Items.COAL)),
                recipe(Ingredient.fromStacks(new ItemStack(Items.APPLE))))).isFalse();
    }

    /** 内容不符：用不相干的物品冒充配方输入必须被拒绝（这正是重构前的漏洞）。 */
    @Test
    public void wrongItemIsRejected() {
        assertThat(IngredientMatcher.matches(
                table(new ItemStack(Items.STICK)),
                recipe(Ingredient.fromStacks(new ItemStack(Items.APPLE))))).isFalse();
    }

    /** 配方中的空 ingredient 槽位不计入有效需求。 */
    @Test
    public void emptyIngredientSlotsAreIgnored() {
        NonNullList<Ingredient> required = NonNullList.create();
        required.add(Ingredient.EMPTY);
        required.add(Ingredient.fromStacks(new ItemStack(Items.APPLE)));

        assertThat(IngredientMatcher.matches(table(new ItemStack(Items.APPLE)), required)).isTrue();
    }

    /** 同一物品需要两份时，只有一份实物必须拒绝。 */
    @Test
    public void duplicatedIngredientNeedsTwoStacks() {
        NonNullList<Ingredient> required = NonNullList.create();
        required.add(Ingredient.fromStacks(new ItemStack(Items.APPLE)));
        required.add(Ingredient.fromStacks(new ItemStack(Items.APPLE)));

        assertThat(IngredientMatcher.matches(table(new ItemStack(Items.APPLE)), required)).isFalse();
        assertThat(IngredientMatcher.matches(
                table(new ItemStack(Items.APPLE), new ItemStack(Items.APPLE)), required)).isTrue();
    }

    @Test
    public void emptyInputOrRecipeIsRejected() {
        assertThat(IngredientMatcher.matches(table(), recipe())).isFalse();
        assertThat(IngredientMatcher.matches(null, recipe())).isFalse();
        assertThat(IngredientMatcher.matches(table(), (IRecipe) null)).isFalse();
        assertThat(IngredientMatcher.matches(table(), (NonNullList<Ingredient>) null)).isFalse();
    }

    // ---- helpers ----

    private static InventoryCrafting table(ItemStack... stacks) {
        InventoryCrafting inv = new InventoryCrafting(mock(Container.class), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            inv.setInventorySlotContents(i, stacks[i]);
        }
        return inv;
    }

    private static NonNullList<Ingredient> ingredients(Ingredient... values) {
        NonNullList<Ingredient> list = NonNullList.create();
        for (Ingredient ingredient : values) {
            list.add(ingredient);
        }
        return list;
    }

    private static IRecipe recipe(Ingredient... values) {
        IRecipe recipe = mock(IRecipe.class);
        when(recipe.getIngredients()).thenReturn(ingredients(values));
        return recipe;
    }
}
