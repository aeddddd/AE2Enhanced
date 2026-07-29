package com.github.aeddddd.ae2enhanced.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 先进 ME 全能工具配置 GUI 的空 Menu（纯配置面板,无物品槽）.
 * 配置数据由客户端从手持工具 NBT 读入,关闭时通过 {@code PacketOmniToolConfig} 写回.
 */
public class OmniToolConfigMenu extends AbstractContainerMenu {

    /** 打开 GUI 时工具所在的手（0=主手,1=副手）. */
    public final int handOrdinal;

    public OmniToolConfigMenu(int id, Inventory inv, int handOrdinal) {
        super(ModMenus.OMNI_TOOL_CONFIG.get(), id);
        this.handOrdinal = handOrdinal;
    }

    public static OmniToolConfigMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        return new OmniToolConfigMenu(id, inv, buf.readByte());
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
