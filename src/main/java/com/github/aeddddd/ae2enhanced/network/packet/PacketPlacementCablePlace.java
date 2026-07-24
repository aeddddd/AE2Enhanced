package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;

/**
 * 客户端请求在两点之间铺设线缆（C→S）.
 * 1.12 中供独立放置工具左键路径使用，全能工具不直接发送，
 * 但同属放置子系统，一并移植。
 */
public class PacketPlacementCablePlace implements ServerboundPacket {

    private final BlockPos start;
    private final BlockPos end;

    public PacketPlacementCablePlace(BlockPos start, BlockPos end) {
        this.start = start;
        this.end = end;
    }

    public static PacketPlacementCablePlace decode(FriendlyByteBuf buffer) {
        return new PacketPlacementCablePlace(
                BlockPos.of(buffer.readLong()),
                BlockPos.of(buffer.readLong()));
    }

    public static void encode(PacketPlacementCablePlace packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.start.asLong());
        buffer.writeLong(packet.end.asLong());
    }

    public static void handle(PacketPlacementCablePlace packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
        PlacementToolHelper.placeCableBetween(player, player.level(),
                start, end, InteractionHand.MAIN_HAND, stack);
    }

    public BlockPos getStart() {
        return start;
    }

    public BlockPos getEnd() {
        return end;
    }
}
