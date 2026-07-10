package com.github.aeddddd.ae2enhanced.assembly;

import java.util.Iterator;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;

/**
 * 将装配枢纽的样板槽区域（升级槽之后）暴露为 AE2 的 {@link InternalInventory}，
 * 供 AE2 样板访问终端（Pattern Access Terminal）直接读取与操作。
 */
public class AssemblyPatternInventory implements InternalInventory {

    private final AssemblyPatternManager patternManager;
    private final int offset;

    public AssemblyPatternInventory(AssemblyPatternManager patternManager) {
        this.patternManager = patternManager;
        this.offset = AssemblyControllerBlockEntity.UPGRADE_SLOTS;
    }

    private int toHandlerSlot(int slot) {
        return offset + slot;
    }

    @Override
    public int size() {
        return patternManager.getPatternSlotCount();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int handlerSlot = toHandlerSlot(slot);
        if (handlerSlot < 0 || handlerSlot >= patternManager.getItemHandler().getSlots()) {
            return ItemStack.EMPTY;
        }
        return patternManager.getItemHandler().getStackInSlot(handlerSlot);
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        int handlerSlot = toHandlerSlot(slot);
        if (handlerSlot < 0 || handlerSlot >= patternManager.getItemHandler().getSlots()) {
            return;
        }
        // 通过 setStackInSlot 以触发 onContentsChanged 与样板缓存刷新
        patternManager.getItemHandler().setStackInSlot(handlerSlot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        int handlerSlot = toHandlerSlot(slot);
        if (handlerSlot < 0 || handlerSlot >= patternManager.getItemHandler().getSlots()) {
            return 1;
        }
        return patternManager.getItemHandler().getSlotLimit(handlerSlot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int handlerSlot = toHandlerSlot(slot);
        if (handlerSlot < 0 || handlerSlot >= patternManager.getItemHandler().getSlots()) {
            return false;
        }
        return patternManager.getItemHandler().isItemValid(handlerSlot, stack);
    }

    @Override
    public Iterator<ItemStack> iterator() {
        return new Iterator<>() {
            private int index = 0;
            private final int size = size();

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public ItemStack next() {
                return getStackInSlot(index++);
            }
        };
    }
}
