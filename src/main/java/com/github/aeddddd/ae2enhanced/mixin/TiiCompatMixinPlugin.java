package com.github.aeddddd.ae2enhanced.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** 仅在 TII 存在时启用其上游兼容修正，不改变无 TII 环境的 AE2 缓存语义。 */
public class TiiCompatMixinPlugin implements IMixinConfigPlugin {
    private static final String TII_API_CLASS_RESOURCE =
            "nyonio/terminal_interaction_integration/api/IResourceProvider.class";
    private boolean tiiLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        tiiLoaded = Thread.currentThread().getContextClassLoader().getResource(TII_API_CLASS_RESOURCE) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return tiiLoaded;
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
