package com.github.aeddddd.ae2enhanced.assembly;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
        // 使用最大槽位初始化，避免扩容时重新分配内存；NBT 通过稀疏序列化只保存非空槽。
        this.itemHandler = new PatternItemHandler(AssemblyControllerBlockEntity.TOTAL_SLOTS_MAX);
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
            // 样板变动时立即写入 SavedData，避免仅依赖区块保存导致崩溃丢失。
            if (slot >= AssemblyControllerBlockEntity.UPGRADE_SLOTS) {
                savePatternsToSavedData();
            }
        }

        /**
         * 将当前样板背包状态写入维度级 SavedData。
         */
        private void savePatternsToSavedData() {
            Level level = controller.getLevel();
            if (level == null || level.isClientSide()) {
                return;
            }
            AssemblyPatternSavedData savedData = AssemblyPatternSavedData.get(level);
            if (savedData != null) {
                savedData.setPatterns(controller.getBlockPos(), buildPatternNbt());
            }
        }

        /**
         * 构建稀疏的样板背包 NBT，只包含非空槽位。
         */
        private CompoundTag buildPatternNbt() {
            CompoundTag nbt = new CompoundTag();
            ListTag itemList = new ListTag();
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putInt("Slot", i);
                    itemList.add(stack.save(itemTag));
                }
            }
            nbt.putInt("Size", stacks.size());
            nbt.put("Items", itemList);
            return nbt;
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
                ItemStack stack = getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    return stack.getItem().getMaxStackSize(stack);
                }
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

        /**
         * 方块实体保存时，将样板数据写入 SavedData，自身 NBT 返回空，避免区块 NBT 膨胀。
         */
        @Override
        public CompoundTag serializeNBT() {
            savePatternsToSavedData();
            return new CompoundTag();
        }

        /**
         * 从 SavedData 读取样板数据；若 SavedData 为空则回退到传入的 NBT（兼容旧版本）。
         */
        @Override
        public void deserializeNBT(CompoundTag nbt) {
            Level level = controller.getLevel();
            CompoundTag dataToLoad = nbt;
            boolean fromSavedData = false;
            if (level != null && !level.isClientSide()) {
                AssemblyPatternSavedData savedData = AssemblyPatternSavedData.get(level);
                if (savedData != null) {
                    CompoundTag saved = savedData.getPatterns(controller.getBlockPos());
                    if (!saved.isEmpty()) {
                        dataToLoad = saved;
                        fromSavedData = true;
                    }
                }
            }

            int size = dataToLoad.contains("Size", Tag.TAG_INT)
                    ? dataToLoad.getInt("Size")
                    : AssemblyControllerBlockEntity.TOTAL_SLOTS_BASE;
            int clampedSize = Math.min(Math.max(size, AssemblyControllerBlockEntity.TOTAL_SLOTS_BASE),
                    AssemblyControllerBlockEntity.TOTAL_SLOTS_MAX);
            setCapacity(clampedSize);
            for (int i = 0; i < clampedSize; i++) {
                stacks.set(i, ItemStack.EMPTY);
            }
            if (dataToLoad.contains("Items", Tag.TAG_LIST)) {
                ListTag itemList = dataToLoad.getList("Items", Tag.TAG_COMPOUND);
                for (int i = 0; i < itemList.size(); i++) {
                    CompoundTag itemTag = itemList.getCompound(i);
                    int slot = itemTag.getInt("Slot");
                    if (slot >= 0 && slot < clampedSize) {
                        ItemStack stack = ItemStack.of(itemTag);
                        if (!stack.isEmpty() && isItemValid(slot, stack)) {
                            stacks.set(slot, stack);
                        }
                    }
                }
            }

            // 旧版本数据迁移：从方块实体 NBT 加载后写入 SavedData
            if (!fromSavedData && level != null && !level.isClientSide() && !nbt.isEmpty()) {
                savePatternsToSavedData();
            }

            onLoad();
        }
    }
}
