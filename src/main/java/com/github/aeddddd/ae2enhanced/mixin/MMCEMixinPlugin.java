package com.github.aeddddd.ae2enhanced.mixin;

import com.github.aeddddd.ae2enhanced.integration.BootstrapCapabilities;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Modular Machinery: Community Edition 智能样板适配 Mixin 配置插件.
 */
public class MMCEMixinPlugin implements IMixinConfigPlugin {

    private boolean mmceLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        mmceLoaded = BootstrapCapabilities.hasResource(BootstrapCapabilities.MMCE_PATTERN_PROVIDER);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return mmceLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
