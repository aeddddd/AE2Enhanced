package com.github.aeddddd.ae2enhanced.mixin.compat.advancedae;

import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;

import appeng.crafting.inv.ListCraftingInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * AdvancedAE {@code AdvCraftingCPULogic} 字段访问器.
 * <p>AdvancedAE 仅以 modCompileOnly 引入（不打包）;Mixin 字段访问器要求精确类型匹配,
 * 不支持 Object 宽化,因此直接引用其真实类型.</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public interface AdvCraftingCPULogicAccessor {
    @Accessor("job")
    ExecutingCraftingJob getJob();

    @Accessor("cpu")
    AdvCraftingCPU getCpu();

    @Accessor("inventory")
    ListCraftingInventory getInventory();
}
