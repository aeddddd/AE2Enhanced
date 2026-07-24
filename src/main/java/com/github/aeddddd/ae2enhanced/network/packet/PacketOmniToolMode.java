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
 * 客户端请求循环切换全能工具模式（C→S,无字段）.
 */
public class PacketOmniToolMode implements ServerboundPacket {

    public PacketOmniToolMode() {
    }

    public static PacketOmniToolMode decode(FriendlyByteBuf buffer) {
        return new PacketOmniToolMode();
    }

    public static void encode(PacketOmniToolMode packet, FriendlyByteBuf buffer) {
    }

    public static void handle(PacketOmniToolMode packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
                AdvancedMEOmniToolItem.cycleMode(stack);
                int newMode = AdvancedMEOmniToolItem.getMode(stack);
                Component modeName = Component.translatable(AdvancedMEOmniToolItem.getModeNameKey(newMode));
                player.displayClientMessage(
                        Component.translatable("message.ae2enhanced.omnitool.mode_changed", modeName), true);
                // 强制同步 NBT 到客户端，防止数据丢失
                player.setItemInHand(hand, stack);
                break;
            }
        }
    }
}
