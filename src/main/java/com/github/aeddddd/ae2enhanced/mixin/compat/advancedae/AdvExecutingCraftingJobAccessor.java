package com.github.aeddddd.ae2enhanced.mixin.compat.advancedae;

import java.util.Map;

import net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * AdvancedAE 发布版 1.3.5 自带 {@code ExecutingCraftingJob} 字段访问器.
 * <p>AdvancedAE 仅以 modCompileOnly 引入（不打包）.tasks 的值类型为其包私有
 * {@code TaskProgress},以通配形式返回,由 {@link AdvTaskProgressAccessor} 访问.</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob", remap = false)
public interface AdvExecutingCraftingJobAccessor {
    @Accessor("tasks")
    Map<IPatternDetails, ?> getTasks();

    @Accessor("waitingFor")
    ListCraftingInventory getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker getTimeTracker();

    @Accessor("finalOutput")
    GenericStack getFinalOutput();
}
