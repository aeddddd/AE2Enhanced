package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketOmniToolDropModeHandler implements IMessageHandler<PacketOmniToolDropMode, IMessage> {
    @Override
    public IMessage onMessage(PacketOmniToolDropMode message, MessageContext ctx) {
        ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            for (net.minecraft.util.EnumHand hand : net.minecraft.util.EnumHand.values()) {
                ItemStack stack = player.getHeldItem(hand);
                if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                    ItemAdvancedMEOmniTool.cycleDropMode(stack);
                    int newMode = ItemAdvancedMEOmniTool.getDropMode(stack);
                    String modeName = new TextComponentTranslation(ItemAdvancedMEOmniTool.getDropModeNameKey(newMode)).getFormattedText();
                    player.sendStatusMessage(new TextComponentTranslation("message.ae2enhanced.omnitool.drop_changed", modeName), true);
                    player.setHeldItem(hand, stack);
                    break;
                }
            }
        });
        return null;
    }
}
