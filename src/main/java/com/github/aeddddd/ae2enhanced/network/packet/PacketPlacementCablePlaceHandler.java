package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
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
            EnumHand hand = EnumHand.MAIN_HAND;
            if (!isPlacementItem(stack)) {
                stack = player.getHeldItemOffhand();
                hand = EnumHand.OFF_HAND;
                if (!isPlacementItem(stack)) {
                    return;
                }
            }
            PlacementToolHelper.placeCableBetween(player, player.world,
                    message.getStart(), message.getEnd(), hand, stack);
            // 与右键两点流程保持一致：放置完成后清除起点
            new com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig(stack).setCableStart(null);
        });
        return null;
    }

    private static boolean isPlacementItem(ItemStack stack) {
        if (stack.getItem() instanceof ItemMEPlacementTool) return true;
        return stack.getItem() instanceof ItemAdvancedMEOmniTool
                && ItemAdvancedMEOmniTool.getMode(stack) == ItemAdvancedMEOmniTool.MODE_PLACEMENT;
    }
}
