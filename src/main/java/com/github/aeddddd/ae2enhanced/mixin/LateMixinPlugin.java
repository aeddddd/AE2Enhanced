package com.github.aeddddd.ae2enhanced.mixin;

import com.github.aeddddd.ae2enhanced.util.compat.HeiCompat;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * mixins.ae2enhanced.late.json 的 plugin.
 *
 * 当前仅用于 HEI 版本兼容：HEI 4.34.0 重构了 GuiContainerWrapper.getIngredientUnderMouse
 * 并新增官方 ISlotIngredientProvider API,旧的 WrapOperation Mixin 在新版上会生成
 * 非法字节码(VerifyError).因此新版 HEI 下跳过 MixinGuiContainerWrapper,
 * 改由 JEI 插件通过官方 API 注册成分提供者.
 */
public class LateMixinPlugin implements IMixinConfigPlugin {

    private static final String HEI_WRAPPER_MIXIN =
            "com.github.aeddddd.ae2enhanced.mixin.late.terminal.MixinGuiContainerWrapper";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 旧版兼容回退：仅在 HEI 缺少 ISlotIngredientProvider API (≤4.33.x) 时应用
        // TODO: 未来几个版本后随旧版 HEI 支持一并移除该 Mixin
        if (HEI_WRAPPER_MIXIN.equals(mixinClassName)) {
            return !HeiCompat.HAS_SLOT_INGREDIENT_PROVIDER;
        }
        return true;
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
