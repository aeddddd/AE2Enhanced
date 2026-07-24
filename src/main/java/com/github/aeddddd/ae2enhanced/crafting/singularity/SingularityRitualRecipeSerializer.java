package com.github.aeddddd.ae2enhanced.crafting.singularity;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;

/**
 * 微型奇点仪式配方序列化器.
 * <p>JSON 格式：
 * <pre>{
 *   "type": "ae2enhanced:singularity_ritual",
 *   "dropped": [ { "item": "ae2:singularity", "count": 64 } ],
 *   "held": { "item": "minecraft:nether_star" },
 *   "target_block": "ae2:controller",
 *   "lifetime": 6000
 * }</pre>
 * held / target_block / lifetime 均可省略.
 */
public class SingularityRitualRecipeSerializer implements RecipeSerializer<SingularityRitualRecipe> {

    @Override
    public SingularityRitualRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
        List<ItemStack> dropped = new ArrayList<>();
        if (json.has("dropped")) {
            JsonArray array = GsonHelper.getAsJsonArray(json, "dropped");
            for (JsonElement element : array) {
                dropped.add(ShapedRecipe.itemStackFromJson(GsonHelper.convertToJsonObject(element, "dropped")));
            }
        }

        ItemStack held = ItemStack.EMPTY;
        if (json.has("held")) {
            held = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "held"));
        }

        Block targetBlock = null;
        if (json.has("target_block")) {
            ResourceLocation blockId = new ResourceLocation(GsonHelper.getAsString(json, "target_block"));
            targetBlock = BuiltInRegistries.BLOCK.get(blockId);
            if (targetBlock == null || BuiltInRegistries.BLOCK.getKey(targetBlock).equals(BuiltInRegistries.BLOCK.getDefaultKey())) {
                throw new JsonSyntaxException("Unknown target_block '" + blockId + "' in singularity ritual recipe: "
                        + recipeId);
            }
        }

        int lifetime = GsonHelper.getAsInt(json, "lifetime", 0);
        return new SingularityRitualRecipe(recipeId, dropped, held, targetBlock, lifetime);
    }

    @Override
    public SingularityRitualRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
        int droppedSize = buffer.readVarInt();
        List<ItemStack> dropped = new ArrayList<>(droppedSize);
        for (int i = 0; i < droppedSize; i++) {
            dropped.add(buffer.readItem());
        }
        ItemStack held = buffer.readItem();
        Block targetBlock = buffer.readBoolean()
                ? BuiltInRegistries.BLOCK.get(buffer.readResourceLocation())
                : null;
        int lifetime = buffer.readVarInt();
        return new SingularityRitualRecipe(recipeId, dropped, held, targetBlock, lifetime);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, SingularityRitualRecipe recipe) {
        buffer.writeVarInt(recipe.getInputs().size());
        for (ItemStack stack : recipe.getInputs()) {
            buffer.writeItem(stack);
        }
        buffer.writeItem(recipe.getHeldItem());
        buffer.writeBoolean(recipe.getTargetBlock() != null);
        if (recipe.getTargetBlock() != null) {
            buffer.writeResourceLocation(BuiltInRegistries.BLOCK.getKey(recipe.getTargetBlock()));
        }
        buffer.writeVarInt(recipe.getLifetimeTicks());
    }
}
