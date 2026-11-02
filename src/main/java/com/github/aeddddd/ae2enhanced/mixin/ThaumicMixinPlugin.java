package com.github.aeddddd.ae2enhanced.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * mixins.ae2enhanced.late.thaumic.json 的配置插件. 在 onLoad 时检测 Thaumic Energistics 是否存在,
 * 不存在时跳过该配置下所有 Mixin.
 */
public class ThaumicMixinPlugin implements IMixinConfigPlugin {

    private boolean thaumicEnergisticsLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            thaumicEnergisticsLoaded = com.github.aeddddd.ae2enhanced.integration.BootstrapCapabilities
                    .hasResource(com.github.aeddddd.ae2enhanced.integration.BootstrapCapabilities.THAUMIC_CHANNEL);
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
