package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 同步先进 ME 收集器的收集区域(中心偏移 + 每轴 min/max)与可视化开关到服务端.
 *
 * <p>子界面打开时原 Container 已关闭,因此携带坐标与维度定位 Tile.
 * 处理完毕后重新打开收集器主 GUI.</p>
 */
public class PacketCollectorConfig implements IMessage {

    private BlockPos pos;
    private int dim;
    private boolean apply;
    private int centerX, centerY, centerZ;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private boolean showBounds;

    public PacketCollectorConfig() {
    }

    public PacketCollectorConfig(BlockPos pos, int dim, boolean apply,
                                 int centerX, int centerY, int centerZ,
                                 int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ,
                                 boolean showBounds) {
        this.pos = pos;
        this.dim = dim;
        this.apply = apply;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.showBounds = showBounds;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.dim = buf.readInt();
        this.apply = buf.readBoolean();
        this.centerX = buf.readInt();
        this.centerY = buf.readInt();
        this.centerZ = buf.readInt();
        this.minX = buf.readInt();
        this.minY = buf.readInt();
        this.minZ = buf.readInt();
        this.maxX = buf.readInt();
        this.maxY = buf.readInt();
        this.maxZ = buf.readInt();
        this.showBounds = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
        buf.writeInt(this.dim);
        buf.writeBoolean(this.apply);
        buf.writeInt(this.centerX);
        buf.writeInt(this.centerY);
        buf.writeInt(this.centerZ);
        buf.writeInt(this.minX);
        buf.writeInt(this.minY);
        buf.writeInt(this.minZ);
        buf.writeInt(this.maxX);
        buf.writeInt(this.maxY);
        buf.writeInt(this.maxZ);
        buf.writeBoolean(this.showBounds);
    }

    public static class Handler implements IMessageHandler<PacketCollectorConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketCollectorConfig message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                World world = player.server.getWorld(message.dim);
                if (world == null) return;
                if (player.getDistanceSq(message.pos) > 64.0) return;
                TileEntity te = world.getTileEntity(message.pos);
                if (!(te instanceof TileAdvancedMECollector)) return;
                TileAdvancedMECollector tile = (TileAdvancedMECollector) te;
                if (message.apply) {
                    tile.setRegion(message.centerX, message.centerY, message.centerZ,
                            message.minX, message.minY, message.minZ,
                            message.maxX, message.maxY, message.maxZ);
                    tile.setShowBounds(message.showBounds);
                }
                // 重新打开主 GUI(子界面打开时原 Container 已被关闭)
                player.openGui(AE2Enhanced.instance, GuiHandler.GUI_ADVANCED_ME_COLLECTOR,
                        world, message.pos.getX(), message.pos.getY(), message.pos.getZ());
            });
            return null;
        }
    }
}
