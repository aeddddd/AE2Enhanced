package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex;
import com.github.aeddddd.ae2enhanced.chamber.RecipeCatalog;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeActionContractTest {
    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static IItemStack stack(ItemStack stack, int amount) {
        IItemStack result = mock(IItemStack.class);
        when(result.getInternal()).thenReturn(stack);
        when(result.getAmount()).thenReturn(amount);
        when(result.getName()).thenReturn(stack.getItem().getRegistryName().toString());
        return result;
    }

    @Test
    void explicitChamberRecipeUsesAlternativeGroupsAndValidatesInputs() {
        IItemStack output = stack(new ItemStack(Items.DIAMOND), 1);
        IItemStack cobble = stack(new ItemStack(Blocks.COBBLESTONE), 1);
        IItemStack stone = stack(new ItemStack(Blocks.STONE), 1);
        IIngredient alternatives = mock(IIngredient.class);
        when(alternatives.getAmount()).thenReturn(2);
        when(alternatives.getItems()).thenReturn(Arrays.asList(cobble, stone));
        SingularityChamberCraftTweaker.AddRecipeAction action =
                new SingularityChamberCraftTweaker.AddRecipeAction(
                        "test:ct_alternatives", output, new IIngredient[]{alternatives}, 80);
        assertThat(action.validate()).isTrue();
        assertThat(new SingularityChamberCraftTweaker.AddRecipeAction(
                "test:invalid", output, new IIngredient[]{alternatives}, 0).validate()).isFalse();
        assertThat(new SingularityChamberCraftTweaker.AddRecipeAction(
                "test:invalid", output, null, 80).validate()).isFalse();

        action.apply();
        try {
            ChamberRecipe recipe = RecipeCatalog.customChamberRecipes().stream()
                    .filter(r -> r.getId().equals("test:ct_alternatives")).findFirst().orElseThrow(AssertionError::new);
            assertThat(recipe.getInputGroups()).hasSize(1);
            assertThat(recipe.getInputGroups().get(0).getTemplates()).hasSize(2);
            assertThat(recipe.getInputGroups().get(0).getCount()).isEqualTo(2);
            assertThat(recipe.getTimeTicks()).isEqualTo(80);
        } finally {
            ChamberRecipeIndex.removeCustomRecipe("test:ct_alternatives");
        }
    }

    @Test
    void scriptedRemoveThenAddIsNotRemovedAtFinalize() {
        String id = "test:ct_remove_add";
        BlackHoleRecipe recipe = new BlackHoleRecipe(id,
                Collections.singletonMap(BlackHoleRecipe.keyOf(new ItemStack(Blocks.STONE)), 1),
                new ItemStack(Items.DIAMOND));
        RecipeCatalog.removeScriptedBlackHole(id);
        RecipeCatalog.addScripted(recipe);
        BlackHoleRecipeRegistry.applyPendingRemovals();
        try {
            assertThat(BlackHoleRecipeRegistry.getRecipes()).anyMatch(r -> r.getId().equals(id));
        } finally {
            BlackHoleRecipeRegistry.removeById(id);
        }
    }

    @Test
    void queuedDefaultRemovalStillAppliesWhenDefaultRegistersLater() {
        String id = "test:ct_default_removal";
        BlackHoleRecipe recipe = new BlackHoleRecipe(id,
                Collections.singletonMap(BlackHoleRecipe.keyOf(new ItemStack(Blocks.STONE)), 1),
                new ItemStack(Items.DIAMOND));
        BlackHoleRecipeRegistry.queueRemoval(id);
        BlackHoleRecipeRegistry.register(recipe);
        BlackHoleRecipeRegistry.applyPendingRemovals();
        assertThat(BlackHoleRecipeRegistry.getRecipes()).noneMatch(r -> r.getId().equals(id));
    }

    @Test
    void blackHoleAndUpgradeActionsRejectInvalidArguments() {
        IItemStack output = stack(new ItemStack(Items.DIAMOND), 1);
        IItemStack input = stack(new ItemStack(Blocks.STONE), 2);
        assertThat(new BlackHoleCraftTweaker.AddRecipeAction(
                "test:valid", output, new IItemStack[]{input}).validate()).isTrue();
        assertThat(new BlackHoleCraftTweaker.AddRecipeAction(
                "", output, new IItemStack[]{input}).validate()).isFalse();
        assertThat(new BlackHoleCraftTweaker.AddRecipeAction(
                "test:invalid", null, new IItemStack[]{input}).validate()).isFalse();
        assertThat(new AssemblyHubCraftTweaker.RegisterUpgradeAction(
                output, "parallel", 2, new long[]{1}).validate()).isFalse();
        assertThat(new AssemblyHubCraftTweaker.RegisterUpgradeAction(
                output, "parallel", 2, new long[]{1, 0}).validate()).isFalse();
        assertThat(new AssemblyHubCraftTweaker.RegisterUpgradeAction(
                output, "parallel", 2, new long[]{1, 4}).validate()).isTrue();
    }
}
