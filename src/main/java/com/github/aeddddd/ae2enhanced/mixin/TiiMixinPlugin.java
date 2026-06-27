package com.github.aeddddd.ae2enhanced.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * TII（Terminal Interaction Integration）条件 Mixin 插件.
 * <p>
 * 当 TII 存在时，AE2E 自有的 Energy/Mana/Starlight 假物品注入 Mixin 必须禁用，
 * 否则会出现重复显示、重复提取/注入等问题。
 * </p>
 * <p>
 * 检测使用 ClassLoader.getResource，避免在 Mixin 早期阶段触发类加载或 CleanroomMC
 * ActualClassLoader 的额外转换器。
 * </p>
 */
public class TiiMixinPlugin implements IMixinConfigPlugin {

    private static final String TII_API_CLASS_RESOURCE =
            "nyonio/terminal_interaction_integration/api/IResourceProvider.class";

    private boolean tiiLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        java.net.URL url = cl.getResource(TII_API_CLASS_RESOURCE);
        tiiLoaded = url != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // TII 存在时，禁用本配置下所有 Mixin
        return !tiiLoaded;
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
