package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用 Forge IItemHandler 适配器.
 */
public class ForgeItemHandlerAdapter implements TargetAdapter {

    private final TileEntity tile;
    private final EnumFacing face;
    private IItemHandler handler;

    public ForgeItemHandlerAdapter(TileEntity tile, EnumFacing face) {
        this.tile = tile;
        this.face = face;
    }

    private IItemHandler getHandler() {
        if (handler == null && tile != null && !tile.isInvalid()) {
            handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
        }
        return handler;
    }

    @Override
    @Nonnull
    public List<ItemStack> scan(boolean simulate) {
        List<ItemStack> result = new ArrayList<>();
        IItemHandler h = getHandler();
        if (h == null) return result;

        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack stack = h.getStackInSlot(i);
            if (!stack.isEmpty()) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    @Override
    @Nullable
    public ItemStack extract(@Nonnull IAEItemStack requested, boolean simulate) {
        IItemHandler h = getHandler();
        if (h == null) return ItemStack.EMPTY;

        ItemStack wanted = requested.createItemStack();
        int remaining = wanted.getCount();
        ItemStack collected = ItemStack.EMPTY;

        for (int i = 0; i < h.getSlots() && remaining > 0; i++) {
            ItemStack slotStack = h.getStackInSlot(i);
            if (slotStack.isEmpty() || !slotStack.isItemEqual(wanted) || !ItemStack.areItemStackTagsEqual(slotStack, wanted)) {
                continue;
            }
            ItemStack extracted = h.extractItem(i, remaining, simulate);
            if (extracted.isEmpty()) continue;

            if (collected.isEmpty()) {
                collected = extracted.copy();
            } else {
                collected.grow(extracted.getCount());
            }
            remaining -= extracted.getCount();
        }

        return collected.isEmpty() ? null : collected;
    }

    @Override
    public void invalidate() {
        handler = null;
    }
}
