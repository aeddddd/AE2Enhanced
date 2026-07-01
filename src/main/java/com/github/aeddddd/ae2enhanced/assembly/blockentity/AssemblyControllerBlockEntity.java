package com.github.aeddddd.ae2enhanced.assembly.blockentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.pattern.ScaledPatternDetails;
import com.github.aeddddd.ae2enhanced.multiblock.IPatternProviderHost;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;

/**
 * 装配枢纽控制器方块实体。
 * <p>向 AE2 网络提供 Long 级别的并行虚拟样板合成，支持升级卡、样板分页与产物缓冲。</p>
 */
public class AssemblyControllerBlockEntity extends MultiblockControllerBlockEntity
        implements IPatternProviderHost {

    public static final int UPGRADE_SLOTS = 6;
    public static final int PATTERN_SLOTS_PER_PAGE = 102; // 17×6
    public static final int PATTERN_PAGES_BASE = 5;
    public static final int PATTERN_PAGES_PER_CAPACITY = 5;
    public static final int PATTERN_PAGES_MAX = 30;
    public static final int TOTAL_SLOTS_MAX = UPGRADE_SLOTS + PATTERN_SLOTS_PER_PAGE * PATTERN_PAGES_MAX;
    public static final int TOTAL_SLOTS_BASE = UPGRADE_SLOTS + PATTERN_SLOTS_PER_PAGE * PATTERN_PAGES_BASE;
    private static final int MAX_PENDING_OUTPUTS = 4096;

    private final PatternItemHandler itemHandler = new PatternItemHandler(TOTAL_SLOTS_BASE);
    private final List<Integer> jobTimers = new ArrayList<>();
    private final List<GenericStack> pendingOutputs = new ArrayList<>();
    private int validationCooldown = 0;
    private int patternRefreshTicks = 0;
    private boolean patternsDirty = false;
    private boolean batchBusy = false;

    public AssemblyControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_CONTROLLER.get(), pos, state);
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    public int getPatternSlotCount() {
        return getPatternPages() * PATTERN_SLOTS_PER_PAGE;
    }

    /**
     * 当前并行上限。0 张并行升级卡 = 64，每多 1 张 ×32，5 张 = Long.MAX_VALUE。
     */
    public long getParallelCap() {
        ItemStack stack = itemHandler.getStackInSlot(0);
        if (stack.isEmpty()) {
            return 64;
        }
        if (stack.getItem() == ModItems.ASSEMBLY_PARALLEL_UPGRADE.get()) {
            int count = stack.getCount();
            if (count >= 5) {
                return Long.MAX_VALUE;
            }
            long cap = 64;
            for (int i = 0; i < count; i++) {
                cap = cap * 32;
                if (cap > 67108864) {
                    return 67108864;
                }
            }
            return cap;
        }
        return 64;
    }

    /**
     * 当前合成延迟 tick 数。0 张速度升级卡 = 20，每张减半，最低 1 tick。
     */
    public int getCraftingTicks() {
        ItemStack stack = itemHandler.getStackInSlot(1);
        if (stack.isEmpty()) {
            return 20;
        }
        if (stack.getItem() == ModItems.ASSEMBLY_SPEED_UPGRADE.get()) {
            int ticks = 20;
            int count = stack.getCount();
            for (int i = 0; i < count && ticks > 1; i++) {
                ticks = Math.max(ticks / 2, 1);
            }
            return ticks;
        }
        return 20;
    }

    /**
     * 当前可用样板页数。基础 5 页，每张扩容升级卡 +5 页，上限 30 页。
     */
    public int getPatternPages() {
        ItemStack stack = itemHandler.getStackInSlot(2);
        int count = 0;
        if (!stack.isEmpty() && stack.getItem() == ModItems.ASSEMBLY_CAPACITY_UPGRADE.get()) {
            count = stack.getCount();
        }
        int pages = PATTERN_PAGES_BASE + count * PATTERN_PAGES_PER_CAPACITY;
        return Math.min(pages, PATTERN_PAGES_MAX);
    }

    /**
     * 检查是否安装了样板自动上传模块升级（槽位 4）。
     */
    public boolean hasAutoUploadUpgrade() {
        ItemStack stack = itemHandler.getStackInSlot(4);
        return !stack.isEmpty() && stack.getItem() == ModItems.ASSEMBLY_AUTO_UPLOAD_UPGRADE.get();
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (validationCooldown-- <= 0) {
            validationCooldown = 20;
            if (isFormed() && !AssemblyStructure.validate(level, worldPosition)) {
                AssemblyStructure.disassemble(level, worldPosition);
            }
        }

        ensurePatternCapacity();

        if (patternsDirty) {
            patternsDirty = false;
            patternRefreshTicks = 1;
        }
        if (patternRefreshTicks > 0) {
            if (--patternRefreshTicks == 0) {
                refreshInterfaceServices();
            }
        }

        if (!pendingOutputs.isEmpty()) {
            tryInjectPendingOutputs();
        }

        List<Integer> nextTimers = new ArrayList<>();
        for (int ticks : jobTimers) {
            int next = ticks - 1;
            if (next > 0) {
                nextTimers.add(next);
            }
        }
        jobTimers.clear();
        jobTimers.addAll(nextTimers);

        // 每 tick 重置 batchBusy，允许下一 tick 继续接收 pushPattern
        this.batchBusy = false;
    }

    private void ensurePatternCapacity() {
        int targetSize = UPGRADE_SLOTS + getPatternPages() * PATTERN_SLOTS_PER_PAGE;
        if (itemHandler.getSlots() < targetSize) {
            itemHandler.setCapacity(targetSize);
        }
    }

    private boolean canReduceCapacity(int newCapacityCount) {
        int oldPages = getPatternPages();
        int newPages = PATTERN_PAGES_BASE + newCapacityCount * PATTERN_PAGES_PER_CAPACITY;
        newPages = Math.min(newPages, PATTERN_PAGES_MAX);
        if (newPages >= oldPages) {
            return true;
        }
        int startSlot = UPGRADE_SLOTS + newPages * PATTERN_SLOTS_PER_PAGE;
        int endSlot = UPGRADE_SLOTS + oldPages * PATTERN_SLOTS_PER_PAGE;
        for (int i = startSlot; i < endSlot && i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void tryInjectPendingOutputs() {
        IManagedGridNode targetNode = resolveNode(null);
        if (targetNode == null || targetNode.getGrid() == null) {
            return;
        }
        IStorageService storageService = targetNode.getGrid().getStorageService();
        if (storageService == null) {
            return;
        }
        MEStorage storage = storageService.getInventory();
        var source = getActionSource();

        Map<AEKey, Long> merged = new HashMap<>();
        for (GenericStack stack : pendingOutputs) {
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            merged.merge(stack.what(), stack.amount(), Long::sum);
        }
        pendingOutputs.clear();

        List<GenericStack> leftovers = new ArrayList<>();
        for (Map.Entry<AEKey, Long> entry : merged.entrySet()) {
            AEKey key = entry.getKey();
            long count = entry.getValue();
            while (count > 0) {
                long batch = Math.min(count, Long.MAX_VALUE);
                long remainder = storage.insert(key, batch, Actionable.MODULATE, source);
                if (remainder >= batch) {
                    leftovers.add(new GenericStack(key, count));
                    break;
                }
                count = remainder;
            }
        }

        if (pendingOutputs.size() + leftovers.size() > MAX_PENDING_OUTPUTS) {
            AE2Enhanced.LOGGER.error("[AE2E] pendingOutputs overflow in AssemblyController at {}, dropping {}",
                    worldPosition, pendingOutputs.size() + leftovers.size() - MAX_PENDING_OUTPUTS);
            while (pendingOutputs.size() + leftovers.size() > MAX_PENDING_OUTPUTS) {
                if (!leftovers.isEmpty()) {
                    leftovers.remove(leftovers.size() - 1);
                } else {
                    pendingOutputs.remove(pendingOutputs.size() - 1);
                }
            }
        }
        pendingOutputs.addAll(leftovers);
    }

    // ---- IPatternProviderHost ----

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        List<IPatternDetails> result = new ArrayList<>();
        Level level = this.level;
        if (level == null || !isFormed()) {
            return result;
        }
        long multiplier = getParallelCap();
        int patternSlots = getPatternSlotCount();
        for (int i = UPGRADE_SLOTS; i < UPGRADE_SLOTS + patternSlots; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            IPatternDetails base = PatternDetailsHelper.decodePattern(stack, level);
            if (base != null) {
                result.add(new ScaledPatternDetails(base, multiplier));
            }
        }
        return result;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        return pushPattern(pattern, inputs, null);
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, @Nullable IManagedGridNode node) {
        if (level == null || level.isClientSide() || !isFormed()) {
            return false;
        }

        long cap = getParallelCap();
        int intCap = cap >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cap;
        if (jobTimers.size() >= intCap || batchBusy) {
            return false;
        }

        IManagedGridNode targetNode = resolveNode(node);
        if (targetNode == null || targetNode.getGrid() == null) {
            return false;
        }
        IStorageService storageService = targetNode.getGrid().getStorageService();
        if (storageService == null) {
            return false;
        }
        MEStorage storage = storageService.getInventory();
        var source = getActionSource();

        // 预检查输入是否足够：优先使用 AE2 传入的 inputs
        for (KeyCounter input : inputs) {
            for (var entry : input) {
                AEKey key = entry.getKey();
                long needed = entry.getLongValue();
                if (needed <= 0) {
                    continue;
                }
                long available = storage.extract(key, needed, Actionable.SIMULATE, source);
                if (available < needed) {
                    return false;
                }
            }
        }

        // 扣除输入
        for (KeyCounter input : inputs) {
            for (var entry : input) {
                AEKey key = entry.getKey();
                long needed = entry.getLongValue();
                if (needed <= 0) {
                    continue;
                }
                storage.extract(key, needed, Actionable.MODULATE, source);
            }
        }

        // 产物与剩余物先进入缓冲，按 getCraftingTicks() 延迟后注入网络
        long multiplier = (pattern instanceof ScaledPatternDetails scaled) ? scaled.getMultiplier() : 1L;
        for (GenericStack output : pattern.getOutputs()) {
            if (output != null && output.amount() > 0) {
                pendingOutputs.add(output);
            }
        }
        IPatternDetails.IInput[] patternInputs = pattern.getInputs();
        for (int i = 0; i < inputs.length && i < patternInputs.length; i++) {
            IPatternDetails.IInput input = patternInputs[i];
            if (input == null) {
                continue;
            }
            for (var entry : inputs[i]) {
                AEKey key = entry.getKey();
                AEKey remaining = input.getRemainingKey(key);
                if (remaining != null) {
                    long remainingAmount = multiplier;
                    GenericStack[] possible = input.getPossibleInputs();
                    if (possible.length > 0 && possible[0].amount() > 0) {
                        remainingAmount = entry.getLongValue() / possible[0].amount();
                    }
                    if (remainingAmount > 0) {
                        pendingOutputs.add(new GenericStack(remaining, remainingAmount));
                    }
                }
            }
        }

        jobTimers.add(getCraftingTicks());
        batchBusy = true;
        return true;
    }

    @Nullable
    private IManagedGridNode resolveNode(@Nullable IManagedGridNode preferred) {
        if (preferred != null && preferred.isReady()) {
            return preferred;
        }
        for (BlockPos pos : getInterfaces()) {
            if (level != null && level.getBlockEntity(pos) instanceof com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity me) {
                IManagedGridNode node = me.getMainNode();
                if (node.isReady()) {
                    return node;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isBusy() {
        long cap = getParallelCap();
        int intCap = cap >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cap;
        return jobTimers.size() >= intCap || batchBusy;
    }

    // ---- NBT ----

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        if (data.contains("items", CompoundTag.TAG_COMPOUND)) {
            itemHandler.deserializeNBT(data.getCompound("items"));
        }
        if (data.contains("pendingOutputs", ListTag.TAG_LIST)) {
            pendingOutputs.clear();
            ListTag list = data.getList("pendingOutputs", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                GenericStack stack = GenericStack.readTag(list.getCompound(i));
                if (stack != null && stack.amount() > 0) {
                    pendingOutputs.add(stack);
                }
            }
        }
        ensurePatternCapacity();
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.put("items", itemHandler.serializeNBT());
        ListTag list = new ListTag();
        for (GenericStack stack : pendingOutputs) {
            if (stack != null && stack.amount() > 0) {
                list.add(GenericStack.writeTag(stack));
            }
        }
        data.put("pendingOutputs", list);
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
            AssemblyControllerBlockEntity.this.setChanged();
            if (slot >= UPGRADE_SLOTS && slot < UPGRADE_SLOTS + getPatternSlotCount()) {
                patternsDirty = true;
            }
            if (slot == 2) {
                ensurePatternCapacity();
            }
            if (slot < UPGRADE_SLOTS) {
                markForUpdate();
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= getSlots()) {
                return false;
            }
            if (slot < UPGRADE_SLOTS) {
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
            Level level = AssemblyControllerBlockEntity.this.level;
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
            if (slot < UPGRADE_SLOTS) {
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
