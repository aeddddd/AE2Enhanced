package com.github.aeddddd.ae2enhanced.container;

import java.util.List;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

final class SplicedShiftClickService {
    private static boolean warnedSlowShiftClick;
    private static int shiftClickCount;
    private static int clicksThisTick;
    private static int lastClickTick = -1;
    private static boolean warnedClickFlood;

    private final ContainerSplicedTerm container;

    SplicedShiftClickService(ContainerSplicedTerm container) {
        this.container = container;
    }

    static ItemStack mergeInto(SplicedTermSlotPolicy policy, ItemStack stack, List<Slot> targets) {
        if (stack.isEmpty() || targets == null || targets.isEmpty()) {
            return stack;
        }
        for (Slot target : targets) {
            if (stack.isEmpty()) {
                break;
            }
            if (!policy.canShiftClickInto(target)) {
                continue;
            }
            ItemStack existing = target.getStack();
            if (existing.isEmpty() || !target.isItemValid(stack)
                    || existing.getItem() != stack.getItem()
                    || !ItemStack.areItemsEqual(existing, stack)
                    || !ItemStack.areItemStackTagsEqual(existing, stack)) {
                continue;
            }
            int limit = Math.min(target.getSlotStackLimit(), stack.getMaxStackSize());
            int space = limit - existing.getCount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, stack.getCount());
            existing.grow(move);
            stack.shrink(move);
            target.onSlotChanged();
        }
        for (Slot target : targets) {
            if (stack.isEmpty()) {
                break;
            }
            if (!policy.canShiftClickInto(target)) {
                continue;
            }
            ItemStack existing = target.getStack();
            if (!existing.isEmpty() || !target.isItemValid(stack)) {
                continue;
            }
            int move = Math.min(Math.min(target.getSlotStackLimit(), stack.getMaxStackSize()), stack.getCount());
            if (move <= 0) {
                continue;
            }
            ItemStack placed = stack.copy();
            placed.setCount(move);
            target.putStack(placed);
            target.onSlotChanged();
            stack.shrink(move);
        }
        return stack;
    }

    static int shiftClickCount() {
        return shiftClickCount;
    }

    private static boolean isViewCell(ItemStack stack) {
        return ContainerOmniTerm.isSameAsCached(appeng.api.AEApi.instance().definitions().items().viewCell(), stack);
    }

    private static void detectShiftClickFlood(EntityPlayer player, int index) {
        int tick;
        try {
            tick = player.getServer().getTickCounter();
        } catch (Throwable t) {
            return;
        }
        if (tick != lastClickTick) {
            lastClickTick = tick;
            clicksThisTick = 0;
        }
        clicksThisTick++;
        if (clicksThisTick == 200 && !warnedClickFlood) {
            warnedClickFlood = true;
            AE2Enhanced.LOGGER.warn("[AE2E] 单个 tick 内 shift-click 已被调用 {} 次（来源槽 {}）——疑似循环发包，"
                    + "下面打印一次调用栈", clicksThisTick, index, new Throwable("shift-click flood"));
        }
    }

    ItemStack transferStackInSlot(EntityPlayer player, int index) {
        detectShiftClickFlood(player, index);
        if (Platform.isClient() || index < 0 || index >= container.inventorySlots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = container.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        SplicedTermSlotPolicy policy = container.slotPolicy();
        if (!policy.canShiftClickFrom(slot)) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getStack();
        shiftClickCount++;
        long clickStart = System.nanoTime();
        List<Slot> targets = policy.shiftClickTargetsFor(slot);
        long targetBuildNanos = System.nanoTime() - clickStart;
        ItemStack remainder;
        if (policy.isPlayerSlot(slot) && isViewCell(source)) {
            remainder = mergeInto(policy, source.copy(), policy.slotsOf(SplicedTermSlotPolicy.MODULE_VIEWCELL));
        } else if (policy.isPlayerSlot(slot)) {
            remainder = container.transferStackToNetwork(source.copy());
            if (!remainder.isEmpty()) {
                remainder = mergeInto(policy, remainder, targets);
            }
        } else {
            remainder = mergeInto(policy, source.copy(), targets);
        }
        long totalNanos = System.nanoTime() - clickStart;
        if (totalNanos > 200000000L && !warnedSlowShiftClick) {
            warnedSlowShiftClick = true;
            AE2Enhanced.LOGGER.warn("[AE2E] shift-click 处理过慢：{}ms（其中算目标 {}ms），来源槽 {}（{}），"
                            + "目标 {} 个；容器槽={}，模块规模={}",
                    totalNanos / 1000000L, targetBuildNanos / 1000000L, index,
                    policy.moduleOf(slot), targets.size(), container.inventorySlots.size(),
                    policy.describeGroupSizes());
        }
        int moved = source.getCount() - remainder.getCount();
        if (moved <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack left = source.copy();
        left.shrink(moved);
        slot.putStack(left.isEmpty() ? ItemStack.EMPTY : left);
        slot.onSlotChanged();
        container.detectAndSendChanges();
        return remainder;
    }
}
