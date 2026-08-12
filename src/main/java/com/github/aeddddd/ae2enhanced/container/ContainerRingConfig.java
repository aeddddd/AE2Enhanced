package com.github.aeddddd.ae2enhanced.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 网络链接指环配置 GUI 的空 Container（纯配置面板，无物品槽）.
 */
public class ContainerRingConfig extends Container {

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
