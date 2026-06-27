package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class PacketOmniToolPlacementSubMode implements IMessage {

    private boolean next; // true = 切换到下一个模式，false = 上一个

    public PacketOmniToolPlacementSubMode() {
    }

    public PacketOmniToolPlacementSubMode(boolean next) {
        this.next = next;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        next = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(next);
    }

    public boolean isNext() {
        return next;
    }
}
