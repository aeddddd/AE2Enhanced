package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.clientapi.ClientPacketHandlers;
import com.github.aeddddd.ae2enhanced.dimension.FloorDefinition;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPersonalDimensionFloorSync implements IMessage {
    private FloorDefinition floor;
    private int repaintPending;

    public PacketPersonalDimensionFloorSync() {
    }

    public PacketPersonalDimensionFloorSync(FloorDefinition floor, int repaintPending) {
        this.floor = floor != null ? floor.copy() : new FloorDefinition();
        this.repaintPending = repaintPending;
    }

    public FloorDefinition getFloor() {
        return floor.copy();
    }

    public int getRepaintPending() {
        return repaintPending;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        String style = ByteBufUtils.readUTF8String(buf);
        int[] colors = new int[FloorDefinition.COLOR_ROLES];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = buf.readUnsignedByte();
        }
        floor = new FloorDefinition();
        floor.setStyle(style, colors);
        repaintPending = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, floor.getFloorStyleId());
        int[] colors = floor.getColorScheme();
        for (int color : colors) {
            buf.writeByte(color);
        }
        buf.writeInt(repaintPending);
    }

    public static class Handler implements IMessageHandler<PacketPersonalDimensionFloorSync, IMessage> {
        @Override
        public IMessage onMessage(PacketPersonalDimensionFloorSync message, MessageContext ctx) {
            ClientPacketHandlers.accept(ClientPacketHandlers.DIMENSION_FLOOR, message);
            return null;
        }
    }
}
