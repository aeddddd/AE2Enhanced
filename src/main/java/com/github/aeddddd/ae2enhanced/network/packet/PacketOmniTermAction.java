package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * OmniTerminal 按钮操作网络包
 */
public class PacketOmniTermAction implements IMessage {

    private String action;
    private String value;

    public PacketOmniTermAction() {
    }

    public PacketOmniTermAction(String action, String value) {
        this.action = action;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = ByteBufUtils.readUTF8String(buf);
        this.value = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.action);
        ByteBufUtils.writeUTF8String(buf, this.value);
    }

    public static class Handler implements IMessageHandler<PacketOmniTermAction, IMessage> {

        @Override
        public IMessage onMessage(PacketOmniTermAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof ContainerOmniTerm)) return;
                ContainerOmniTerm c = (ContainerOmniTerm) player.openContainer;
                switch (message.action) {
                    case "CraftMode":
                        c.setPatternCraftMode("1".equals(message.value));
                        break;
                    case "Encode":
                        if ("2".equals(message.value)) {
                            c.encodeAndMoveToInventory();
                        } else {
                            c.encode();
                        }
                        break;
                    case "ClearCrafting":
                        c.clearCrafting();
                        break;
                    case "ClearPattern":
                        c.clearPattern();
                        break;
                    case "MultiplyByTwo":
                        c.multiply(2);
                        break;
                    case "MultiplyByThree":
                        c.multiply(3);
                        break;
                    case "DivideByTwo":
                        c.divide(2);
                        break;
                    case "DivideByThree":
                        c.divide(3);
                        break;
                    case "IncreaseByOne":
                        c.increase(1);
                        break;
                    case "DecreaseByOne":
                        c.decrease(1);
                        break;
                    case "Substitute":
                        c.setSubstitute("1".equals(message.value));
                        break;
                    case "Scroll":
                        try {
                            int offset = Integer.parseInt(message.value);
                            c.setRCSlot(offset);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                        break;
                }
            });
            return null;
        }
    }
}
