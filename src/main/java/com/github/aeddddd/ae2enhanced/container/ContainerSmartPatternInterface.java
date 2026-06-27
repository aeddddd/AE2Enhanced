package com.github.aeddddd.ae2enhanced.container;

import appeng.container.slot.SlotFake;
import com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 智能样板接口的 Container.
 *
 * <p>槽位布局(v4 - 81 输入 + 滚动条)：</p>
 * <ul>
 *   <li>0~44:   配方显示槽位 (9列 x 5行, SlotFake)</li>
 *   <li>45:     空白样板输入槽 (116, 20)</li>
 *   <li>46:     编码样板输出槽 (152, 20)</li>
 *   <li>47~82:  玩家背包 (3行, Y=141/159/177)</li>
 *   <li>83~163: MiniGUI 输入槽 (9组 x 3x3, SlotFake, 仅当前组可见)</li>
 *   <li>164~172: MiniGUI 输出槽 (3x3, SlotFake, 始终可见)</li>
 *   <li>173~174: 替换槽位 (176/212, 162, SlotItemHandler)</li>
 * </ul>
 */
public class ContainerSmartPatternInterface extends Container {

    private static final int[] RECIPE_COL_X = {8, 26, 44, 62, 80, 98, 116, 134, 152};
    private static final int[] RECIPE_ROW_Y = {36, 54, 72, 90, 108};

    private static final int[] MINIGUI_COL_X = {178, 196, 214};
    private static final int[] MINIGUI_INPUT_ROW_Y = {19, 37, 55};
    private static final int[] MINIGUI_OUTPUT_ROW_Y = {104, 122, 140};

    public static final int SLOT_RECIPE_START = 0;
    public static final int SLOT_RECIPE_COUNT = 45;
    public static final int SLOT_BLANK_INPUT = 45;
    public static final int SLOT_ENCODED_OUTPUT = 46;
    public static final int SLOT_PLAYER_START = 47;
    public static final int SLOT_MINIGUI_INPUT_START = 83;
    public static final int SLOT_MINIGUI_INPUT_COUNT = 81;
    public static final int SLOT_MINIGUI_OUTPUT_START = 164;
    public static final int SLOT_MINIGUI_OUTPUT_COUNT = 9;
    public static final int SLOT_REPLACE_LEFT = 173;
    public static final int SLOT_REPLACE_RIGHT = 174;

    private final TileSmartPatternInterface tile;
    private final Slot[][] miniGuiInputSlots = new Slot[9][9];
    private final Slot[] miniGuiOutputSlots = new Slot[9];

