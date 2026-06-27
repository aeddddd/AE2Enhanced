package com.github.aeddddd.ae2enhanced.integration.terminal.tii.starlight;

import appeng.api.networking.security.IActionSource;
import net.minecraft.item.ItemStack;
import nyonio.terminal_interaction_integration.api.IContainerHandler;

/**
 * TII Astral Sorcery Starlight 容器处理器.
 * <p>
 * 当前 Starlight 不支持通过手持容器直接存取,因此所有方法均返回不可处理.
 * </p>
 */
public class StarlightContainerHandler implements IContainerHandler {

    @Override
    public boolean canHandle(ItemStack container) {
        return false;
    }

    @Override
    public long getStoredAmount(ItemStack container) {
        return 0;
    }

    @Override
    public long getMaxCapacity(ItemStack container) {
        return 0;
    }

    @Override
    public long extract(ItemStack container, long amount, IActionSource source) {
        return 0;
    }

    @Override
    public long inject(ItemStack container, long amount, IActionSource source) {
        return 0;
    }

    @Override
    public String getContainerDisplayName(ItemStack container) {
        return "";
    }

    @Override
    public ItemStack getEmptyContainer() {
        return ItemStack.EMPTY;
    }
}
