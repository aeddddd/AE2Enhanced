package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimRulesSubmitPacket;

/**
 * {@link PersonalDimRulesSubmitPacket} 编解码对称性测试.
 * <p>该包未提供 getter,rules 通过反射取出后逐字段比对.</p>
 */
class PersonalDimRulesSubmitPacketTest {

    private static void assertRulesEqual(PersonalDimensionRules expected, PersonalDimensionRules actual) {
        assertEquals(expected.disableMobSpawning, actual.disableMobSpawning, "disableMobSpawning 应一致");
        assertEquals(expected.lockWeather, actual.lockWeather, "lockWeather 应一致");
        assertEquals(expected.lockTime, actual.lockTime, "lockTime 应一致");
        assertEquals(expected.daylightCycle, actual.daylightCycle, "daylightCycle 应一致");
        assertEquals(expected.timeValue, actual.timeValue, "timeValue 应一致");
        assertEquals(expected.flightEnabled, actual.flightEnabled, "flightEnabled 应一致");
        assertEquals(expected.movementSpeed, actual.movementSpeed, "movementSpeed 应一致");
        assertEquals(expected.noFlightInertia, actual.noFlightInertia, "noFlightInertia 应一致");
    }

    @Test
    void testCustomRulesRoundTrip() {
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.disableMobSpawning = true;
        rules.lockWeather = true;
        rules.lockTime = true;
        rules.daylightCycle = false;
        rules.timeValue = 123_456L;
        rules.flightEnabled = true;
        rules.movementSpeed = 0.25f;
        rules.noFlightInertia = true;
        PersonalDimRulesSubmitPacket packet = new PersonalDimRulesSubmitPacket(
                new BlockPos(1, 2, 3), UUID.randomUUID(), rules);

        PersonalDimRulesSubmitPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimRulesSubmitPacket::encode, PersonalDimRulesSubmitPacket::decode);

        assertEquals(PacketCodecTestSupport.readField(packet, "pos"),
                PacketCodecTestSupport.readField(decoded, "pos"), "pos 应一致");
        assertEquals(PacketCodecTestSupport.readField(packet, "owner"),
                PacketCodecTestSupport.readField(decoded, "owner"), "owner 应一致");
        PersonalDimensionRules decodedRules =
                (PersonalDimensionRules) PacketCodecTestSupport.readField(decoded, "rules");
        assertRulesEqual(rules, decodedRules);
    }

    @Test
    void testDefaultRulesRoundTrip() {
        PersonalDimRulesSubmitPacket packet = new PersonalDimRulesSubmitPacket(
                BlockPos.ZERO, new UUID(1L, 2L), new PersonalDimensionRules());

        PersonalDimRulesSubmitPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimRulesSubmitPacket::encode, PersonalDimRulesSubmitPacket::decode);

        PersonalDimensionRules decodedRules =
                (PersonalDimensionRules) PacketCodecTestSupport.readField(decoded, "rules");
        assertRulesEqual(new PersonalDimensionRules(), decodedRules);
    }
}
