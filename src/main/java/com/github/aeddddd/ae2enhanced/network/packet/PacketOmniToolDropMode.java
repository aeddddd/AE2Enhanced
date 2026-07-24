package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;

/**
 * 客户端请求循环切换全能工具掉落模式（C→S,无字段）.
 */
public class PacketOmniToolDropMode implements ServerboundPacket {

    public PacketOmniToolDropMode() {
    }

    public static PacketOmniToolDropMode decode(FriendlyByteBuf buffer) {
        return new PacketOmniToolDropMode();
    }

    public static void encode(PacketOmniToolDropMode packet, FriendlyByteBuf buffer) {
    }

    public static void handle(PacketOmniToolDropMode packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof AdvancedMEOmniToolItem) {
                AdvancedMEOmniToolItem.cycleDropMode(stack);
                int newMode = AdvancedMEOmniToolItem.getDropMode(stack);
                Component modeName = Component.translatable(AdvancedMEOmniToolItem.getDropModeNameKey(newMode));
                player.displayClientMessage(
                        Component.translatable("message.ae2enhanced.omnitool.drop_changed", modeName), true);
                player.setItemInHand(hand, stack);
                break;
            }
        }
    }
}
