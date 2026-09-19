package com.github.aeddddd.ae2enhanced.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * NuclearCraft Mixin 配置插件.
 * 按 NC 大版本分流: NC:O 2o.x 重制版的 produceProducts() 是 IProcessor 的 default 方法, 走接口
 * Mixin MixinIProcessor; NC 2.19a 的定义在具体处理器基类中, 走 MixinTileItemProcessor 等类 Mixin.
 * 重制版依据独有的 AbstractProcessorElement.class 判别.
 */
public class NuclearCraftMixinPlugin implements IMixinConfigPlugin {

    private boolean overhauledLoaded = false;
    private boolean legacyLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            overhauledLoaded = cl.getResource("nc/tile/internal/processor/AbstractProcessorElement.class") != null;
            legacyLoaded = !overhauledLoaded
                    && cl.getResource("nc/tile/processor/TileItemProcessor.class") != null;
        } catch (Exception ignored) {
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".MixinNCOEnergyProcessor")
                || mixinClassName.endsWith(".MixinNCONuclearFurnace")) {
            // 重制版类 Mixin: 向 TileEnergyProcessor / TileNuclearFurnace 添加
            // produceProducts 的类级重写, 接口 default 方法无法直接注入
            return overhauledLoaded;
        }
        if (mixinClassName.endsWith(".MixinRadiationHandler")) {
            // 辐射 Mixin 两个版本通用
            return overhauledLoaded || legacyLoaded;
        }
        // 其余为 2.19a 具体处理器类 mixin
        return legacyLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
