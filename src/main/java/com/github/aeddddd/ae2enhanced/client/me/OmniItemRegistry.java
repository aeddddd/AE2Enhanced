package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.storage.data.IAEItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Omni Terminal 客户端物品注册表。
 *
 * <p>维护服务端分配的 int ID 与本地 {@link IAEItemStack} 副本的映射。
 * 生命周期与 Omni Terminal GUI Session 绑定，关闭 GUI 后由 GC 回收。</p>
 */
public class OmniItemRegistry {

    private final Int2ObjectMap<IAEItemStack> idToStack = new Int2ObjectOpenHashMap<>();

    public void register(int id, IAEItemStack stack, long count) {
        IAEItemStack copy = stack.copy();
        copy.setStackSize(count);
        this.idToStack.put(id, copy);
    }

    public IAEItemStack getStack(int id) {
        return this.idToStack.get(id);
    }

    public void clear() {
        this.idToStack.clear();
    }
}
