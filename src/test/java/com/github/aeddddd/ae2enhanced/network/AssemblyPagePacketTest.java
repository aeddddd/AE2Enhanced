package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.github.aeddddd.ae2enhanced.network.packet.AssemblyPagePacket;

/**
 * {@link AssemblyPagePacket} 编解码对称性测试.
 */
class AssemblyPagePacketTest {

    @Test
    void testRoundTrip() {
        AssemblyPagePacket packet = new AssemblyPagePacket(new BlockPos(10, 64, -20), 3);

        AssemblyPagePacket decoded = PacketCodecTestSupport.roundTrip(packet,
                AssemblyPagePacket::encode, AssemblyPagePacket::decode);

        assertEquals(packet.pos(), decoded.pos());
        assertEquals(packet.pageIndex(), decoded.pageIndex());
    }

    @Test
    void testNegativePageIndex() {
        // 负数页码由服务端钳制,但编解码必须原样传输
        AssemblyPagePacket packet = new AssemblyPagePacket(new BlockPos(-100, -60, 255), -1);

        AssemblyPagePacket decoded = PacketCodecTestSupport.roundTrip(packet,
                AssemblyPagePacket::encode, AssemblyPagePacket::decode);

        assertEquals(new BlockPos(-100, -60, 255), decoded.pos());
        assertEquals(-1, decoded.pageIndex());
    }

    @Test
    void testZeroValues() {
        AssemblyPagePacket packet = new AssemblyPagePacket(BlockPos.ZERO, 0);

        AssemblyPagePacket decoded = PacketCodecTestSupport.roundTrip(packet,
                AssemblyPagePacket::encode, AssemblyPagePacket::decode);

        assertEquals(BlockPos.ZERO, decoded.pos());
        assertEquals(0, decoded.pageIndex());
    }
}
