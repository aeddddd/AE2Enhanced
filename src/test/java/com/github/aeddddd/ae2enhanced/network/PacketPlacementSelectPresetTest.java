package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementSelectPreset;

/**
 * {@link PacketPlacementSelectPreset} 编解码对称性测试.
 */
class PacketPlacementSelectPresetTest {

    @Test
    void testPresetSlot() {
        PacketPlacementSelectPreset decoded = PacketCodecTestSupport.roundTrip(
                new PacketPlacementSelectPreset(5),
                PacketPlacementSelectPreset::encode, PacketPlacementSelectPreset::decode);

        assertEquals(5, decoded.getSlot());
    }

    @Test
    void testPickTargetSlot() {
        // 槽位 9（MAX_PRESETS）= 中键选取准星目标
        PacketPlacementSelectPreset decoded = PacketCodecTestSupport.roundTrip(
                new PacketPlacementSelectPreset(9),
                PacketPlacementSelectPreset::encode, PacketPlacementSelectPreset::decode);

        assertEquals(9, decoded.getSlot());
    }

    @Test
    void testClearSelectionSlot() {
        // 槽位 -2（SLOT_EMPTY）= 径向菜单空选项,负值经 byte 传输必须原样还原
        PacketPlacementSelectPreset decoded = PacketCodecTestSupport.roundTrip(
                new PacketPlacementSelectPreset(-2),
                PacketPlacementSelectPreset::encode, PacketPlacementSelectPreset::decode);

        assertEquals(-2, decoded.getSlot());
    }
}
