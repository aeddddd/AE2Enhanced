package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.block.HyperdimensionalControllerBlock;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.computation.block.ComputationControllerBlock;
import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;

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
        Level level = player.serverLevel();
        BlockPos pos = controllerPos;
        boolean success = false;

        if (level.getBlockState(pos).getBlock() instanceof AssemblyControllerBlock) {
            if (level.getBlockEntity(pos) instanceof AssemblyControllerBlockEntity tile) {
                if (!tile.isFormed()) {
                    if (player.getAbilities().instabuild) {
                        AssemblyStructure.placeMissingBlocks(level, pos, player);
                        success = true;
                    } else {
                        success = AssemblyStructure.tryConsumeAndPlace(level, pos, player);
                    }
                }
            }
        } else if (level.getBlockState(pos).getBlock() instanceof HyperdimensionalControllerBlock) {
            if (level.getBlockEntity(pos) instanceof HyperdimensionalControllerBlockEntity tile) {
                if (!tile.isFormed()) {
                    if (player.getAbilities().instabuild) {
                        HyperdimensionalStructure.placeMissingBlocks(level, pos, player);
                        success = true;
                    } else {
                        success = HyperdimensionalStructure.tryConsumeAndPlace(level, pos, player);
                    }
                }
            }
        } else if (level.getBlockState(pos).getBlock() instanceof ComputationControllerBlock) {
            if (level.getBlockEntity(pos) instanceof ComputationCoreBlockEntity tile) {
                if (!tile.isFormed()) {
                    if (player.getAbilities().instabuild) {
                        SupercausalStructure.placeMissingBlocks(level, pos, player);
                        success = true;
                    } else {
                        success = SupercausalStructure.tryConsumeAndPlace(level, pos, player);
                    }
                }
            }
        }

        if (success) {
            player.closeContainer();
        }
    }

    public BlockPos controllerPos() {
        return controllerPos;
    }
}
