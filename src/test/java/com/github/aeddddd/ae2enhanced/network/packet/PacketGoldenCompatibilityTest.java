package com.github.aeddddd.ae2enhanced.network.packet;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 逐 discriminator 固定生产序列化字节，并证明 classpath golden 可被生产解码器读取。 */
class PacketGoldenCompatibilityTest {

    private static Map<Integer, Golden> loadGolden() throws IOException {
        InputStream stream = PacketGoldenCompatibilityTest.class
                .getResourceAsStream("/protocol/packet-wire-v1.tsv");
        assertThat(stream).as("缺少 packet-wire-v1.tsv").isNotNull();
        Map<Integer, Golden> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("id\t")) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                assertThat(columns).as("非法 golden 行: " + line).hasSize(3);
                int id = Integer.parseInt(columns[0]);
                byte[] bytes = "-".equals(columns[2]) ? new byte[0] : parseHex(columns[2]);
                assertThat(result.put(id, new Golden(columns[1], bytes))).isNull();
            }
        }
        return result;
    }

    private static byte[] parseHex(String hex) {
        assertThat(hex.length() % 2).as("hex 必须成对").isZero();
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    @Test
    void goldenAndFactoryCoverExactlyTheSame58Discriminators() throws IOException {
        Map<Integer, Golden> golden = loadGolden();
        assertThat(PacketWireCases.createAll().keySet()).containsExactlyInAnyOrderElementsOf(golden.keySet());
        assertThat(golden).hasSize(58);
        assertThat(golden.keySet()).doesNotContain(12, 13, 14, 47);
    }

    @TestFactory
    Stream<DynamicTest> productionBytesMatchGoldenAndGoldenRoundTrips() throws IOException {
        Map<Integer, Golden> golden = loadGolden();
        Map<Integer, IMessage> cases = PacketWireCases.createAll();
        return golden.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey() + " " + entry.getValue().packetClass, () -> {
                    IMessage sample = cases.get(entry.getKey());
                    assertThat(sample).as("缺少 discriminator " + entry.getKey() + " 样本").isNotNull();
                    assertThat(sample.getClass().getSimpleName()).isEqualTo(entry.getValue().packetClass);

                    byte[] actual = PacketWireCases.encode(sample);
                    assertThat(actual).as("生产 toBytes 必须逐字节匹配 golden")
                            .containsExactly(entry.getValue().bytes);

                    IMessage decoded = PacketWireCases.decode(sample.getClass().asSubclass(IMessage.class),
                            entry.getValue().bytes);
                    assertThat(PacketWireCases.encode(decoded)).as("golden 解码后重新编码必须稳定")
                            .containsExactly(entry.getValue().bytes);
                }));
    }

    private static final class Golden {
        final String packetClass;
        final byte[] bytes;

        Golden(String packetClass, byte[] bytes) {
            this.packetClass = packetClass;
            this.bytes = bytes;
        }
    }
}