    public ContainerSmartPatternInterface(InventoryPlayer playerInv, TileSmartPatternInterface tile) {
        this.tile = tile;

        // 配方显示槽位 (45个 SlotFake)
        int slotIndex = 0;
        for (int row = 0; row < RECIPE_ROW_Y.length; row++) {
            for (int col = 0; col < RECIPE_COL_X.length; col++) {
                this.addSlotToContainer(new SlotFake(
                    tile.getRecipeDisplayInventory(), slotIndex,
                    RECIPE_COL_X[col], RECIPE_ROW_Y[row]
                ));
                slotIndex++;
            }
        }

        // 空白样板输入槽
        this.addSlotToContainer(new SlotItemHandler(tile.getInventory(), 0, 116, 20) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() instanceof ItemSmartBlankPattern;
            }
        });

        // 编码样板输出槽
        // 允许放入已编码的智能样板以触发配方数据重载
        this.addSlotToContainer(new SlotItemHandler(tile.getInventory(), 1, 152, 20) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() instanceof ItemSmartPattern;
            }
        });

        // 玩家背包 (3行)
        for (int row = 0; row < 3; row++) {
            int y = 141 + row * 18;
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, y));
            }
        }
        // 玩家快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 199));
        }

        // MiniGUI 输入槽 (9组 x 3x3 = 81个 SlotFake)
        for (int g = 0; g < 9; g++) {
            for (int s = 0; s < 9; s++) {
                int row = s / 3;
                int col = s % 3;
                int idx = g * 9 + s;
                int x = MINIGUI_COL_X[col];
                int y = MINIGUI_INPUT_ROW_Y[row];
                Slot slot = new SlotFake(tile.getMiniGuiInventory(), idx, x, y);
                this.miniGuiInputSlots[g][s] = slot;
                this.addSlotToContainer(slot);
            }
        }

        // MiniGUI 输出槽 (3x3 = 9个 SlotFake)
        for (int s = 0; s < 9; s++) {
            int row = s / 3;
            int col = s % 3;
            int idx = 81 + s;
            int x = MINIGUI_COL_X[col];
            int y = MINIGUI_OUTPUT_ROW_Y[row];
            Slot slot = new SlotFake(tile.getMiniGuiInventory(), idx, x, y);
            this.miniGuiOutputSlots[s] = slot;
            this.addSlotToContainer(slot);
        }

        // 底部替换槽位 (2个 SlotItemHandler)
        this.addSlotToContainer(new SlotItemHandler(tile.getReplaceInventory(), 0, 176, 162));
        this.addSlotToContainer(new SlotItemHandler(tile.getReplaceInventory(), 1, 212, 162));

        // 初始化可见性
        setScrollOffset(tile.getMiniGuiScrollOffset());
    }

    /**
     * 设置 MiniGUI 滚动偏移,控制哪一组输入/输出可见.
     */
    public void setScrollOffset(int offset) {
        offset = Math.max(0, Math.min(8, offset));
        for (int g = 0; g < 9; g++) {
            boolean visible = (g == offset);
            for (int s = 0; s < 9; s++) {
                Slot slot = this.miniGuiInputSlots[g][s];
                slot.xPos = visible ? MINIGUI_COL_X[s % 3] : -9000;
            }
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !tile.isInvalid() && player.getDistanceSq(tile.getPos()) <= 64.0;
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (slotId >= SLOT_MINIGUI_INPUT_START && slotId < SLOT_MINIGUI_INPUT_START + SLOT_MINIGUI_INPUT_COUNT) {
            // MiniGUI 输入槽位：未锁定时禁止编辑
            if (tile.getLockedRecipeIndex() < 0) {
                return player.inventory.getItemStack();
            }
            // 覆盖 SlotFake 的默认交互,确保能正常编辑
            if (clickTypeIn == ClickType.PICKUP) {
                Slot slot = this.inventorySlots.get(slotId);
                ItemStack held = player.inventory.getItemStack();
                if (held.isEmpty()) {
                    slot.putStack(ItemStack.EMPTY);
                } else {
                    ItemStack copy = held.copy();
                    slot.putStack(copy);
                }
                return player.inventory.getItemStack();
            }
        }
        if (slotId >= SLOT_MINIGUI_OUTPUT_START && slotId < SLOT_MINIGUI_OUTPUT_START + SLOT_MINIGUI_OUTPUT_COUNT) {
            // MiniGUI 输出槽位：未锁定时禁止编辑
            if (tile.getLockedRecipeIndex() < 0) {
                return player.inventory.getItemStack();
            }
            if (clickTypeIn == ClickType.PICKUP) {
                Slot slot = this.inventorySlots.get(slotId);
                ItemStack held = player.inventory.getItemStack();
                if (held.isEmpty()) {
                    slot.putStack(ItemStack.EMPTY);
                } else {
                    ItemStack copy = held.copy();
                    slot.putStack(copy);
                }
                return player.inventory.getItemStack();
            }
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stackInSlot = slot.getStack();
            itemstack = stackInSlot.copy();

            if (index == SLOT_BLANK_INPUT || index == SLOT_ENCODED_OUTPUT
                    || index == SLOT_REPLACE_LEFT || index == SLOT_REPLACE_RIGHT) {
                // 从 TileEntity 槽位移到玩家背包
                if (!this.mergeItemStack(stackInSlot, SLOT_PLAYER_START, SLOT_PLAYER_START + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= SLOT_PLAYER_START) {
                // 从玩家背包移到空白样板输入槽
                if (!this.mergeItemStack(stackInSlot, SLOT_BLANK_INPUT, SLOT_BLANK_INPUT + 1, false)) {
                    // 空白槽失败时，如果是已编码智能样板，尝试放入编码输出槽以触发重载
                    if (stackInSlot.getItem() instanceof ItemSmartPattern
                            && this.mergeItemStack(stackInSlot, SLOT_ENCODED_OUTPUT, SLOT_ENCODED_OUTPUT + 1, false)) {
                        // 已成功移入编码输出槽
                    } else {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }
        return itemstack;
    }

    public TileSmartPatternInterface getTile() {
        return tile;
    }
}
