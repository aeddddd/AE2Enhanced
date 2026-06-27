package com.github.aeddddd.ae2enhanced.container;

import appeng.container.slot.SlotFakeTypeOnly;
import appeng.tile.inventory.AppEngInternalAEInventory;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEHelper;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * EMC 接口容器.
 *
 * <p>复用 3.png 纹理图集风格；顶部白名单网格使用装配枢纽（ContainerAssemblyPattern）
 * 中的明确坐标：起点 (8,25)、17 列 × 6 行，共 20 页。
 * 过滤槽仅允许存在 EMC 值的物品；空手点击已标记槽位可取消标记。</p>
 */
public class ContainerEMCInterface extends Container {

    public static final int PAGE_ROWS = 6;
    public static final int PAGE_COLS = 17;
    public static final int SLOTS_PER_PAGE = PAGE_ROWS * PAGE_COLS; // 102

    // 白名单槽位起点：与 ContainerAssemblyPattern 的 PATTERN_X/PATTERN_Y 一致
    private static final int WHITELIST_X = 8;
    private static final int WHITELIST_Y = 25;

    // 玩家背包坐标：与 ContainerAssemblyPattern 的 INV_X/INV_Y/HOTBAR_Y 一致
    private static final int INV_X = 80;
    private static final int INV_Y = 145;
    private static final int HOTBAR_Y = 203;

    private final TileEMCInterface tile;
    private int currentPage = 0;

    public ContainerEMCInterface(InventoryPlayer playerInventory, TileEMCInterface tile) {
        this.tile = tile;

        AppEngInternalAEInventory config = tile.getConfig();
        // 创建所有过滤槽，初始只显示第 0 页
        for (int i = 0; i < TileEMCInterface.WHITELIST_SIZE; i++) {
            this.addSlotToContainer(new SlotFakeEMCOnly(config, i, -1000, -1000));
        }
        refreshSlotPositions();

        // 玩家背包 9×3（居中于 3.png 中部过滤格）
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9, INV_X + j * 18, INV_Y + i * 18));
            }
        }
        // 快捷栏
        for (int i = 0; i < 9; i++) {
            addSlotToContainer(new Slot(playerInventory, i, INV_X + i * 18, HOTBAR_Y));
        }
    }

    public TileEMCInterface getTile() {
        return tile;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        if (page < 0) page = 0;
        if (page >= TileEMCInterface.WHITELIST_PAGES) page = TileEMCInterface.WHITELIST_PAGES - 1;
        if (page == this.currentPage) return;
        this.currentPage = page;
        refreshSlotPositions();
    }

    private void refreshSlotPositions() {
        int startSlot = this.currentPage * SLOTS_PER_PAGE;
        for (int i = 0; i < TileEMCInterface.WHITELIST_SIZE; i++) {
            Slot slot = this.inventorySlots.get(i);
            int relative = i - startSlot;
            if (relative >= 0 && relative < SLOTS_PER_PAGE) {
                int x = relative % PAGE_COLS;
                int y = relative / PAGE_COLS;
                slot.xPos = WHITELIST_X + x * 18;
                slot.yPos = WHITELIST_Y + y * 18;
            } else {
                slot.xPos = -1000;
                slot.yPos = -1000;
            }
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return tile.getWorld().getTileEntity(tile.getPos()) == tile
                && playerIn.getDistanceSq(tile.getPos().add(0.5, 0.5, 0.5)) <= 64.0;
    }

    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (slotId < 0 || slotId >= TileEMCInterface.WHITELIST_SIZE
                || clickTypeIn != ClickType.PICKUP) {
            return super.slotClick(slotId, dragType, clickTypeIn, player);
        }

        Slot slot = this.inventorySlots.get(slotId);
        ItemStack cursor = player.inventory.getItemStack();

        // 空手点击已标记槽位 -> 取消标记
        if (cursor.isEmpty() && slot.getHasStack()) {
            slot.putStack(ItemStack.EMPTY);
            slot.onSlotChanged();
            detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // 手中有物品且槽位为空/可替换 -> 设置标记（需有 EMC）
        if (!cursor.isEmpty()) {
            ItemStack copy = cursor.copy();
            copy.setCount(1);
            if (slot.isItemValid(copy)) {
                slot.putStack(copy);
                slot.onSlotChanged();
                detectAndSendChanges();
            }
            return ItemStack.EMPTY;
        }

        return ItemStack.EMPTY;
    }

    @Override
    @Nonnull
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer playerIn, int index) {
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();

        // 过滤槽 -> 背包
        if (index < TileEMCInterface.WHITELIST_SIZE) {
            if (!this.mergeItemStack(stack, TileEMCInterface.WHITELIST_SIZE, this.inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        }
        // 背包 -> 过滤槽：仅允许有 EMC 值的物品，放入第一个空槽
        else {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            if (!hasEmc(copy)) return ItemStack.EMPTY;
            for (int i = 0; i < TileEMCInterface.WHITELIST_SIZE; i++) {
                Slot fakeSlot = this.inventorySlots.get(i);
                if (!fakeSlot.getHasStack() && fakeSlot.isItemValid(copy)) {
                    fakeSlot.putStack(copy);
                    return ItemStack.EMPTY;
                }
            }
            return ItemStack.EMPTY;
        }

        slot.onSlotChanged();
        return stack;
    }

    private static boolean hasEmc(@Nonnull ItemStack stack) {
        return ProjectEHelper.isAvailable() && ProjectEHelper.getEmcValue(stack) > 0;
    }

    /**
     * 仅接受存在 EMC 值的假物品槽.
     */
    private static class SlotFakeEMCOnly extends SlotFakeTypeOnly {
        SlotFakeEMCOnly(AppEngInternalAEInventory inv, int idx, int x, int y) {
            super(inv, idx, x, y);
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return hasEmc(stack);
        }
    }
}
