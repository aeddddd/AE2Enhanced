package com.github.aeddddd.ae2enhanced.integration.terminal.tii.starlight;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;

/**
 * TII Astral Sorcery Starlight 资源提供者.
 * <p>
 * Starlight 始终使用 AE2E 自有通道,无外部通道替代.
 * </p>
 */
public class StarlightResourceProvider implements IResourceProvider {

    private static final String NAME = "ae2enhanced:starlight";
    private static final int PRIORITY = 50;

    private final IStorageChannel<?> channel;
    private final StarlightPacketType packetType;
    private final StarlightContainerHandler containerHandler;

    public StarlightResourceProvider() {
        this.channel = AEApi.instance().storage().getStorageChannel(IStarlightStorageChannel.class);
        this.packetType = new StarlightPacketType();
        this.containerHandler = new StarlightContainerHandler();
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
