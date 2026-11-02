package com.github.aeddddd.ae2enhanced.client.network;

import com.github.aeddddd.ae2enhanced.chamber.packet.PacketChamberSync;
import com.github.aeddddd.ae2enhanced.client.ClientPersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.client.gui.GuiSingularityChamber;
import com.github.aeddddd.ae2enhanced.client.render.ChunkPowerHighlightRenderer;
import com.github.aeddddd.ae2enhanced.client.specialcrafting.SpecialPlanClientCache;
import com.github.aeddddd.ae2enhanced.clientapi.ClientPacketHandlers;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.display.packet.PacketDisplayTick;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerHighlight;
import com.github.aeddddd.ae2enhanced.specialcrafting.packet.PacketSpecialPlanInfo;
import com.github.aeddddd.ae2enhanced.tile.TileDisplayPanel;
import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;

/** 将 S→C DTO 投递到客户端线程和对应界面。 */
public final class ClientPacketHandlerRegistration {

    private ClientPacketHandlerRegistration() {
    }

    public static void register() {
        ClientPacketHandlers.register(ClientPacketHandlers.CHAMBER_SYNC, value -> {
            PacketChamberSync message = (PacketChamberSync) value;
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().world != null) {
                    TileEntity tile = Minecraft.getMinecraft().world.getTileEntity(message.getPos());
                    if (tile instanceof TileSingularityChamber) {
                        TileSingularityChamber chamber = (TileSingularityChamber) tile;
                        chamber.getInputStore().replaceAll(message.getInputItems(), message.getInputCounts());
                        chamber.getOutputStore().replaceAll(message.getOutputItems(), message.getOutputCounts());
                    }
                }
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiSingularityChamber) {
                    ((GuiSingularityChamber) screen).acceptSync(message);
                }
            });
        });
        ClientPacketHandlers.register(ClientPacketHandlers.CHUNK_POWER_HIGHLIGHT, value ->
                Minecraft.getMinecraft().addScheduledTask(
                        () -> ChunkPowerHighlightRenderer.addHighlights((PacketChunkPowerHighlight) value)));
        ClientPacketHandlers.register(ClientPacketHandlers.DIMENSION_RULES, value ->
                Minecraft.getMinecraft().addScheduledTask(
                        () -> ClientPersonalDimensionRules.update((PersonalDimensionRules) value)));
        ClientPacketHandlers.register(ClientPacketHandlers.DIMENSION_INFO, value ->
                Minecraft.getMinecraft().addScheduledTask(() -> ClientPersonalDimensionRules.updateInfo(
                        (com.github.aeddddd.ae2enhanced.dimension.PrivateDimensionInfo) value)));
        ClientPacketHandlers.register(ClientPacketHandlers.DIMENSION_FLOOR, value -> {
            com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionFloorSync message =
                    (com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionFloorSync) value;
            Minecraft.getMinecraft().addScheduledTask(() -> ClientPersonalDimensionRules.updateFloor(
                    message.getFloor(), message.getRepaintPending()));
        });
        ClientPacketHandlers.register(ClientPacketHandlers.SPECIAL_PLAN, value ->
                Minecraft.getMinecraft().addScheduledTask(() ->
                        SpecialPlanClientCache.update(((PacketSpecialPlanInfo) value).getInfo())));
        ClientPacketHandlers.register(ClientPacketHandlers.GUIDE_SYNC, value -> {
            com.github.aeddddd.ae2enhanced.network.packet.PacketGuideSync message =
                    (com.github.aeddddd.ae2enhanced.network.packet.PacketGuideSync) value;
            Minecraft.getMinecraft().addScheduledTask(() ->
                    com.github.aeddddd.ae2enhanced.guide.client.ScriptGuideClient.accept(
                            message.getRevision(), message.getTotalSize(), message.getChunkIndex(),
                            message.getChunkCount(), message.getPayload()));
        });
        ClientPacketHandlers.register(ClientPacketHandlers.DISPLAY_TICK, value -> {
            PacketDisplayTick message = (PacketDisplayTick) value;
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().world == null) {
                    return;
                }
                TileEntity tile = Minecraft.getMinecraft().world.getTileEntity(message.getPos());
                if (tile instanceof TileDisplayPanel) {
                    ((TileDisplayPanel) tile).applyTickPacket(message.getValues(), message.getValidMask(),
                            message.isPowered(), message.getSampleTotal());
                }
            });
        });
    }
}
