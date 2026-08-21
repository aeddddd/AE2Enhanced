package com.github.aeddddd.ae2enhanced.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * ae2fc (AE2 Fluid Crafting) 适配 Mixin 配置插件。
 * 仅在 ae2fc 存在时应用（目标类在其 jar 内）。
 */
public class Ae2fcMixinPlugin implements IMixinConfigPlugin {

    private boolean ae2fcLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.net.URL url = cl.getResource("com/glodblock/github/inventory/FluidConvertingInventoryAdaptor.class");
            ae2fcLoaded = url != null;
        } catch (Exception ignored) {
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return ae2fcLoaded;
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
