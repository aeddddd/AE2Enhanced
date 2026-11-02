package com.github.aeddddd.ae2enhanced.guiapi;

/** 线上 GUI ID 与各类编码规则；不同路由的位段不得混用。 */
public final class GuiCatalog {
    public static final int GUI_ASSEMBLY_CONTROLLER = 0;
    public static final int GUI_ASSEMBLY_PATTERN = 1;
    public static final int GUI_HYPERDIMENSIONAL_NEXUS = 2;
    public static final int GUI_HYPERDIMENSIONAL_UNFORMED = 3;
    public static final int GUI_COMPUTATION_FORMED = 4;
    public static final int GUI_COMPUTATION_UNFORMED = 5;
    public static final int GUI_UNIVERSAL_IMPORT_BUS = 6;
    public static final int GUI_UNIVERSAL_EXPORT_BUS = 7;
    public static final int GUI_STOCKING_BUS = 8;
    public static final int GUI_WIRELESS_CHANNEL_TRANSMITTER = 9;
    public static final int GUI_UNIVERSAL_MEMORY_CARD = 10;
    public static final int GUI_OMNI_TERMINAL = 11;
    public static final int GUI_CENTRAL_ME_INTERFACE = 12;
    public static final int GUI_SMART_PATTERN_INTERFACE = 13;
    public static final int GUI_ENERGY_STORAGE_BUS = 14;
    public static final int GUI_SPLICED_TERMINAL = 15;
    public static final int GUI_OMNI_TOOL_CONFIG = 26;
    public static final int GUI_ADVANCED_ME_COLLECTOR = 27;
    public static final int GUI_ME_NETWORK_RECYCLER = 28;
    public static final int GUI_EMC_INTERFACE = 29;
    public static final int GUI_PERSONAL_DIMENSION_CONFIG = 30;
    public static final int GUI_DISPLAY_WALL = 31;
    public static final int GUI_SINGULARITY_CHAMBER = 32;
    public static final int GUI_RING_CONFIG = 33;
    public static final int GUI_CHUNK_POWER_NODE = 34;

    private GuiCatalog() {
    }

    public static int encodePatternId(int page, int patternPages) {
        return GUI_ASSEMBLY_PATTERN | (page << 8) | (patternPages << 16);
    }

    public static int assemblyBaseId(int id) {
        return id & 0xF;
    }

    public static int decodePatternPage(int id) {
        return (id >> 8) & 0xFF;
    }

    public static int decodePatternPages(int id) {
        return (id >> 16) & 0x1F;
    }

    public static int partBaseId(int id) {
        return id & 0xFF;
    }

    public static int partSideOrdinal(int id) {
        return (id >> 8) & 0xFF;
    }

    public static boolean isOmniTerminal(int id) {
        return id == GUI_OMNI_TERMINAL || id == GUI_SPLICED_TERMINAL;
    }
}
