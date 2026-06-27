package com.github.aeddddd.ae2enhanced.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 先进ME工具配置GUI的空Container（纯配置面板，无物品槽）.
 */
public class ContainerOmniToolConfig extends Container {

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
