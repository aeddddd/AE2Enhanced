package com.github.aeddddd.ae2enhanced.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipeSerializer;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRecipe;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRecipeRegistry;

/**
 * 配方与配方序列化器注册中心。
 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> DR = DeferredRegister.create(
            net.minecraftforge.registries.ForgeRegistries.RECIPE_SERIALIZERS, AE2Enhanced.MOD_ID);

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE,
            AE2Enhanced.MOD_ID);

    public static final RegistryObject<RecipeSerializer<BlackHoleRecipe>> BLACK_HOLE_SERIALIZER = DR.register("black_hole",
            BlackHoleRecipeSerializer::new);

    public static final RegistryObject<RecipeType<BlackHoleRecipe>> BLACK_HOLE_TYPE = RECIPE_TYPES.register("black_hole",
            () -> new RecipeType<BlackHoleRecipe>() {
                @Override
                public String toString() {
                    return AE2Enhanced.MOD_ID + ":black_hole";
                }
            });

    /**
     * 注册代码级配方：微型奇点仪式。
     * 应在 FMLCommonSetupEvent 中调用，确保 AE2 物品/方块已注册。
     */
    public static void init() {
        registerSingularityRecipes();
    }

    private static void registerSingularityRecipes() {
        Item singularity = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "singularity"));
        Block controller = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("ae2", "controller"));
        if (singularity != null && controller != null) {
            List<ItemStack> ritualInputs = new ArrayList<>();
            ritualInputs.add(new ItemStack(singularity, 64));
            ritualInputs.add(new ItemStack(Items.NETHER_STAR, 4));
            SingularityRecipeRegistry.register(new SingularityRecipe(
                    "micro_singularity_ritual",
                    ritualInputs,
                    new ItemStack(Items.NETHER_STAR),
                    controller,
                    6000));
        } else {
            AE2Enhanced.LOGGER.warn("无法获取 AE2 奇点或控制器方块，微型奇点仪式配方未注册");
        }
        SingularityRecipeRegistry.applyPendingRemovals();
    }

    private ModRecipes() {
    }
}
