package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.storage.data.IAEItemStack;

/**
 * 表示一个正在 Crafting CPU 中合成的物品及其进度.
 */
public class CraftingStatus {
    public final IAEItemStack output;
    public final long remaining;
    public final long start;

    public CraftingStatus(IAEItemStack output, long remaining, long start) {
        this.output = output;
        this.remaining = remaining;
        this.start = start;
    }

    /**
     * 返回剩余比例 (0.0 ~ 1.0).1.0 表示刚开始,0.0 表示即将完成.
     */
    public float getRatio() {
        return start > 0 ? (float) remaining / (float) start : 0f;
    }

    /**
     * 是否已完成(remaining <= 0).
     */
    public boolean isDone() {
        return remaining <= 0;
    }
}
