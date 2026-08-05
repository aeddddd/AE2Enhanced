package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;

/**
 * {@link PacketUMCAction} 编解码对称性测试.
 */
class PacketUMCActionTest {

    @Test
    void testPosFaceConstructor() {
        PacketUMCAction packet = new PacketUMCAction(
                PacketUMCAction.ActionType.COPY, new BlockPos(3, 70, -9), Direction.NORTH);

        PacketUMCAction decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketUMCAction::encode, PacketUMCAction::decode);

        assertEquals(PacketUMCAction.ActionType.COPY, decoded.getType());
        assertEquals(new BlockPos(3, 70, -9), decoded.getPos());
        assertEquals(Direction.NORTH, decoded.getFace());
        assertEquals(-1, decoded.getIndex(), "pos/face 构造的 index 应为 -1");
    }

    @Test
    void testIndexConstructor() {
        PacketUMCAction packet = new PacketUMCAction(PacketUMCAction.ActionType.REMOVE_SELECTION, 3);

        PacketUMCAction decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketUMCAction::encode, PacketUMCAction::decode);

        assertEquals(PacketUMCAction.ActionType.REMOVE_SELECTION, decoded.getType());
        assertEquals(3, decoded.getIndex());
        assertEquals(BlockPos.ZERO, decoded.getPos(), "index 构造的 pos 应为原点");
        assertEquals(Direction.DOWN, decoded.getFace(), "index 构造的 face 应为 3D 值 0 对应的方向");
    }

    @Test
    void testTypeOnlyConstructor() {
        PacketUMCAction packet = new PacketUMCAction(PacketUMCAction.ActionType.OPEN_GUI);

        PacketUMCAction decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketUMCAction::encode, PacketUMCAction::decode);

        assertEquals(PacketUMCAction.ActionType.OPEN_GUI, decoded.getType());
        assertEquals(BlockPos.ZERO, decoded.getPos());
        assertEquals(Direction.DOWN, decoded.getFace());
        assertEquals(-1, decoded.getIndex());
    }

    @Test
    void testNullPosAndFace() {
        // 构造器允许 null,按 0 值编码,解码为原点与 DOWN
        PacketUMCAction packet = new PacketUMCAction(PacketUMCAction.ActionType.PASTE, null, null);

        PacketUMCAction decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketUMCAction::encode, PacketUMCAction::decode);

        assertEquals(BlockPos.ZERO, decoded.getPos());
        assertEquals(Direction.DOWN, decoded.getFace());
    }

    @Test
    void testAllActionTypes() {
        // 全部动作类型的 ordinal 必须能经 byte 往返
        for (PacketUMCAction.ActionType type : PacketUMCAction.ActionType.values()) {
            PacketUMCAction decoded = PacketCodecTestSupport.roundTrip(
                    new PacketUMCAction(type),
                    PacketUMCAction::encode, PacketUMCAction::decode);
            assertEquals(type, decoded.getType());
        }
    }
}
