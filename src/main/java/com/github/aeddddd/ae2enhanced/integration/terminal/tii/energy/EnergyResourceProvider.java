package com.github.aeddddd.ae2enhanced.integration.terminal.tii.energy;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
import nyonio.terminal_interaction_integration.api.IContainerHandler;
import nyonio.terminal_interaction_integration.api.IPacketType;
import nyonio.terminal_interaction_integration.api.IResourceProvider;

/**
 * TII RF 能量资源提供者.
 * <p>
 * 优先使用外部 Flux 通道(若 Flux_Applied 加载),否则回退到 AE2E 自有能量通道.
 * 优先级设为 50,低于 Flux 自身提供者的 100,保证 Flux 存在时 Flux 提供者获胜.
 * </p>
 */
public class EnergyResourceProvider implements IResourceProvider {

    private static final String NAME = "ae2enhanced:energy";
    private static final int PRIORITY = 50;

    private final IStorageChannel<?> channel;
    private final EnergyPacketType packetType;
    private final EnergyContainerHandler containerHandler;

    public EnergyResourceProvider() {
        this.channel = EnergyChannelResolver.getChannel();
        this.packetType = new EnergyPacketType(this.channel);
        this.containerHandler = new EnergyContainerHandler();
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
