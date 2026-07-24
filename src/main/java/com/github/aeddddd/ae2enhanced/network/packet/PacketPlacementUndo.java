package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;

/**
 * 客户端请求撤销最后一次放置（Ctrl+右键，C→S，无字段）.
 */
public class PacketPlacementUndo implements ServerboundPacket {

    public PacketPlacementUndo() {
    }

    public static PacketPlacementUndo decode(FriendlyByteBuf buffer) {
        return new PacketPlacementUndo();
    }

    public static void encode(PacketPlacementUndo packet, FriendlyByteBuf buffer) {
    }

    public static void handle(PacketPlacementUndo packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                packet.handleOnServer(player);
            }
        });
        context.setPacketHandled(true);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof AdvancedMEOmniToolItem
                && AdvancedMEOmniToolItem.getMode(held) == AdvancedMEOmniToolItem.MODE_PLACEMENT) {
            PlacementToolHelper.undoLast(player, player.level(), held);
        }
    }
}
