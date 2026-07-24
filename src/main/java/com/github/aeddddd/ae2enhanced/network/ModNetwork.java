package com.github.aeddddd.ae2enhanced.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.AssemblyPagePacket;
import com.github.aeddddd.ae2enhanced.network.packet.CraftAmountLongPacket;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolDropMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniToolGui;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolPlacementSubMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolSilkTouch;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementCablePlace;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementSelectPreset;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementUndo;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimColorSchemePacket;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimCreateSubmitPacket;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimManagerStatePacket;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimPermissionPacket;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimRulesSubmitPacket;
import com.github.aeddddd.ae2enhanced.network.packet.RequestAssemblyPacket;

/**
 * 网络包注册中心（Forge 1.20.1 SimpleChannel）.
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

        CHANNEL.messageBuilder(CraftAmountLongPacket.class, nextId())
                .encoder(CraftAmountLongPacket::encode)
                .decoder(CraftAmountLongPacket::decode)
                .consumerMainThread(CraftAmountLongPacket::handle)
                .add();

        CHANNEL.messageBuilder(PersonalDimManagerStatePacket.class, nextId())
                .encoder(PersonalDimManagerStatePacket::encode)
                .decoder(PersonalDimManagerStatePacket::decode)
                .consumerMainThread(PersonalDimManagerStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(PersonalDimRulesSubmitPacket.class, nextId())
                .encoder(PersonalDimRulesSubmitPacket::encode)
                .decoder(PersonalDimRulesSubmitPacket::decode)
                .consumerMainThread(PersonalDimRulesSubmitPacket::handle)
                .add();

        CHANNEL.messageBuilder(PersonalDimPermissionPacket.class, nextId())
                .encoder(PersonalDimPermissionPacket::encode)
                .decoder(PersonalDimPermissionPacket::decode)
                .consumerMainThread(PersonalDimPermissionPacket::handle)
                .add();

        CHANNEL.messageBuilder(PersonalDimColorSchemePacket.class, nextId())
                .encoder(PersonalDimColorSchemePacket::encode)
                .decoder(PersonalDimColorSchemePacket::decode)
                .consumerMainThread(PersonalDimColorSchemePacket::handle)
                .add();

        CHANNEL.messageBuilder(PersonalDimCreateSubmitPacket.class, nextId())
                .encoder(PersonalDimCreateSubmitPacket::encode)
                .decoder(PersonalDimCreateSubmitPacket::decode)
                .consumerMainThread(PersonalDimCreateSubmitPacket::handle)
                .add();

        CHANNEL.messageBuilder(PacketOmniToolMode.class, nextId())
                .encoder(PacketOmniToolMode::encode)
                .decoder(PacketOmniToolMode::decode)
                .consumerMainThread(PacketOmniToolMode::handle)
                .add();

        CHANNEL.messageBuilder(PacketOmniToolSilkTouch.class, nextId())
                .encoder(PacketOmniToolSilkTouch::encode)
                .decoder(PacketOmniToolSilkTouch::decode)
                .consumerMainThread(PacketOmniToolSilkTouch::handle)
                .add();

        CHANNEL.messageBuilder(PacketOmniToolDropMode.class, nextId())
                .encoder(PacketOmniToolDropMode::encode)
                .decoder(PacketOmniToolDropMode::decode)
                .consumerMainThread(PacketOmniToolDropMode::handle)
                .add();

        CHANNEL.messageBuilder(PacketPlacementUndo.class, nextId())
                .encoder(PacketPlacementUndo::encode)
                .decoder(PacketPlacementUndo::decode)
                .consumerMainThread(PacketPlacementUndo::handle)
                .add();

        CHANNEL.messageBuilder(PacketOmniToolPlacementSubMode.class, nextId())
                .encoder(PacketOmniToolPlacementSubMode::encode)
                .decoder(PacketOmniToolPlacementSubMode::decode)
                .consumerMainThread(PacketOmniToolPlacementSubMode::handle)
                .add();

        CHANNEL.messageBuilder(PacketPlacementSelectPreset.class, nextId())
                .encoder(PacketPlacementSelectPreset::encode)
                .decoder(PacketPlacementSelectPreset::decode)
                .consumerMainThread(PacketPlacementSelectPreset::handle)
                .add();

        CHANNEL.messageBuilder(PacketPlacementCablePlace.class, nextId())
                .encoder(PacketPlacementCablePlace::encode)
                .decoder(PacketPlacementCablePlace::decode)
                .consumerMainThread(PacketPlacementCablePlace::handle)
                .add();

        CHANNEL.messageBuilder(PacketOpenOmniToolGui.class, nextId())
                .encoder(PacketOpenOmniToolGui::encode)
                .decoder(PacketOpenOmniToolGui::decode)
                .consumerMainThread(PacketOpenOmniToolGui::handle)
                .add();

        CHANNEL.messageBuilder(PacketOmniToolConfig.class, nextId())
                .encoder(PacketOmniToolConfig::encode)
                .decoder(PacketOmniToolConfig::decode)
                .consumerMainThread(PacketOmniToolConfig::handle)
                .add();
    }

    private static int nextId() {
        return packetId++;
    }
}
