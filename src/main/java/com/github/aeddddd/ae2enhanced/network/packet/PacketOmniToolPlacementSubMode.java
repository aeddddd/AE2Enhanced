package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;

/**
 * 客户端请求切换放置子模式（SINGLE ↔ BULK，C→S）.
 */
public class PacketOmniToolPlacementSubMode implements ServerboundPacket {

    private final boolean next; // true = 切换到下一个模式，false = 上一个

    public PacketOmniToolPlacementSubMode(boolean next) {
        this.next = next;
    }

    public static PacketOmniToolPlacementSubMode decode(FriendlyByteBuf buffer) {
        return new PacketOmniToolPlacementSubMode(buffer.readBoolean());
    }

    public static void encode(PacketOmniToolPlacementSubMode packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.next);
    }

    public static void handle(PacketOmniToolPlacementSubMode packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AdvancedMEOmniToolItem)
                || AdvancedMEOmniToolItem.getMode(stack) != AdvancedMEOmniToolItem.MODE_PLACEMENT) {
            return;
        }
        PlacementConfig config = new PlacementConfig(stack);
        PlacementMode current = config.getPlacementMode();
        PlacementMode nextMode = current == PlacementMode.SINGLE ? PlacementMode.BULK : PlacementMode.SINGLE;
        config.setPlacementMode(nextMode);
    }

    public boolean isNext() {
        return next;
    }
}
