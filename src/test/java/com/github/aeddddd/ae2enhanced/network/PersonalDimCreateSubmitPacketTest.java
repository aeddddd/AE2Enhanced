package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimCreateSubmitPacket;

/**
 * {@link PersonalDimCreateSubmitPacket} 编解码对称性测试.
 * <p>该包未提供 getter,字段通过反射逐一比对.</p>
 */
class PersonalDimCreateSubmitPacketTest {

    @Test
    void testRoundTrip() {
        PersonalDimCreateSubmitPacket packet = new PersonalDimCreateSubmitPacket(3, 7, 15);

        PersonalDimCreateSubmitPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimCreateSubmitPacket::encode, PersonalDimCreateSubmitPacket::decode);

        assertEquals(PacketCodecTestSupport.readField(packet, "roadColor"),
                PacketCodecTestSupport.readField(decoded, "roadColor"), "roadColor 应一致");
        assertEquals(PacketCodecTestSupport.readField(packet, "lineColor"),
                PacketCodecTestSupport.readField(decoded, "lineColor"), "lineColor 应一致");
        assertEquals(PacketCodecTestSupport.readField(packet, "platformColor"),
                PacketCodecTestSupport.readField(decoded, "platformColor"), "platformColor 应一致");
    }

    @Test
    void testZeroColors() {
        PersonalDimCreateSubmitPacket decoded = PacketCodecTestSupport.roundTrip(
                new PersonalDimCreateSubmitPacket(0, 0, 0),
                PersonalDimCreateSubmitPacket::encode, PersonalDimCreateSubmitPacket::decode);

        assertEquals(0, PacketCodecTestSupport.readField(decoded, "roadColor"));
        assertEquals(0, PacketCodecTestSupport.readField(decoded, "lineColor"));
        assertEquals(0, PacketCodecTestSupport.readField(decoded, "platformColor"));
    }
}
