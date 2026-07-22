package com.github.aeddddd.ae2enhanced.mixin.accessor;

import appeng.api.networking.security.IActionHost;
import appeng.menu.AEBaseMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 访问 {@link AEBaseMenu} 的 protected 成员.
 */
@Mixin(value = AEBaseMenu.class, remap = false)
public interface AEBaseMenuAccessor {

    @Invoker("getActionHost")
    IActionHost invokeGetActionHost();
}
