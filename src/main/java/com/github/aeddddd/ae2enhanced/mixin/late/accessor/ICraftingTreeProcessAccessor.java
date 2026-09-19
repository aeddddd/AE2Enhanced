package com.github.aeddddd.ae2enhanced.mixin.late.accessor;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingTreeProcess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * CraftingTreeProcess 的包私有方法访问器, 供计算侧批量请求使用.
 */
@Mixin(value = CraftingTreeProcess.class, remap = false)
public interface ICraftingTreeProcessAccessor {

    /** 单次合成产出 what 的数量, 内部拷贝, 不修改入参. */
    @Invoker("getAmountCrafted")
    IAEItemStack ae2e$invokeGetAmountCrafted(IAEItemStack what);

    /** 满足 remaining 所需份数: 自引用配方 (产物∈输入) 恒返回 1, 否则 ceil(remaining/per). */
    @Invoker("getTimes")
    long ae2e$invokeGetTimes(long remaining, long per);
}
