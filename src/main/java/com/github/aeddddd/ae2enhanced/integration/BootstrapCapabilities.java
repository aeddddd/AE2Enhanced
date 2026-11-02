package com.github.aeddddd.ae2enhanced.integration;

/** early/Mixin 阶段专用资源探测；不加载目标类，也不读取 Forge 运行时注册状态。 */
public final class BootstrapCapabilities {

    public static final String AE2FC_MAIN = "com/glodblock/github/FluidCraft.class";
    public static final String AE2FC_DUALITY = "com/glodblock/github/interfaces/FCDualityInterface.class";
    public static final String AE2FC_TILE = "com/glodblock/github/common/tile/TileDualInterface.class";
    public static final String GAS_CHANNEL = "com/mekeng/github/common/me/storage/IGasStorageChannel.class";
    public static final String THAUMIC_CHANNEL = "thaumicenergistics/api/storage/IEssentiaStorageChannel.class";
    public static final String MEK_CEU_V10 = "mekanism/api/fluid/IExtendedFluidTank.class";
    public static final String MMCE_PATTERN_PROVIDER = "github/kasuminova/mmce/common/tile/MEPatternProvider.class";
    public static final String FTB_UNIVERSE = "com/feed_the_beast/ftblib/lib/data/Universe.class";

    private BootstrapCapabilities() {
    }

    public static boolean hasResource(String path) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = BootstrapCapabilities.class.getClassLoader();
        }
        return loader != null && loader.getResource(path) != null;
    }

    public static boolean hasAe2fc() {
        return hasResource(AE2FC_MAIN);
    }

    public static boolean hasAe2fcWrapCacheApi() {
        return hasAe2fc() && hasResource(AE2FC_DUALITY) && hasResource(AE2FC_TILE);
    }

    public static boolean hasGasChannel() {
        return hasResource(GAS_CHANNEL);
    }

    public static boolean hasMekCeuV10() {
        return hasResource(MEK_CEU_V10);
    }
}
