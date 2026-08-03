package com.github.aeddddd.ae2enhanced.test.crafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.github.aeddddd.ae2enhanced.crafting.omnitool.OmniToolUpgradeRecipe;
import com.github.aeddddd.ae2enhanced.crafting.omnitool.OmniToolUpgradeRecipeSerializer;

import io.netty.buffer.Unpooled;

/**
 * {@link OmniToolUpgradeRecipeSerializer} 单元测试:JSON 解析与网络编解码.
 */
class OmniToolUpgradeRecipeSerializerTest {

    static {
        CraftingTestFixtures.init();
    }

    private static final ResourceLocation ID = new ResourceLocation("ae2enhanced", "upgrade");
    private final OmniToolUpgradeRecipeSerializer serializer = new OmniToolUpgradeRecipeSerializer();

    /** 四种合法 upgrade 字符串均正确解析. */
    @Test
    void testFromJsonAllTypes() {
        assertThat(parse("{ \"upgrade\": \"enchanted_book\" }").getUpgradeType())
                .isEqualTo(OmniToolUpgradeRecipe.Type.ENCHANTED_BOOK);
        assertThat(parse("{ \"upgrade\": \"bedrock\" }").getUpgradeType())
                .isEqualTo(OmniToolUpgradeRecipe.Type.BEDROCK);
        assertThat(parse("{ \"upgrade\": \"conformal_charge\" }").getUpgradeType())
                .isEqualTo(OmniToolUpgradeRecipe.Type.CONFORMAL_CHARGE);
        assertThat(parse("{ \"upgrade\": \"chaos\" }").getUpgradeType())
                .isEqualTo(OmniToolUpgradeRecipe.Type.CHAOS);
    }

    /** 未知 upgrade 字符串 → IllegalArgumentException. */
    @Test
    void testFromJsonUnknownType() {
        assertThatThrownBy(() -> parse("{ \"upgrade\": \"no_such_upgrade\" }"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 网络编解码往返一致. */
    @Test
    void testNetworkRoundTrip() {
        for (var type : OmniToolUpgradeRecipe.Type.values()) {
            var original = new OmniToolUpgradeRecipe(ID, type);

            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            serializer.toNetwork(buffer, original);
            var decoded = serializer.fromNetwork(ID, buffer);

            assertThat(decoded.getUpgradeType()).isEqualTo(type);
            assertThat(decoded.getId()).isEqualTo(ID);
            buffer.release();
        }
    }

    private OmniToolUpgradeRecipe parse(String json) {
        return serializer.fromJson(ID, JsonParser.parseString(json).getAsJsonObject());
    }
}
