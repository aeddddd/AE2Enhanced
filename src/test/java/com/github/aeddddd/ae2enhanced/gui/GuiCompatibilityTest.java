package com.github.aeddddd.ae2enhanced.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.github.aeddddd.ae2enhanced.guiapi.GuiCatalog;

/** 普通 GUI、Assembly 位段和 Part 位段的 M0 兼容基线。 */
class GuiCompatibilityTest {

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }

    private static String nameForLowId(int id) {
        String[] names = { "GUI_ASSEMBLY_CONTROLLER", "GUI_ASSEMBLY_PATTERN",
                "GUI_HYPERDIMENSIONAL_NEXUS", "GUI_HYPERDIMENSIONAL_UNFORMED",
                "GUI_COMPUTATION_FORMED", "GUI_COMPUTATION_UNFORMED",
                "GUI_UNIVERSAL_IMPORT_BUS", "GUI_UNIVERSAL_EXPORT_BUS", "GUI_STOCKING_BUS",
                "GUI_WIRELESS_CHANNEL_TRANSMITTER", "GUI_UNIVERSAL_MEMORY_CARD", "GUI_OMNI_TERMINAL",
                "GUI_CENTRAL_ME_INTERFACE", "GUI_SMART_PATTERN_INTERFACE", "GUI_ENERGY_STORAGE_BUS",
                "GUI_SPLICED_TERMINAL" };
        return names[id];
    }

    @Test
    void allGuiConstantsAreFrozen() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (int id = 0; id <= 15; id++) {
            expected.put(nameForLowId(id), id);
        }
        expected.put("GUI_OMNI_TOOL_CONFIG", 26);
        expected.put("GUI_ADVANCED_ME_COLLECTOR", 27);
        expected.put("GUI_ME_NETWORK_RECYCLER", 28);
        expected.put("GUI_EMC_INTERFACE", 29);
        expected.put("GUI_PERSONAL_DIMENSION_CONFIG", 30);
        expected.put("GUI_DISPLAY_WALL", 31);
        expected.put("GUI_SINGULARITY_CHAMBER", 32);
        expected.put("GUI_RING_CONFIG", 33);
        expected.put("GUI_CHUNK_POWER_NODE", 34);

        Map<String, Integer> actual = new LinkedHashMap<>();
        for (Field field : GuiHandler.class.getFields()) {
            if (field.getType() == int.class && Modifier.isStatic(field.getModifiers())
                    && field.getName().startsWith("GUI_")) {
                try {
                    actual.put(field.getName(), field.getInt(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        assertThat(actual).isEqualTo(expected);
        assertThat(GuiHandler.GUI_OMNI_TERMINAL).isEqualTo(11);
        assertThat(GuiHandler.GUI_SPLICED_TERMINAL).isEqualTo(15);
    }

    @Test
    void assemblyPatternUsesBaseLow4PageBits8To15AndPagesBits16To20() {
        int encoded = GuiHandler.encodePatternId(0xA7, 0x1D);
        assertThat(encoded).isEqualTo(1 | (0xA7 << 8) | (0x1D << 16));
        assertThat(encoded & 0xF).isEqualTo(GuiHandler.GUI_ASSEMBLY_PATTERN);
        assertThat(GuiHandler.decodePatternPage(encoded)).isEqualTo(0xA7);
        assertThat(GuiHandler.decodePatternPages(encoded)).isEqualTo(0x1D);
        assertThat(GuiHandler.decodePatternPage(encoded | 0x20_0000)).isEqualTo(0xA7);
        assertThat(GuiHandler.decodePatternPages(encoded | 0x20_0000)).isEqualTo(0x1D);
    }

    @Test
    void partIdsUseFullLow8AndSideAtBit8RatherThanAssemblyLow4() {
        int[] partIds = { GuiHandler.GUI_UNIVERSAL_IMPORT_BUS, GuiHandler.GUI_UNIVERSAL_EXPORT_BUS,
                GuiHandler.GUI_STOCKING_BUS, GuiHandler.GUI_ENERGY_STORAGE_BUS };
        for (int base : partIds) {
            int encoded = base | (0xA5 << 8);
            assertThat(encoded & 0xFF).isEqualTo(base);
            assertThat((encoded >> 8) & 0xFF).isEqualTo(0xA5);
            assertThat(encoded & 0xF).isEqualTo(base & 0xF);
        }
        int futurePartId = 0xAB | (0xA5 << 8);
        assertThat(futurePartId & 0xFF).isEqualTo(0xAB);
        assertThat(futurePartId & 0xF).isNotEqualTo(futurePartId & 0xFF);
    }

    @Test
    void endpointsKeepAssemblyAndPartMasksSeparateAndHandleBothOmniIds() throws IOException {
        Path root = Paths.get(System.getProperty("ae2e.projectDir", ""))
                .resolve("src/main/java/com/github/aeddddd/ae2enhanced");
        String server = new String(Files.readAllBytes(root.resolve("gui/GuiServerEndpoint.java")),
                StandardCharsets.UTF_8);
        String client = new String(Files.readAllBytes(root.resolve("client/gui/GuiClientEndpoint.java")),
                StandardCharsets.UTF_8);
        for (String text : new String[] { server, client }) {
            assertThat(count(text, "int baseId = assemblyBaseId(ID);")).isEqualTo(1);
            assertThat(count(text, "int baseId = partBaseId(ID);")).isEqualTo(1);
            assertThat(count(text, "AEPartLocation.fromOrdinal(partSideOrdinal(ID))")).isEqualTo(4);
            assertThat(count(text, "if (isOmniTerminal(ID))")).isEqualTo(1);
            assertThat(count(text, "OmniTerminalFinder.findExactSlot(player, x, y)")).isEqualTo(1);
        }
        assertThat(GuiCatalog.isOmniTerminal(GuiCatalog.GUI_OMNI_TERMINAL)).isTrue();
        assertThat(GuiCatalog.isOmniTerminal(GuiCatalog.GUI_SPLICED_TERMINAL)).isTrue();
    }
}
