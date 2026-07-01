package com.github.aeddddd.ae2enhanced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.core.AppEngBase;
import appeng.hotkeys.HotkeyActions;
import appeng.init.internal.InitGridLinkables;
import appeng.init.internal.InitStorageCells;

/**
 * 开发环境兼容性 Mixin：延迟 AE2 对 {@link appeng.core.definitions.AEItems} 的静态初始化，
 * 并跳过在 NeoGradle 反混淆环境下已经冻结的 {@code CHUNK_GENERATOR} 注册。
 *
 * <p>这些修改只通过 {@link AE2EnhancedMixinPlugin} 在开发环境启用；生产环境（SRG）下
 * 不会应用，避免破坏 AE2 的正常初始化流程。
 */
@Mixin(value = AppEngBase.class, remap = false)
public class AppEngBaseMixin {

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/init/internal/InitGridLinkables;init()V",
                    remap = false
            ),
            remap = false
    )
    private void ae2e$deferInitGridLinkables() {
        // no-op，延迟到 RegisterEvent 之后
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/init/internal/InitStorageCells;init()V",
                    remap = false
            ),
            remap = false
    )
    private void ae2e$deferInitStorageCells() {
        // no-op，延迟到 RegisterEvent 之后
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/hotkeys/HotkeyActions;init()V",
                    remap = false
            ),
            remap = false
    )
    private void ae2e$deferHotkeyActions() {
        // no-op，延迟到 RegisterEvent 之后
    }

    /**
     * 在 NeoGradle 反混淆开发环境下，{@code BuiltInRegistries.CHUNK_GENERATOR}
     * 在 {@code NewRegistryEvent} 触发前已冻结，导致 AE2 注册
     * {@code ae2:spatial_storage} chunk generator 时抛出
     * {@code IllegalStateException: Registry is already frozen}。
     *
     * <p>这里直接取消 AE2 的 {@code registerRegistries} 方法体，由
     * {@link com.github.aeddddd.ae2enhanced.event.AE2CompatEventHandler}
     * 负责创建 AE2 自定义的 {@code keytypes} 注册表（该方法原本的第二件事）。
     * chunk generator 的注册在开发环境下被跳过，因此 spatial storage 维度不可用，
     * 但服务器可以正常启动。
     */
    @Inject(method = "registerRegistries", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$cancelAe2RegisterRegistries(CallbackInfo ci) {
        ci.cancel();
    }
}
