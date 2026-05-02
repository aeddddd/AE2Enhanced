package com.github.aeddddd.ae2enhanced.crafting;

/**
 * 并行槽位分配器。
 * 超因果计算核心的并行上限固定为 16384（由 AE2EnhancedConfig 决定）。
 */
public class ParallelAllocator {

    public static final int MAX_PARALLEL = 16384;

    private int usedParallel = 0;

    public synchronized int allocate(int requested) {
        if (requested <= 0) return 0;
        int available = MAX_PARALLEL - usedParallel;
        int granted = Math.min(requested, available);
        usedParallel += granted;
        return granted;
    }

    public synchronized void release(int amount) {
        usedParallel = Math.max(0, usedParallel - amount);
    }

    public synchronized int getAvailable() {
        return MAX_PARALLEL - usedParallel;
    }

    public int getMaxParallel() {
        return MAX_PARALLEL;
    }

    public synchronized int getUsedParallel() {
        return usedParallel;
    }

    public synchronized void reset() {
        usedParallel = 0;
    }
}
