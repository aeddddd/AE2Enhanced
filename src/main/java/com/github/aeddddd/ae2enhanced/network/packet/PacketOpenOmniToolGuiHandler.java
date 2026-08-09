package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketOpenOmniToolGuiHandler implements IMessageHandler<PacketOpenOmniToolGui, IMessage> {
    @Override
    public IMessage onMessage(PacketOpenOmniToolGui message, MessageContext ctx) {
        ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            int handOrdinal = message.getHandOrdinal();
            // 校验手部索引,防止恶意客户端发送越界值导致数组越界崩服
            if (handOrdinal < 0 || handOrdinal >= EnumHand.values().length) {
                return;
            }
            EnumHand hand = EnumHand.values()[handOrdinal];
            ItemStack stack = player.getHeldItem(hand);
            if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                player.openGui(AE2Enhanced.instance, GuiHandler.GUI_OMNI_TOOL_CONFIG,
                        player.world, hand.ordinal(), 0, 0);
            }
        });
        return null;
    }
}
