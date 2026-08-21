package com.github.aeddddd.ae2enhanced.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 物品槽位索引键：(Item, meta, NBT) 三元组.
 *
 * <p>用于 MMCE 槽位索引（{@code MixinIItemHandlerImpl}）的可合并槽分组。
 * 已核实 1.12.2 语义：isItemEqual 要求 item+meta 严格相等,
 * areItemStacksEqual 额外要求 count+NBT+caps 相等——因此本键（不含 count）
 * 是可合并槽的必要条件超集,候选槽仍需 insertItem 原逻辑复验,不会错误排除。</p>
 */
public final class SlotItemKey {

    private final Item item;
    private final int meta;
    private final NBTTagCompound nbt;
    private final int hash;

    public SlotItemKey(ItemStack stack) {
        this.item = stack.getItem();
        this.meta = stack.getMetadata();
        this.nbt = stack.getTagCompound();
        int h = item.hashCode();
        h = 31 * h + meta;
        h = 31 * h + (nbt != null ? nbt.hashCode() : 0);
        this.hash = h;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SlotItemKey)) return false;
        SlotItemKey other = (SlotItemKey) obj;
        if (this.hash != other.hash) return false;
        if (item != other.item || meta != other.meta) return false;
        return nbt == null ? other.nbt == null : nbt.equals(other.nbt);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
