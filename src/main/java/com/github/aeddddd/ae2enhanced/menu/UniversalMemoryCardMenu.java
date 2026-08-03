package com.github.aeddddd.ae2enhanced.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 通用内存卡管理 GUI 的空 Menu(纯管理面板,无物品槽).
 * 配置/选区数据由客户端从手持内存卡 NBT 读入,操作通过 {@code PacketUMCAction} 发到服务端.
 */
public class UniversalMemoryCardMenu extends AbstractContainerMenu {

    /** 打开 GUI 时内存卡所在的手(0=主手,1=副手). */
    public final int handOrdinal;

    public UniversalMemoryCardMenu(int id, Inventory inv, int handOrdinal) {
        super(ModMenus.UNIVERSAL_MEMORY_CARD.get(), id);
        this.handOrdinal = handOrdinal;
    }

    public static UniversalMemoryCardMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        return new UniversalMemoryCardMenu(id, inv, buf.readByte());
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
