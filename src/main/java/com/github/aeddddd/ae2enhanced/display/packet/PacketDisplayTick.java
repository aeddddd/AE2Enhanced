package com.github.aeddddd.ae2enhanced.display.packet;

import com.github.aeddddd.ae2enhanced.clientapi.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 趋势显示幕墙增量采样包(S→C).
 * 每秒一次,携带 8 个监控槽的最新采样值与有效位掩码.
 */
public class PacketDisplayTick implements IMessage {

    public static final int TRACKED_SLOTS = 8;

    private BlockPos pos;
    private long[] values = new long[TRACKED_SLOTS];
    private int validMask;
    private boolean powered;
    /** 采样序号(服务端单调递增),客户端据此对齐缓冲,防止重复/丢包导致的图表跳变 */
    private long sampleTotal;

    public PacketDisplayTick() {}

    public PacketDisplayTick(BlockPos pos, long[] values, int validMask, boolean powered, long sampleTotal) {
        this.pos = pos;
        System.arraycopy(values, 0, this.values, 0, Math.min(values.length, this.values.length));
        this.validMask = validMask;
        this.powered = powered;
        this.sampleTotal = sampleTotal;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readLong();
        }
        this.validMask = buf.readByte();
        this.powered = buf.readBoolean();
        this.sampleTotal = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        for (long v : values) {
            buf.writeLong(v);
        }
        buf.writeByte(validMask);
        buf.writeBoolean(powered);
        buf.writeLong(sampleTotal);
    }

    public BlockPos getPos() {
        return pos;
    }

    public long[] getValues() {
        return values;
    }

    public int getValidMask() {
        return validMask;
    }

    public boolean isPowered() {
        return powered;
    }

    public long getSampleTotal() {
        return sampleTotal;
    }

    public static class Handler implements IMessageHandler<PacketDisplayTick, IMessage> {
        @Override
        public IMessage onMessage(PacketDisplayTick message, MessageContext ctx) {
            ClientPacketHandlers.accept(ClientPacketHandlers.DISPLAY_TICK, message);
            return null;
        }
    }
}
