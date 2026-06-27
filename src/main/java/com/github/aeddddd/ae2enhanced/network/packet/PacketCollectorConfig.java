package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.container.ContainerAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 同步先进 ME 收集器的范围到服务端.
 */
public class PacketCollectorConfig implements IMessage {

    private int newRange;

    public PacketCollectorConfig() {
    }

    public PacketCollectorConfig(int newRange) {
        this.newRange = newRange;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.newRange = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.newRange);
    }

    public static class Handler implements IMessageHandler<PacketCollectorConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketCollectorConfig message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerAdvancedMECollector) {
                    ContainerAdvancedMECollector container = (ContainerAdvancedMECollector) player.openContainer;
                    TileAdvancedMECollector tile = container.getTile();
                    if (tile != null) {
                        tile.setRange(message.newRange);
                    }
                }
            });
            return null;
        }
    }
}
