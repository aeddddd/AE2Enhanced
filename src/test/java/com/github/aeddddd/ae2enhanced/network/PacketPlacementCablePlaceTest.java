package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementCablePlace;

/**
 * {@link PacketPlacementCablePlace} 编解码对称性测试.
 */
class PacketPlacementCablePlaceTest {

    @Test
    void testRoundTrip() {
        PacketPlacementCablePlace packet = new PacketPlacementCablePlace(
                new BlockPos(1, 64, 2), new BlockPos(8, 64, 15));

        PacketPlacementCablePlace decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketPlacementCablePlace::encode, PacketPlacementCablePlace::decode);

        assertEquals(packet.getStart(), decoded.getStart());
        assertEquals(packet.getEnd(), decoded.getEnd());
    }

    @Test
    void testNegativeCoordinates() {
        // 起点终点以 BlockPos.asLong() 打包,负坐标必须无损还原
        PacketPlacementCablePlace packet = new PacketPlacementCablePlace(
                new BlockPos(-500, -64, -500), new BlockPos(0, 319, 0));

        PacketPlacementCablePlace decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketPlacementCablePlace::encode, PacketPlacementCablePlace::decode);

        assertEquals(new BlockPos(-500, -64, -500), decoded.getStart());
        assertEquals(new BlockPos(0, 319, 0), decoded.getEnd());
    }
}
