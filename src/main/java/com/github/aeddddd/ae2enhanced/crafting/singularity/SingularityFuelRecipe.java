package com.github.aeddddd.ae2enhanced.crafting.singularity;

import javax.annotation.Nullable;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.registry.ModRecipes;

/**
 * 微型奇点燃料配方（JSON 数据驱动,KJS 可通过标准配方管理器增删）.
 * <p>手持匹配物品右键微型奇点时消耗 1 个：增加 {@code ticks} 存在时间,
 * 或当 {@code permanent == true} 时使奇点永久存在.</p>
 */
public class SingularityFuelRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final Ingredient item;
    private final int ticks;
    private final boolean permanent;

    public SingularityFuelRecipe(ResourceLocation id, Ingredient item, int ticks, boolean permanent) {
        this.id = id;
        this.item = item;
        this.ticks = ticks;
        this.permanent = permanent;
    }

    public Ingredient getItem() {
        return item;
    }

    public int getTicks() {
        return ticks;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && item.test(stack);
    }

    /**
     * 在配方管理器中查找第一个匹配该物品的燃料配方.
     */
    @Nullable
    public static SingularityFuelRecipe findFor(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (SingularityFuelRecipe recipe : level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.SINGULARITY_FUEL_TYPE.get())) {
            if (recipe.matches(stack)) {
                return recipe;
            }
        }
        return null;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SINGULARITY_FUEL_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.SINGULARITY_FUEL_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
