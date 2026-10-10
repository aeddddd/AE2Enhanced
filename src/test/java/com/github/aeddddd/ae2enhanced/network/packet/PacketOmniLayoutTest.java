package com.github.aeddddd.ae2enhanced.network.packet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 阶段 4a：{@code PacketOmniLayout} 的序列化往返。
 *
 * <p>包本身只是"哑载体"：长度闸门在两端——客户端拒绝上传超限载荷，服务端 handler 也复核一次
 * （见 {@code PacketOmniLayout.Handler}）。这里只钉住 UTF8 字符串往返与空载默认值。</p>
 */
public class PacketOmniLayoutTest {

    private static final String JSON =
            "{\"version\":6,\"roots\":[{\"rootId\":\"root:repo\",\"bounds\":{\"x\":10,\"y\":20,\"width\":384,\"height\":306}}]}";

    private static PacketOmniLayout roundTrip(PacketOmniLayout packet) {
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        PacketOmniLayout decoded = new PacketOmniLayout();
        decoded.fromBytes(buf);
        return decoded;
    }

    @Test
    public void roundTripKeepsThePayloadVerbatim() {
        assertThat(roundTrip(new PacketOmniLayout(JSON)).getLayoutJson()).isEqualTo(JSON);
    }

    @Test
    public void roundTripKeepsMultibyteText() {
        String json = "{\"note\":\"物品仓库·メモ·Ω\"}";
        assertThat(roundTrip(new PacketOmniLayout(json)).getLayoutJson()).isEqualTo(json);
    }

    @Test
    public void nullPayloadBecomesEmptyString() {
        assertThat(new PacketOmniLayout(null).getLayoutJson()).isEmpty();
        assertThat(roundTrip(new PacketOmniLayout(null)).getLayoutJson()).isEmpty();
    }

    @Test
    public void wireCarriesPayloadsUpToOurPolicyLimit() {
        // 我们的策略上限 16000 字节必须稳稳过线（Forge 的硬上限是 16383 字节）
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 16000; i++) {
            builder.append('x');
        }
        PacketOmniLayout decoded = roundTrip(new PacketOmniLayout(builder.toString()));
        assertThat(decoded.getLayoutJson()).hasSize(16000);
    }
}
