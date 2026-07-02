package com.github.aeddddd.ae2enhanced.crafting.blackhole;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * 黑洞配方序列化器。
 */
public class BlackHoleRecipeSerializer implements RecipeSerializer<BlackHoleRecipe> {

    @Override
    public BlackHoleRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
        JsonObject inputsObject = GsonHelper.getAsJsonObject(json, "inputs");
        Map<String, Integer> inputs = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : inputsObject.entrySet()) {
            inputs.put(entry.getKey(), GsonHelper.convertToInt(entry.getValue(), "input." + entry.getKey()));
        }

        if (!json.has("output")) {
            throw new JsonSyntaxException("Missing output for black hole recipe: " + recipeId);
        }
        ItemStack output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "output"));

        return new BlackHoleRecipe(recipeId, inputs, output);
    }

    @Override
    public BlackHoleRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<String, Integer> inputs = new HashMap<>();
        for (int i = 0; i < size; i++) {
            inputs.put(buffer.readUtf(), buffer.readVarInt());
        }
        ItemStack output = buffer.readItem();
        return new BlackHoleRecipe(recipeId, inputs, output);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, BlackHoleRecipe recipe) {
        buffer.writeVarInt(recipe.getInputs().size());
        for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
        buffer.writeItem(recipe.getOutput());
    }
}
