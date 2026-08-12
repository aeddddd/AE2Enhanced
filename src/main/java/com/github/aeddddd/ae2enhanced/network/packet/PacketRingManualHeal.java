package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.ring.RingManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端请求瞬间完全回血(消耗网络 RF,受每秒上限节流).
 */
public class PacketRingManualHeal implements IMessage {

    public PacketRingManualHeal() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketRingManualHeal, IMessage> {
        @Override
        public IMessage onMessage(PacketRingManualHeal message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> RingManager.healToFull(player));
            return null;
        }
    }
}
