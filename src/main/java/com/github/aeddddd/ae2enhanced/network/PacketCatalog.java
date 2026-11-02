package com.github.aeddddd.ae2enhanced.network;

import com.github.aeddddd.ae2enhanced.centralinterface.packet.PacketVirtualCraftingParticles;
import com.github.aeddddd.ae2enhanced.chamber.packet.PacketChamberSync;
import com.github.aeddddd.ae2enhanced.display.packet.PacketDisplayAction;
import com.github.aeddddd.ae2enhanced.display.packet.PacketDisplayTick;
import com.github.aeddddd.ae2enhanced.item.packet.PacketUMCAction;
import com.github.aeddddd.ae2enhanced.network.packet.*;
import com.github.aeddddd.ae2enhanced.recycler.packet.PacketRecyclerSync;
import com.github.aeddddd.ae2enhanced.specialcrafting.packet.PacketSpecialPlanInfo;
import com.github.aeddddd.ae2enhanced.terminal.packet.PacketMEMonitorableAction;
import com.github.aeddddd.ae2enhanced.tile.packet.PacketCentralBindingOutline;
import com.github.aeddddd.ae2enhanced.tile.packet.PacketChamberAction;
import com.github.aeddddd.ae2enhanced.tile.packet.PacketChunkPowerNodeAction;
import com.github.aeddddd.ae2enhanced.tile.packet.PacketChunkPowerNodeSync;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.relauncher.Side;

import java.util.*;

/** 协议 v1 的唯一注册目录。ID 只追加，12-14 与 47 永久保留。 */
public final class PacketCatalog {
    public static final int NEXT_DISCRIMINATOR = 68;

    private static final List<PacketDescriptor> DESCRIPTORS = createDescriptors();

    private PacketCatalog() {
    }

    public static List<PacketDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    private static PacketDescriptor packet(int id, Side side, Class<? extends IMessage> packet,
            Class<? extends IMessageHandler> handler) {
        return new PacketDescriptor(id, side, packet, handler);
    }

