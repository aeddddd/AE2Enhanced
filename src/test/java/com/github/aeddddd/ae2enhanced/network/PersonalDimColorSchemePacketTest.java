package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimColorSchemePacket;

/**
 * {@link PersonalDimColorSchemePacket} 编解码对称性测试.
 * <p>该包未提供 getter,字段通过反射逐一比对.</p>
 */
class PersonalDimColorSchemePacketTest {

    private static void assertFieldsEqual(PersonalDimColorSchemePacket expected,
            PersonalDimColorSchemePacket actual) {
        String[] fields = { "pos", "owner", "roadColor", "lineColor", "platformColor", "recarpet" };
        for (String field : fields) {
            assertEquals(PacketCodecTestSupport.readField(expected, field),
                    PacketCodecTestSupport.readField(actual, field), field + " 应一致");
        }
    }

    @Test
    void testRoundTrip() {
        PersonalDimColorSchemePacket packet = new PersonalDimColorSchemePacket(
                new BlockPos(7, 65, -3), UUID.randomUUID(), 1, 15, 0, true);

        PersonalDimColorSchemePacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimColorSchemePacket::encode, PersonalDimColorSchemePacket::decode);

        assertFieldsEqual(packet, decoded);
    }

    @Test
    void testNoRecarpet() {
        PersonalDimColorSchemePacket packet = new PersonalDimColorSchemePacket(
                BlockPos.ZERO, new UUID(0L, 0L), 14, 14, 14, false);

        PersonalDimColorSchemePacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimColorSchemePacket::encode, PersonalDimColorSchemePacket::decode);

        assertFieldsEqual(packet, decoded);
    }
}
