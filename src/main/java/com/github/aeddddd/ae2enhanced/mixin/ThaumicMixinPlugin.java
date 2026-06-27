package com.github.aeddddd.ae2enhanced.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * mixins.ae2enhanced.late.thaumic.json 的 plugin.
 * 在 onLoad 时检测 Thaumic Energistics 是否存在,若不存在则跳过该配置文件下的所有 Mixin.
 */
public class ThaumicMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("AE2E-ThaumicMixinPlugin");
    private boolean thaumicEnergisticsLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        // CleanroomMC 兼容：使用 getResource 检查类文件是否存在,避免 Class.forName 触发 transformer
        // 导致类被 ActualClassLoader 标记为 invalid,进而导致 Thaumic Energistics 自身初始化失败
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.net.URL url = cl.getResource("thaumicenergistics/api/storage/IEssentiaStorageChannel.class");
            thaumicEnergisticsLoaded = url != null;
            // thaumicEnergisticsLoaded 已设置,日志已移除
        } catch (Exception e) {
            thaumicEnergisticsLoaded = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return thaumicEnergisticsLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
