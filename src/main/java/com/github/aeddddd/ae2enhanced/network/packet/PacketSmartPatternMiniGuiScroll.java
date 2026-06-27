package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 智能样板接口：更新 MiniGUI 的滚动偏移.
 *
 * <p>客户端发送新的 miniGuiScrollOffset,服务端更新并同步到 Container.</p>
 */
public class PacketSmartPatternMiniGuiScroll implements IMessage {

    private long pos;
    private int scrollOffset;

    public PacketSmartPatternMiniGuiScroll() {
    }

    public PacketSmartPatternMiniGuiScroll(BlockPos pos, int scrollOffset) {
        this.pos = pos.toLong();
        this.scrollOffset = scrollOffset;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = buf.readLong();
        this.scrollOffset = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        buf.writeInt(scrollOffset);
    }

    public BlockPos getPos() {
        return BlockPos.fromLong(pos);
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public static class Handler implements IMessageHandler<PacketSmartPatternMiniGuiScroll, IMessage> {

        @Override
        public IMessage onMessage(PacketSmartPatternMiniGuiScroll message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                World world = ctx.getServerHandler().player.world;
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(message.getPos());
                if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) {
                    com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface tile =
                            (com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) te;
                    tile.setMiniGuiScrollOffset(message.getScrollOffset());
                    // 同步到打开的 Container
                    if (ctx.getServerHandler().player.openContainer instanceof
                            com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface) {
                        ((com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface)
                                ctx.getServerHandler().player.openContainer).setScrollOffset(message.getScrollOffset());
                    }
                }
            });
            return null;
        }
    }
}
