package com.github.aeddddd.ae2enhanced.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * mixins.ae2enhanced.late.gas.json 的 plugin.
 * onLoad 时检测 MekanismEnergistics 的 IGasStorageChannel 是否存在,
 * 不存在则跳过该配置下的全部 Mixin.
 */
public class GasMixinPlugin implements IMixinConfigPlugin {

    private boolean gasChannelLoaded = false;
    private boolean ae2fcLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        // 只探测资源，不触发目标类加载或 transformer。
        gasChannelLoaded = com.github.aeddddd.ae2enhanced.integration.BootstrapCapabilities.hasGasChannel();
        ae2fcLoaded = com.github.aeddddd.ae2enhanced.integration.BootstrapCapabilities.hasAe2fc();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return gasChannelLoaded && !ae2fcLoaded;
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
