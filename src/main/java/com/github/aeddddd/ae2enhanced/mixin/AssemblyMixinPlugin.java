package com.github.aeddddd.ae2enhanced.mixin;

import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Coremod 空壳.
 * ILateMixinLoader 功能已迁移到 {@link LateMixinLoader},避免 coremod 过早加载
 * 导致 CleanroomMC ActualClassLoader 将 JEI 内部类标记为 invalid.
 */
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
@IFMLLoadingPlugin.Name("AE2EnhancedMixinPlugin")
public class AssemblyMixinPlugin implements IFMLLoadingPlugin {

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
