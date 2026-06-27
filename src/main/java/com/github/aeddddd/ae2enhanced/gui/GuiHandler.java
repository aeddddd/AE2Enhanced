package com.github.aeddddd.ae2enhanced.gui;
import com.github.aeddddd.ae2enhanced.client.gui.GuiAssemblyFormed;
import com.github.aeddddd.ae2enhanced.client.gui.GuiAssemblyPattern;
import com.github.aeddddd.ae2enhanced.client.gui.GuiAssemblyUnformed;
import com.github.aeddddd.ae2enhanced.client.gui.GuiComputationFormed;
import com.github.aeddddd.ae2enhanced.client.gui.GuiComputationUnformed;
import com.github.aeddddd.ae2enhanced.client.gui.GuiHyperdimensionalNexus;
import com.github.aeddddd.ae2enhanced.client.gui.GuiHyperdimensionalUnformed;
import com.github.aeddddd.ae2enhanced.client.gui.GuiStockingBus;
import com.github.aeddddd.ae2enhanced.client.gui.GuiUniversalExportBus;
import com.github.aeddddd.ae2enhanced.client.gui.GuiUniversalImportBus;
import com.github.aeddddd.ae2enhanced.client.gui.GuiUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.client.gui.GuiAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.client.gui.GuiMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.client.gui.GuiWirelessChannelTransmitter;
import com.github.aeddddd.ae2enhanced.client.gui.GuiOmniToolConfig;
import com.github.aeddddd.ae2enhanced.client.gui.GuiPersonalDimensionConfig;
import com.github.aeddddd.ae2enhanced.client.gui.GuiEMCInterface;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyFormed;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyPattern;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyUnformed;
import com.github.aeddddd.ae2enhanced.container.ContainerComputationUnformed;
import com.github.aeddddd.ae2enhanced.container.ContainerHyperdimensionalNexus;
import com.github.aeddddd.ae2enhanced.container.ContainerHyperdimensionalUnformed;
import com.github.aeddddd.ae2enhanced.container.ContainerStockingBus;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.container.ContainerAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.container.ContainerEMCInterface;
import com.github.aeddddd.ae2enhanced.container.ContainerMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.container.ContainerWirelessChannelTransmitter;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniToolConfig;
import com.github.aeddddd.ae2enhanced.container.ContainerPersonalDimensionConfig;

