package com.github.aeddddd.ae2enhanced.crafting.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link BlackHoleRecipeSerializer} 单元测试.
 */
class BlackHoleRecipeSerializerTest {

    private static final ResourceLocation ID = new ResourceLocation("ae2enhanced", "test_serializer");
    private static final BlackHoleRecipeSerializer SERIALIZER = new BlackHoleRecipeSerializer();

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    /** fromJson:输入表与输出物品正确解析. */
    @Test
    void testFromJson() {
        JsonObject json = JsonParser.parseString("""
                {
                  "inputs": { "minecraft:stone": 3, "minecraft:dirt": 2 },
                  "output": { "item": "minecraft:diamond", "count": 2 }
                }
                """).getAsJsonObject();

        BlackHoleRecipe recipe = SERIALIZER.fromJson(ID, json);

        assertEquals(ID, recipe.getId());
        assertEquals(Map.of("minecraft:stone", 3, "minecraft:dirt", 2), recipe.getInputs());
        assertEquals(Items.DIAMOND, recipe.getOutput().getItem());
        assertEquals(2, recipe.getOutput().getCount());
    }

    /** fromJson:缺少 output 时抛出 JsonSyntaxException. */
    @Test
    void testFromJsonMissingOutput() {
        JsonObject json = JsonParser.parseString("""
                { "inputs": { "minecraft:stone": 3 } }
                """).getAsJsonObject();

        JsonSyntaxException e = assertThrows(JsonSyntaxException.class, () -> SERIALIZER.fromJson(ID, json));
        assertTrue(e.getMessage().contains(ID.toString()));
    }

    /** fromJson:缺少 inputs 时抛出 JsonSyntaxException. */
    @Test
    void testFromJsonMissingInputs() {
        JsonObject json = JsonParser.parseString("""
                { "output": { "item": "minecraft:diamond" } }
                """).getAsJsonObject();

        assertThrows(JsonSyntaxException.class, () -> SERIALIZER.fromJson(ID, json));
    }

    /** fromJson:输入数量非整数时抛出 JsonSyntaxException. */
    @Test
    void testFromJsonInvalidInputCount() {
        JsonObject json = JsonParser.parseString("""
                {
                  "inputs": { "minecraft:stone": "three" },
                  "output": { "item": "minecraft:diamond" }
                }
                """).getAsJsonObject();

        assertThrows(JsonSyntaxException.class, () -> SERIALIZER.fromJson(ID, json));
    }

    /** 网络序列化往返:输入表与输出堆完全保留. */
    @Test
    void testNetworkRoundTrip() {
        BlackHoleRecipe original = new BlackHoleRecipe(ID,
                Map.of("minecraft:stone", 3, "minecraft:dirt", 2),
                new ItemStack(Items.DIAMOND, 2));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        SERIALIZER.toNetwork(buffer, original);
        BlackHoleRecipe decoded = SERIALIZER.fromNetwork(ID, buffer);

        assertEquals(ID, decoded.getId());
        assertEquals(original.getInputs(), decoded.getInputs());
        assertTrue(ItemStack.matches(original.getOutput(), decoded.getOutput()));
    }
}
