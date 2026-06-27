package com.github.aeddddd.ae2enhanced.integration.terminal.tii.mana;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.storage.mana.ManaChannelResolver;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;

/**
 * TII Botania Mana 资源提供者.
 * <p>
 * 优先使用外部 Mana 通道(若 Botania_Applie 加载),否则回退到 AE2E 自有 Mana 通道.
 * </p>
 */
public class ManaResourceProvider implements IResourceProvider {

    private static final String NAME = "ae2enhanced:mana";
    private static final int PRIORITY = 50;

    private final IStorageChannel<?> channel;
    private final ManaPacketType packetType;
    private final ManaContainerHandler containerHandler;

    public ManaResourceProvider() {
        this.channel = ManaChannelResolver.getChannel();
        this.packetType = new ManaPacketType(this.channel);
        this.containerHandler = new ManaContainerHandler();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @SuppressWarnings("unchecked")
    @Override
    public IStorageChannel<? extends IAEStack<?>> getStorageChannel() {
        return (IStorageChannel<? extends IAEStack<?>>) channel;
    }

    @Override
    public IPacketType getPacketType() {
        return packetType;
    }

    @Override
    public IContainerHandler getContainerHandler() {
        return containerHandler;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
