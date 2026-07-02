package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipeSerializer;

/**
 * 配方与配方序列化器注册中心。
 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> DR = DeferredRegister.create(
            net.minecraftforge.registries.ForgeRegistries.RECIPE_SERIALIZERS, AE2Enhanced.MOD_ID);

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(
            Registries.RECIPE_TYPE, AE2Enhanced.MOD_ID);

    public static final RegistryObject<RecipeSerializer<BlackHoleRecipe>> BLACK_HOLE_SERIALIZER = DR.register("black_hole",
            BlackHoleRecipeSerializer::new);

    public static final RegistryObject<RecipeType<BlackHoleRecipe>> BLACK_HOLE_TYPE = RECIPE_TYPES.register("black_hole",
            () -> new RecipeType<BlackHoleRecipe>() {
                @Override
                public String toString() {
                    return AE2Enhanced.MOD_ID + ":black_hole";
                }
            });

    private ModRecipes() {
    }
}
