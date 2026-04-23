package com.github.aeddddd.ae2enhanced.network;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPatternPage implements IMessage {

    private BlockPos pos;
    private int targetPage;

    public PacketPatternPage() {
    }

    public PacketPatternPage(BlockPos pos, int targetPage) {
        this.pos = pos;
        this.targetPage = targetPage;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        targetPage = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(targetPage);
    }

    public static class Handler implements IMessageHandler<PacketPatternPage, IMessage> {

        @Override
        public IMessage onMessage(PacketPatternPage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                World world = player.world;
                BlockPos pos = message.pos;
                if (player.getDistanceSq(pos) > 64.0) return;

                TileEntity te = world.getTileEntity(pos);
                if (!(te instanceof TileAssemblyController)) return;

                TileAssemblyController tile = (TileAssemblyController) te;
                int page = message.targetPage;
                int maxPage = tile.getPatternPages() - 1;
                if (page < 0) page = 0;
                if (page > maxPage) page = maxPage;
                int guiId = GuiHandler.encodePatternId(page);
                player.openGui(AE2Enhanced.instance, guiId, world, pos.getX(), pos.getY(), pos.getZ());
            });
            return null;
        }
    }
}
