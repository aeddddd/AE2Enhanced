package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketOmniToolModeHandler implements IMessageHandler<PacketOmniToolMode, IMessage> {
    @Override
    public IMessage onMessage(PacketOmniToolMode message, MessageContext ctx) {
        ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            for (net.minecraft.util.EnumHand hand : net.minecraft.util.EnumHand.values()) {
                ItemStack stack = player.getHeldItem(hand);
                if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                    ItemAdvancedMEOmniTool.cycleMode(stack);
                    int newMode = ItemAdvancedMEOmniTool.getMode(stack);
                    String modeName = new TextComponentTranslation(ItemAdvancedMEOmniTool.getModeNameKey(newMode)).getFormattedText();
                    player.sendStatusMessage(new TextComponentTranslation("message.ae2enhanced.omnitool.mode_changed", modeName), true);
                    // 强制同步 NBT 到客户端，防止数据丢失
                    player.setHeldItem(hand, stack);
                    break;
                }
            }
        });
        return null;
    }
}
