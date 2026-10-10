package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;

/**
 * 一个可读写槽位的稳定引用.
 *
 * <p>把"怎么访问这台机器的某个槽位"（哪个面的哪个 capability、还是反射出来的内部数组）
 * 收敛为一个对象，使框架层（{@link ItemTransferHelper} / {@link ProductFilter}）无需知道
 * 目标 mod 的存储实现。</p>
 *
 * <p>注意 {@link #extract} 在空槽上返回空堆，因此<b>不能</b>用"对空槽做 simulate 抽取是否成功"
 * 来推断角色；角色由 {@link CatalogBuilder} 在构造目录时显式给出。</p>
 */
public final class SlotRef {

    private final IItemHandler handler;
    private final int index;
    private final EnumFacing face;
    private final SlotRole role;

    public SlotRef(IItemHandler handler, int index, EnumFacing face, SlotRole role) {
        this.handler = handler;
        this.index = index;
        this.face = face;
        this.role = role != null ? role : SlotRole.UNKNOWN;
    }

    /** 读取当前内容（只读，不做防御性拷贝）。 */
    public ItemStack peek() {
        return this.handler.getStackInSlot(this.index);
    }

    /**
     * 插入物品.
     *
     * @param simulate true 时不改变槽位内容
     * @return 未能放入的剩余部分（空表示全部放入）
     */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        return this.handler.insertItem(this.index, stack, simulate);
    }

    /**
     * 抽取物品.
     *
     * @param simulate true 时不改变槽位内容
     * @return 实际（模拟）抽取到的数量，空表示无可抽取
     */
    public ItemStack extract(int amount, boolean simulate) {
        return this.handler.extractItem(this.index, amount, simulate);
    }

    public SlotRole role() {
        return this.role;
    }

    public EnumFacing face() {
        return this.face;
    }

    public int index() {
        return this.index;
    }

    public IItemHandler handler() {
        return this.handler;
    }

    @Override
    public String toString() {
        return (this.face == null ? "internal" : this.face.name().toLowerCase()) + ":" + this.index + "/" + this.role;
    }
}
