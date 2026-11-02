package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 智能样板接口绑定请求.
 *
 * <p>客户端查询 JEI 后,将 SmartPatternData 的 NBT 与绑定目标信息发送到服务端,
 * 服务端反序列化并保存到 TileEntity 和外部存储文件.</p>
 */
public class PacketSmartPatternBind implements IMessage {

    private long pos;           // TileSmartPatternInterface 的 BlockPos
    private NBTTagCompound data; // SmartPatternData 的 NBT
    private long boundPos;      // 绑定目标方块的 BlockPos
    private int boundDim;       // 绑定目标方块的维度

    public PacketSmartPatternBind() {
    }

    public PacketSmartPatternBind(BlockPos pos, NBTTagCompound data, BlockPos boundPos, int boundDim) {
        this.pos = pos.toLong();
        this.data = data;
        this.boundPos = boundPos.toLong();
        this.boundDim = boundDim;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = buf.readLong();
        this.data = ByteBufUtils.readTag(buf);
        this.boundPos = buf.readLong();
        this.boundDim = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        ByteBufUtils.writeTag(buf, data);
        buf.writeLong(boundPos);
        buf.writeInt(boundDim);
    }

    public BlockPos getPos() {
        return BlockPos.fromLong(pos);
    }

    public NBTTagCompound getData() {
        return data;
    }

    public BlockPos getBoundPos() {
        return BlockPos.fromLong(boundPos);
    }

    public int getBoundDim() {
        return boundDim;
    }

    public static class Handler implements IMessageHandler<PacketSmartPatternBind, IMessage> {

        @Override
        public IMessage onMessage(PacketSmartPatternBind message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
                TileSmartPatternInterface tile = com.github.aeddddd.ae2enhanced.network.RequestContext
                        .smartPatternTile(player, message.getPos());
                if (tile != null && message.getData() != null) {
                    SmartPatternData data = SmartPatternData.fromNBT(message.getData());
                    if (data != null) {
                        tile.bindWithData(message.getBoundPos(), message.getBoundDim(), data.getTargetBlockId(), data);
                        tile.updateRecipeDisplay();
                        tile.markDirty();
                        ((com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface) player.openContainer)
                                .setExpectedRevision(tile.getPatternRevision());
                    }
                }
            });
            return null;
        }
    }
}
