package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.crafting.AssemblyHubUpgradeRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerAssemblyFormed extends Container {

    private static final int UPGRADE_X = 8;
    private static final int UPGRADE_Y = 8;
    private static final int INV_X = 8;
    private static final int INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final TileAssemblyController tile;

    public ContainerAssemblyFormed(IInventory playerInv, TileAssemblyController tile) {
        this.tile = tile;
        IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);

        // 升级槽：2行×3列,槽位 0~5,每种升级卡对应固定槽位
        for (int row = 0; row < 2; ++row) {
            for (int col = 0; col < 3; ++col) {
                final int index = row * 3 + col;
                this.addSlotToContainer(new SlotItemHandler(handler, index,
                    UPGRADE_X + col * 18, UPGRADE_Y + row * 18) {
                    @Override
                    public boolean isItemValid(ItemStack stack) {
                        // 原生升级卡：metadata 与槽位一一对应
                        if (stack.getItem() instanceof ItemUpgradeCard && stack.getMetadata() == index) {
                            return true;
                        }
                        // 注册表中的自定义升级卡
                        AssemblyHubUpgradeRegistry.UpgradeDefinition def = AssemblyHubUpgradeRegistry.findFor(stack);
                        if (def != null) {
                            if (index == 0 && def.type == AssemblyHubUpgradeRegistry.UpgradeType.PARALLEL) return true;
                            if (index == 1 && def.type == AssemblyHubUpgradeRegistry.UpgradeType.SPEED) return true;
                        }
                        return false;
                    }

                    @Override
                    public int getItemStackLimit(ItemStack stack) {
                        // 注册表自定义堆叠上限
                        int custom = AssemblyHubUpgradeRegistry.getCustomMaxStack(stack);
                        if (custom > 0) return custom;
                        return ItemUpgradeCard.getMaxStackForMeta(index);
                    }

                    @Override
                    public int getSlotStackLimit() {
                        // 优先以当前槽位中已有物品决定上限
                        ItemStack current = handler.getStackInSlot(index);
                        int custom = AssemblyHubUpgradeRegistry.getCustomMaxStack(current);
                        if (custom > 0) return custom;
                        return ItemUpgradeCard.getMaxStackForMeta(index);
                    }

                    @Override
                    public boolean canTakeStack(EntityPlayer playerIn) {
                        // 扩容升级：如果扩展页面留有样板,禁止取出
                        if (index == ItemUpgradeCard.META_CAPACITY) {
                            ItemStack stack = handler.getStackInSlot(index);
                            int newCount = Math.max(0, stack.getCount() - 1);
                            return tile.canReduceCapacity(newCount);
                        }
                        return super.canTakeStack(playerIn);
                    }
                });
            }
        }

        // 玩家背包 3行×9列
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInv, col + row * 9 + 9,
                    INV_X + col * 18, INV_Y + row * 18));
            }
        }

        // 玩家快捷栏
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInv, col,
                INV_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return tile.getWorld().getTileEntity(tile.getPos()) == tile
                && playerIn.getDistanceSq(tile.getPos()) <= 64.0;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();

            int upgradeStart = 0;
            int upgradeEnd = TileAssemblyController.UPGRADE_SLOTS;
            int playerStart = upgradeEnd;
            int playerEnd = playerStart + 36;

            if (index < upgradeEnd) {
                // 从升级槽移到玩家背包
                if (!this.mergeItemStack(itemstack1, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从玩家背包移到对应升级槽(按 metadata 一一对应)
                if (itemstack1.getItem() instanceof ItemUpgradeCard) {
                    int meta = itemstack1.getMetadata();
                    if (meta >= 0 && meta < TileAssemblyController.UPGRADE_SLOTS) {
                        if (!this.mergeItemStack(itemstack1, meta, meta + 1, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }
        return itemstack;
    }

    public TileAssemblyController getTile() {
        return tile;
    }
}
