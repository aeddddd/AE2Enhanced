package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketRequestAssembly implements IMessage {

    private BlockPos pos;

    public PacketRequestAssembly() {
    }

    public PacketRequestAssembly(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
    }

    public static class Handler implements IMessageHandler<PacketRequestAssembly, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestAssembly message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                World world = player.world;
                BlockPos pos = message.pos;

                if (player.getDistanceSq(pos) > 64.0) return;

                TileEntity te = world.getTileEntity(pos);
                boolean success = false;

                if (world.getBlockState(pos).getBlock() == BlockRegistry.ASSEMBLY_CONTROLLER
                        && te instanceof TileAssemblyController) {
                    TileAssemblyController tile = (TileAssemblyController) te;
                    if (tile.isFormed()) return;
                    if (player.isCreative()) {
                        AssemblyStructure.placeMissingBlocks(world, pos, player);
                        success = true;
                    } else {
                        success = AssemblyStructure.tryConsumeAndPlace(world, pos, player);
                    }
                } else if (world.getBlockState(pos).getBlock() == BlockRegistry.HYPERDIMENSIONAL_CONTROLLER
                        && te instanceof TileHyperdimensionalController) {
                    TileHyperdimensionalController tile = (TileHyperdimensionalController) te;
                    if (tile.isFormed()) return;
                    if (player.isCreative()) {
                        HyperdimensionalStructure.placeMissingBlocks(world, pos, player);
                        success = true;
                    } else {
                        success = HyperdimensionalStructure.tryConsumeAndPlace(world, pos, player);
                    }
                } else if (world.getBlockState(pos).getBlock() == BlockRegistry.COMPUTATION_CORE
                        && te instanceof TileComputationCore) {
                    TileComputationCore tile = (TileComputationCore) te;
                    if (tile.isFormed()) return;
                    if (player.isCreative()) {
                        SupercausalStructure.placeMissingBlocks(world, pos, player);
                        success = true;
                    } else {
                        success = SupercausalStructure.tryConsumeAndPlace(world, pos, player);
                    }
                }

                if (success) {
                    player.closeScreen();
                }
            });
            return null;
        }
    }
}
