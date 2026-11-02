package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.dimension.FloorDefinition;
import com.github.aeddddd.ae2enhanced.dimension.FloorRepaintManager;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPersonalDimensionFloorAction implements IMessage {
    private String styleId;
    private int[] colors = new int[FloorDefinition.COLOR_ROLES];
    private boolean repaint;

    public PacketPersonalDimensionFloorAction() {
    }

    public PacketPersonalDimensionFloorAction(FloorDefinition floor, boolean repaint) {
        this.styleId = floor.getFloorStyleId();
        this.colors = floor.getColorScheme();
        this.repaint = repaint;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        styleId = ByteBufUtils.readUTF8String(buf);
        for (int i = 0; i < colors.length; i++) {
            colors[i] = buf.readUnsignedByte();
        }
        repaint = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, styleId);
        for (int color : colors) {
            buf.writeByte(color);
        }
        buf.writeBoolean(repaint);
    }

    public static class Handler implements IMessageHandler<PacketPersonalDimensionFloorAction, IMessage> {
        @Override
        public IMessage onMessage(PacketPersonalDimensionFloorAction message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                FloorDefinition floor = new FloorDefinition();
                try {
                    floor.setStyle(message.styleId, message.colors);
                } catch (IllegalArgumentException exception) {
                    PersonalDimensionManager.sendInfoToPlayer(player);
                    return;
                }
                if (FloorRepaintManager.updateFloor(player, floor.getFloorStyleId(), floor.getColorScheme())
                        && message.repaint) {
                    FloorRepaintManager.startRepaint(player);
                }
                PersonalDimensionManager.sendInfoToPlayer(player);
            });
            return null;
        }
    }
}
