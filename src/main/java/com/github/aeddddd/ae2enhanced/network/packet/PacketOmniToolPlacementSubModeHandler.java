package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
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
            PlacementMode current = config.getPlacementMode();
            PlacementMode nextMode = current == PlacementMode.SINGLE ? PlacementMode.BULK : PlacementMode.SINGLE;
            config.setPlacementMode(nextMode);
        });
        return null;
    }
}
