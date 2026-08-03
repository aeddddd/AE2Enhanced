package com.github.aeddddd.ae2enhanced.crafting.omnitool;

import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * 全能工具升级配方序列化器。
 * JSON 格式：{ "type": "ae2enhanced:omni_tool_upgrade", "upgrade": "enchanted_book|bedrock|conformal_charge|chaos" }
 */
public class OmniToolUpgradeRecipeSerializer implements RecipeSerializer<OmniToolUpgradeRecipe> {

    @Override
    public OmniToolUpgradeRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
        String upgrade = GsonHelper.getAsString(json, "upgrade");
        OmniToolUpgradeRecipe.Type type = switch (upgrade) {
            case "enchanted_book" -> OmniToolUpgradeRecipe.Type.ENCHANTED_BOOK;
            case "bedrock" -> OmniToolUpgradeRecipe.Type.BEDROCK;
            case "conformal_charge" -> OmniToolUpgradeRecipe.Type.CONFORMAL_CHARGE;
            case "chaos" -> OmniToolUpgradeRecipe.Type.CHAOS;
            default -> throw new IllegalArgumentException("Unknown omni tool upgrade type: " + upgrade);
        };
        return new OmniToolUpgradeRecipe(recipeId, type);
    }

    @Override
    public OmniToolUpgradeRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
        return new OmniToolUpgradeRecipe(recipeId, buffer.readEnum(OmniToolUpgradeRecipe.Type.class));
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, OmniToolUpgradeRecipe recipe) {
        buffer.writeEnum(recipe.getUpgradeType());
    }
}
