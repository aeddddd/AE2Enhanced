package com.github.aeddddd.ae2enhanced.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 元件终端(Cell Terminal)相关 Mixin 的条件加载插件。
 *
 * 当 cellterminal 不存在时整体跳过，避免类找不到导致崩溃。
 */
public class CellTerminalMixinPlugin implements IMixinConfigPlugin {

    private boolean cellTerminalLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        cellTerminalLoaded = cl.getResource("com/cellterminal/CellTerminal.class") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return cellTerminalLoaded;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
