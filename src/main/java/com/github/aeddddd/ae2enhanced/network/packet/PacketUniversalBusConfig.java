package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.part.PartUniversalExportBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalImportBus;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * E1a：同步通用总线的调度模式到服务端.
 */
public class PacketUniversalBusConfig implements IMessage {

    private int modeOrdinal;

    public PacketUniversalBusConfig() {
    }

    public PacketUniversalBusConfig(int modeOrdinal) {
        this.modeOrdinal = modeOrdinal;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.modeOrdinal = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.modeOrdinal);
    }

    public static class Handler implements IMessageHandler<PacketUniversalBusConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketUniversalBusConfig message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof com.github.aeddddd.ae2enhanced.container.ContainerUniversalImportBus) {
                    com.github.aeddddd.ae2enhanced.container.ContainerUniversalImportBus container =
                            (com.github.aeddddd.ae2enhanced.container.ContainerUniversalImportBus) player.openContainer;
                    PartUniversalImportBus part = container.getPart();
                    if (part != null) {
                        PartUniversalImportBus.BusMode[] modes = PartUniversalImportBus.BusMode.values();
                        if (message.modeOrdinal >= 0 && message.modeOrdinal < modes.length) {
                            part.setBusMode(modes[message.modeOrdinal]);
                        }
                    }
                } else if (player.openContainer instanceof com.github.aeddddd.ae2enhanced.container.ContainerUniversalExportBus) {
                    com.github.aeddddd.ae2enhanced.container.ContainerUniversalExportBus container =
                            (com.github.aeddddd.ae2enhanced.container.ContainerUniversalExportBus) player.openContainer;
                    PartUniversalExportBus part = container.getPart();
                    if (part != null) {
                        PartUniversalExportBus.BusMode[] modes = PartUniversalExportBus.BusMode.values();
                        if (message.modeOrdinal >= 0 && message.modeOrdinal < modes.length) {
                            part.setBusMode(modes[message.modeOrdinal]);
                        }
                    }
                }
            });
            return null;
        }
    }
}
