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
        // 必须用资源探测而非 Class.forName:
        // 本类在 MixinBooter 收集 late mixin 配置阶段即被 LateMixinLoader 初始化,
        // 早于 ae2fc 的 late mixin 配置 PREPARE;
        // Class.forName 会初始化 TileDualInterface 并连带加载其父类链
        // (appeng.tile.AEBaseTile),导致 ae2fc 的 mixins.ae2fc.json
        // 因目标类过早加载而 MixinTargetAlreadyLoadedException 崩溃.
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = Ae2fcCompat.class.getClassLoader();
        }
        AE2FC_LOADED = cl != null && cl.getResource("com/glodblock/github/FluidCraft.class") != null;
        // ae2fc 2.6.6-r 等旧版缺少 2.7.x 接口
        AE2FC_WRAP_CACHE_SUPPORTED = AE2FC_LOADED
                && cl.getResource("com/glodblock/github/interfaces/FCDualityInterface.class") != null
                && cl.getResource("com/glodblock/github/common/tile/TileDualInterface.class") != null;
    }
}
