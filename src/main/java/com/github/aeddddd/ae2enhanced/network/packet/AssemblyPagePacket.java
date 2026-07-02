package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.client.gui.AssemblyMenu;

/**
 * 客户端通知装配枢纽菜单切换页码的服务端包。
 */
public class AssemblyPagePacket implements ServerboundPacket {

    private final BlockPos pos;
    private final int pageIndex;

    public AssemblyPagePacket(BlockPos pos, int pageIndex) {
        this.pos = pos;
        this.pageIndex = pageIndex;
    }

    public static AssemblyPagePacket decode(FriendlyByteBuf buffer) {
        return new AssemblyPagePacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void encode(AssemblyPagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.pageIndex);
    }

    public static void handle(AssemblyPagePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
        if (player.level().isClientSide()) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof AssemblyMenu assemblyMenu)) {
            return;
        }
        if (!assemblyMenu.getControllerPos().equals(pos)) {
            return;
        }
        if (!assemblyMenu.stillValid(player)) {
            return;
        }
        assemblyMenu.setPageIndex(pageIndex);
    }

    public BlockPos pos() {
        return pos;
    }

    public int pageIndex() {
        return pageIndex;
    }
}
