package com.github.aeddddd.ae2enhanced.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin 配置插件，用于将开发环境兼容性 Mixin 限制在开发环境内。
 *
 * <p>AE2 的官方映射开发环境兼容性 Mixin（{@link AppEngBaseMixin}）会修改 AE2 的
 * 初始化时机。该修改只应在 NeoGradle 反混淆开发环境下启用；在生产环境（SRG 运行）
 * 中必须跳过，否则会导致 AE2 初始化被错误延迟。
 */
public class AE2EnhancedMixinPlugin implements IMixinConfigPlugin {

    private static final String DEV_COMPAT_PROPERTY = "ae2enhanced.devCompat";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.equals(AppEngBaseMixin.class.getName())) {
            return true;
        }
        return Boolean.getBoolean(DEV_COMPAT_PROPERTY);
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