    private static List<PacketDescriptor> createDescriptors() {
        List<PacketDescriptor> result = new ArrayList<PacketDescriptor>();
        result.add(packet(0, Side.SERVER, PacketRequestAssembly.class, PacketRequestAssembly.Handler.class));
        result.add(packet(1, Side.SERVER, PacketPatternPage.class, PacketPatternPage.Handler.class));
        result.add(packet(2, Side.SERVER, PacketCraftRequestLong.class, PacketCraftRequestLong.Handler.class));
        result.add(packet(3, Side.SERVER, PacketMEMonitorableAction.class, PacketMEMonitorableAction.Handler.class));
        result.add(packet(4, Side.SERVER, PacketUniversalBusConfig.class, PacketUniversalBusConfig.Handler.class));
        result.add(packet(5, Side.SERVER, PacketStockingBusConfig.class, PacketStockingBusConfig.Handler.class));
        result.add(packet(6, Side.SERVER, PacketCollectorConfig.class, PacketCollectorConfig.Handler.class));
        result.add(packet(7, Side.SERVER, PacketUMCAction.class, PacketUMCAction.Handler.class));
        result.add(packet(8, Side.SERVER, PacketOmniTermAction.class, PacketOmniTermAction.Handler.class));
        result.add(packet(9, Side.SERVER, PacketLoadOmniRecipe.class, PacketLoadOmniRecipe.Handler.class));
        result.add(packet(10, Side.SERVER, PacketSetSlotAmount.class, PacketSetSlotAmount.Handler.class));
        result.add(packet(11, Side.CLIENT, PacketOmniCraftingUpdate.class, PacketOmniCraftingUpdate.Handler.class));
        result.add(packet(15, Side.SERVER, PacketOmniPageRequest.class, PacketOmniPageRequest.Handler.class));
        result.add(packet(16, Side.CLIENT, PacketOmniPageResult.class, PacketOmniPageResult.Handler.class));
        result.add(packet(17, Side.CLIENT, PacketOmniUpdateNotify.class, PacketOmniUpdateNotify.Handler.class));
        result.add(packet(18, Side.SERVER, PacketOpenOmniTerminal.class, PacketOpenOmniTerminal.Handler.class));
        result.add(packet(19, Side.SERVER, PacketToggleMagnet.class, PacketToggleMagnet.Handler.class));
        result.add(packet(20, Side.SERVER, PacketPickerAction.class, PacketPickerAction.Handler.class));
        result.add(packet(21, Side.SERVER, PacketSmartPatternToggle.class, PacketSmartPatternToggle.Handler.class));
        result.add(packet(22, Side.SERVER, PacketSmartPatternScroll.class, PacketSmartPatternScroll.Handler.class));
        result.add(packet(23, Side.SERVER, PacketSmartPatternEncode.class, PacketSmartPatternEncode.Handler.class));
        result.add(packet(24, Side.SERVER, PacketSmartPatternBind.class, PacketSmartPatternBind.Handler.class));
        result.add(packet(25, Side.SERVER, PacketSmartPatternModify.class, PacketSmartPatternModify.Handler.class));
        result.add(packet(26, Side.SERVER, PacketSmartPatternReplace.class, PacketSmartPatternReplace.Handler.class));
        result.add(packet(27, Side.SERVER, PacketSmartPatternMiniGuiScroll.class,
                PacketSmartPatternMiniGuiScroll.Handler.class));
        result.add(packet(28, Side.SERVER, PacketOmniToolMode.class, PacketOmniToolModeHandler.class));
        result.add(packet(29, Side.SERVER, PacketOmniToolSilkTouch.class, PacketOmniToolSilkTouchHandler.class));
        result.add(packet(30, Side.SERVER, PacketOmniToolDropMode.class, PacketOmniToolDropModeHandler.class));
        result.add(packet(31, Side.SERVER, PacketOpenOmniToolGui.class, PacketOpenOmniToolGuiHandler.class));
        result.add(packet(32, Side.SERVER, PacketOmniToolConfig.class, PacketOmniToolConfigHandler.class));
        result.add(packet(33, Side.CLIENT, PacketChunkPowerHighlight.class,
                com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerHighlightHandler.class));
        result.add(packet(34, Side.CLIENT, PacketVirtualCraftingParticles.class, PacketVirtualCraftingParticles.Handler.class));
        result.add(packet(35, Side.CLIENT, PacketRecyclerSync.class, PacketRecyclerSync.Handler.class));
        result.add(packet(36, Side.SERVER, PacketEMCInterfaceBind.class, PacketEMCInterfaceBind.Handler.class));
        result.add(packet(37, Side.SERVER, PacketPlacementUndo.class, PacketPlacementUndoHandler.class));
        result.add(packet(38, Side.SERVER, PacketPlacementCablePlace.class, PacketPlacementCablePlaceHandler.class));
        result.add(packet(39, Side.SERVER, PacketPlacementSelectPreset.class, PacketPlacementSelectPresetHandler.class));
        result.add(packet(40, Side.SERVER, PacketOmniToolPlacementSubMode.class,
                PacketOmniToolPlacementSubModeHandler.class));
        result.add(packet(41, Side.SERVER, PacketPersonalDimensionRules.class, PacketPersonalDimensionRulesHandler.class));
        result.add(packet(42, Side.CLIENT, PacketPersonalDimensionRulesSync.class,
                PacketPersonalDimensionRulesSync.Handler.class));
        result.add(packet(43, Side.CLIENT, PacketPersonalDimensionRegistrySync.class,
                PacketPersonalDimensionRegistrySync.Handler.class));
        result.add(packet(44, Side.SERVER, PacketEnergyStorageBusAction.class, PacketEnergyStorageBusAction.Handler.class));
        result.add(packet(45, Side.CLIENT, PacketChamberSync.class, PacketChamberSync.Handler.class));
        result.add(packet(46, Side.SERVER, PacketChamberAction.class, PacketChamberAction.Handler.class));
        result.add(packet(48, Side.CLIENT, PacketSpecialPlanInfo.class, PacketSpecialPlanInfo.Handler.class));
        result.add(packet(49, Side.SERVER, PacketDisplayAction.class, PacketDisplayActionHandler.class));
        result.add(packet(50, Side.CLIENT, PacketDisplayTick.class, PacketDisplayTick.Handler.class));
        result.add(packet(51, Side.SERVER, PacketSmartPatternFill.class, PacketSmartPatternFill.Handler.class));
        result.add(packet(52, Side.SERVER, PacketOpenRingGui.class, PacketOpenRingGui.Handler.class));
        result.add(packet(53, Side.SERVER, PacketRingConfig.class, PacketRingConfig.Handler.class));
        result.add(packet(54, Side.SERVER, PacketRingManualHeal.class, PacketRingManualHeal.Handler.class));
        result.add(packet(55, Side.SERVER, PacketRingToggleFlight.class, PacketRingToggleFlight.Handler.class));
        result.add(packet(56, Side.CLIENT, PacketChunkPowerNodeSync.class, PacketChunkPowerNodeSync.Handler.class));
        result.add(packet(57, Side.SERVER, PacketChunkPowerNodeAction.class, PacketChunkPowerNodeAction.Handler.class));
        result.add(packet(58, Side.SERVER, PacketEMCInterfaceMode.class, PacketEMCInterfaceMode.Handler.class));
        result.add(packet(59, Side.CLIENT, PacketCentralBindingOutline.class, PacketCentralBindingOutline.Handler.class));
        result.add(packet(60, Side.SERVER, PacketOmniLayout.class, PacketOmniLayout.Handler.class));
        result.add(packet(61, Side.SERVER, PacketOmniPageRequestV2.class, PacketOmniPageRequestV2.Handler.class));
        result.add(packet(62, Side.CLIENT, PacketOmniPageResultV2.class, PacketOmniPageResultV2.Handler.class));
        result.add(packet(63, Side.SERVER, PacketSmartPatternEdit.class, PacketSmartPatternEdit.Handler.class));
        result.add(packet(64, Side.CLIENT, PacketGuideSync.class, PacketGuideSync.Handler.class));
        result.add(packet(65, Side.CLIENT, PacketPrivateDimensionInfo.class, PacketPrivateDimensionInfo.Handler.class));
        result.add(packet(66, Side.CLIENT, PacketPersonalDimensionFloorSync.class,
                PacketPersonalDimensionFloorSync.Handler.class));
        result.add(packet(67, Side.SERVER, PacketPersonalDimensionFloorAction.class,
                PacketPersonalDimensionFloorAction.Handler.class));

        Set<Integer> ids = new HashSet<Integer>();
        for (PacketDescriptor descriptor : result) {
            if (!ids.add(descriptor.discriminator())) {
                throw new ExceptionInInitializerError("duplicate packet discriminator " + descriptor.discriminator());
            }
        }
        if (result.size() != 64) {
            throw new ExceptionInInitializerError("protocol requires 64 descriptors, got " + result.size());
        }
        return Collections.unmodifiableList(result);
    }
}
