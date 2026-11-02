package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.client.gui.terminal.GuiSplicedTerm;
import com.github.aeddddd.ae2enhanced.collector.TileAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.container.*;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import com.github.aeddddd.ae2enhanced.omni.OmniTerminalFinder;
import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalExportBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalImportBus;
import com.github.aeddddd.ae2enhanced.recycler.TileMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.tile.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static com.github.aeddddd.ae2enhanced.guiapi.GuiCatalog.*;

/** 仅由 ClientProxy 加载的屏幕路由。 */
@SideOnly(Side.CLIENT)
public final class GuiClientEndpoint implements IGuiHandler {
    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (isOmniTerminal(ID)) {
            ItemStack held = OmniTerminalFinder.findExactSlot(player, x, y);
            if (held.getItem() instanceof ItemOmniWirelessTerminal) {
                appeng.api.features.IWirelessTermHandler handler =
                        appeng.api.AEApi.instance().registries().wireless().getWirelessTerminalHandler(held);
                if (handler == null) {
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                            "[AE2E] No wireless handler found for OmniTerminal (client)");
                    return null;
                }
                appeng.helpers.WirelessTerminalGuiObject host =
                        new appeng.helpers.WirelessTerminalGuiObject(handler, held, player, world, x, y, 0);
                return ID == GUI_SPLICED_TERMINAL
                        ? new GuiSplicedTerm(player.inventory, host)
                        : new GuiOmniTerm(player.inventory, host);
            }
            return null;
        }

        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (te instanceof TileAssemblyController) {
            TileAssemblyController tile = (TileAssemblyController) te;
            int baseId = assemblyBaseId(ID);
            if (baseId == GUI_ASSEMBLY_CONTROLLER) {
                return tile.isFormed()
                        ? new GuiAssemblyFormed(player.inventory, tile)
                        : new GuiAssemblyUnformed(player.inventory, tile);
            } else if (baseId == GUI_ASSEMBLY_PATTERN) {
                return new GuiAssemblyPattern(player.inventory, tile,
                        decodePatternPage(ID), decodePatternPages(ID));
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

        int baseId = partBaseId(ID);
        if (baseId == GUI_UNIVERSAL_IMPORT_BUS && te instanceof IPartHost) {
            AEPartLocation side = AEPartLocation.fromOrdinal(partSideOrdinal(ID));
            IPart part = ((IPartHost) te).getPart(side);
            if (part instanceof PartUniversalImportBus) {
                return new GuiUniversalImportBus(player.inventory, (PartUniversalImportBus) part);
            }
        }
        if (baseId == GUI_UNIVERSAL_EXPORT_BUS && te instanceof IPartHost) {
            AEPartLocation side = AEPartLocation.fromOrdinal(partSideOrdinal(ID));
            IPart part = ((IPartHost) te).getPart(side);
            if (part instanceof PartUniversalExportBus) {
                return new GuiUniversalExportBus(player.inventory, (PartUniversalExportBus) part);
            }
        }
        if (baseId == GUI_STOCKING_BUS && te instanceof IPartHost) {
            AEPartLocation side = AEPartLocation.fromOrdinal(partSideOrdinal(ID));
            IPart part = ((IPartHost) te).getPart(side);
            if (part instanceof PartStockingBus) {
                return new GuiStockingBus(player.inventory, (PartStockingBus) part);
            }
        }
        if (baseId == GUI_ENERGY_STORAGE_BUS && te instanceof IPartHost) {
            AEPartLocation side = AEPartLocation.fromOrdinal(partSideOrdinal(ID));
            IPart part = ((IPartHost) te).getPart(side);
            if (part instanceof com.github.aeddddd.ae2enhanced.part.PartEnergyStorageBus) {
                return new GuiEnergyStorageBus(player.inventory,
                        (com.github.aeddddd.ae2enhanced.part.PartEnergyStorageBus) part);
            }
        }
        if (baseId == GUI_WIRELESS_CHANNEL_TRANSMITTER && te instanceof TileWirelessChannelTransmitter) {
            return new GuiWirelessChannelTransmitter(player.inventory, (TileWirelessChannelTransmitter) te);
        }
        if (ID == GUI_ADVANCED_ME_COLLECTOR && te instanceof TileAdvancedMECollector) {
            return new GuiAdvancedMECollector(player.inventory,
                    new ContainerAdvancedMECollector(player.inventory, (TileAdvancedMECollector) te));
        }
        if (ID == GUI_ME_NETWORK_RECYCLER && te instanceof TileMENetworkRecycler) {
            return new GuiMENetworkRecycler(player.inventory,
                    new ContainerMENetworkRecycler(player.inventory, (TileMENetworkRecycler) te));
        }
        if (ID == GUI_EMC_INTERFACE && te instanceof TileEMCInterface) {
            return new GuiEMCInterface(player.inventory,
                    new ContainerEMCInterface(player.inventory, (TileEMCInterface) te));
        }
        if (ID == GUI_UNIVERSAL_MEMORY_CARD) {
            return new GuiUniversalMemoryCard(player);
        }
        if (ID == GUI_CENTRAL_ME_INTERFACE && te instanceof TileCentralMEInterface) {
            return new GuiCentralInterface(player.inventory, (TileCentralMEInterface) te);
        }
        if (ID == GUI_SMART_PATTERN_INTERFACE && te instanceof TileSmartPatternInterface) {
            return new GuiSmartPatternInterface(player.inventory, (TileSmartPatternInterface) te);
        }
        if (ID == GUI_OMNI_TOOL_CONFIG) {
            return new GuiOmniToolConfig(player, new ContainerOmniToolConfig(player, x));
        }
        if (ID == GUI_RING_CONFIG) {
            return new GuiRingConfig(player, new ContainerRingConfig());
        }
        if (ID == GUI_PERSONAL_DIMENSION_CONFIG) {
            return new GuiPersonalDimensionConfig(player,
                    new ContainerPersonalDimensionConfig(player.getUniqueID()));
        }
        if (ID == GUI_DISPLAY_WALL) {
            if (te instanceof TileDisplayPanel) {
                return new GuiDisplayWall(player.inventory, (TileDisplayPanel) te);
            }
            return null;
        }
        if (ID == GUI_SINGULARITY_CHAMBER) {
            if (te instanceof TileSingularityChamber) {
                return new GuiSingularityChamber(player.inventory, (TileSingularityChamber) te);
            }
            return null;
        }
        if (ID == GUI_CHUNK_POWER_NODE) {
            if (te instanceof TileChunkPowerNode) {
                return new GuiChunkPowerNode(player.inventory, (TileChunkPowerNode) te);
            }
            return null;
        }
        return null;
    }
}
