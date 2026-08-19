package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketOmniToolPlacementSubModeHandler implements IMessageHandler<PacketOmniToolPlacementSubMode, IMessage> {

    @Override
    public IMessage onMessage(PacketOmniToolPlacementSubMode message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            ItemStack stack = player.getHeldItemMainhand();
            if (!(stack.getItem() instanceof ItemAdvancedMEOmniTool)
                    || ItemAdvancedMEOmniTool.getMode(stack) != ItemAdvancedMEOmniTool.MODE_PLACEMENT) {
                return;
            }
            PlacementConfig config = new PlacementConfig(stack);
            PlacementMode[] values = PlacementMode.values();
            int idx = config.getPlacementMode().ordinal() + (message.isNext() ? 1 : -1);
            PlacementMode nextMode = values[((idx % values.length) + values.length) % values.length];
            config.setPlacementMode(nextMode);

            String modeName = new TextComponentTranslation(
                    "gui.ae2enhanced.placement.mode." + nextMode.name().toLowerCase()).getFormattedText();
            player.sendStatusMessage(new TextComponentTranslation(
                    "message.ae2enhanced.placement.mode_changed", modeName), true);
            // 强制同步 NBT 到客户端，防止数据丢失
            player.setHeldItem(EnumHand.MAIN_HAND, stack);
        });
        return null;
    }
}
