package com.github.aeddddd.ae2enhanced.integration.terminal;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * 将装配中枢的样板库存按行（每行 9 槽）切片，供接口终端显示。
 *
 * <p>每一行对应一个独立的 {@link AssemblyInterfaceTracker}，这样接口终端
 * 的滚动条可以按行精确计算，避免单页显示问题。</p>
 */
public class AssemblyPatternRowWrapper implements IItemHandler {

    private final AssemblyPatternInventoryWrapper parent;
    private final int startSlot;
    private final int actualSize;

    public AssemblyPatternRowWrapper(AssemblyPatternInventoryWrapper parent, int startSlot, int actualSize) {
        this.parent = parent;
        this.startSlot = startSlot;
        this.actualSize = Math.max(0, Math.min(actualSize, 9));
    }

    @Override
    public int getSlots() {
        // 固定返回 9，保证客户端 AppEngInternalInventory 也是 9 槽，
        // 接口终端绘制时统一访问 0-8 槽，不会越界。
        return 9;
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= actualSize) {
            return ItemStack.EMPTY;
        }
        return parent.getStackInSlot(startSlot + slot);
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= actualSize) {
            return stack;
        }
        return parent.insertItem(startSlot + slot, stack, simulate);
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= actualSize) {
            return ItemStack.EMPTY;
        }
        return parent.extractItem(startSlot + slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot < 0 || slot >= actualSize) {
            return 0;
        }
        return parent.getSlotLimit(startSlot + slot);
    }
}
