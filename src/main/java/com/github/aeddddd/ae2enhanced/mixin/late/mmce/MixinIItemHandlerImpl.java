package com.github.aeddddd.ae2enhanced.mixin.late.mmce;

import com.github.aeddddd.ae2enhanced.mixin.bridge.ISlotIndexProvider;
import com.github.aeddddd.ae2enhanced.util.SlotItemKey;
import hellfirepvp.modularmachinery.common.util.IItemHandlerImpl;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 为 MMCE 的 {@link IItemHandlerImpl} 附加"物品 → 候选槽位"索引.
 *
 * <p>背景：AE2 接口每 tick 的模拟插入经 AdaptorItemHandler.addItems 对所有槽位
 * 线性扫描,每个非空槽都做 canMergeItemStacks → NBTTagCompound 深层比较
 * （spark 采样 ~7%）。本索引把候选槽缩小到"同 key 可合并槽 ∪ 空 inSlots"。</p>
 *
 * <p>失效策略：写入即置脏 + 查询时懒重建。置脏点覆盖全部槽位写入口：
 * setStackInSlot / insertItemInternal(!simulate) / extractItemInternal(!simulate) /
 * clear / setMiscSlots；IOInventory.readNBT（整体重建 inventory 数组）
 * 由 MixinIOInventory 补充置脏。insertItemInternal 的原地 count 增长不改变
 * 索引键（Item/meta/NBT 均不变）,但空槽填充会,故 !simulate 一律置脏。</p>
 */
@Mixin(value = IItemHandlerImpl.class, remap = false)
public abstract class MixinIItemHandlerImpl implements ISlotIndexProvider {

    @Shadow
    protected int[] inSlots;

    @Shadow
    public boolean allowAnySlots;

    @Shadow
    public abstract ItemStack getStackInSlot(int slot);

    @Unique
    private boolean ae2e$slotIndexDirty = true;

    /** 同 key 可堆叠非空槽（升序）. */
    @Unique
    private Map<SlotItemKey, int[]> ae2e$mergeableSlots;

    /** 空 inSlots（升序）. */
    @Unique
    private int[] ae2e$emptySlots = new int[0];

    @Override
    public void ae2e$markSlotIndexDirty() {
        ae2e$slotIndexDirty = true;
    }

    @Override
    public int[] ae2e$getInsertCandidates(ItemStack stack) {
        // allowAnySlots 模式（GUI 临时态）下任何槽位都可插入,索引不适用,回退全扫
        if (allowAnySlots || stack.isEmpty()) {
            return null;
        }
        if (ae2e$slotIndexDirty) {
            ae2e$rebuildSlotIndex();
        }
        int[] mergeable = ae2e$mergeableSlots.get(new SlotItemKey(stack));
        return ae2e$mergeAscending(mergeable, ae2e$emptySlots);
    }

    @Unique
    private void ae2e$rebuildSlotIndex() {
        Map<SlotItemKey, List<Integer>> mergeable = new HashMap<>();
        IntArrayList empty = new IntArrayList();
        for (int slot : inSlots) {
            ItemStack s = getStackInSlot(slot);
            if (s.isEmpty()) {
                empty.add(slot);
                continue;
            }
            // 不可堆叠的栈永远不能被合并（canMergeItemStacks 前置条件）,不进入索引
            if (!s.isStackable()) {
                continue;
            }
            mergeable.computeIfAbsent(new SlotItemKey(s), k -> new ArrayList<>()).add(slot);
        }
        Map<SlotItemKey, int[]> map = new HashMap<>(mergeable.size());
        for (Map.Entry<SlotItemKey, List<Integer>> e : mergeable.entrySet()) {
            int[] arr = new int[e.getValue().size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = e.getValue().get(i);
            }
            map.put(e.getKey(), arr);
        }
        ae2e$mergeableSlots = map;
        ae2e$emptySlots = empty.toIntArray();
        ae2e$slotIndexDirty = false;
    }

    /** 合并两个升序槽位列表,保持与原全槽扫描一致的尝试顺序. */
    @Unique
    private static int[] ae2e$mergeAscending(int[] a, int[] b) {
        if (a == null) return b;
        if (a.length == 0) return b;
        if (b.length == 0) return a;
        int[] out = new int[a.length + b.length];
        int i = 0, j = 0, k = 0;
        while (i < a.length && j < b.length) {
            out[k++] = a[i] <= b[j] ? a[i++] : b[j++];
        }
        while (i < a.length) out[k++] = a[i++];
        while (j < b.length) out[k++] = b[j++];
        return out;
    }

    // ---- 写入口置脏 ----

    @Inject(method = "setStackInSlot", at = @At("RETURN"), require = 0)
    private void ae2e$dirtyOnSet(int slot, ItemStack stack, CallbackInfo ci) {
        ae2e$slotIndexDirty = true;
    }

    @Inject(method = "insertItemInternal", at = @At("RETURN"), require = 0)
    private void ae2e$dirtyOnInsert(int slot, ItemStack stack, boolean simulate,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!simulate) {
            ae2e$slotIndexDirty = true;
        }
    }

    @Inject(method = "extractItemInternal", at = @At("RETURN"), require = 0)
    private void ae2e$dirtyOnExtract(int slot, int amount, boolean simulate,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!simulate) {
            ae2e$slotIndexDirty = true;
        }
    }

    @Inject(method = "clear", at = @At("RETURN"), require = 0)
    private void ae2e$dirtyOnClear(CallbackInfo ci) {
        ae2e$slotIndexDirty = true;
    }

    @Inject(method = "setMiscSlots", at = @At("RETURN"), require = 0)
    private void ae2e$dirtyOnMiscSlots(int[] miscSlots, CallbackInfoReturnable<IItemHandlerImpl> cir) {
        ae2e$slotIndexDirty = true;
    }
}
