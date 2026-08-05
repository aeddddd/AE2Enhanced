package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolPlacementSubMode;

/**
 * {@link PacketOmniToolPlacementSubMode} 编解码对称性测试.
 */
class PacketOmniToolPlacementSubModeTest {

    @Test
    void testNextTrue() {
        PacketOmniToolPlacementSubMode decoded = PacketCodecTestSupport.roundTrip(
                new PacketOmniToolPlacementSubMode(true),
                PacketOmniToolPlacementSubMode::encode, PacketOmniToolPlacementSubMode::decode);

        assertTrue(decoded.isNext());
    }

    @Test
    void testNextFalse() {
        PacketOmniToolPlacementSubMode decoded = PacketCodecTestSupport.roundTrip(
                new PacketOmniToolPlacementSubMode(false),
                PacketOmniToolPlacementSubMode::encode, PacketOmniToolPlacementSubMode::decode);

        assertFalse(decoded.isNext());
    }
}
