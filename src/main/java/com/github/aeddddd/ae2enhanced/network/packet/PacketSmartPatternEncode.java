package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 智能样板接口：请求编码空白样板.
 *
 * <p>客户端发送编码请求,服务端验证冲突并执行编码.</p>
 */
public class PacketSmartPatternEncode implements IMessage {

    private long pos;      // BlockPos.toLong()

    public PacketSmartPatternEncode() {
    }

    public PacketSmartPatternEncode(BlockPos pos) {
        this.pos = pos.toLong();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
    }

    public BlockPos getPos() {
        return BlockPos.fromLong(pos);
    }

    public static class Handler implements IMessageHandler<PacketSmartPatternEncode, IMessage> {

        @Override
        public IMessage onMessage(PacketSmartPatternEncode message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                World world = ctx.getServerHandler().player.world;
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(message.getPos());
                if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) {
                    com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface tile =
                            (com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) te;
                    tile.encodePattern(ctx.getServerHandler().player);
                }
            });
            return null;
        }
    }
}
