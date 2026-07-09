package com.github.aeddddd.ae2enhanced.mixin.accessor;

import java.util.Map;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link ExecutingCraftingJob} 字段访问器，替代运行时反射。
 * <p>tasks 的值类型为包级私有内部类，因此返回通配类型，由调用方通过
 * {@link TaskProgressAccessor} 访问具体任务进度。</p>
 */
@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {
    @Accessor("tasks")
    Map<IPatternDetails, ?> getTasks();

    @Accessor("waitingFor")
    ListCraftingInventory getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker getTimeTracker();

    @Accessor("finalOutput")
    GenericStack getFinalOutput();

    @Accessor("finalOutput")
    void setFinalOutput(GenericStack finalOutput);

    @Accessor("remainingAmount")
    long getRemainingAmount();

    @Accessor("remainingAmount")
    void setRemainingAmount(long remainingAmount);
}
