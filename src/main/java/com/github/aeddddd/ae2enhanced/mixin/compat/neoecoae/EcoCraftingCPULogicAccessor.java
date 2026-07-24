package com.github.aeddddd.ae2enhanced.mixin.compat.neoecoae;

import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob;

import appeng.crafting.inv.ListCraftingInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * NeoECOAE {@code ECOCraftingCPULogic} 字段访问器.
 * <p>NeoECOAE 仅以 modCompileOnly 引入（不打包）;Mixin 字段访问器要求精确类型匹配,
 * 不支持 Object 宽化,因此直接引用其真实类型.</p>
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", remap = false)
public interface EcoCraftingCPULogicAccessor {
    @Accessor("job")
    ExecutingCraftingJob getJob();

    @Accessor("cpu")
    ECOCraftingCPU getCpu();

    @Accessor("inventory")
    ListCraftingInventory getInventory();
}
