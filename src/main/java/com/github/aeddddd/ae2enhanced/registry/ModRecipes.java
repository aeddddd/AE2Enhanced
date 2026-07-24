package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipeSerializer;
import com.github.aeddddd.ae2enhanced.crafting.omnitool.OmniToolUpgradeRecipe;
import com.github.aeddddd.ae2enhanced.crafting.omnitool.OmniToolUpgradeRecipeSerializer;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityFuelRecipe;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityFuelRecipeSerializer;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRitualRecipe;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRitualRecipeSerializer;

/**
 * 配方与配方序列化器注册中心.
 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> DR = DeferredRegister.create(
            net.minecraftforge.registries.ForgeRegistries.RECIPE_SERIALIZERS, AE2Enhanced.MOD_ID);

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE,
            AE2Enhanced.MOD_ID);

    public static final RegistryObject<RecipeSerializer<BlackHoleRecipe>> BLACK_HOLE_SERIALIZER = DR.register("black_hole",
            BlackHoleRecipeSerializer::new);

    public static final RegistryObject<RecipeSerializer<OmniToolUpgradeRecipe>> OMNI_TOOL_UPGRADE_SERIALIZER = DR
            .register("omni_tool_upgrade", OmniToolUpgradeRecipeSerializer::new);

    public static final RegistryObject<RecipeType<BlackHoleRecipe>> BLACK_HOLE_TYPE = RECIPE_TYPES.register("black_hole",
            () -> new RecipeType<BlackHoleRecipe>() {
                @Override
                public String toString() {
                    return AE2Enhanced.MOD_ID + ":black_hole";
                }
            });

    public static final RegistryObject<RecipeSerializer<SingularityRitualRecipe>> SINGULARITY_RITUAL_SERIALIZER = DR
            .register("singularity_ritual", SingularityRitualRecipeSerializer::new);

    public static final RegistryObject<RecipeType<SingularityRitualRecipe>> SINGULARITY_RITUAL_TYPE = RECIPE_TYPES
            .register("singularity_ritual", () -> new RecipeType<SingularityRitualRecipe>() {
                @Override
                public String toString() {
                    return AE2Enhanced.MOD_ID + ":singularity_ritual";
                }
            });

    public static final RegistryObject<RecipeSerializer<SingularityFuelRecipe>> SINGULARITY_FUEL_SERIALIZER = DR
            .register("singularity_fuel", SingularityFuelRecipeSerializer::new);

    public static final RegistryObject<RecipeType<SingularityFuelRecipe>> SINGULARITY_FUEL_TYPE = RECIPE_TYPES
            .register("singularity_fuel", () -> new RecipeType<SingularityFuelRecipe>() {
                @Override
                public String toString() {
                    return AE2Enhanced.MOD_ID + ":singularity_fuel";
                }
            });

    private ModRecipes() {
    }
}
