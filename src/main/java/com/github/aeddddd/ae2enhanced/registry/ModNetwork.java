package com.github.aeddddd.ae2enhanced.registry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketCraftRequestLong;
import com.github.aeddddd.ae2enhanced.network.packet.PacketMEMonitorableAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPatternPage;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRequestAssembly;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSetSlotAmount;
import com.github.aeddddd.ae2enhanced.network.packet.PacketCollectorConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRecyclerSync;
import com.github.aeddddd.ae2enhanced.network.packet.PacketStockingBusConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUniversalBusConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniTermAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketLoadOmniRecipe;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniCraftingUpdate;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniInventoryUpdate;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniSearchRequest;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniSearchResult;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequest;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniUpdateNotify;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniTerminal;
import com.github.aeddddd.ae2enhanced.network.packet.PacketToggleMagnet;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPickerAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternToggle;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternScroll;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternEncode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternBind;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternModify;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternReplace;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternMiniGuiScroll;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolModeHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolSilkTouch;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolSilkTouchHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolDropMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolDropModeHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniToolGui;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniToolGuiHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerHighlight;
import com.github.aeddddd.ae2enhanced.network.packet.PacketEMCInterfaceBind;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerHighlightHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketVirtualCraftingParticles;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolConfigHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRulesHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRulesSync;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRegistrySync;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementUndo;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementUndoHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementCablePlace;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementCablePlaceHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementSelectPreset;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementSelectPresetHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolPlacementSubMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolPlacementSubModeHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 网络包注册中心
 */
public final class ModNetwork {

    private ModNetwork() {}

