package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 客户端请求打开先进ME工具配置GUI.
 */
public class PacketOpenOmniToolGui implements IMessage {

    private int handOrdinal;

    public PacketOpenOmniToolGui() {
    }

    public PacketOpenOmniToolGui(int handOrdinal) {
        this.handOrdinal = handOrdinal;
    }

    public int getHandOrdinal() {
        return handOrdinal;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        handOrdinal = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(handOrdinal);
    }
}
