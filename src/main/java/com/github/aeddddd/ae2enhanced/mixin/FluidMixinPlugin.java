package com.github.aeddddd.ae2enhanced.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * mixins.ae2enhanced.late.fluid.json 的 plugin.
 * onLoad 时检测 AE2FC 是否安装, 已安装则跳过该配置下的全部 Mixin, 流体交互交由 AE2FC 处理.
 */
public class FluidMixinPlugin implements IMixinConfigPlugin {

    private boolean ae2fcNotLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            ae2fcNotLoaded = !com.github.aeddddd.ae2enhanced.integration.BootstrapCapabilities.hasAe2fc();
        } catch (Exception e) {
            ae2fcNotLoaded = true;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return ae2fcNotLoaded;
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
