package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.ring.RingLocator;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端请求打开网络链接指环配置 GUI.
 */
public class PacketOpenRingGui implements IMessage {

    public PacketOpenRingGui() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketOpenRingGui, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenRingGui message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!RingLocator.findRing(player).isEmpty()) {
                    player.openGui(AE2Enhanced.instance, GuiHandler.GUI_RING_CONFIG, player.world, 0, 0, 0);
                }
            });
            return null;
        }
    }
}
