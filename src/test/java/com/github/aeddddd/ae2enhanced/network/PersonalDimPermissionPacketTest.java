package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimPermissionPacket;

/**
 * {@link PersonalDimPermissionPacket} 编解码对称性测试.
 * <p>该包仅能通过静态工厂构造且未提供 getter,字段通过反射逐一比对.</p>
 */
class PersonalDimPermissionPacketTest {

    private static final BlockPos POS = new BlockPos(4, 64, -8);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    private static void assertFieldsEqual(PersonalDimPermissionPacket expected,
            PersonalDimPermissionPacket actual) {
        String[] fields = { "pos", "owner", "action", "targetName", "target", "permissionOrdinal", "value" };
        for (String field : fields) {
            assertEquals(PacketCodecTestSupport.readField(expected, field),
                    PacketCodecTestSupport.readField(actual, field), field + " 应一致");
        }
    }

    @Test
    void testInviteRoundTrip() {
        PersonalDimPermissionPacket packet = PersonalDimPermissionPacket.invite(POS, OWNER, "Steve");

        PersonalDimPermissionPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimPermissionPacket::encode, PersonalDimPermissionPacket::decode);

        assertFieldsEqual(packet, decoded);
        assertEquals(PersonalDimPermissionPacket.ACTION_INVITE,
                PacketCodecTestSupport.readField(decoded, "action"));
        assertEquals("Steve", PacketCodecTestSupport.readField(decoded, "targetName"));
    }

    @Test
    void testKickRoundTrip() {
        PersonalDimPermissionPacket packet = PersonalDimPermissionPacket.kick(POS, OWNER, TARGET);

        PersonalDimPermissionPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimPermissionPacket::encode, PersonalDimPermissionPacket::decode);

        assertFieldsEqual(packet, decoded);
        assertEquals(PersonalDimPermissionPacket.ACTION_KICK,
                PacketCodecTestSupport.readField(decoded, "action"));
        assertEquals(TARGET, PacketCodecTestSupport.readField(decoded, "target"));
    }

    @Test
    void testSetPermRoundTrip() {
        PersonalDimPermissionPacket packet = PersonalDimPermissionPacket.setPerm(
                POS, OWNER, TARGET, PersonalDimPermission.BUILD, true);

        PersonalDimPermissionPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimPermissionPacket::encode, PersonalDimPermissionPacket::decode);

        assertFieldsEqual(packet, decoded);
        assertEquals(PersonalDimPermissionPacket.ACTION_SET_PERM,
                PacketCodecTestSupport.readField(decoded, "action"));
        assertEquals(PersonalDimPermission.BUILD.ordinal(),
                PacketCodecTestSupport.readField(decoded, "permissionOrdinal"));
        assertEquals(true, PacketCodecTestSupport.readField(decoded, "value"));
    }
}
