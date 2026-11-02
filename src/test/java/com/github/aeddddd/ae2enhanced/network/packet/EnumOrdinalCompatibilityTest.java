package com.github.aeddddd.ae2enhanced.network.packet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 仅冻结真正越过网络或 NBT 边界的项目内枚举；外部 AE2/MC 枚举不在此清单。 */
class EnumOrdinalCompatibilityTest {

    @Test
    void everyBaselineConstantKeepsItsNameAndOrdinal() throws IOException {
        InputStream stream = EnumOrdinalCompatibilityTest.class
                .getResourceAsStream("/protocol/enum-ordinals-v1.tsv");
        assertThat(stream).as("缺少 enum-ordinals-v1.tsv").isNotNull();
        int rows = 0;
        Set<String> classes = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("class\t")) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                assertThat(columns).as("非法枚举行: " + line).hasSize(4);
                assertThat(columns[1]).isIn("wire", "nbt", "wire+nbt");
                Class<?> type;
                try {
                    type = Class.forName(columns[0]);
                } catch (ClassNotFoundException e) {
                    throw new AssertionError("枚举类不存在: " + columns[0], e);
                }
                assertThat(type.isEnum()).as(columns[0]).isTrue();
                Object constant;
                try {
                    @SuppressWarnings({ "rawtypes", "unchecked" })
                    Object value = Enum.valueOf((Class) type, columns[2]);
                    constant = value;
                } catch (IllegalArgumentException e) {
                    throw new AssertionError("枚举常量不存在: " + columns[0] + "." + columns[2], e);
                }
                assertThat(((Enum<?>) constant).ordinal())
                        .as(columns[0] + "." + columns[2])
                        .isEqualTo(Integer.parseInt(columns[3]));
                classes.add(columns[0]);
                rows++;
            }
        }
        assertThat(rows).isEqualTo(57);
        assertThat(classes).hasSize(13);
    }
}
