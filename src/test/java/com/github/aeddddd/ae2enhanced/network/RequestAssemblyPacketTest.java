package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.github.aeddddd.ae2enhanced.network.packet.RequestAssemblyPacket;

/**
 * {@link RequestAssemblyPacket} 编解码对称性测试.
 */
class RequestAssemblyPacketTest {

    @Test
    void testRoundTrip() {
        RequestAssemblyPacket packet = new RequestAssemblyPacket(new BlockPos(5, 70, -13));

        RequestAssemblyPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                RequestAssemblyPacket::encode, RequestAssemblyPacket::decode);

        assertEquals(packet.controllerPos(), decoded.controllerPos());
    }

    @Test
    void testExtremeCoordinates() {
        // 世界边界附近的坐标,验证 BlockPos 打包位运算不丢精度
        BlockPos pos = new BlockPos(30_000_000, -64, -30_000_000);
        RequestAssemblyPacket packet = new RequestAssemblyPacket(pos);

        RequestAssemblyPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                RequestAssemblyPacket::encode, RequestAssemblyPacket::decode);

        assertEquals(pos, decoded.controllerPos());
    }
}
