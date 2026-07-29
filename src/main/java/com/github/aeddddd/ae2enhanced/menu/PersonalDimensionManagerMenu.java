package com.github.aeddddd.ae2enhanced.menu;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 个人维度管理器菜单：纯配置界面,无任何槽位.
 * 状态通过 {@code PersonalDimManagerStatePacket} 同步到客户端.
 */
public class PersonalDimensionManagerMenu extends AbstractContainerMenu {

    public final BlockPos pos;
    public final UUID owner;

    public PersonalDimensionManagerMenu(int id, Inventory inv, BlockPos pos, UUID owner) {
        super(ModMenus.PERSONAL_DIMENSION_MANAGER.get(), id);
        this.pos = pos;
        this.owner = owner;
    }

    public static PersonalDimensionManagerMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        return new PersonalDimensionManagerMenu(id, inv, buf.readBlockPos(), buf.readUUID());
    }

    @Override
    public boolean stillValid(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return PersonalDimensionManager.canManage(serverPlayer, owner);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
