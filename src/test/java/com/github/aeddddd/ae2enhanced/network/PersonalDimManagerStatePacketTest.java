package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimManagerStatePacket;

/**
 * {@link PersonalDimManagerStatePacket} 编解码对称性测试.
 * <p>完整状态包仅能通过 {@code create(MinecraftServer, ...)} 构造,单测环境没有服务器实例,
 * 因此通过反射调用私有构造器直接构造样例数据.字段均为 public final,可直接比对.</p>
 */
class PersonalDimManagerStatePacketTest {

    /**
     * 反射调用私有全参构造器构造状态包.
     */
    private static PersonalDimManagerStatePacket newPacket(List<PersonalDimManagerStatePacket.PlayerPerm> players,
            PersonalDimensionRules rules, List<String> palette, int[] states) {
        try {
            Constructor<PersonalDimManagerStatePacket> constructor =
                    PersonalDimManagerStatePacket.class.getDeclaredConstructor(
                            BlockPos.class, UUID.class, String.class, boolean.class,
                            PersonalDimensionRules.class, List.class,
                            int.class, int.class, int.class, int.class, List.class, int[].class,
                            int.class, int.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    new BlockPos(2, 64, -6), UUID.randomUUID(), "Owner", true, rules, players,
                    60, 61, 3, 3, palette, states, 14, 0, 7);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法构造 PersonalDimManagerStatePacket 测试实例", e);
        }
    }

    private static PersonalDimensionRules customRules() {
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.disableMobSpawning = true;
        rules.lockTime = true;
        rules.daylightCycle = false;
        rules.timeValue = 18000L;
        rules.flightEnabled = true;
        rules.movementSpeed = 0.2f;
        rules.noFlightInertia = true;
        return rules;
    }

    @Test
    void testFullStateRoundTrip() {
        List<PersonalDimManagerStatePacket.PlayerPerm> players = List.of(
                new PersonalDimManagerStatePacket.PlayerPerm(UUID.randomUUID(), "Alice",
                        PersonalDimManagerStatePacket.permissionMask(
                                Set.of(PersonalDimPermission.ENTER, PersonalDimPermission.BUILD))),
                new PersonalDimManagerStatePacket.PlayerPerm(UUID.randomUUID(), "Bob",
                        PersonalDimManagerStatePacket.permissionMask(
                                Set.of(PersonalDimPermission.MANAGE_RULES))));
        List<String> palette = List.of("minecraft:stone", "minecraft:glass");
        int[] states = { 0, 1, 1, 0, 0, 1, 1, 0, 0 };
        PersonalDimManagerStatePacket packet = newPacket(players, customRules(), palette, states);

        PersonalDimManagerStatePacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimManagerStatePacket::encode, PersonalDimManagerStatePacket::decode);

        assertEquals(packet.pos, decoded.pos);
        assertEquals(packet.owner, decoded.owner);
        assertEquals(packet.ownerName, decoded.ownerName);
        assertEquals(packet.created, decoded.created);
        assertRulesEqual(packet.rules, decoded.rules);
        assertEquals(packet.players, decoded.players, "玩家权限列表应一致");
        assertEquals(packet.floorY, decoded.floorY);
        assertEquals(packet.entryY, decoded.entryY);
        assertEquals(packet.presetWidth, decoded.presetWidth);
        assertEquals(packet.presetDepth, decoded.presetDepth);
        assertEquals(packet.presetPalette, decoded.presetPalette, "预设调色板应一致");
        assertArrayEquals(packet.presetStates, decoded.presetStates, "预设状态表应一致");
        assertEquals(packet.roadColor, decoded.roadColor);
        assertEquals(packet.lineColor, decoded.lineColor);
        assertEquals(packet.platformColor, decoded.platformColor);
    }

    @Test
    void testEmptyPlayersAndPaletteRoundTrip() {
        PersonalDimManagerStatePacket packet = newPacket(List.of(), new PersonalDimensionRules(),
                List.of(), new int[0]);

        PersonalDimManagerStatePacket decoded = PacketCodecTestSupport.roundTrip(packet,
                PersonalDimManagerStatePacket::encode, PersonalDimManagerStatePacket::decode);

        assertTrue(decoded.players.isEmpty(), "空玩家列表应往返为空");
        assertTrue(decoded.presetPalette.isEmpty(), "空调色板应往返为空");
        assertEquals(0, decoded.presetStates.length, "空状态表应往返为空");
        assertRulesEqual(packet.rules, decoded.rules);
    }

    @Test
    void testPermissionMask() {
        assertEquals(0, PersonalDimManagerStatePacket.permissionMask(Set.of()));
        assertEquals(1 << PersonalDimPermission.ENTER.ordinal(),
                PersonalDimManagerStatePacket.permissionMask(Set.of(PersonalDimPermission.ENTER)));
        int expected = (1 << PersonalDimPermission.ENTER.ordinal())
                | (1 << PersonalDimPermission.INTERACT.ordinal());
        assertEquals(expected, PersonalDimManagerStatePacket.permissionMask(
                Set.of(PersonalDimPermission.ENTER, PersonalDimPermission.INTERACT)));
    }

    @Test
    void testHasPermission() {
        PersonalDimManagerStatePacket packet = newPacket(List.of(), new PersonalDimensionRules(),
                List.of(), new int[0]);
        var player = new PersonalDimManagerStatePacket.PlayerPerm(UUID.randomUUID(), "Alice",
                PersonalDimManagerStatePacket.permissionMask(
                        Set.of(PersonalDimPermission.ENTER, PersonalDimPermission.BUILD)));

        assertTrue(packet.hasPermission(player, PersonalDimPermission.ENTER));
        assertTrue(packet.hasPermission(player, PersonalDimPermission.BUILD));
        assertFalse(packet.hasPermission(player, PersonalDimPermission.INTERACT));
        assertFalse(packet.hasPermission(player, PersonalDimPermission.MANAGE_RULES));
    }

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
}
