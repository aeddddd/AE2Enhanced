package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import com.github.aeddddd.ae2enhanced.network.packet.SpecialPlanInfoPacket;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;
import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

/**
 * {@link SpecialPlanInfoPacket} 编解码对称性测试.
 * <p>负载含 {@link AEKey},需要原版注册表与 AE2 key type 注册表引导.
 * 该包未提供 getter,内部 info 通过反射取出后按 record 相等性比对.</p>
 */
class SpecialPlanInfoPacketTest {

    @BeforeAll
    static void bootstrap() {
        // AE2KeyTypeTestBootstrap 内部会先完成原版引导
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    private static SpecialPlanInfo infoOf(SpecialPlanInfoPacket packet) {
        return (SpecialPlanInfo) PacketCodecTestSupport.readField(packet, "info");
    }

    @Test
    void testEmptyRoundTrip() {
        SpecialPlanInfoPacket packet = new SpecialPlanInfoPacket(SpecialPlanInfo.EMPTY);

        SpecialPlanInfoPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                SpecialPlanInfoPacket::encode, SpecialPlanInfoPacket::decode);

        assertTrue(infoOf(decoded).isEmpty(), "空信息解码后应为空");
        assertEquals(SpecialPlanInfo.EMPTY, infoOf(decoded));
    }

    @Test
    void testEntriesRoundTrip() {
        AEKey stone = AEItemKey.of(Items.STONE);
        Map<AEKey, SpecialPlanInfo.Entry> entries = new LinkedHashMap<>();
        entries.put(stone, new SpecialPlanInfo.Entry(
                SpecialPlanInfo.KIND_CYCLE, 4, 2, 1, 0, 8));
        Map<AEKey, Long> callCounts = new LinkedHashMap<>();
        callCounts.put(stone, 10L);
        SpecialPlanInfoPacket packet = new SpecialPlanInfoPacket(new SpecialPlanInfo(entries, callCounts));

        SpecialPlanInfoPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                SpecialPlanInfoPacket::encode, SpecialPlanInfoPacket::decode);

        assertEquals(infoOf(packet), infoOf(decoded), "解码后的 SpecialPlanInfo 应与原始一致");
    }

    @Test
    void testMultipleKeysRoundTrip() {
        AEKey stone = AEItemKey.of(Items.STONE);
        AEKey dirt = AEItemKey.of(Items.DIRT);
        Map<AEKey, SpecialPlanInfo.Entry> entries = new LinkedHashMap<>();
        entries.put(stone, new SpecialPlanInfo.Entry(
                SpecialPlanInfo.KIND_SELF_DUP, 1, 2, 1, 16, 1));
        entries.put(dirt, new SpecialPlanInfo.Entry(
                SpecialPlanInfo.KIND_CYCLE, 3, 4, 2, 0, 0));
        Map<AEKey, Long> callCounts = new LinkedHashMap<>();
        callCounts.put(stone, 16L);
        callCounts.put(dirt, 9L);
        SpecialPlanInfoPacket packet = new SpecialPlanInfoPacket(new SpecialPlanInfo(entries, callCounts));

        SpecialPlanInfoPacket decoded = PacketCodecTestSupport.roundTrip(packet,
                SpecialPlanInfoPacket::encode, SpecialPlanInfoPacket::decode);

        SpecialPlanInfo decodedInfo = infoOf(decoded);
        assertEquals(infoOf(packet), decodedInfo, "多键信息应与原始一致");
        // 顺序敏感的 LinkedHashMap,迭代顺序也应保持
        assertEquals(infoOf(packet).entries().keySet().stream().toList(),
                decodedInfo.entries().keySet().stream().toList(), "条目顺序应保持");
    }
}
