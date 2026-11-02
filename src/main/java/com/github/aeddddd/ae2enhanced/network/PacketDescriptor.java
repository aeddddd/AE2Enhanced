package com.github.aeddddd.ae2enhanced.network;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** 一条与线上协议对应的注册描述；消息编解码仍由原消息类负责。 */
public final class PacketDescriptor {
    private final int discriminator;
    private final Side receivingSide;
    private final Class<? extends IMessage> packetType;
    private final Class<? extends IMessageHandler> handlerType;

    public PacketDescriptor(int discriminator, Side receivingSide, Class<? extends IMessage> packetType,
            Class<? extends IMessageHandler> handlerType) {
        if (discriminator < 0 || discriminator > 255) {
            throw new IllegalArgumentException("discriminator out of range: " + discriminator);
        }
        if (receivingSide == null || packetType == null || handlerType == null) {
            throw new NullPointerException("packet descriptor fields must not be null");
        }
        this.discriminator = discriminator;
        this.receivingSide = receivingSide;
        this.packetType = packetType;
        this.handlerType = handlerType;
    }

    public int discriminator() {
        return discriminator;
    }

    public Side receivingSide() {
        return receivingSide;
    }

    public Class<? extends IMessage> packetType() {
        return packetType;
    }

    public Class<? extends IMessageHandler> handlerType() {
        return handlerType;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void register(SimpleNetworkWrapper network) {
        ((SimpleNetworkWrapper) network).registerMessage((Class) handlerType, (Class) packetType,
                discriminator, receivingSide);
    }
}
