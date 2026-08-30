package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import net.minecraftforge.fml.common.Loader;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;

/**
 * 机器配方 dump 适配器调度.
 * <p>反射隔离:各适配器类直接引用第三方 mod 类,只在本类经
 * {@code Loader.isModLoaded} + {@code Class.forName} 门控后加载——
 * 本类自身不持有任何适配器的常量池引用,mod 缺失时优雅跳过.</p>
 */
public final class MachineDumps {

    private MachineDumps() {
    }

    /** 全部适配器(modid → 适配器类名);按存在性门控执行. */
    private static final String[][] ADAPTERS = {
            { "extendedcrafting",
                    "com.github.aeddddd.ae2enhanced.diag.harvest.machine.ExtendedCraftingDump" },
            { "thaumcraft",
                    "com.github.aeddddd.ae2enhanced.diag.harvest.machine.ThaumcraftDump" },
            { "modularmachinery",
                    "com.github.aeddddd.ae2enhanced.diag.harvest.machine.ModularMachineryDump" },
    };

    public static void dumpAll(HarvestSnapshot snapshot) {
        for (String[] adapter : ADAPTERS) {
            String modid = adapter[0];
            if (!Loader.isModLoaded(modid)) {
                snapshot.stats.adapters.put(modid, "skipped:mod_not_loaded");
                continue;
            }
            try {
                Class.forName(adapter[1])
                        .getMethod("dump", HarvestSnapshot.class)
                        .invoke(null, snapshot);
                snapshot.stats.adapters.put(modid, "ok");
            } catch (Throwable t) {
                // 单个适配器失败不中断整体采集
                snapshot.stats.adapters.put(modid, "error:" + t.getClass().getSimpleName());
                AE2Enhanced.LOGGER.warn("[harvest] 机器配方 dump 失败: {} ({})", modid, t.toString());
            }
        }
    }
}
