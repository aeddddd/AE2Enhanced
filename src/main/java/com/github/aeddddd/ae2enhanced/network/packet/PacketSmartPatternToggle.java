package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 智能样板接口：切换指定配方的禁用/启用状态.
 *
 * <p>客户端发送 recipeIndex,服务端根据当前 scrollOffset 计算实际配方索引并切换.</p>
 */
public class PacketSmartPatternToggle implements IMessage {

    private long pos;      // BlockPos.toLong()
    private int recipeIndex;

    public PacketSmartPatternToggle() {
    }

    public PacketSmartPatternToggle(BlockPos pos, int recipeIndex) {
        this.pos = pos.toLong();
        this.recipeIndex = recipeIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = buf.readLong();
        this.recipeIndex = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        buf.writeInt(recipeIndex);
    }

    public BlockPos getPos() {
        return BlockPos.fromLong(pos);
    }

    public int getRecipeIndex() {
        return recipeIndex;
    }

    public static class Handler implements IMessageHandler<PacketSmartPatternToggle, IMessage> {

        @Override
        public IMessage onMessage(PacketSmartPatternToggle message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface tile =
                        com.github.aeddddd.ae2enhanced.network.RequestContext.smartPatternTile(
                                ctx.getServerHandler().player, message.getPos());
                if (tile != null) {
                    com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface container =
                            (com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface)
                                    ctx.getServerHandler().player.openContainer;
                    tile.toggleRecipe(message.getRecipeIndex(), container.getExpectedRevision());
                    container.setExpectedRevision(tile.getPatternRevision());
                }
            });
            return null;
        }
    }
}
