package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 菜单类型注册中心。
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> DR = DeferredRegister.create(Registries.MENU, AE2Enhanced.MOD_ID);

    private ModMenus() {
    }
}
