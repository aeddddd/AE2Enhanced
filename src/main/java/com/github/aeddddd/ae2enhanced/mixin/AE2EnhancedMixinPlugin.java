package com.github.aeddddd.ae2enhanced.mixin;

import java.util.List;
import java.util.Set;

import net.minecraftforge.fml.loading.LoadingModList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin 配置插件,用于将开发环境兼容性 Mixin 限制在开发环境内,
 * 并按目标模组是否加载条件启用第三方兼容 Mixin.
 *
 * <p>AE2 的官方映射开发环境兼容性 Mixin（{@link AppEngBaseMixin}）会修改 AE2 的
 * 初始化时机.该修改只应在 NeoGradle 反混淆开发环境下启用；在生产环境（SRG 运行）
 * 中必须跳过,否则会导致 AE2 初始化被错误延迟.</p>
 *
 * <p>compat.advancedae / compat.neoecoae 包下的兼容 Mixin 以字符串 target 指向
 * 第三方模组类,仅在对应模组加载时启用,避免目标缺失时的噪音与潜在冲突.</p>
 */
public class AE2EnhancedMixinPlugin implements IMixinConfigPlugin {

    private static final String DEV_COMPAT_PROPERTY = "ae2enhanced.devCompat";
    private static final String COMPAT_ADVANCED_AE_PREFIX = "com.github.aeddddd.ae2enhanced.mixin.compat.advancedae.";
    private static final String COMPAT_NEOECOAE_PREFIX = "com.github.aeddddd.ae2enhanced.mixin.compat.neoecoae.";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(COMPAT_ADVANCED_AE_PREFIX)) {
            return isModLoaded("advanced_ae");
        }
        if (mixinClassName.startsWith(COMPAT_NEOECOAE_PREFIX)) {
            return isModLoaded("neoecoae");
        }
        if (!mixinClassName.equals(AppEngBaseMixin.class.getName())) {
            return true;
        }
        return Boolean.getBoolean(DEV_COMPAT_PROPERTY);
    }

    private static boolean isModLoaded(String modId) {
        try {
            LoadingModList modList = LoadingModList.get();
            return modList != null && modList.getModFileById(modId) != null;
        } catch (Throwable t) {
            return false;
        }
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
