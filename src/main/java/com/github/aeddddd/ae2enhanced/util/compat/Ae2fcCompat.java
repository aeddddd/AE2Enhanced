package com.github.aeddddd.ae2enhanced.util.compat;

/**
 * ae2fc 兼容性检测工具类.
 * 当 ae2fc (ae2-fluid-crafting) 已安装时,本 mod 的流体/气体假物品功能应自动禁用,
 * 避免与 ae2fc 的 FakeMonitor 体系冲突(重复显示、双重提取等).
 */
public class Ae2fcCompat {

    public static final boolean AE2FC_LOADED;

    /**
     * ae2fc wrap 缓存 mixin 依赖的 2.7.x API 是否可用.
     * 官方 ae2fc 2.6.6-r 等旧版缺少 FCDualityInterface/TileDualInterface,
     * 硬引用这些类的 FluidAdaptorCache 一旦被加载,首次调用即 NoClassDefFoundError.
     */
    public static final boolean AE2FC_WRAP_CACHE_SUPPORTED;

    static {
        AE2FC_LOADED = com.github.aeddddd.ae2enhanced.integration.BootstrapCapabilities.hasAe2fc();
        AE2FC_WRAP_CACHE_SUPPORTED = com.github.aeddddd.ae2enhanced.integration.BootstrapCapabilities.hasAe2fcWrapCacheApi();
    }
}
