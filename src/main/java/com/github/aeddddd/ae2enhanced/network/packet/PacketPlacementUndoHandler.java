package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
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
            ItemStack held = player.getHeldItemMainhand();
            if (held.getItem() instanceof ItemMEPlacementTool) {
                PlacementToolHelper.undoLast(player, player.world, held);
            }
        });
        return null;
    }
}
