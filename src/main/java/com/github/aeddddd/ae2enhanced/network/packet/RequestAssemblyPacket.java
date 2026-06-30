package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端请求一键放置多方块结构的占位包。
 */
public class RequestAssemblyPacket implements ServerboundPacket {

    private final BlockPos controllerPos;

    public RequestAssemblyPacket(BlockPos controllerPos) {
        this.controllerPos = controllerPos;
    }

    public static RequestAssemblyPacket decode(FriendlyByteBuf buffer) {
        return new RequestAssemblyPacket(buffer.readBlockPos());
    }

    public static void encode(RequestAssemblyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.controllerPos);
    }

    public static void handle(RequestAssemblyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
        var level = player.serverLevel();
        var state = level.getBlockState(controllerPos);
        if (state.is(com.github.aeddddd.ae2enhanced.registry.ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get())) {
            if (player.getAbilities().instabuild) {
                com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure.placeMissingBlocks(level, controllerPos, player);
            } else {
                com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure.tryConsumeAndPlace(level, controllerPos, player);
            }
        }
    }

    public BlockPos controllerPos() {
        return controllerPos;
    }
}
