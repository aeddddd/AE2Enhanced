package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 智能样板接口：在所有配方中替换物品.
 *
 * <p>将左侧槽位物品替换为右侧槽位物品,作用于全部配方的输入和输出.</p>
 */
public class PacketSmartPatternReplace implements IMessage {

    private long pos;
    private ItemStack from;
    private ItemStack to;

    public PacketSmartPatternReplace() {
    }

    public PacketSmartPatternReplace(BlockPos pos, ItemStack from, ItemStack to) {
        this.pos = pos.toLong();
        this.from = from;
        this.to = to;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = buf.readLong();
        this.from = ByteBufUtils.readItemStack(buf);
        this.to = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        ByteBufUtils.writeItemStack(buf, from);
        ByteBufUtils.writeItemStack(buf, to);
    }

    public BlockPos getPos() {
        return BlockPos.fromLong(pos);
    }

    public ItemStack getFrom() {
        return from;
    }

    public ItemStack getTo() {
        return to;
    }

    public static class Handler implements IMessageHandler<PacketSmartPatternReplace, IMessage> {

        @Override
        public IMessage onMessage(PacketSmartPatternReplace message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                World world = ctx.getServerHandler().player.world;
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(message.getPos());
                if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) {
                    com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface tile =
                            (com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) te;
                    tile.replaceInAllRecipes(message.getFrom(), message.getTo());
                }
            });
            return null;
        }
    }
}
