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
                net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
                // 距离校验,防止远程触发任意坐标的样板编码
                if (player.getDistanceSq(message.getPos()) > 64.0) return;
                World world = player.world;
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(message.getPos());
                if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) {
                    com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface tile =
                            (com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) te;
                    // 容器校验：必须正打开该智能样板接口的 GUI 才允许触发编码
                    if (!(player.openContainer instanceof com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface)
                            || ((com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface) player.openContainer).getTile() != tile) {
                        return;
                    }
                    tile.encodePattern(player);
                }
            });
            return null;
        }
    }
}
