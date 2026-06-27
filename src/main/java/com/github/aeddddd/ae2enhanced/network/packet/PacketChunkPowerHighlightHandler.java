package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.client.render.ChunkPowerHighlightRenderer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketChunkPowerHighlightHandler implements IMessageHandler<PacketChunkPowerHighlight, IMessage> {
    @Override
    public IMessage onMessage(PacketChunkPowerHighlight message, MessageContext ctx) {
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
            ChunkPowerHighlightRenderer.addHighlights(message);
        });
        return null;
    }
}
