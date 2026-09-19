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
        // CleanroomMC 兼容: 用 getResource 检查类文件是否存在, 避免 Class.forName 触发 transformer
        // 导致类被 ActualClassLoader 标记 invalid, 进而导致 MekanismEnergistics 自身初始化失败
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.net.URL url = cl.getResource("com/mekeng/github/common/me/storage/IGasStorageChannel.class");
            gasChannelLoaded = url != null;
        } catch (Exception e) {
            gasChannelLoaded = false;
        }

        // AE2FC 已安装时气体交互也交给 AE2FC 处理, 跳过 AE2E 的气体 Mixin
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.net.URL url = cl.getResource("com/glodblock/github/FluidCraft.class");
            ae2fcLoaded = url != null;
        } catch (Exception e) {
            ae2fcLoaded = false;
        }
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