    public static void init() {
        AE2Enhanced.network = new SimpleNetworkWrapper(AE2Enhanced.MOD_ID);
        SimpleNetworkWrapper nw = AE2Enhanced.network;

        nw.registerMessage(PacketRequestAssembly.Handler.class, PacketRequestAssembly.class, 0, Side.SERVER);
        nw.registerMessage(PacketPatternPage.Handler.class, PacketPatternPage.class, 1, Side.SERVER);
        nw.registerMessage(PacketCraftRequestLong.Handler.class, PacketCraftRequestLong.class, 2, Side.SERVER);
        nw.registerMessage(PacketMEMonitorableAction.Handler.class, PacketMEMonitorableAction.class, 3, Side.SERVER);
        nw.registerMessage(PacketUniversalBusConfig.Handler.class, PacketUniversalBusConfig.class, 4, Side.SERVER);
        nw.registerMessage(PacketStockingBusConfig.Handler.class, PacketStockingBusConfig.class, 5, Side.SERVER);
        nw.registerMessage(PacketCollectorConfig.Handler.class, PacketCollectorConfig.class, 40, Side.SERVER);
        nw.registerMessage(PacketUMCAction.Handler.class, PacketUMCAction.class, 6, Side.SERVER);
        nw.registerMessage(PacketOmniTermAction.Handler.class, PacketOmniTermAction.class, 7, Side.SERVER);
        nw.registerMessage(PacketLoadOmniRecipe.Handler.class, PacketLoadOmniRecipe.class, 8, Side.SERVER);
        nw.registerMessage(PacketSetSlotAmount.Handler.class, PacketSetSlotAmount.class, 9, Side.SERVER);
        nw.registerMessage(PacketOmniCraftingUpdate.Handler.class, PacketOmniCraftingUpdate.class, 10, Side.CLIENT);
        nw.registerMessage(PacketOmniInventoryUpdate.Handler.class, PacketOmniInventoryUpdate.class, 33, Side.CLIENT);
        nw.registerMessage(PacketOmniSearchRequest.Handler.class, PacketOmniSearchRequest.class, 34, Side.SERVER);
        nw.registerMessage(PacketOmniSearchResult.Handler.class, PacketOmniSearchResult.class, 35, Side.CLIENT);
        nw.registerMessage(PacketOmniPageRequest.Handler.class, PacketOmniPageRequest.class, 36, Side.SERVER);
        nw.registerMessage(PacketOmniPageResult.Handler.class, PacketOmniPageResult.class, 37, Side.CLIENT);
        nw.registerMessage(PacketOmniUpdateNotify.Handler.class, PacketOmniUpdateNotify.class, 38, Side.CLIENT);
        nw.registerMessage(PacketOpenOmniTerminal.Handler.class, PacketOpenOmniTerminal.class, 11, Side.SERVER);
        nw.registerMessage(PacketToggleMagnet.Handler.class, PacketToggleMagnet.class, 12, Side.SERVER);
        nw.registerMessage(PacketPickerAction.Handler.class, PacketPickerAction.class, 13, Side.SERVER);
        nw.registerMessage(PacketSmartPatternToggle.Handler.class, PacketSmartPatternToggle.class, 14, Side.SERVER);
        nw.registerMessage(PacketSmartPatternScroll.Handler.class, PacketSmartPatternScroll.class, 15, Side.SERVER);
        nw.registerMessage(PacketSmartPatternEncode.Handler.class, PacketSmartPatternEncode.class, 16, Side.SERVER);
        nw.registerMessage(PacketSmartPatternBind.Handler.class, PacketSmartPatternBind.class, 17, Side.SERVER);
        nw.registerMessage(PacketSmartPatternModify.Handler.class, PacketSmartPatternModify.class, 18, Side.SERVER);
        nw.registerMessage(PacketSmartPatternReplace.Handler.class, PacketSmartPatternReplace.class, 19, Side.SERVER);
        nw.registerMessage(PacketSmartPatternMiniGuiScroll.Handler.class, PacketSmartPatternMiniGuiScroll.class, 20, Side.SERVER);

        nw.registerMessage(PacketOmniToolModeHandler.class, PacketOmniToolMode.class, 28, Side.SERVER);
        nw.registerMessage(PacketOmniToolSilkTouchHandler.class, PacketOmniToolSilkTouch.class, 29, Side.SERVER);
        nw.registerMessage(PacketOmniToolDropModeHandler.class, PacketOmniToolDropMode.class, 30, Side.SERVER);
        nw.registerMessage(PacketOpenOmniToolGuiHandler.class, PacketOpenOmniToolGui.class, 31, Side.SERVER);
        nw.registerMessage(PacketOmniToolConfigHandler.class, PacketOmniToolConfig.class, 32, Side.SERVER);
        nw.registerMessage(PacketChunkPowerHighlightHandler.class, PacketChunkPowerHighlight.class, 39, Side.CLIENT);
        nw.registerMessage(PacketVirtualCraftingParticles.Handler.class, PacketVirtualCraftingParticles.class, 49, Side.CLIENT);
        nw.registerMessage(PacketRecyclerSync.Handler.class, PacketRecyclerSync.class, 41, Side.CLIENT);
        nw.registerMessage(PacketEMCInterfaceBind.Handler.class, PacketEMCInterfaceBind.class, 42, Side.SERVER);
        nw.registerMessage(PacketPlacementUndoHandler.class, PacketPlacementUndo.class, 45, Side.SERVER);
        nw.registerMessage(PacketPlacementCablePlaceHandler.class, PacketPlacementCablePlace.class, 46, Side.SERVER);
        nw.registerMessage(PacketPlacementSelectPresetHandler.class, PacketPlacementSelectPreset.class, 47, Side.SERVER);
        nw.registerMessage(PacketOmniToolPlacementSubModeHandler.class, PacketOmniToolPlacementSubMode.class, 48, Side.SERVER);
        nw.registerMessage(PacketPersonalDimensionRulesHandler.class, PacketPersonalDimensionRules.class, 50, Side.SERVER);
        nw.registerMessage(PacketPersonalDimensionRulesSync.Handler.class, PacketPersonalDimensionRulesSync.class, 51, Side.CLIENT);
        nw.registerMessage(PacketPersonalDimensionRegistrySync.Handler.class, PacketPersonalDimensionRegistrySync.class, 52, Side.CLIENT);
    }
}
