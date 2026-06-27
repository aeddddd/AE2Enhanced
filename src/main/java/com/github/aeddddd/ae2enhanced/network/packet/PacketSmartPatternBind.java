package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternStorageFile;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 智能样板接口绑定请求.
 *
 * <p>客户端查询 JEI 后,将 SmartPatternData 的 NBT 发送到服务端,
 * 服务端反序列化并保存到 TileEntity 和外部存储文件.</p>
 */
public class PacketSmartPatternBind implements IMessage {

    private long pos;           // TileSmartPatternInterface 的 BlockPos
    private NBTTagCompound data; // SmartPatternData 的 NBT

    public PacketSmartPatternBind() {
    }

    public PacketSmartPatternBind(BlockPos pos, NBTTagCompound data) {
        this.pos = pos.toLong();
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = buf.readLong();
        this.data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        ByteBufUtils.writeTag(buf, data);
    }

    public BlockPos getPos() {
        return BlockPos.fromLong(pos);
    }

    public NBTTagCompound getData() {
        return data;
    }

    public static class Handler implements IMessageHandler<PacketSmartPatternBind, IMessage> {

        @Override
        public IMessage onMessage(PacketSmartPatternBind message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                World world = ctx.getServerHandler().player.world;
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(message.getPos());
                if (te instanceof TileSmartPatternInterface) {
                    TileSmartPatternInterface tile = (TileSmartPatternInterface) te;
                    SmartPatternData data = SmartPatternData.fromNBT(message.getData());
                    if (data != null) {
                        tile.setPatternData(data);
                        tile.updateRecipeDisplay();
                        tile.markDirty();
                        SmartPatternStorageFile.save(world, data);
                    }
                }
            });
            return null;
        }
    }
}
