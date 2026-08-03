package com.github.aeddddd.ae2enhanced.test.crafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import com.github.aeddddd.ae2enhanced.blockentity.MicroSingularityBlockEntity;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRitualRecipeSerializer;

import io.netty.buffer.Unpooled;

/**
 * {@link SingularityRitualRecipeSerializer} 单元测试:JSON 解析与网络编解码.
 */
class SingularityRitualRecipeSerializerTest {

    static {
        CraftingTestFixtures.init();
    }

    private static final ResourceLocation ID = new ResourceLocation("ae2enhanced", "ritual");
    private final SingularityRitualRecipeSerializer serializer = new SingularityRitualRecipeSerializer();

    /** 完整 JSON:dropped/held/target_block/lifetime 全部解析. */
    @Test
    void testFromJsonFull() {
        var json = JsonParser.parseString("""
                {
                  "dropped": [ { "item": "minecraft:stone", "count": 64 } ],
                  "held": { "item": "minecraft:nether_star" },
                  "target_block": "minecraft:beacon",
                  "lifetime": 6000
                }""").getAsJsonObject();

        var recipe = serializer.fromJson(ID, json);

        assertThat(recipe.getId()).isEqualTo(ID);
        assertThat(recipe.getInputs()).hasSize(1);
        assertThat(recipe.getInputs().get(0).is(Items.STONE)).isTrue();
        assertThat(recipe.getInputs().get(0).getCount()).isEqualTo(64);
        assertThat(recipe.getHeldItem().is(Items.NETHER_STAR)).isTrue();
        assertThat(recipe.getTargetBlock()).isSameAs(Blocks.BEACON);
        assertThat(recipe.getLifetimeTicks()).isEqualTo(6000);
    }

    /** 最小 JSON:可省略字段取默认值. */
    @Test
    void testFromJsonMinimal() {
        var json = JsonParser.parseString("{}").getAsJsonObject();

        var recipe = serializer.fromJson(ID, json);

        assertThat(recipe.getInputs()).isEmpty();
        assertThat(recipe.getHeldItem()).isSameAs(ItemStack.EMPTY);
        assertThat(recipe.getTargetBlock()).isNull();
        assertThat(recipe.getLifetimeTicks()).isEqualTo(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS);
    }

    /** 未知 target_block → JsonSyntaxException. */
    @Test
    void testFromJsonUnknownTargetBlock() {
        var json = JsonParser.parseString(
                "{ \"target_block\": \"ae2enhanced:no_such_block\" }").getAsJsonObject();

        assertThatThrownBy(() -> serializer.fromJson(ID, json))
                .isInstanceOf(JsonSyntaxException.class);
    }

    /** 网络编解码往返(含目标方块). */
    @Test
    void testNetworkRoundTripWithTargetBlock() {
        var json = JsonParser.parseString("""
                {
                  "dropped": [ { "item": "minecraft:stone", "count": 32 } ],
                  "held": { "item": "minecraft:nether_star" },
                  "target_block": "minecraft:beacon",
                  "lifetime": 3000
                }""").getAsJsonObject();
        var original = serializer.fromJson(ID, json);

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        serializer.toNetwork(buffer, original);
        var decoded = serializer.fromNetwork(ID, buffer);

        assertThat(decoded.getInputs()).hasSize(1);
        assertThat(decoded.getInputs().get(0).getCount()).isEqualTo(32);
        assertThat(decoded.getHeldItem().is(Items.NETHER_STAR)).isTrue();
        assertThat(decoded.getTargetBlock()).isSameAs(Blocks.BEACON);
        assertThat(decoded.getLifetimeTicks()).isEqualTo(3000);
        buffer.release();
    }

    /** 网络编解码往返(无目标方块). */
    @Test
    void testNetworkRoundTripWithoutTargetBlock() {
        var json = JsonParser.parseString("{}").getAsJsonObject();
        var original = serializer.fromJson(ID, json);

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        serializer.toNetwork(buffer, original);
        var decoded = serializer.fromNetwork(ID, buffer);

        assertThat(decoded.getInputs()).isEmpty();
        assertThat(decoded.getHeldItem()).isSameAs(ItemStack.EMPTY);
        assertThat(decoded.getTargetBlock()).isNull();
        buffer.release();
    }
}
