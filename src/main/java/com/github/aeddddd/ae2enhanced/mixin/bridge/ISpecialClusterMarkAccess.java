package com.github.aeddddd.ae2enhanced.mixin.bridge;

/**
 * CraftingCPUCluster 特殊标记的字段级访问(执行性能优化).
 * <p>原实现经 synchronized WeakHashMap 查询(spark:逐次 canCraft 调用前都查,
 * 大单下占 tick ~2.6%);mixin 将标记直接落在集群字段上,查询零锁零哈希.
 * 测试环境(mixin 不生效)由 SpecialCraftingRuntime 回落到 WeakHashMap.</p>
 */
public interface ISpecialClusterMarkAccess {

    boolean ae2e$isSpecialMarked();

    void ae2e$setSpecialMarked(boolean marked);
}
