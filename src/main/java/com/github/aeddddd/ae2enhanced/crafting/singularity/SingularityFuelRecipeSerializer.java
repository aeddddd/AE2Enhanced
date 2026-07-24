package com.github.aeddddd.ae2enhanced.crafting.singularity;

import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * 微型奇点燃料配方序列化器.
 * <p>JSON 格式：
 * <pre>{ "type": "ae2enhanced:singularity_fuel", "item": { "item": "..." }, "ticks": 12000 }</pre>
 * 或永久形态：
 * <pre>{ "type": "ae2enhanced:singularity_fuel", "item": { "item": "..." }, "permanent": true }</pre>
 */
public class SingularityFuelRecipeSerializer implements RecipeSerializer<SingularityFuelRecipe> {

    @Override
    public SingularityFuelRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
        Ingredient item = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "item"));
        boolean permanent = GsonHelper.getAsBoolean(json, "permanent", false);
        int ticks = GsonHelper.getAsInt(json, "ticks", 0);
        return new SingularityFuelRecipe(recipeId, item, ticks, permanent);
    }

    @Override
    public SingularityFuelRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
        Ingredient item = Ingredient.fromNetwork(buffer);
        int ticks = buffer.readVarInt();
        boolean permanent = buffer.readBoolean();
        return new SingularityFuelRecipe(recipeId, item, ticks, permanent);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, SingularityFuelRecipe recipe) {
        recipe.getItem().toNetwork(buffer);
        buffer.writeVarInt(recipe.getTicks());
        buffer.writeBoolean(recipe.isPermanent());
    }
}
