package com.github.aeddddd.ae2enhanced.mixin.compat.neoecoae;

import java.util.List;
import java.util.Map;

import cn.dancingsnow.neoecoae.api.me.ElapsedTimeTracker;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * NeoECOAE 自带 {@code ExecutingCraftingJob} 字段访问器.
 * <p>NeoECOAE 仅以 modCompileOnly 引入（不打包）.tasks 的值类型为其自带 TaskProgress,
 * 以通配形式返回,由 {@link EcoTaskProgressAccessor} 访问；timeTracker 为其自带
 * {@link ElapsedTimeTracker} 副本（同包,非 AE2 类型）,由
 * {@link EcoElapsedTimeTrackerInvoker} 访问.</p>
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob", remap = false)
public interface EcoExecutingCraftingJobAccessor {
    @Accessor("tasks")
    Map<IPatternDetails, ?> getTasks();

    @Accessor("waitingFor")
    ListCraftingInventory getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker getTimeTracker();

    @Accessor("finalOutput")
    GenericStack getFinalOutput();

    @Invoker("addInFlightOutputs")
    void invokeAddInFlightOutputs(List<GenericStack> stacks, int multiplier);
}
