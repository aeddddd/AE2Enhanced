package com.github.aeddddd.ae2enhanced.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import java.util.UUID;

/**
 * 个人维度配置 GUI 的 Container（无物品槽）。
 */
public class ContainerPersonalDimensionConfig extends Container {

    private final UUID playerId;

    public ContainerPersonalDimensionConfig(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return player.getUniqueID().equals(playerId);
    }
}
