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
            { "nuclearcraft",
                    "com.github.aeddddd.ae2enhanced.diag.harvest.machine.NuclearCraftDump" },
            { "enderio",
                    "com.github.aeddddd.ae2enhanced.diag.harvest.machine.EnderIoDump" },
            { "thermalexpansion",
                    "com.github.aeddddd.ae2enhanced.diag.harvest.machine.ThermalExpansionDump" },
            { "techreborn",
                    "com.github.aeddddd.ae2enhanced.diag.harvest.machine.TechRebornDump" },
            { "draconicevolution",
                    "com.github.aeddddd.ae2enhanced.diag.harvest.machine.DraconicEvolutionDump" },
            { "actuallyadditions",
                    "com.github.aeddddd.ae2enhanced.diag.harvest.machine.ActuallyAdditionsDump" },
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
        // 通用 JEI/HEI 注册表 dump:覆盖全部 JEI 可见机器配方(含流体),
        // 只在物理客户端可用(注册表是客户端结构;专用服务器无)。
        // 注意必须用 getSide()(物理侧):getEffectiveSide() 按线程名判断,
        // 命令在整合式服务器的 "Server thread" 上执行会误判为 SERVER。
        if (!Loader.isModLoaded("jei")
                || !net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
            snapshot.stats.adapters.put("jei", "skipped:unavailable");
            return;
        }
        try {
            Class.forName("com.github.aeddddd.ae2enhanced.diag.harvest.machine.JeiDump")
                    .getMethod("dump", HarvestSnapshot.class)
                    .invoke(null, snapshot);
            snapshot.stats.adapters.put("jei", "ok");
        } catch (Throwable t) {
            snapshot.stats.adapters.put("jei", "error:" + t.getClass().getSimpleName());
            AE2Enhanced.LOGGER.warn("[harvest] jei dump 失败: {}", t.toString());
        }
    }
}
