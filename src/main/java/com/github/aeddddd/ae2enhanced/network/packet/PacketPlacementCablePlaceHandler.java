package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPlacementCablePlaceHandler implements IMessageHandler<PacketPlacementCablePlace, IMessage> {

    @Override
    public IMessage onMessage(PacketPlacementCablePlace message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            ItemStack stack = player.getHeldItemMainhand();
            if (!(stack.getItem() instanceof ItemMEPlacementTool)) {
                stack = player.getHeldItemOffhand();
                if (!(stack.getItem() instanceof ItemMEPlacementTool)) {
                    return;
                }
            }
            PlacementToolHelper.placeCableBetween(player, player.world,
                    message.getStart(), message.getEnd(), EnumHand.MAIN_HAND, stack);
        });
        return null;
    }
}
