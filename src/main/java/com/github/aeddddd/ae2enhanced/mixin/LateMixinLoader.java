package com.github.aeddddd.ae2enhanced.mixin;

import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Late Mixin 加载器.
 * 从 AssemblyMixinPlugin 中拆分出来,避免 IFMLLoadingPlugin(coremod)在过早阶段
 * 触发 JEI 内部类的加载,导致 CleanroomMC ActualClassLoader 标记 invalid.
 *
 * ae2fc 采用相同的架构：LateMixinLoader 仅实现 ILateMixinLoader,不作为 coremod.
 */
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>(Arrays.asList(
                "mixins.ae2enhanced.late.json",
                "mixins.ae2enhanced.late.tii.json",
                "mixins.ae2enhanced.late.thaumic.json",
                "mixins.ae2enhanced.late.gas.json",
                "mixins.ae2enhanced.late.ring.json"
        ));
        if (!Ae2fcCompat.AE2FC_LOADED) {
            configs.add("mixins.ae2enhanced.late.fluid.json");
        }
        if (Loader.isModLoaded("storagedrawers")) {
            configs.add("mixins.ae2enhanced.late.sd.json");
        }
        if (Loader.isModLoaded("cells")) {
            configs.add("mixins.ae2enhanced.late.cells.json");
        }
        if (Loader.isModLoaded("thermalexpansion")) {
            configs.add("mixins.ae2enhanced.late.thermal.json");
        }
        if (Loader.isModLoaded("mekanism")) {
            if (isMekCeuV10()) {
                configs.add("mixins.ae2enhanced.late.mekceuv10.json");
            } else {
                configs.add("mixins.ae2enhanced.late.mekanism.json");
            }
        }
        if (Loader.isModLoaded("techreborn")) {
            configs.add("mixins.ae2enhanced.late.techreborn.json");
        }
        if (Loader.isModLoaded("enderio")) {
            configs.add("mixins.ae2enhanced.late.eio.json");
        }
        if (Loader.isModLoaded("industrialforegoing")) {
            configs.add("mixins.ae2enhanced.late.industrialforegoing.json");
        }
        if (Loader.isModLoaded("nuclearcraft")) {
            configs.add("mixins.ae2enhanced.late.nuclearcraft.json");
        }
        if (Loader.isModLoaded("thaumcraft")) {
            configs.add("mixins.ae2enhanced.late.thaumcraft.json");
        }
        if (Loader.isModLoaded("projecte")) {
            configs.add("mixins.ae2enhanced.late.projecte.json");
        }
        if (Loader.isModLoaded("botania")) {
            configs.add("mixins.ae2enhanced.late.botania.json");
        }
        if (Loader.isModLoaded("modularmachinery")) {
            configs.add("mixins.ae2enhanced.late.mmce.json");
        }
        return configs;
    }

    private static boolean isMekCeuV10() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null && loader.getResource("mekanism/api/fluid/IExtendedFluidTank.class") != null;
    }
}
