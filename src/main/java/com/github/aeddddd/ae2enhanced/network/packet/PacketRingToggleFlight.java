package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.ring.RingLocator;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端请求切换指环飞行开关.
 */
public class PacketRingToggleFlight implements IMessage {

    public PacketRingToggleFlight() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketRingToggleFlight, IMessage> {
        @Override
        public IMessage onMessage(PacketRingToggleFlight message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack ring = RingLocator.findRing(player);
                if (ring.isEmpty() || !RingNBT.tierAtLeast(ring, 1)) return;
                boolean now = !RingNBT.isFlightEnabled(ring);
                RingNBT.setBool(ring, RingNBT.FLIGHT, now);
                player.sendStatusMessage(new TextComponentTranslation(
                        now ? "message.ae2enhanced.ring.flight_on" : "message.ae2enhanced.ring.flight_off"), true);
            });
            return null;
        }
    }
}
