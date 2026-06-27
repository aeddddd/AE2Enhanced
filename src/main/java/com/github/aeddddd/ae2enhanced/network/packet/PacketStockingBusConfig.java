package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 同步 Stocking 总线的目标数量到服务端.
 * 滚轮操作触发,每次只同步一个槽的变更.
 */
public class PacketStockingBusConfig implements IMessage {

    private int slot;
    private long newAmount;

    public PacketStockingBusConfig() {
    }

    public PacketStockingBusConfig(int slot, long newAmount) {
        this.slot = slot;
        this.newAmount = newAmount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.slot = buf.readByte();
        this.newAmount = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.slot);
        buf.writeLong(this.newAmount);
    }

    public static class Handler implements IMessageHandler<PacketStockingBusConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketStockingBusConfig message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof com.github.aeddddd.ae2enhanced.container.ContainerStockingBus) {
                    com.github.aeddddd.ae2enhanced.container.ContainerStockingBus container =
                            (com.github.aeddddd.ae2enhanced.container.ContainerStockingBus) player.openContainer;
                    PartStockingBus part = container.getPart();
                    if (part == null) return;

                    if (message.slot < 0) {
                        // 模式切换
                        PartStockingBus.StockingMode[] modes = PartStockingBus.StockingMode.values();
                        int modeIdx = (int) message.newAmount;
                        if (modeIdx >= 0 && modeIdx < modes.length) {
                            part.setMode(modes[modeIdx]);
                        }
                    } else if (message.slot >= 0 && message.slot < 9) {
                        // 数量变更
                        part.setTargetAmount(message.slot, message.newAmount);
                        container.syncTargetAmount(message.slot, message.newAmount);
                    }
                }
            });
            return null;
        }
    }
}
