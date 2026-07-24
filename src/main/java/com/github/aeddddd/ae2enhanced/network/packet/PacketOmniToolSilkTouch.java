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
 * 客户端请求切换全能工具精准采集（C→S,无字段）.
 */
public class PacketOmniToolSilkTouch implements ServerboundPacket {

    public PacketOmniToolSilkTouch() {
    }

    public static PacketOmniToolSilkTouch decode(FriendlyByteBuf buffer) {
        return new PacketOmniToolSilkTouch();
    }

    public static void encode(PacketOmniToolSilkTouch packet, FriendlyByteBuf buffer) {
    }

    public static void handle(PacketOmniToolSilkTouch packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
                boolean enabled = !AdvancedMEOmniToolItem.isSilkTouchEnabled(stack);
                AdvancedMEOmniToolItem.setSilkTouchEnabled(stack, enabled);
                String stateKey = enabled
                        ? "message.ae2enhanced.omnitool.silk_on"
                        : "message.ae2enhanced.omnitool.silk_off";
                player.displayClientMessage(Component.translatable(stateKey), true);
                // 强制同步 NBT 到客户端，防止数据丢失
                player.setItemInHand(hand, stack);
                break;
            }
        }
    }
}
