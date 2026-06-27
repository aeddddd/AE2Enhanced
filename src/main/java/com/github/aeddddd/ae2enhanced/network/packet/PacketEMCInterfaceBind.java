package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端请求绑定当前玩家到 EMC 接口.
 */
public class PacketEMCInterfaceBind implements IMessage {

    private BlockPos pos;

    public PacketEMCInterfaceBind() {}

    public PacketEMCInterfaceBind(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
    }

    public static class Handler implements IMessageHandler<PacketEMCInterfaceBind, IMessage> {
        @Override
        public IMessage onMessage(PacketEMCInterfaceBind message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.getDistanceSq(message.pos) > 64.0) return;
                World world = player.world;
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof TileEMCInterface) {
                    ((TileEMCInterface) te).setOwner(player);
                    player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                            "chat.ae2enhanced.emc_interface.bound", player.getName()));
                }
            });
            return null;
        }
    }
}
