package com.github.aeddddd.ae2enhanced.integration.drawer.fsl;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.github.aeddddd.ae2enhanced.integration.drawer.IDrawerIndexAdapter;
import com.xinyihl.functionalstoragelegacy.api.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.controller.ControllerItemHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * FunctionalStorageLegacy Hash 索引适配器.
 *
 * <p>完全替代 {@link com.xinyihl.functionalstoragelegacy.common.integration.ae2.ControllerMEItemHandler},
 * 在 {@link ControllerItemHandler} 之上建立 {@code Map<Item, Map<meta, List<SlotRef>>>} Hash 索引,
 * 使 injectItems / extractItems / getAvailableItems 全部走 O(同物品槽位数) 路径.</p>
 *
 * <p>索引在首次使用时惰性构建,每次 MODULATE 操作后标记失效,下次使用时重建.
 * 所有 NPE 检查已封装在内部.</p>
 */
public class FSLAdapter implements IDrawerIndexAdapter {

    private final ControllerItemHandler handler;
    private final IItemStorageChannel channel;

    // Hash 索引: 完整物品类型(Item + meta + NBT) -> SlotRef 列表
    // 必须包含 NBT,否则同 Item+meta 但 NBT 不同的物品会被错误合并.
    private final Map<ItemKey, List<SlotRef>> itemIndex = new HashMap<>();
    // 空槽位列表(用于 insert 时快速定位)
    private final List<SlotRef> emptySlots = new ArrayList<>();
    // 索引是否失效
    private boolean indexDirty = true;

    private static class SlotRef {
        final IBigItemHandler handler;
        final int slot;

        SlotRef(IBigItemHandler handler, int slot) {
            this.handler = handler;
            this.slot = slot;
        }
    }

    /**
     * 完整物品类型键,包含 Item、meta 和 NBT.
     *
     * <p>必须区分 NBT,否则同 Item+meta 但 NBT 不同的抽屉物品会被错误合并.</p>
     */
    private static final class ItemKey {
        final Item item;
        final int meta;
        final NBTTagCompound tag;

        ItemKey(ItemStack stack) {
            this.item = stack.getItem();
            this.meta = stack.getMetadata();
            NBTTagCompound t = stack.getTagCompound();
            this.tag = t != null ? t.copy() : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey other = (ItemKey) o;
            return this.meta == other.meta
                    && this.item == other.item
                    && (this.tag == null ? other.tag == null : this.tag.equals(other.tag));
        }

        @Override
        public int hashCode() {
            int result = item.hashCode();
            result = 31 * result + meta;
            result = 31 * result + (tag != null ? tag.hashCode() : 0);
            return result;
        }
    }

    public FSLAdapter(ControllerItemHandler handler, IItemStorageChannel channel) {
        this.handler = handler;
        this.channel = channel;
    }

    private synchronized void rebuildIndex() {
        this.itemIndex.clear();
        this.emptySlots.clear();
        List<IBigItemHandler> handlers = this.handler.getHandlers();
        if (handlers == null) {
            this.indexDirty = false;
            return;
        }
        for (IBigItemHandler h : handlers) {
            if (h == null) continue;
            int slots = h.getRealSlotCount();
            for (int s = 0; s < slots; s++) {
                ItemStack type = h.getStoredType(s);
                if (type == null || type.isEmpty()) {
                    this.emptySlots.add(new SlotRef(h, s));
                } else {
                    ItemKey key = new ItemKey(type);
                    this.itemIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(new SlotRef(h, s));
                }
            }
        }
        this.indexDirty = false;
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) {
            return null;
        }
        if (this.indexDirty) {
            rebuildIndex();
        }

        ItemStack inputStack = input.getDefinition();
        long remaining = input.getStackSize();
        boolean simulate = type == Actionable.SIMULATE;

