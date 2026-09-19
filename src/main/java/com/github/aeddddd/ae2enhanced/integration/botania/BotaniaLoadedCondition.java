package com.github.aeddddd.ae2enhanced.integration.botania;

import com.google.gson.JsonObject;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.Loader;

import java.util.function.BooleanSupplier;

/**
 * 配方条件: 仅在 Botania 已安装时启用魔力节点配方.
 *
 * <p>与 GameRegistryManager 中方块注册条件保持一致.
 * 不引用任何 Botania 类，可安全无条件加载.</p>
 */
public class BotaniaLoadedCondition implements IConditionFactory {

    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        return () -> Loader.isModLoaded("botania");
    }
}
