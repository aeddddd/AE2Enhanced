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
        // CleanroomMC 兼容: 用 getResource 检查类文件是否存在, 避免 Class.forName 触发 transformer
        // 导致类被 ActualClassLoader 标记 invalid, 进而导致 AE2FC 自身初始化失败
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.net.URL url = cl.getResource("com/glodblock/github/FluidCraft.class");
            ae2fcNotLoaded = url == null;
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
