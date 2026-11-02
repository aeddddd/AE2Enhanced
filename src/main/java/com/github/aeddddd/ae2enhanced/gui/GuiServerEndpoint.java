package com.github.aeddddd.ae2enhanced.gui;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
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

import static com.github.aeddddd.ae2enhanced.guiapi.GuiCatalog.*;

/** 专用服务端与物理客户端共用的容器路由。 */
public final class GuiServerEndpoint implements IGuiHandler {
    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (isOmniTerminal(ID)) {
            ItemStack held = OmniTerminalFinder.findExactSlot(player, x, y);
            if (held.getItem() instanceof ItemOmniWirelessTerminal) {
                appeng.api.features.IWirelessTermHandler handler =
                        appeng.api.AEApi.instance().registries().wireless().getWirelessTerminalHandler(held);
                if (handler == null) {
                    AE2Enhanced.LOGGER.warn("[AE2E] No wireless handler found for OmniTerminal");
                    return null;
                }
                appeng.helpers.WirelessTerminalGuiObject host =
                        new appeng.helpers.WirelessTerminalGuiObject(handler, held, player, world, x, y, 0);
                if (host.getActionableNode() == null) {
                    player.sendMessage(appeng.core.localization.PlayerMessages.OutOfRange.get());
                    return null;
                }
                return ID == GUI_SPLICED_TERMINAL
                        ? new ContainerSplicedTerm(player.inventory, host)
                        : new ContainerOmniTerm(player.inventory, host);
            }
            AE2Enhanced.LOGGER.warn("[AE2E] OmniTerminal not found for {}", player.getName());
            return null;
        }

        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (te instanceof TileAssemblyController) {
            TileAssemblyController tile = (TileAssemblyController) te;
            int baseId = assemblyBaseId(ID);
            if (baseId == GUI_ASSEMBLY_CONTROLLER) {
                return tile.isFormed()
                        ? new ContainerAssemblyFormed(player.inventory, tile)
                        : new ContainerAssemblyUnformed(player.inventory, tile);
            } else if (baseId == GUI_ASSEMBLY_PATTERN) {
                return new ContainerAssemblyPattern(player.inventory, tile,
                        decodePatternPage(ID), decodePatternPages(ID));
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
                return new ContainerComputationFormed(tile);
            }
            return null;
        }

        int baseId = partBaseId(ID);
        if (baseId == GUI_UNIVERSAL_IMPORT_BUS && te instanceof IPartHost) {
            AEPartLocation side = AEPartLocation.fromOrdinal(partSideOrdinal(ID));
            IPart part = ((IPartHost) te).getPart(side);
            if (part instanceof PartUniversalImportBus) {
                return new ContainerUniversalImportBus(player.inventory, (PartUniversalImportBus) part);
            }
        }
        if (baseId == GUI_UNIVERSAL_EXPORT_BUS && te instanceof IPartHost) {
            AEPartLocation side = AEPartLocation.fromOrdinal(partSideOrdinal(ID));
            IPart part = ((IPartHost) te).getPart(side);
            if (part instanceof PartUniversalExportBus) {
                return new ContainerUniversalExportBus(player.inventory, (PartUniversalExportBus) part);
            }
        }
        if (baseId == GUI_STOCKING_BUS && te instanceof IPartHost) {
            AEPartLocation side = AEPartLocation.fromOrdinal(partSideOrdinal(ID));
            IPart part = ((IPartHost) te).getPart(side);
            if (part instanceof PartStockingBus) {
                return new ContainerStockingBus(player.inventory, (PartStockingBus) part);
            }
        }
        if (baseId == GUI_ENERGY_STORAGE_BUS && te instanceof IPartHost) {
            AEPartLocation side = AEPartLocation.fromOrdinal(partSideOrdinal(ID));
            IPart part = ((IPartHost) te).getPart(side);
            if (part instanceof com.github.aeddddd.ae2enhanced.part.PartEnergyStorageBus) {
                ContainerEnergyStorageBus container = new ContainerEnergyStorageBus(player.inventory,
                        (com.github.aeddddd.ae2enhanced.part.PartEnergyStorageBus) part);
                appeng.container.ContainerOpenContext context = new appeng.container.ContainerOpenContext(part);
                context.setWorld(world);
                context.setX(x);
                context.setY(y);
                context.setZ(z);
                context.setSide(side);
                container.setOpenContext(context);
                return container;
            }
        }
        if (baseId == GUI_WIRELESS_CHANNEL_TRANSMITTER && te instanceof TileWirelessChannelTransmitter) {
            return new ContainerWirelessChannelTransmitter(player.inventory, (TileWirelessChannelTransmitter) te);
        }
        if (ID == GUI_ADVANCED_ME_COLLECTOR && te instanceof TileAdvancedMECollector) {
            return new ContainerAdvancedMECollector(player.inventory, (TileAdvancedMECollector) te);
        }
        if (ID == GUI_ME_NETWORK_RECYCLER && te instanceof TileMENetworkRecycler) {
            return new ContainerMENetworkRecycler(player.inventory, (TileMENetworkRecycler) te);
        }
        if (ID == GUI_EMC_INTERFACE && te instanceof TileEMCInterface) {
            return new ContainerEMCInterface(player.inventory, (TileEMCInterface) te);
        }
        if (ID == GUI_UNIVERSAL_MEMORY_CARD) {
            return new ContainerUniversalMemoryCard(player);
        }
        if (ID == GUI_CENTRAL_ME_INTERFACE && te instanceof TileCentralMEInterface) {
            return new ContainerCentralInterface(player.inventory, (TileCentralMEInterface) te);
        }
        if (ID == GUI_SMART_PATTERN_INTERFACE && te instanceof TileSmartPatternInterface) {
            return new ContainerSmartPatternInterface(player.inventory, (TileSmartPatternInterface) te);
        }
        if (ID == GUI_OMNI_TOOL_CONFIG) {
            return new ContainerOmniToolConfig(player, x);
        }
        if (ID == GUI_RING_CONFIG) {
            return new ContainerRingConfig();
        }
        if (ID == GUI_PERSONAL_DIMENSION_CONFIG) {
            java.util.UUID target = player.getUniqueID();
            if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager.sendRulesToPlayer(
                        target, (net.minecraft.entity.player.EntityPlayerMP) player);
            }
            return new ContainerPersonalDimensionConfig(target);
        }
        if (ID == GUI_DISPLAY_WALL) {
            if (te instanceof TileDisplayPanel) {
                TileDisplayPanel tile = (TileDisplayPanel) te;
                if (tile.isMasterRole()) {
                    return new ContainerDisplayWall(player.inventory, tile);
                }
            }
            return null;
        }
        if (ID == GUI_SINGULARITY_CHAMBER) {
            if (te instanceof TileSingularityChamber) {
                return new ContainerSingularityChamber(player.inventory, (TileSingularityChamber) te);
            }
            return null;
        }
        if (ID == GUI_CHUNK_POWER_NODE) {
            if (te instanceof TileChunkPowerNode) {
                return new ContainerChunkPowerNode(player.inventory, (TileChunkPowerNode) te);
            }
            return null;
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
