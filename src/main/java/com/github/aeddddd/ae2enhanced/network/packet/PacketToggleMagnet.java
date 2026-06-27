package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemOmniUpgradeCard;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import com.github.aeddddd.ae2enhanced.storage.OmniTerminalData;
import com.github.aeddddd.ae2enhanced.storage.OmniTerminalInventory;
import com.github.aeddddd.ae2enhanced.storage.OmniTerminalStorage;
import com.github.aeddddd.ae2enhanced.util.OmniTerminalFinder;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端发送 H 键按下,服务器切换磁引卡模式.
 */
public class PacketToggleMagnet implements IMessage {

    public PacketToggleMagnet() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketToggleMagnet, IMessage> {

        @Override
        public IMessage onMessage(PacketToggleMagnet message, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack terminal = OmniTerminalFinder.findOmniTerminal(player);
                if (terminal.isEmpty()) {
                    return;
                }

                java.util.UUID storageId = ItemOmniWirelessTerminal.getStorageId(terminal);
                OmniTerminalStorage storage = OmniTerminalData.get(player.world).getOrCreate(storageId);
                OmniTerminalInventory upgrades = storage.getUpgradeInventory();

                for (int i = 0; i < upgrades.getSlots(); i++) {
                    ItemStack card = upgrades.getStackInSlot(i);
                    if (card.getItem() instanceof ItemOmniUpgradeCard && card.getMetadata() == ItemOmniUpgradeCard.META_MAGNET) {
                        int mode = ItemOmniUpgradeCard.getMagnetMode(card);
                        mode = (mode + 1) % 3;
                        ItemOmniUpgradeCard.setMagnetMode(card, mode);
                        upgrades.setStackInSlot(i, card);

                        String msgKey;
                        switch (mode) {
                            case 1:
                                msgKey = "message.ae2enhanced.magnet.inventory";
                                break;
                            case 2:
                                msgKey = "message.ae2enhanced.magnet.network";
                                break;
                            default:
                                msgKey = "message.ae2enhanced.magnet.off";
                                break;
                        }
                        player.sendMessage(new TextComponentTranslation(msgKey));
                        return;
                    }
                }
            });
            return null;
        }
    }
}
