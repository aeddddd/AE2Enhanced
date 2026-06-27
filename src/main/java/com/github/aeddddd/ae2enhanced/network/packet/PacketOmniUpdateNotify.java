package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 服务端 -> 客户端：Omni Terminal 存储变化通知。
 *
 * <p>R3 架构中的增量更新机制。服务端存储发生变化时，向所有打开的终端发送此空包。
 * 客户端收到后，按需重新请求当前页数据。</p>
 *
 * <p>注意：此包不含任何数据，仅作为"有变化"的信号。具体刷新由客户端节流控制
 *（默认 200ms 冷却），避免刷新风暴。</p>
 */
public class PacketOmniUpdateNotify implements IMessage {

    public PacketOmniUpdateNotify() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // 空包，无需读取
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // 空包，无需写入
    }

    public static class Handler implements IMessageHandler<PacketOmniUpdateNotify, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniUpdateNotify message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                net.minecraft.client.gui.GuiScreen gui = net.minecraft.client.Minecraft.getMinecraft().currentScreen;
                if (gui instanceof com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm) {
                    ((com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm) gui).handleUpdateNotify();
                }
            });
            return null;
        }
    }
}
