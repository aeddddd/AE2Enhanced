package com.github.aeddddd.ae2enhanced.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 通用内存卡 GUI 的 Container(无实际槽位,仅作为 GUI 的桥梁).
 */
public class ContainerUniversalMemoryCard extends Container {

    private final EntityPlayer player;

    public ContainerUniversalMemoryCard(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }

    public EntityPlayer getPlayer() {
        return player;
    }
}
