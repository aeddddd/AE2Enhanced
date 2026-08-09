package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.container.ContainerEnergyStorageBus;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.nio.charset.StandardCharsets;

/**
 * 能源存储总线 GUI 动作包(分区 / 清空).
 * 对标原版存储总线的 "StorageBus.Action" 行为,但走项目自有网络通道.
 */
public class PacketEnergyStorageBusAction implements IMessage {

    public static final String ACTION_PARTITION = "Partition";
    public static final String ACTION_CLEAR = "Clear";

    private String action;

    public PacketEnergyStorageBusAction() {
    }

    public PacketEnergyStorageBusAction(String action) {
        this.action = action;
    }

    /** 动作名最大字节数,防止恶意长度前缀导致异常或超大读取 */
    private static final int MAX_ACTION_BYTES = 32;

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readByte();
        // 长度前缀为有符号 byte,负值或超限置为空动作(handler 中不匹配任何动作)
        if (len < 0 || len > MAX_ACTION_BYTES || len > buf.readableBytes()) {
            this.action = "";
            return;
        }
        this.action = buf.readCharSequence(len, StandardCharsets.UTF_8).toString();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] data = this.action.getBytes(StandardCharsets.UTF_8);
        buf.writeByte(data.length);
        buf.writeBytes(data);
    }

    public static class Handler implements IMessageHandler<PacketEnergyStorageBusAction, IMessage> {

        @Override
        public IMessage onMessage(PacketEnergyStorageBusAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerEnergyStorageBus) {
                    ContainerEnergyStorageBus container = (ContainerEnergyStorageBus) player.openContainer;
                    if (ACTION_PARTITION.equals(message.action)) {
                        container.partition();
                    } else if (ACTION_CLEAR.equals(message.action)) {
                        container.clear();
                    }
                }
            });
            return null;
        }
    }
}
