package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 处理撤销请求。
 */
public class PacketPlacementUndoHandler implements IMessageHandler<PacketPlacementUndo, PacketPlacementUndo> {

    @Override
    public PacketPlacementUndo onMessage(PacketPlacementUndo message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            for (EnumHand hand : EnumHand.values()) {
                ItemStack held = player.getHeldItem(hand);
                if (isPlacementItem(held)) {
                    PlacementToolHelper.undoLast(player, player.world, held);
                    return;
                }
            }
        });
        return null;
    }

    private static boolean isPlacementItem(ItemStack stack) {
        if (stack.getItem() instanceof ItemMEPlacementTool) return true;
        return stack.getItem() instanceof ItemAdvancedMEOmniTool
                && ItemAdvancedMEOmniTool.getMode(stack) == ItemAdvancedMEOmniTool.MODE_PLACEMENT;
    }
}
