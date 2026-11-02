package com.github.aeddddd.ae2enhanced.api;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

/** 微型奇点与黑洞配方的只读 API；世界查询只允许在服务端主线程调用。 */
public final class MicroSingularityApi {
    private static volatile Provider provider;

    private MicroSingularityApi() {
    }

    public static void registerProvider(Provider newProvider) {
        provider = newProvider;
    }

    public static Optional<MicroSingularitySnapshot> snapshot(World world, BlockPos pos) {
        return provider == null ? Optional.empty() : provider.snapshot(world, pos);
    }

    public static List<RecipeMatchView> matchNearby(World world, BlockPos pos) {
        return provider == null ? java.util.Collections.emptyList() : provider.matchNearby(world, pos);
    }

    public static List<RecipeMatchView> matchStacks(List<CountedStackView> inputs) {
        return provider == null ? java.util.Collections.emptyList() : provider.matchStacks(inputs);
    }

    public static List<RecipeMatchView> blackHoleRecipes() {
        return provider == null ? java.util.Collections.emptyList() : provider.blackHoleRecipes();
    }

    public static List<RitualRecipeView> ritualRecipes() {
        return provider == null ? java.util.Collections.emptyList() : provider.ritualRecipes();
    }

    public static Optional<RitualRecipeView> matchRitual(World world, BlockPos center, ItemStack heldItem) {
        return provider == null ? Optional.empty() : provider.matchRitual(world, center, heldItem);
    }

    public static List<SingularityFuelView> fuelRecipes() {
        return provider == null ? java.util.Collections.emptyList() : provider.fuelRecipes();
    }

    public static Optional<SingularityFuelView> matchFuel(ItemStack stack) {
        return provider == null ? Optional.empty() : provider.matchFuel(stack);
    }

    public static Optional<ConstrainedSingularitySnapshot> constrainedSnapshot(ItemStack stack) {
        return provider == null ? Optional.empty() : provider.constrainedSnapshot(stack);
    }

    public interface Provider {
        Optional<MicroSingularitySnapshot> snapshot(World world, BlockPos pos);
        List<RecipeMatchView> matchNearby(World world, BlockPos pos);
        List<RecipeMatchView> matchStacks(List<CountedStackView> inputs);
        List<RecipeMatchView> blackHoleRecipes();
        List<RitualRecipeView> ritualRecipes();
        Optional<RitualRecipeView> matchRitual(World world, BlockPos center, ItemStack heldItem);
        List<SingularityFuelView> fuelRecipes();
        Optional<SingularityFuelView> matchFuel(ItemStack stack);
        Optional<ConstrainedSingularitySnapshot> constrainedSnapshot(ItemStack stack);
    }
}
