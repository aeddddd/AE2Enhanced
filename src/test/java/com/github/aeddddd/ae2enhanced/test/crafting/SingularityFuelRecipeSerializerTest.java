package com.github.aeddddd.ae2enhanced.test.crafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityFuelRecipeSerializer;

import io.netty.buffer.Unpooled;

/**
 * {@link SingularityFuelRecipeSerializer} 单元测试:JSON 解析与网络编解码.
 */
class SingularityFuelRecipeSerializerTest {

    static {
        CraftingTestFixtures.init();
    }

    private static final ResourceLocation ID = new ResourceLocation("ae2enhanced", "fuel");
    private final SingularityFuelRecipeSerializer serializer = new SingularityFuelRecipeSerializer();

    /** 计时形态:{item, ticks} → permanent 默认 false. */
    @Test
    void testFromJsonTimed() {
        var json = JsonParser.parseString(
                "{ \"item\": { \"item\": \"minecraft:redstone\" }, \"ticks\": 12000 }").getAsJsonObject();

        var recipe = serializer.fromJson(ID, json);

        assertThat(recipe.getId()).isEqualTo(ID);
        assertThat(recipe.getTicks()).isEqualTo(12000);
        assertThat(recipe.isPermanent()).isFalse();
        assertThat(recipe.getItem().test(new ItemStack(Items.REDSTONE))).isTrue();
    }

    /** 永久形态:{item, permanent: true} → ticks 默认 0. */
    @Test
    void testFromJsonPermanent() {
        var json = JsonParser.parseString(
                "{ \"item\": { \"item\": \"minecraft:nether_star\" }, \"permanent\": true }").getAsJsonObject();

        var recipe = serializer.fromJson(ID, json);

        assertThat(recipe.isPermanent()).isTrue();
        assertThat(recipe.getTicks()).isZero();
        assertThat(recipe.getItem().test(new ItemStack(Items.NETHER_STAR))).isTrue();
    }

    /** 网络编解码往返一致. */
    @Test
    void testNetworkRoundTrip() {
        var json = JsonParser.parseString(
                "{ \"item\": { \"item\": \"minecraft:redstone\" }, \"ticks\": 6000, \"permanent\": false }")
                .getAsJsonObject();
        var original = serializer.fromJson(ID, json);

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        serializer.toNetwork(buffer, original);
        var decoded = serializer.fromNetwork(ID, buffer);

        assertThat(decoded.getTicks()).isEqualTo(6000);
        assertThat(decoded.isPermanent()).isFalse();
        assertThat(decoded.getItem().test(new ItemStack(Items.REDSTONE))).isTrue();
        buffer.release();
    }

    /** 永久形态的网络编解码往返. */
    @Test
    void testNetworkRoundTripPermanent() {
        var json = JsonParser.parseString(
                "{ \"item\": { \"item\": \"minecraft:nether_star\" }, \"permanent\": true }").getAsJsonObject();
        var original = serializer.fromJson(ID, json);

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        serializer.toNetwork(buffer, original);
        var decoded = serializer.fromNetwork(ID, buffer);

        assertThat(decoded.isPermanent()).isTrue();
        buffer.release();
    }
}
