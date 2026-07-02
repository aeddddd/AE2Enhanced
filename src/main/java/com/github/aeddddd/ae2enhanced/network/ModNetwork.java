package com.github.aeddddd.ae2enhanced.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.AssemblyPagePacket;
import com.github.aeddddd.ae2enhanced.network.packet.RequestAssemblyPacket;

/**
 * 网络包注册中心（Forge 1.20.1 SimpleChannel）。
 */
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AE2Enhanced.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId = 0;

    private ModNetwork() {
    }

    public static void init() {
        CHANNEL.messageBuilder(RequestAssemblyPacket.class, nextId())
                .encoder(RequestAssemblyPacket::encode)
                .decoder(RequestAssemblyPacket::decode)
                .consumerMainThread(RequestAssemblyPacket::handle)
                .add();

        CHANNEL.messageBuilder(AssemblyPagePacket.class, nextId())
                .encoder(AssemblyPagePacket::encode)
                .decoder(AssemblyPagePacket::decode)
                .consumerMainThread(AssemblyPagePacket::handle)
                .add();
    }

    private static int nextId() {
        return packetId++;
    }
}
