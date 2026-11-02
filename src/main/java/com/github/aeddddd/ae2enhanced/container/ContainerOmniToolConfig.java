package com.github.aeddddd.ae2enhanced.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 先进ME工具配置GUI的空Container（纯配置面板，无物品槽）.
 */
public class ContainerOmniToolConfig extends Container {
    private final int handOrdinal;
    private final net.minecraft.item.ItemStack openedStack;

    public ContainerOmniToolConfig(EntityPlayer player, int handOrdinal) {
        this.handOrdinal = handOrdinal;
        this.openedStack = handOrdinal >= 0 && handOrdinal < net.minecraft.util.EnumHand.values().length
                ? player.getHeldItem(net.minecraft.util.EnumHand.values()[handOrdinal])
                : net.minecraft.item.ItemStack.EMPTY;
    }

    public net.minecraft.item.ItemStack getToolStack(EntityPlayer player) {
        if (handOrdinal < 0 || handOrdinal >= net.minecraft.util.EnumHand.values().length) {
            return net.minecraft.item.ItemStack.EMPTY;
        }
        net.minecraft.item.ItemStack current = player.getHeldItem(
                net.minecraft.util.EnumHand.values()[handOrdinal]);
        return current == openedStack
                && current.getItem() instanceof com.github.aeddddd.ae2enhanced.omnitool.ItemAdvancedMEOmniTool
                ? current : net.minecraft.item.ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !getToolStack(player).isEmpty();
    }
}