import com.github.aeddddd.ae2enhanced.container.ContainerUniversalExportBus;
import com.github.aeddddd.ae2enhanced.container.ContainerUniversalImportBus;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalExportBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalImportBus;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

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
    public static final int GUI_OMNI_TOOL_CONFIG = 26;

    public static final int GUI_ADVANCED_ME_COLLECTOR = 27;
    public static final int GUI_ME_NETWORK_RECYCLER = 28;
    public static final int GUI_EMC_INTERFACE = 29;
    public static final int GUI_PERSONAL_DIMENSION_CONFIG = 30;


    /** 编码页码到 GUI ID：低4位为 base ID,bit8-15为页码,bit16-20为 patternPages */
    public static int encodePatternId(int page, int patternPages) {
        return GUI_ASSEMBLY_PATTERN | (page << 8) | (patternPages << 16);
    }

    /** 从 GUI ID 解码页码 */
    public static int decodePatternPage(int ID) {
        return (ID >> 8) & 0xFF;
    }

    /** 从 GUI ID 解码 patternPages */
    public static int decodePatternPages(int ID) {
        return (ID >> 16) & 0x1F;
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        // Omni 终端是无线物品 GUI,不依赖 TileEntity,优先处理避免与 (0,0,0) 处 TileEntity 冲突
        if (ID == GUI_OMNI_TERMINAL) {
            ItemStack held = findOmniTerminalStack(player, x, y);
            if (held.getItem() instanceof ItemOmniWirelessTerminal) {
                appeng.api.features.IWirelessTermHandler handler = appeng.api.AEApi.instance().registries().wireless().getWirelessTerminalHandler(held);
                if (handler == null) {
                    AE2Enhanced.LOGGER.warn("[AE2E] No wireless handler found for OmniTerminal");
                    return null;
                }
                appeng.helpers.WirelessTerminalGuiObject host = new appeng.helpers.WirelessTerminalGuiObject(handler, held, player, world, x, y, 0);
                if (host.getActionableNode() == null) {
                    player.sendMessage(appeng.core.localization.PlayerMessages.OutOfRange.get());
                    return null;
                }
                return new ContainerOmniTerm(player.inventory, host);
            }
            AE2Enhanced.LOGGER.warn("[AE2E] OmniTerminal not found for {}", player.getName());
            return null;
        }

        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (te instanceof TileAssemblyController) {
            TileAssemblyController tile = (TileAssemblyController) te;
            int baseId = ID & 0xF;
            if (baseId == GUI_ASSEMBLY_CONTROLLER) {
                if (tile.isFormed()) {
                    return new ContainerAssemblyFormed(player.inventory, tile);
                } else {
                    return new ContainerAssemblyUnformed(player.inventory, tile);
                }
            } else if (baseId == GUI_ASSEMBLY_PATTERN) {
                int page = decodePatternPage(ID);
                int patternPages = decodePatternPages(ID);
                return new ContainerAssemblyPattern(player.inventory, tile, page, patternPages);
            }
        } else if (te instanceof TileHyperdimensionalController) {
            TileHyperdimensionalController tile = (TileHyperdimensionalController) te;
            if (ID == GUI_HYPERDIMENSIONAL_UNFORMED && !tile.isFormed()) {
                return new ContainerHyperdimensionalUnformed(player.inventory, tile);
            } else if (ID == GUI_HYPERDIMENSIONAL_NEXUS && tile.isFormed()) {
                return new ContainerHyperdimensionalNexus(player.inventory);
            }
            return null;
        } else if (te instanceof TileComputationCore) {
            TileComputationCore tile = (TileComputationCore) te;
            if (ID == GUI_COMPUTATION_UNFORMED && !tile.isFormed()) {
                return new ContainerComputationUnformed(player.inventory, tile);
            } else if (ID == GUI_COMPUTATION_FORMED && tile.isFormed()) {
                return new ContainerHyperdimensionalNexus(player.inventory); // Dummy container for pure-display GUI
            }
            return null;
        }

        // Part GUI：解码 side ordinal
        int baseId = ID & 0xFF;
        if (baseId == GUI_UNIVERSAL_IMPORT_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartUniversalImportBus) {
                    return new ContainerUniversalImportBus(player.inventory, (PartUniversalImportBus) part);
                }
            }
        }
        if (baseId == GUI_UNIVERSAL_EXPORT_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartUniversalExportBus) {
                    return new ContainerUniversalExportBus(player.inventory, (PartUniversalExportBus) part);
                }
            }
        }
        if (baseId == GUI_STOCKING_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartStockingBus) {
                    return new ContainerStockingBus(player.inventory, (PartStockingBus) part);
                }
            }
        }
        if (baseId == GUI_WIRELESS_CHANNEL_TRANSMITTER) {
            if (te instanceof TileWirelessChannelTransmitter) {
                return new ContainerWirelessChannelTransmitter(player.inventory, (TileWirelessChannelTransmitter) te);
            }
        }
        if (ID == GUI_ADVANCED_ME_COLLECTOR) {
            if (te instanceof TileAdvancedMECollector) {
                return new ContainerAdvancedMECollector(player.inventory, (TileAdvancedMECollector) te);
            }
        }
        if (ID == GUI_ME_NETWORK_RECYCLER) {
            if (te instanceof TileMENetworkRecycler) {
                return new ContainerMENetworkRecycler(player.inventory, (TileMENetworkRecycler) te);
            }
        }
        if (ID == GUI_EMC_INTERFACE) {
            if (te instanceof TileEMCInterface) {
                return new ContainerEMCInterface(player.inventory, (TileEMCInterface) te);
            }
        }
        if (ID == GUI_UNIVERSAL_MEMORY_CARD) {
            return new com.github.aeddddd.ae2enhanced.container.ContainerUniversalMemoryCard(player);
        }
        if (ID == GUI_CENTRAL_ME_INTERFACE) {
            if (te instanceof TileCentralMEInterface) {
                return new com.github.aeddddd.ae2enhanced.container.ContainerCentralInterface(player.inventory, (TileCentralMEInterface) te);
            }
        }
        if (ID == GUI_SMART_PATTERN_INTERFACE) {
            if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) {
                return new com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface(player.inventory, (com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) te);
            }
        }
        if (ID == GUI_OMNI_TOOL_CONFIG) {
            return new ContainerOmniToolConfig();
        }
        if (ID == GUI_PERSONAL_DIMENSION_CONFIG) {
            return new ContainerPersonalDimensionConfig(player.getUniqueID());
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        // Omni 终端是无线物品 GUI,不依赖 TileEntity,优先处理避免与 (0,0,0) 处 TileEntity 冲突
        if (ID == GUI_OMNI_TERMINAL) {
            ItemStack held = findOmniTerminalStack(player, x, y);
            if (held.getItem() instanceof ItemOmniWirelessTerminal) {
                appeng.api.features.IWirelessTermHandler handler = appeng.api.AEApi.instance().registries().wireless().getWirelessTerminalHandler(held);
                if (handler == null) {
                    AE2Enhanced.LOGGER.warn("[AE2E] No wireless handler found for OmniTerminal (client)");
                    return null;
                }
                appeng.helpers.WirelessTerminalGuiObject host = new appeng.helpers.WirelessTerminalGuiObject(handler, held, player, world, x, y, 0);
                return new com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm(player.inventory, host);
            }
            return null;
        }

        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (te instanceof TileAssemblyController) {
            TileAssemblyController tile = (TileAssemblyController) te;
            int baseId = ID & 0xF;
            if (baseId == GUI_ASSEMBLY_CONTROLLER) {
                if (tile.isFormed()) {
                    return new GuiAssemblyFormed(player.inventory, tile);
                } else {
                    return new GuiAssemblyUnformed(player.inventory, tile);
                }
            } else if (baseId == GUI_ASSEMBLY_PATTERN) {
                int page = decodePatternPage(ID);
                int patternPages = decodePatternPages(ID);
                return new GuiAssemblyPattern(player.inventory, tile, page, patternPages);
            }
        } else if (te instanceof TileHyperdimensionalController) {
            TileHyperdimensionalController tile = (TileHyperdimensionalController) te;
            if (ID == GUI_HYPERDIMENSIONAL_NEXUS) {
                return new GuiHyperdimensionalNexus(player.inventory, tile);
            } else if (ID == GUI_HYPERDIMENSIONAL_UNFORMED) {
                return new GuiHyperdimensionalUnformed(player.inventory, tile);
            }
        } else if (te instanceof TileComputationCore) {
            TileComputationCore tile = (TileComputationCore) te;
            if (ID == GUI_COMPUTATION_FORMED) {
                return new GuiComputationFormed(tile);
            } else if (ID == GUI_COMPUTATION_UNFORMED) {
                return new GuiComputationUnformed(player.inventory, tile);
            }
        }

        // Part GUI：解码 side ordinal
        int baseId = ID & 0xFF;
        if (baseId == GUI_UNIVERSAL_IMPORT_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartUniversalImportBus) {
                    return new GuiUniversalImportBus(player.inventory, (PartUniversalImportBus) part);
                }
            }
        }
        if (baseId == GUI_UNIVERSAL_EXPORT_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartUniversalExportBus) {
                    return new GuiUniversalExportBus(player.inventory, (PartUniversalExportBus) part);
                }
            }
        }
        if (baseId == GUI_STOCKING_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartStockingBus) {
                    return new GuiStockingBus(player.inventory, (PartStockingBus) part);
                }
            }
        }
        if (baseId == GUI_WIRELESS_CHANNEL_TRANSMITTER) {
            if (te instanceof TileWirelessChannelTransmitter) {
                return new GuiWirelessChannelTransmitter(player.inventory, (TileWirelessChannelTransmitter) te);
            }
        }
        if (ID == GUI_ADVANCED_ME_COLLECTOR) {
            if (te instanceof TileAdvancedMECollector) {
                return new GuiAdvancedMECollector(player.inventory, new ContainerAdvancedMECollector(player.inventory, (TileAdvancedMECollector) te));
            }
        }
        if (ID == GUI_ME_NETWORK_RECYCLER) {
            if (te instanceof TileMENetworkRecycler) {
                return new GuiMENetworkRecycler(player.inventory, new ContainerMENetworkRecycler(player.inventory, (TileMENetworkRecycler) te));
            }
        }
        if (ID == GUI_EMC_INTERFACE) {
            if (te instanceof TileEMCInterface) {
                return new GuiEMCInterface(player.inventory, new ContainerEMCInterface(player.inventory, (TileEMCInterface) te));
            }
        }
        if (ID == GUI_UNIVERSAL_MEMORY_CARD) {
            return new GuiUniversalMemoryCard(player);
        }
        if (ID == GUI_CENTRAL_ME_INTERFACE) {
            if (te instanceof TileCentralMEInterface) {
                return new com.github.aeddddd.ae2enhanced.client.gui.GuiCentralInterface(player.inventory, (TileCentralMEInterface) te);
            }
        }
        if (ID == GUI_SMART_PATTERN_INTERFACE) {
            if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) {
                return new com.github.aeddddd.ae2enhanced.client.gui.GuiSmartPatternInterface(player.inventory, (com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) te);
            }
        }
        if (ID == GUI_OMNI_TOOL_CONFIG) {
            return new GuiOmniToolConfig(player, new ContainerOmniToolConfig());
        }
        if (ID == GUI_PERSONAL_DIMENSION_CONFIG) {
            return new GuiPersonalDimensionConfig(player, new ContainerPersonalDimensionConfig(player.getUniqueID()));
        }
        return null;
    }

    /**
     * 根据 GUI 参数查找 Omni Terminal ItemStack.
     * y == 1 表示从 Baubles 饰品槽位读取(x 为 Baubles 槽位索引).
     */
    private static ItemStack findOmniTerminalStack(EntityPlayer player, int x, int y) {
        if (y == 1 && net.minecraftforge.fml.common.Loader.isModLoaded("baubles")) {
            // 从 Baubles 饰品槽获取
            try {
                Object handler = Class.forName("baubles.api.BaublesApi")
                        .getMethod("getBaublesHandler", EntityPlayer.class)
                        .invoke(null, player);
                ItemStack stack = (ItemStack) handler.getClass()
                        .getMethod("getStackInSlot", int.class)
                        .invoke(handler, x);
                if (!stack.isEmpty() && stack.getItem() instanceof ItemOmniWirelessTerminal) {
                    return stack;
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to get Bauble stack", e);
            }
        }
        // 主手
        ItemStack held = player.getHeldItemMainhand();
        if (held.getItem() instanceof ItemOmniWirelessTerminal) {
            return held;
        }
        // 副手
        held = player.getHeldItemOffhand();
        if (held.getItem() instanceof ItemOmniWirelessTerminal) {
            return held;
        }
        // 物品栏
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.getItem() instanceof ItemOmniWirelessTerminal) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
