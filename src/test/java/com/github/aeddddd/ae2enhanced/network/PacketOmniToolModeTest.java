package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import net.minecraft.network.FriendlyByteBuf;

import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolMode;

/**
 * {@link PacketOmniToolMode} 编解码对称性测试（无字段包）.
 */
class PacketOmniToolModeTest {

    @Test
    void testEmptyPayloadRoundTrip() {
        FriendlyByteBuf buffer = PacketCodecTestSupport.newBuffer();
        PacketOmniToolMode.encode(new PacketOmniToolMode(), buffer);
        assertEquals(0, buffer.readableBytes(), "无字段包编码后应为空载荷");

        PacketOmniToolMode decoded = PacketOmniToolMode.decode(buffer);
        assertNotNull(decoded, "空载荷应能正常解码");
    }
}
