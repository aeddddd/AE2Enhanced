package com.github.aeddddd.ae2enhanced.network.packet;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** 注册清单棘轮：扫描用于盘点，不能替代真实 FML 通道的运行时注册验证。 */
class ProtocolRegistrationCompatibilityTest {

    private static Baseline loadBaseline() throws IOException {
        InputStream stream = ProtocolRegistrationCompatibilityTest.class
                .getResourceAsStream("/protocol/protocol-v1.tsv");
        assertThat(stream).as("缺少 protocol-v1.tsv").isNotNull();
        Map<Integer, Entry> entries = new LinkedHashMap<>();
        int next = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("# next=")) {
                    next = Integer.parseInt(line.substring(7).trim());
                }
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("id\t")) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                assertThat(columns).as("非法协议行: " + line).hasSize(4);
                int id = Integer.parseInt(columns[0]);
                assertThat(columns[1]).isIn("C2S", "S2C");
                assertThat(columns[2]).matches("Packet\\w+");
                assertThat(columns[3]).isNotBlank();
                assertThat(entries.put(id, new Entry(columns[1], columns[2]))).isNull();
            }
        }
        return new Baseline(entries, next);
    }

    @Test
    void descriptorCatalogExactlyMatchesFrozenProtocol() throws IOException {
        Baseline baseline = loadBaseline();
        Map<Integer, String> actual = new LinkedHashMap<>();
        for (com.github.aeddddd.ae2enhanced.network.PacketDescriptor descriptor
                : com.github.aeddddd.ae2enhanced.network.PacketCatalog.descriptors()) {
            int id = descriptor.discriminator();
            String direction = descriptor.receivingSide() == net.minecraftforge.fml.relauncher.Side.SERVER
                    ? "C2S" : "S2C";
            String previous = actual.put(id, direction + ":" + descriptor.packetType().getSimpleName());
            assertThat(previous).as("重复 discriminator " + id).isNull();
        }

        Map<Integer, String> expected = new LinkedHashMap<>();
        baseline.entries.forEach((id, entry) -> expected.put(id, entry.direction + ":" + entry.packetClass));
        assertThat(actual).isEqualTo(expected);
        assertThat(actual).hasSize(64);
        assertThat(com.github.aeddddd.ae2enhanced.network.PacketCatalog.NEXT_DISCRIMINATOR)
                .isEqualTo(baseline.next);
    }

    @Test
    void idsDirectionsAndRetiredHolesAreFrozen() throws IOException {
        Baseline baseline = loadBaseline();
        assertThat(baseline.entries).hasSize(64);
        assertThat(baseline.entries.keySet()).doesNotContain(12, 13, 14, 47);
        assertThat(baseline.entries.values()).filteredOn(e -> e.direction.equals("C2S")).hasSize(47);
        assertThat(baseline.entries.values()).filteredOn(e -> e.direction.equals("S2C")).hasSize(17);

        Set<Integer> allocatedOrRetired = new HashSet<>(baseline.entries.keySet());
        allocatedOrRetired.addAll(Arrays.asList(12, 13, 14, 47));
        Set<Integer> completeRange = new HashSet<>();
        for (int id = 0; id <= 67; id++) {
            completeRange.add(id);
        }
        assertThat(allocatedOrRetired).isEqualTo(completeRange);
        assertThat(baseline.next).isEqualTo(68);
    }

    private static final class Baseline {
        final Map<Integer, Entry> entries;
        final int next;

        Baseline(Map<Integer, Entry> entries, int next) {
            this.entries = entries;
            this.next = next;
        }
    }

    private static final class Entry {
        final String direction;
        final String packetClass;

        Entry(String direction, String packetClass) {
            this.direction = direction;
            this.packetClass = packetClass;
        }
    }
}
