package com.github.aeddddd.ae2enhanced.integration.terminal;

import appeng.api.implementations.ICraftingPatternItem;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * 包装装配中枢控制器的样板库存,仅向接口终端暴露样板槽区域.
 *
 * <p>接口终端的槽位索引从 0 开始,对应控制器 {@link TileAssemblyController#UPGRADE_SLOTS}
 * 起的实际槽位.所有读/写/插入/提取操作都会做此偏移映射.</p>
 */
public class AssemblyPatternInventoryWrapper implements IItemHandler {

    private final TileAssemblyController controller;

    public AssemblyPatternInventoryWrapper(TileAssemblyController controller) {
        this.controller = controller;
    }

    @Override
    public int getSlots() {
        return controller.getPatternSlotCount();
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return controller.getItemHandler().getStackInSlot(toRealSlot(slot));
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (!isValidSlot(slot) || stack.isEmpty()) {
            return stack;
        }
        if (!(stack.getItem() instanceof ICraftingPatternItem)) {
            return stack;
        }
        return controller.getItemHandler().insertItem(toRealSlot(slot), stack, simulate);
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isValidSlot(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }
        return controller.getItemHandler().extractItem(toRealSlot(slot), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (!isValidSlot(slot)) {
            return 0;
        }
        return controller.getItemHandler().getSlotLimit(toRealSlot(slot));
    }

    /**
     * 注意：包装器的 isItemValid 与底层 ItemStackHandler 的语义一致,
     * 但接口终端还会额外校验 ICraftingPatternItem.
     */
    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < getSlots();
    }

    private int toRealSlot(int slot) {
        return slot + TileAssemblyController.UPGRADE_SLOTS;
    }
}
