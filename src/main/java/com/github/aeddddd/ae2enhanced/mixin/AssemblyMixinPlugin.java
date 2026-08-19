package com.github.aeddddd.ae2enhanced.mixin;

import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.Context;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Coremod 空壳 + Early Mixin 加载器.
 * ILateMixinLoader 功能在 {@link LateMixinLoader},避免 coremod 过早加载
 * 导致 CleanroomMC ActualClassLoader 将 JEI 内部类标记为 invalid.
 *
 * 目标为 MC/Forge 原生类的 mixin 必须通过 IEarlyMixinLoader 注册:
 * Cleanroom 下原生类在 late mixin 应用前已被加载,late 配置中的原生目标
 * 会被拒绝("loaded too early")导致功能静默失效.MixinBooter 从 coremod
 * 列表发现 IEarlyMixinLoader 实现,因此挂载在本 coremod 上.
 *
 * 注意:本类常量池不得引入任何第三方 mod 类引用.
 */
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
@IFMLLoadingPlugin.Name("AE2EnhancedMixinPlugin")
public class AssemblyMixinPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    private static final String CONFIG_EARLY = "mixins.ae2enhanced.early.json";
    private static final String CONFIG_EARLY_PROJECTE = "mixins.ae2enhanced.early.projecte.json";
    // 指环 mixin 全部以 MC/Forge 原生类为目标,必须走 early 注册:
    // Cleanroom 下原生类在 late mixin 应用前已被加载,late 注册会被静默拒绝
    // (实测: Cleanroom 服务端 /clear 防护不生效,日志无任何 ring 配置记录).
    // mixin 应用是纯 ASM 字节码读取,不会过早加载本 mod 处理器类.
    private static final String CONFIG_EARLY_RING = "mixins.ae2enhanced.late.ring.json";

    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList(CONFIG_EARLY, CONFIG_EARLY_PROJECTE, CONFIG_EARLY_RING);
    }

    @Override
    public boolean shouldMixinConfigQueue(Context context) {
        if (CONFIG_EARLY_PROJECTE.equals(context.mixinConfig())) {
            return context.isModPresent("projecte");
        }
        return true;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
