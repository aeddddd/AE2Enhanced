package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 智能样板接口：更新配方显示的滚动偏移.
 *
 * <p>客户端发送新的 scrollOffset,服务端更新 recipeDisplayInventory 的内容.</p>
 */
public class PacketSmartPatternScroll implements IMessage {

    private long pos;      // BlockPos.toLong()
    private int scrollOffset;

    public PacketSmartPatternScroll() {
    }

    public PacketSmartPatternScroll(BlockPos pos, int scrollOffset) {
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

    public static class Handler implements IMessageHandler<PacketSmartPatternScroll, IMessage> {

        @Override
        public IMessage onMessage(PacketSmartPatternScroll message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                World world = ctx.getServerHandler().player.world;
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(message.getPos());
                if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) {
                    com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface tile =
                            (com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) te;
                    tile.setScrollOffset(message.getScrollOffset());
                }
            });
            return null;
        }
    }
}
