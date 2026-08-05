package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniToolGui;

/**
 * {@link PacketOpenOmniToolGui} 编解码对称性测试.
 */
class PacketOpenOmniToolGuiTest {

    @Test
    void testMainHand() {
        PacketOpenOmniToolGui decoded = PacketCodecTestSupport.roundTrip(
                new PacketOpenOmniToolGui(0),
                PacketOpenOmniToolGui::encode, PacketOpenOmniToolGui::decode);

        assertEquals(0, PacketCodecTestSupport.readField(decoded, "handOrdinal"));
    }

    @Test
    void testOffHand() {
        PacketOpenOmniToolGui decoded = PacketCodecTestSupport.roundTrip(
                new PacketOpenOmniToolGui(1),
                PacketOpenOmniToolGui::encode, PacketOpenOmniToolGui::decode);

        assertEquals(1, PacketCodecTestSupport.readField(decoded, "handOrdinal"));
    }
}
