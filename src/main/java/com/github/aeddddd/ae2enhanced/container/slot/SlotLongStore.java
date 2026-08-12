package com.github.aeddddd.ae2enhanced.container.slot;

import com.github.aeddddd.ae2enhanced.chamber.LongItemStore;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Long 缓存槽的虚拟槽位：映射到 {@link LongItemStore} 的第 N 个条目.
 *
 * <p>点击逻辑全部由 Container.slotClick 拦截处理（标准 CPacketClickWindow 链路）;
 * 本槽位只负责展示模板物品与悬停高亮,不持有真实库存,
 * getHasStack 恒为 false 以避免原版 tooltip 与自定义计数 tooltip 重复.</p>
 */
public class SlotLongStore extends Slot {

    private static final InventoryBasic DUMMY = new InventoryBasic("ae2e_long_store", true, 1);

    private final LongItemStore store;
    private final int storeIndex;

    public SlotLongStore(LongItemStore store, int storeIndex, int x, int y) {
        super(DUMMY, 0, x, y);
        this.store = store;
        this.storeIndex = storeIndex;
    }

    public LongItemStore.Entry getEntry() {
        return store.entryAt(storeIndex);
    }

    @Override
    public ItemStack getStack() {
        LongItemStore.Entry entry = getEntry();
        // 数量为 1 的模板：原版渲染不画计数,由 GUI 覆盖层绘制 long 计数
        return entry != null ? entry.getTemplate() : ItemStack.EMPTY;
    }

    @Override
    public boolean getHasStack() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return getEntry() != null;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        // 禁止标准 putStack,放入逻辑走 Container.slotClick
        return false;
    }

    @Override
    public void putStack(ItemStack stack) {
        // 不允许直接写入
    }

    @Override
    public ItemStack decrStackSize(int amount) {
        return ItemStack.EMPTY;
    }
}
