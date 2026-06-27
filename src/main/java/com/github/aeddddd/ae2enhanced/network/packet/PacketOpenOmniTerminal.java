package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.util.OmniTerminalFinder;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端按键打开 Omni Terminal 的请求包.
 * 服务端收到后,在玩家物品栏和 Baubles 饰品槽中查找 Omni Terminal 并打开 GUI.
 */
public class PacketOpenOmniTerminal implements IMessage {

    public PacketOpenOmniTerminal() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketOpenOmniTerminal, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenOmniTerminal message, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack target = OmniTerminalFinder.findOmniTerminal(player);
                if (target.isEmpty()) {
                    return;
                }
                int slot = OmniTerminalFinder.findSlotIndex(player, target);
                boolean isBauble = OmniTerminalFinder.isInBaublesSlot(player, target);
                player.openGui(AE2Enhanced.instance, GuiHandler.GUI_OMNI_TERMINAL, player.world, slot, isBauble ? 1 : 0, 0);
            });
            return null;
        }
    }
}
