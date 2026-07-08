package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import com.github.aeddddd.ae2enhanced.client.gui.AssemblyMenu;
import com.github.aeddddd.ae2enhanced.client.gui.AssemblyPatternMenu;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 客户端请求装配枢纽菜单切换到指定样板页的服务端包。
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
        if (!(menu instanceof AssemblyMenu || menu instanceof AssemblyPatternMenu)) {
            return;
        }
        if (!getControllerPos(menu).equals(pos)) {
            return;
        }
        if (!menu.stillValid(player)) {
            return;
        }

        int totalPages = 1;
        var be = player.serverLevel().getBlockEntity(pos);
        if (be instanceof com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity controller) {
            totalPages = Math.max(1, controller.getPatternPages());
        }
        int target = Math.max(0, Math.min(pageIndex, totalPages - 1));

        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inv, p) -> new AssemblyPatternMenu(id, inv, pos, target),
                Component.translatable("gui.ae2enhanced.pattern.title")),
                buf -> AssemblyPatternMenu.encodeExtra(buf, pos, target));
    }

    private static BlockPos getControllerPos(AbstractContainerMenu menu) {
        if (menu instanceof AssemblyMenu assembly) {
            return assembly.getControllerPos();
        }
        if (menu instanceof AssemblyPatternMenu pattern) {
            return pattern.getControllerPos();
        }
        return BlockPos.ZERO;
    }

    public BlockPos pos() {
        return pos;
    }

    public int pageIndex() {
        return pageIndex;
    }
}
