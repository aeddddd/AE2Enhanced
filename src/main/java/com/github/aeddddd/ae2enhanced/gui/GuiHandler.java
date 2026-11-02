package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.guiapi.GuiCatalog;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;

/** 旧 FML 入口与常量别名；实际路由由两侧 endpoint 承担。 */
public final class GuiHandler implements IGuiHandler {
    public static final int GUI_ASSEMBLY_CONTROLLER = GuiCatalog.GUI_ASSEMBLY_CONTROLLER;
    public static final int GUI_ASSEMBLY_PATTERN = GuiCatalog.GUI_ASSEMBLY_PATTERN;
    public static final int GUI_HYPERDIMENSIONAL_NEXUS = GuiCatalog.GUI_HYPERDIMENSIONAL_NEXUS;
    public static final int GUI_HYPERDIMENSIONAL_UNFORMED = GuiCatalog.GUI_HYPERDIMENSIONAL_UNFORMED;
    public static final int GUI_COMPUTATION_FORMED = GuiCatalog.GUI_COMPUTATION_FORMED;
    public static final int GUI_COMPUTATION_UNFORMED = GuiCatalog.GUI_COMPUTATION_UNFORMED;
    public static final int GUI_UNIVERSAL_IMPORT_BUS = GuiCatalog.GUI_UNIVERSAL_IMPORT_BUS;
    public static final int GUI_UNIVERSAL_EXPORT_BUS = GuiCatalog.GUI_UNIVERSAL_EXPORT_BUS;
    public static final int GUI_STOCKING_BUS = GuiCatalog.GUI_STOCKING_BUS;
    public static final int GUI_WIRELESS_CHANNEL_TRANSMITTER = GuiCatalog.GUI_WIRELESS_CHANNEL_TRANSMITTER;
    public static final int GUI_UNIVERSAL_MEMORY_CARD = GuiCatalog.GUI_UNIVERSAL_MEMORY_CARD;
    public static final int GUI_OMNI_TERMINAL = GuiCatalog.GUI_OMNI_TERMINAL;
    public static final int GUI_CENTRAL_ME_INTERFACE = GuiCatalog.GUI_CENTRAL_ME_INTERFACE;
    public static final int GUI_SMART_PATTERN_INTERFACE = GuiCatalog.GUI_SMART_PATTERN_INTERFACE;
    public static final int GUI_ENERGY_STORAGE_BUS = GuiCatalog.GUI_ENERGY_STORAGE_BUS;
    public static final int GUI_SPLICED_TERMINAL = GuiCatalog.GUI_SPLICED_TERMINAL;
    public static final int GUI_OMNI_TOOL_CONFIG = GuiCatalog.GUI_OMNI_TOOL_CONFIG;
    public static final int GUI_ADVANCED_ME_COLLECTOR = GuiCatalog.GUI_ADVANCED_ME_COLLECTOR;
    public static final int GUI_ME_NETWORK_RECYCLER = GuiCatalog.GUI_ME_NETWORK_RECYCLER;
    public static final int GUI_EMC_INTERFACE = GuiCatalog.GUI_EMC_INTERFACE;
    public static final int GUI_PERSONAL_DIMENSION_CONFIG = GuiCatalog.GUI_PERSONAL_DIMENSION_CONFIG;
    public static final int GUI_DISPLAY_WALL = GuiCatalog.GUI_DISPLAY_WALL;
    public static final int GUI_SINGULARITY_CHAMBER = GuiCatalog.GUI_SINGULARITY_CHAMBER;
    public static final int GUI_RING_CONFIG = GuiCatalog.GUI_RING_CONFIG;
    public static final int GUI_CHUNK_POWER_NODE = GuiCatalog.GUI_CHUNK_POWER_NODE;

    public static int encodePatternId(int page, int patternPages) {
        return GuiCatalog.encodePatternId(page, patternPages);
    }

    public static int decodePatternPage(int id) {
        return GuiCatalog.decodePatternPage(id);
    }

    public static int decodePatternPages(int id) {
        return GuiCatalog.decodePatternPages(id);
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        IGuiHandler endpoint = GuiEndpointRegistry.endpoint(Side.SERVER);
        return endpoint == null ? null : endpoint.getServerGuiElement(id, player, world, x, y, z);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        IGuiHandler endpoint = GuiEndpointRegistry.endpoint(Side.CLIENT);
        return endpoint == null ? null : endpoint.getClientGuiElement(id, player, world, x, y, z);
    }
}
