package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 设置编码区 ghost slot 的数量(中键二级 GUI 用)
 */
public class PacketSetSlotAmount implements IMessage {

    private int invType; // 0=patternCraftingInv, 1=patternOutputInv
    private int slotIndex;
    private int amount;

    public PacketSetSlotAmount() {
    }

    public PacketSetSlotAmount(int invType, int slotIndex, int amount) {
        this.invType = invType;
        this.slotIndex = slotIndex;
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.invType = buf.readByte();
        this.slotIndex = buf.readInt();
        this.amount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.invType);
        buf.writeInt(this.slotIndex);
        buf.writeInt(this.amount);
    }

    public static class Handler implements IMessageHandler<PacketSetSlotAmount, IMessage> {

        @Override
        public IMessage onMessage(PacketSetSlotAmount message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (!(ctx.getServerHandler().player.openContainer instanceof ContainerOmniTerm)) {
                    return;
                }
                ContainerOmniTerm c = (ContainerOmniTerm) ctx.getServerHandler().player.openContainer;

                ItemStack stack;
                if (message.invType == 0) {
                    if (message.slotIndex < 0 || message.slotIndex >= c.patternCraftingInv.getSlots()) return;
                    stack = c.patternCraftingInv.getStackInSlot(message.slotIndex);
                } else if (message.invType == 1) {
                    if (message.slotIndex < 0 || message.slotIndex >= c.patternOutputInv.getSlots()) return;
                    stack = c.patternOutputInv.getStackInSlot(message.slotIndex);
                } else {
                    return;
                }

                if (!stack.isEmpty()) {
                    int newAmount = Math.max(1, message.amount);
                    stack.setCount(newAmount);
                    c.detectAndSendChanges();
                }
            });
            return null;
        }
    }
}
