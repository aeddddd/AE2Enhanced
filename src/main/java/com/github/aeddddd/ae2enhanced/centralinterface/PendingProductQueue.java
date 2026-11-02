package com.github.aeddddd.ae2enhanced.centralinterface;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟合成产物暂存队列.
 *
 * <p>虚拟批量合成的产物先暂存于此，待 AE2 CPU 注册 waitingFor 后再注入网络。
 * 队列内容持久化到 NBT（键 {@code pendingVirtualProducts}），
 * 避免 chunk 卸载/服务器重启时丢失。</p>
 */
public class PendingProductQueue {

    private final List<ItemStack> products = new ArrayList<>();
    private final List<NBTTagCompound> preservedEntries = new ArrayList<>();
    private final Runnable changeListener;

    public PendingProductQueue() {
        this(() -> { });
    }

    public PendingProductQueue(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public void addAll(List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;
        this.products.addAll(items);
        this.changeListener.run();
    }

    public boolean isEmpty() {
        return this.products.isEmpty();
    }

    /**
     * 取出全部待注入产物并清空队列。
     */
    public List<ItemStack> drainAll() {
        List<ItemStack> copy = new ArrayList<>(this.products);
        if (!this.products.isEmpty()) {
            this.products.clear();
            this.changeListener.run();
        }
        return copy;
    }

    /**
     * 将待注入的虚拟合成产物写入 NBT，避免 chunk 卸载/服务器重启时丢失。
     */
    public void writeToNBT(NBTTagCompound data) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound raw : this.preservedEntries) list.appendTag(raw.copy());
        for (ItemStack product : this.products) {
            if (product.isEmpty()) continue;
            list.appendTag(product.serializeNBT());
        }
        if (list.tagCount() > 0) {
            data.setTag("pendingVirtualProducts", list);
        } else {
            data.removeTag("pendingVirtualProducts");
        }
    }

    /**
     * 从 NBT 恢复待注入的虚拟合成产物。
     */
    public void readFromNBT(NBTTagCompound data) {
        this.products.clear();
        this.preservedEntries.clear();
        if (!data.hasKey("pendingVirtualProducts")) {
            return;
        }
        NBTTagList list = data.getTagList("pendingVirtualProducts", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack stack = new ItemStack(list.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                this.products.add(stack);
            } else {
                this.preservedEntries.add(list.getCompoundTagAt(i).copy());
            }
        }
    }
}