        // 1. 先尝试放入已有相同物品类型(含 NBT)的槽位
        List<SlotRef> slots = this.itemIndex.get(new ItemKey(inputStack));
        if (slots != null) {
            for (SlotRef ref : slots) {
                if (ref.handler == null) continue;
                remaining = ref.handler.insertItemLong(ref.slot, inputStack, remaining, simulate);
                if (remaining <= 0) break;
            }
        }

        // 2. 再放入空槽位
        if (remaining > 0) {
            Iterator<SlotRef> it = this.emptySlots.iterator();
            while (it.hasNext() && remaining > 0) {
                SlotRef ref = it.next();
                if (ref.handler == null) continue;
                long before = remaining;
                remaining = ref.handler.insertItemLong(ref.slot, inputStack, remaining, simulate);
                if (!simulate && remaining < before) {
                    // 该槽位已被占用,从 emptySlots 移除
                    it.remove();
                }
            }
        }

        if (type == Actionable.MODULATE) {
            this.indexDirty = true;
        }

        if (remaining <= 0) {
            return null;
        }
        if (remaining >= input.getStackSize()) {
            return input;
        }
        IAEItemStack result = input.copy();
        result.setStackSize(remaining);
        return result;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) {
            return null;
        }
        if (this.indexDirty) {
            rebuildIndex();
        }

        ItemStack requestStack = request.getDefinition();
        long toExtract = request.getStackSize();
        long extracted = 0;
        boolean simulate = mode == Actionable.SIMULATE;

        List<SlotRef> slots = this.itemIndex.get(new ItemKey(requestStack));
        if (slots != null) {
            for (SlotRef ref : slots) {
                if (ref.handler == null) continue;
                long ext = ref.handler.extractItemLong(ref.slot, toExtract - extracted, simulate);
                extracted += ext;
                if (extracted >= toExtract) break;
            }
        }

        if (mode == Actionable.MODULATE) {
            this.indexDirty = true;
        }

        if (extracted <= 0) {
            return null;
        }
        IAEItemStack result = request.copy();
        result.setStackSize(extracted);
        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        if (this.indexDirty) {
            rebuildIndex();
        }
        for (Map.Entry<ItemKey, List<SlotRef>> entry : this.itemIndex.entrySet()) {
            long totalCount = 0L;
            for (SlotRef ref : entry.getValue()) {
                if (ref.handler == null) continue;
                totalCount += ref.handler.getStoredAmount(ref.slot);
            }
            if (totalCount > 0) {
                // 从第一个槽位获取包含 NBT 的原型，避免 new ItemStack 丢失 NBT
                ItemStack prototype = null;
                for (SlotRef ref : entry.getValue()) {
                    if (ref.handler == null) continue;
                    ItemStack stored = ref.handler.getStoredType(ref.slot);
                    if (stored != null && !stored.isEmpty()) {
                        prototype = stored.copy();
                        prototype.setCount(1);
                        break;
                    }
                }
                if (prototype == null || prototype.isEmpty()) {
                    ItemKey key = entry.getKey();
                    prototype = new ItemStack(key.item, 1, key.meta);
                    if (key.tag != null) {
                        prototype.setTagCompound(key.tag.copy());
                    }
                }
                IAEItemStack aeStack = this.channel.createStack(prototype);
                if (aeStack != null) {
                    aeStack.setStackSize(totalCount);
                    out.add(aeStack);
                }
            }
        }
        return out;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        if (input == null) {
            return false;
        }
        if (this.indexDirty) {
            rebuildIndex();
        }
        ItemStack inputStack = input.getDefinition();
        // 如果已有相同物品类型(含 NBT)且未满,或有空槽位,则可以接受
        List<SlotRef> slots = this.itemIndex.get(new ItemKey(inputStack));
        if (slots != null) {
            for (SlotRef ref : slots) {
                if (ref.handler == null) continue;
                if (ref.handler.getStoredAmount(ref.slot) < ref.handler.getLongSlotLimit(ref.slot)) {
                    return true;
                }
            }
        }
        return !this.emptySlots.isEmpty();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return true;
    }
}
