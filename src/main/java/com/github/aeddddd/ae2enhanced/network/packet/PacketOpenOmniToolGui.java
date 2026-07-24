package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import com.github.aeddddd.ae2enhanced.common.menu.OmniToolConfigMenu;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;

/**
 * 客户端请求打开先进 ME 全能工具配置 GUI（C→S,byte handOrdinal）.
 * 服务端校验指定手持有全能工具后通过 {@link NetworkHooks#openScreen} 打开.
 */
public class PacketOpenOmniToolGui implements ServerboundPacket {

    private final int handOrdinal;

    public PacketOpenOmniToolGui(int handOrdinal) {
        this.handOrdinal = handOrdinal;
    }

    public static PacketOpenOmniToolGui decode(FriendlyByteBuf buffer) {
        return new PacketOpenOmniToolGui(buffer.readByte());
    }

    public static void encode(PacketOpenOmniToolGui packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.handOrdinal);
    }

    public static void handle(PacketOpenOmniToolGui packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
        InteractionHand[] hands = InteractionHand.values();
        if (handOrdinal < 0 || handOrdinal >= hands.length) {
            return;
        }
        InteractionHand hand = hands[handOrdinal];
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof AdvancedMEOmniToolItem)) {
            return;
        }
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inv, p) -> new OmniToolConfigMenu(id, inv, handOrdinal),
                Component.translatable("gui.ae2enhanced.omni_tool_config.title")),
                buf -> buf.writeByte(handOrdinal));
    }
}
