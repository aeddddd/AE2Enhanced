package com.github.aeddddd.ae2enhanced.chamber;

import com.github.aeddddd.ae2enhanced.crafting.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 默认配方、脚本修改和最终派生视图的生命周期入口。 */
public final class RecipeCatalog {

    private static final List<ChamberRecipe> CUSTOM_CHAMBER_RECIPES = new ArrayList<>();
    private static Phase phase = Phase.EMPTY;
    private RecipeCatalog() {
    }

    public static synchronized void beginDefaults() {
        if (phase == Phase.DEFAULTS || phase == Phase.FINALIZED) {
            throw new IllegalStateException("Recipe defaults cannot start during " + phase);
        }
        phase = Phase.DEFAULTS;
    }

    public static synchronized void completeDefaults() {
        requirePhase(Phase.DEFAULTS);
        applyPendingRemovals();
        phase = Phase.SCRIPTED_CHANGES;
    }

    public static synchronized void finalizeRecipes() {
        if (phase == Phase.DEFAULTS) {
            throw new IllegalStateException("Recipe defaults are still being registered");
        }
        if (phase == Phase.EMPTY) {
            phase = Phase.SCRIPTED_CHANGES;
        }
        if (phase != Phase.FINALIZED) {
            applyPendingRemovals();
            phase = Phase.FINALIZED;
        }
    }

    public static synchronized Phase phase() {
        return phase;
    }

    public static synchronized void addDefault(SingularityRecipe recipe) {
        requirePhase(Phase.DEFAULTS);
        SingularityRecipeRegistry.register(recipe);
    }

    public static synchronized void addDefault(BlackHoleRecipe recipe) {
        requirePhase(Phase.DEFAULTS);
        BlackHoleRecipeRegistry.register(recipe);
    }

    public static synchronized void addDefault(SingularityFuelRecipe recipe) {
        requirePhase(Phase.DEFAULTS);
        SingularityFuelRegistry.register(recipe);
    }

    public static void addScripted(SingularityRecipe recipe) {
        scriptedChange();
        SingularityRecipeRegistry.cancelRemoval(recipe.getId());
        SingularityRecipeRegistry.register(recipe);
    }

    public static void addScripted(BlackHoleRecipe recipe) {
        scriptedChange();
        BlackHoleRecipeRegistry.cancelRemoval(recipe.getId());
        BlackHoleRecipeRegistry.register(recipe);
        ChamberRecipeIndex.markDirty();
    }

    public static void addScripted(SingularityFuelRecipe recipe) {
        scriptedChange();
        SingularityFuelRegistry.cancelRemoval(recipe.getId());
        SingularityFuelRegistry.register(recipe);
    }

    public static synchronized void addScripted(ChamberRecipe recipe) {
        scriptedChange();
        CUSTOM_CHAMBER_RECIPES.removeIf(r -> r.getId().equals(recipe.getId()));
        CUSTOM_CHAMBER_RECIPES.add(recipe);
        ChamberRecipeIndex.markDirty();
    }

    public static void removeScriptedSingularity(String id) {
        scriptedChange();
        SingularityRecipeRegistry.queueRemoval(id);
    }

    public static void removeScriptedBlackHole(String id) {
        scriptedChange();
        BlackHoleRecipeRegistry.queueRemoval(id);
        ChamberRecipeIndex.markDirty();
    }

    public static void removeScriptedFuel(String id) {
        scriptedChange();
        SingularityFuelRegistry.queueRemoval(id);
    }

    public static synchronized boolean removeScriptedChamber(String id) {
        scriptedChange();
        boolean removed = CUSTOM_CHAMBER_RECIPES.removeIf(r -> r.getId().equals(id));
        if (removed) {
            ChamberRecipeIndex.markDirty();
        }
        return removed;
    }

    public static List<BlackHoleRecipe> blackHoleRecipes() {
        finalizeRecipes();
        return BlackHoleRecipeRegistry.getRecipes();
    }

    @Nullable
    public static BlackHoleRecipe findBlackHole(Map<String, Integer> found) {
        finalizeRecipes();
        return BlackHoleRecipeRegistry.findMatching(found);
    }

    public static List<SingularityRecipe> singularityRecipes() {
        finalizeRecipes();
        return SingularityRecipeRegistry.getRecipes();
    }

    @Nullable
    public static SingularityRecipe findSingularity(World world, BlockPos center, ItemStack heldItem) {
        finalizeRecipes();
        return SingularityRecipeRegistry.findMatching(world, center, heldItem);
    }

    public static List<SingularityFuelRecipe> singularityFuels() {
        finalizeRecipes();
        return SingularityFuelRegistry.getRecipes();
    }

    @Nullable
    public static SingularityFuelRecipe findSingularityFuel(ItemStack stack) {
        finalizeRecipes();
        return SingularityFuelRegistry.findFor(stack);
    }

    public static synchronized List<ChamberRecipe> customChamberRecipes() {
        finalizeRecipes();
        return Collections.unmodifiableList(new ArrayList<>(CUSTOM_CHAMBER_RECIPES));
    }

    private static synchronized void scriptedChange() {
        if (phase == Phase.DEFAULTS) {
            throw new IllegalStateException("Scripted recipe change during default registration");
        }
        phase = Phase.SCRIPTED_CHANGES;
    }

    private static void applyPendingRemovals() {
        SingularityRecipeRegistry.applyPendingRemovals();
        BlackHoleRecipeRegistry.applyPendingRemovals();
        SingularityFuelRegistry.applyPendingRemovals();
    }

    private static void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Expected recipe phase " + expected + " but was " + phase);
        }
    }

    public enum Phase {
        EMPTY,
        DEFAULTS,
        SCRIPTED_CHANGES,
        FINALIZED
    }
}
