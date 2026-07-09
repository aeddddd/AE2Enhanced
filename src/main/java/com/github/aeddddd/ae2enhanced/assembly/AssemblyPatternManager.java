package com.github.aeddddd.ae2enhanced.assembly;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModItems;

/**
 * 负责装配枢纽的样板槽、分页管理与可用样板缓存。
 */
public class AssemblyPatternManager {

    private final AssemblyControllerBlockEntity controller;
    private final AssemblyUpgradeManager upgradeManager;
    private final PatternItemHandler itemHandler;

    private boolean patternsDirty = false;
    private int patternRefreshTicks = 0;

    public AssemblyPatternManager(AssemblyControllerBlockEntity controller, AssemblyUpgradeManager upgradeManager) {
        this.controller = controller;
        this.upgradeManager = upgradeManager;
        this.itemHandler = new PatternItemHandler(AssemblyControllerBlockEntity.TOTAL_SLOTS_BASE);
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    public int getPatternSlotCount() {
        return upgradeManager.getPatternPages() * AssemblyControllerBlockEntity.PATTERN_SLOTS_PER_PAGE;
    }

    public boolean isPatternsDirty() {
        return patternsDirty;
    }

    public void markPatternsDirty() {
        this.patternsDirty = true;
    }

    /**
     * 每 tick 刷新样板缓存状态，必要时通知控制器刷新接口样板列表。
     */
    public void tickRefresh() {
        if (patternsDirty) {
            patternsDirty = false;
            patternRefreshTicks = 1;
        }
        if (patternRefreshTicks > 0) {
            if (--patternRefreshTicks == 0) {
                controller.refreshInterfaceServices();
            }
        }
    }

    /**
     * 确保当前物品槽容量不低于目标页数所需。
     */
    public void ensurePatternCapacity() {
        int targetSize = AssemblyControllerBlockEntity.UPGRADE_SLOTS
                + upgradeManager.getPatternPages() * AssemblyControllerBlockEntity.PATTERN_SLOTS_PER_PAGE;
        if (itemHandler.getSlots() < targetSize) {
            itemHandler.setCapacity(targetSize);
        }
    }

    private boolean canReduceCapacity(int newCapacityCount) {
        int oldPages = upgradeManager.getPatternPages();
        int newPages = AssemblyControllerBlockEntity.PATTERN_PAGES_BASE
                + newCapacityCount * AssemblyControllerBlockEntity.PATTERN_PAGES_PER_CAPACITY;
        newPages = Math.min(newPages, AssemblyControllerBlockEntity.PATTERN_PAGES_MAX);
        if (newPages >= oldPages) {
            return true;
        }
        int startSlot = AssemblyControllerBlockEntity.UPGRADE_SLOTS + newPages * AssemblyControllerBlockEntity.PATTERN_SLOTS_PER_PAGE;
        int endSlot = AssemblyControllerBlockEntity.UPGRADE_SLOTS + oldPages * AssemblyControllerBlockEntity.PATTERN_SLOTS_PER_PAGE;
        for (int i = startSlot; i < endSlot && i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public List<IPatternDetails> getAvailablePatterns() {
        List<IPatternDetails> result = new ArrayList<>();
        Level level = controller.getLevel();
        if (level == null || !controller.isFormed()) {
            return result;
        }
        int patternSlots = getPatternSlotCount();
        for (int i = AssemblyControllerBlockEntity.UPGRADE_SLOTS;
             i < AssemblyControllerBlockEntity.UPGRADE_SLOTS + patternSlots; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            IPatternDetails base = PatternDetailsHelper.decodePattern(stack, level);
            if (base != null) {
                result.add(base);
            }
        }
        return result;
    }

    public void load(CompoundTag data) {
        if (data.contains("items", CompoundTag.TAG_COMPOUND)) {
            itemHandler.deserializeNBT(data.getCompound("items"));
        }
    }

    public void save(CompoundTag data) {
        data.put("items", itemHandler.serializeNBT());
    }

    /**
     * 装配枢纽专用动态物品背包：前 6 槽为升级卡，其余为样板槽。
     */
    private class PatternItemHandler extends ItemStackHandler {

        PatternItemHandler(int size) {
            super(size);
        }

        @Override
        protected void onContentsChanged(int slot) {
            controller.setChanged();
            if (slot >= AssemblyControllerBlockEntity.UPGRADE_SLOTS
                    && slot < AssemblyControllerBlockEntity.UPGRADE_SLOTS + getPatternSlotCount()) {
                patternsDirty = true;
            }
            if (slot == 2) {
                ensurePatternCapacity();
            }
            if (slot < AssemblyControllerBlockEntity.UPGRADE_SLOTS) {
                controller.markForUpdate();
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= getSlots()) {
                return false;
            }
            if (slot < AssemblyControllerBlockEntity.UPGRADE_SLOTS) {
                return switch (slot) {
                    case 0 -> stack.getItem() == ModItems.ASSEMBLY_PARALLEL_UPGRADE.get();
                    case 1 -> stack.getItem() == ModItems.ASSEMBLY_SPEED_UPGRADE.get();
                    case 2 -> stack.getItem() == ModItems.ASSEMBLY_CAPACITY_UPGRADE.get();
                    case 4 -> stack.getItem() == ModItems.ASSEMBLY_AUTO_UPLOAD_UPGRADE.get();
                    default -> false;
                };
            }
            if (stack.isEmpty()) {
                return true;
            }
            Level level = controller.getLevel();
            return level != null && PatternDetailsHelper.decodePattern(stack, level) != null;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= getSlots()) {
                return ItemStack.EMPTY;
            }
            if (slot == 2 && !simulate) {
                ItemStack current = getStackInSlot(slot);
                int newCount = Math.max(0, current.getCount() - amount);
                if (!canReduceCapacity(newCount)) {
                    return ItemStack.EMPTY;
                }
            }
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot < 0 || slot >= getSlots()) {
                return;
            }
            if (!stack.isEmpty() && !isItemValid(slot, stack)) {
                return;
            }
            super.setStackInSlot(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < AssemblyControllerBlockEntity.UPGRADE_SLOTS) {
                return 64;
            }
            return 1;
        }

        public void setCapacity(int newSize) {
            if (newSize == stacks.size()) {
                return;
            }
            NonNullList<ItemStack> newStacks = NonNullList.withSize(newSize, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(stacks.size(), newSize); i++) {
                newStacks.set(i, stacks.get(i));
            }
            stacks = newStacks;
        }
    }
}
