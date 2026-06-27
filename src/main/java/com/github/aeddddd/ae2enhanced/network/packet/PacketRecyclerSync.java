package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * S→C packet for ME Network Recycler status sync.
 */
public class PacketRecyclerSync implements IMessage {

    private BlockPos pos;
    private int targetCount;
    private long lastRecycledCount;
    private boolean active;
    private boolean powered;

    public PacketRecyclerSync() {
    }

    public PacketRecyclerSync(BlockPos pos, int targetCount, long lastRecycledCount, boolean active, boolean powered) {
        this.pos = pos;
        this.targetCount = targetCount;
        this.lastRecycledCount = lastRecycledCount;
        this.active = active;
        this.powered = powered;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.targetCount = buf.readInt();
        this.lastRecycledCount = buf.readLong();
        this.active = buf.readBoolean();
        this.powered = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(targetCount);
        buf.writeLong(lastRecycledCount);
        buf.writeBoolean(active);
        buf.writeBoolean(powered);
    }

    public int getTargetCount() {
        return targetCount;
    }

    public long getLastRecycledCount() {
        return lastRecycledCount;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPowered() {
        return powered;
    }

    public static class Handler implements IMessageHandler<PacketRecyclerSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketRecyclerSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                TileEntity te = Minecraft.getMinecraft().world.getTileEntity(message.pos);
                if (te instanceof TileMENetworkRecycler) {
                    ((TileMENetworkRecycler) te).handleSyncPacket(message);
                }
            });
            return null;
        }
    }
}
