package com.github.aeddddd.ae2enhanced.mixin.bridge;

/**
 * MECraftingInventory 修改版本号访问接口.
 *
 * <p>canCraft 失败缓存（{@code MixinCraftingCPUClusterCanCraft}）依赖该版本号判断
 * CPU 本地库存是否发生变化：MODULATE 写入时自增，SIMULATE 路径零开销。
 * 绕过 injectItems/extractItems 直接操作 {@code getItemList()} 的写入方
 * 必须在写入后显式调用 {@link #ae2e$bumpVersion()}。</p>
 */
public interface IMeInventoryVersionAccess {

    /** 当前修改版本号,每次库存内容变化后递增. */
    long ae2e$getModVersion();

    /** 显式递增版本号（直接操作底层 IItemList 的写入路径使用）. */
    void ae2e$bumpVersion();
}
