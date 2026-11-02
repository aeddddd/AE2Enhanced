package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.display.packet.PacketDisplayAction;
import com.github.aeddddd.ae2enhanced.network.RequestContext;
import com.github.aeddddd.ae2enhanced.tile.TileDisplayPanel;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** 显示配置 DTO 的服务端执行器。 */
public final class PacketDisplayActionHandler implements IMessageHandler<PacketDisplayAction, IMessage> {

    @Override
    public IMessage onMessage(PacketDisplayAction message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            TileDisplayPanel tile = RequestContext.displayTile(player, message.getPos());
            if (tile == null || !tile.isMasterRole()) {
                return;
            }
            switch (message.getAction()) {
                case PacketDisplayAction.ACTION_CYCLE_CHART:
                    tile.cycleChartType();
                    break;
                case PacketDisplayAction.ACTION_CYCLE_RANGE:
                    tile.cycleTimeRange();
                    break;
                case PacketDisplayAction.ACTION_CYCLE_YMODE:
                    tile.cycleYMode();
                    break;
                case PacketDisplayAction.ACTION_CYCLE_COLOR:
                    tile.cycleSlotColor(message.getArg());
                    break;
                case PacketDisplayAction.ACTION_CYCLE_COLOR_PREV:
                    tile.cycleSlotColorPrev(message.getArg());
                    break;
                case PacketDisplayAction.ACTION_TOGGLE_VISIBLE:
                    tile.toggleSlotVisible(message.getArg());
                    break;
                case PacketDisplayAction.ACTION_REQUEST_SYNC:
                    tile.syncToClient();
                    break;
                case PacketDisplayAction.ACTION_SET_CHART:
                    tile.setChartType(message.getArg());
                    break;
                case PacketDisplayAction.ACTION_SET_RANGE:
                    tile.setTimeRange(message.getArg());
                    break;
                default:
                    break;
            }
        });
        return null;
    }
}
