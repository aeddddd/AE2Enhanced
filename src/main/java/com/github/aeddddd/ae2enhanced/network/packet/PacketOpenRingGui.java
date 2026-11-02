package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.guiapi.GuiCatalog;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端请求打开 ME 工具的指环模块配置；保留原包 ID 以兼容旧客户端。
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
                if (com.github.aeddddd.ae2enhanced.ring.RingNBT.hasRingModule(player.getHeldItemMainhand())) {
                    player.openGui(AE2Enhanced.instance, GuiCatalog.GUI_OMNI_TOOL_CONFIG, player.world, 0, 0, 0);
                } else if (com.github.aeddddd.ae2enhanced.ring.RingNBT.hasRingModule(player.getHeldItemOffhand())) {
                    player.openGui(AE2Enhanced.instance, GuiCatalog.GUI_OMNI_TOOL_CONFIG, player.world, 1, 0, 0);
                }
            });
            return null;
        }
    }
}
