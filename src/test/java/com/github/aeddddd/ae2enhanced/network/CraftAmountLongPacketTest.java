package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.network.packet.CraftAmountLongPacket;

/**
 * {@link CraftAmountLongPacket} 编解码对称性测试.
 * <p>该包未提供 getter,字段通过反射逐一比对.</p>
 */
class CraftAmountLongPacketTest {

    private static void assertFieldsEqual(CraftAmountLongPacket expected, CraftAmountLongPacket actual) {
        assertEquals(PacketCodecTestSupport.readField(expected, "amount"),
                PacketCodecTestSupport.readField(actual, "amount"), "amount 应一致");
        assertEquals(PacketCodecTestSupport.readField(expected, "craftMissingAmount"),
                PacketCodecTestSupport.readField(actual, "craftMissingAmount"), "craftMissingAmount 应一致");
        assertEquals(PacketCodecTestSupport.readField(expected, "autoStart"),
                PacketCodecTestSupport.readField(actual, "autoStart"), "autoStart 应一致");
    }

    @Test
    void testMaxLongAmount() {
        // 该包的核心用途：突破 int 上限,Long.MAX_VALUE 必须无损传输
        CraftAmountLongPacket packet = new CraftAmountLongPacket(Long.MAX_VALUE, true, true);

        CraftAmountLongPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                CraftAmountLongPacket::encode, CraftAmountLongPacket::decode);

        assertFieldsEqual(packet, decoded);
    }

    @Test
    void testZeroAmount() {
        CraftAmountLongPacket packet = new CraftAmountLongPacket(0L, false, false);

        CraftAmountLongPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                CraftAmountLongPacket::encode, CraftAmountLongPacket::decode);

        assertFieldsEqual(packet, decoded);
    }

    @Test
    void testTypicalLargeAmount() {
        CraftAmountLongPacket packet = new CraftAmountLongPacket(1_234_567_890_123_456_789L, false, true);

        CraftAmountLongPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                CraftAmountLongPacket::encode, CraftAmountLongPacket::decode);

        assertFieldsEqual(packet, decoded);
    }
}
