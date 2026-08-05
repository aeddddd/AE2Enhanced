package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolConfig;

/**
 * {@link PacketOmniToolConfig} 编解码对称性测试.
 * <p>该包未提供 getter,字段通过反射逐一比对.负载仅含原始类型与 NBT,无需原版引导.</p>
 */
class PacketOmniToolConfigTest {

    private static ListTag sampleEnchantments() {
        ListTag list = new ListTag();
        CompoundTag fortune = new CompoundTag();
        fortune.putString("id", "minecraft:fortune");
        fortune.putShort("lvl", (short) 3);
        list.add(fortune);
        CompoundTag efficiency = new CompoundTag();
        efficiency.putString("id", "minecraft:efficiency");
        efficiency.putShort("lvl", (short) 5);
        list.add(efficiency);
        return list;
    }

    private static PacketOmniToolConfig newPacket(int paramEnabled, ListTag enchantments) {
        return new PacketOmniToolConfig(2, 1, true, 3, 16.5, 4, paramEnabled,
                true, false, true, false, 7, 6.5f, 2, enchantments);
    }

    @Test
    void testFullRoundTrip() {
        PacketOmniToolConfig packet = newPacket(0x0A5, sampleEnchantments());

        PacketOmniToolConfig decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketOmniToolConfig::encode, PacketOmniToolConfig::decode);

        String[] fields = { "mode", "dropMode", "silkTouch", "fortune", "blinkDistance", "breakCooldown",
                "paramEnabled", "chaosForceKill", "conformalEnabled", "advancedSilkTouch", "wallPhase",
                "cableColor", "reachDistance", "placementRestriction" };
        for (String field : fields) {
            assertEquals(PacketCodecTestSupport.readField(packet, field),
                    PacketCodecTestSupport.readField(decoded, field), field + " 应一致");
        }
        assertEquals(packetEnchantments(packet), packetEnchantments(decoded), "附魔列表应一致");
    }

    @Test
    void testEmptyEnchantments() {
        PacketOmniToolConfig packet = newPacket(0, new ListTag());

        PacketOmniToolConfig decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketOmniToolConfig::encode, PacketOmniToolConfig::decode);

        assertTrue(packetEnchantments(decoded).isEmpty(), "空附魔列表解码后应为空");
    }

    @Test
    void testNullEnchantments() {
        // encode 对 null 按空列表处理,decode 得到空列表,二次编码仍为相同的空 wrapper
        PacketOmniToolConfig packet = newPacket(0xFFF, null);

        PacketOmniToolConfig decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketOmniToolConfig::encode, PacketOmniToolConfig::decode);

        assertTrue(packetEnchantments(decoded).isEmpty(), "null 附魔解码后应为空列表");
    }

    @Test
    void testParamEnabledMaskedToLow12Bits() {
        // encode 仅写入低 12 位,解码结果应被截断为 0xFFF
        PacketOmniToolConfig packet = newPacket(0xFFFF, new ListTag());

        PacketOmniToolConfig decoded = PacketCodecTestSupport.roundTrip(packet,
                PacketOmniToolConfig::encode, PacketOmniToolConfig::decode);

        assertEquals(0xFFF, PacketCodecTestSupport.readField(decoded, "paramEnabled"),
                "paramEnabled 应只保留低 12 位");
    }

    private static ListTag packetEnchantments(PacketOmniToolConfig packet) {
        return (ListTag) PacketCodecTestSupport.readField(packet, "enchantments");
    }
}
