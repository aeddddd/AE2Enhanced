package com.github.aeddddd.ae2enhanced.mixin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 在 {@link CraftingCpuLogic#executeCrafting} 中拦截样板推送，
 * 当目标提供者是装配枢纽的通用 ME 接口时，一次性批量处理多个副本，
 * 使并行升级真正生效。
 * <p>具体做法：从 CPU 本地库存中额外提取 {@code batchSize - 1} 份输入，
 * 将原 1 份输入与额外输入一并交给装配枢纽消耗，并修正 CPU 的预期产物与剩余物统计。</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public class MixinCraftingCpuLogic {

    @Unique
    private static final Field AE2E$TASK_PROGRESS_VALUE_FIELD;

    static {
        try {
            Class<?> taskProgressClass = Class.forName("appeng.crafting.execution.ExecutingCraftingJob$TaskProgress");
            AE2E$TASK_PROGRESS_VALUE_FIELD = taskProgressClass.getDeclaredField("value");
            AE2E$TASK_PROGRESS_VALUE_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] Failed to access ExecutingCraftingJob.TaskProgress.value", e);
        }
    }

    @WrapOperation(method = "executeCrafting", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"), remap = false)
    private boolean ae2e$wrapPushPattern(
            ICraftingProvider provider,
            IPatternDetails details,
            KeyCounter[] inputs,
            Operation<Boolean> original,
            @Local(ordinal = 0) Map.Entry<?, ?> task,
            @Local(ordinal = 0) KeyCounter expectedOutputs,
            @Local(ordinal = 1) KeyCounter expectedContainerItems) {

        // 仅当提供者是装配枢纽接口时才启用批量逻辑
        if (!(provider instanceof MultiblockMeInterfaceBlockEntity meInterface)) {
            return original.call(provider, details, inputs);
        }
        var controller = meInterface.getController();
        if (!(controller instanceof AssemblyControllerBlockEntity hub)) {
            return original.call(provider, details, inputs);
        }

        long remaining = ae2e$getTaskValue(task);
        long cap = hub.getParallelCap();
        long batchSize = Math.min(remaining, cap);
        if (batchSize <= 1) {
            return original.call(provider, details, inputs);
        }

        // 从 CPU 本地库存中额外提取 batchSize - 1 份输入
        ListCraftingInventory cpuInv = ((CraftingCpuLogic) (Object) this).getInventory();
        KeyCounter[] extraInputs = ae2e$tryExtractExtraInputs(cpuInv, inputs, batchSize - 1);
        if (extraInputs == null) {
            // CPU 库存不足以支撑批量，回退到原始单份推送
            return original.call(provider, details, inputs);
        }

        // 执行批量推送
        if (!hub.pushPatternBatch(details, inputs, null, batchSize)) {
            // 批量推送失败，将额外提取的输入重新注入 CPU 库存
            ae2e$reinjectKeyCounters(extraInputs, cpuInv);
            return false;
        }

        // 原始代码会再将 value 减 1，因此这里只扣除 batchSize - 1
        ae2e$setTaskValue(task, remaining - (batchSize - 1));

        // 原始代码只插入 1 份预期输出，这里补充 batchSize - 1 份
        ae2e$multiplyKeyCounter(expectedOutputs, batchSize - 1);
        ae2e$multiplyKeyCounter(expectedContainerItems, batchSize - 1);

        return true;
    }

    /**
     * 尝试从 CPU 本地库存中提取额外副本的输入。
     *
     * @param cpuInv     CPU 本地库存
     * @param inputs     已提取的 1 份输入
     * @param extraCount 需要额外提取的副本数
     * @return 提取结果，若库存不足则返回 null
     */
    @Unique
    private KeyCounter[] ae2e$tryExtractExtraInputs(ListCraftingInventory cpuInv, KeyCounter[] inputs, long extraCount) {
        KeyCounter[] result = new KeyCounter[inputs.length];

        // 先模拟提取，确保库存足够
        for (int i = 0; i < inputs.length; i++) {
            result[i] = new KeyCounter();
            for (var entry : inputs[i]) {
                AEKey key = entry.getKey();
                long needed = ae2e$safeMultiply(entry.getLongValue(), extraCount);
                if (needed <= 0) {
                    continue;
                }
                long available = cpuInv.extract(key, needed, Actionable.SIMULATE);
                if (available < needed) {
                    return null;
                }
                result[i].add(key, available);
            }
        }

        // 实际扣除
        for (KeyCounter extra : result) {
            for (var entry : extra) {
                cpuInv.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
        }

        return result;
    }

    @Unique
    private static void ae2e$reinjectKeyCounters(KeyCounter[] counters, ListCraftingInventory cpuInv) {
        if (counters == null) {
            return;
        }
        for (KeyCounter counter : counters) {
            for (var entry : counter) {
                cpuInv.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
        }
    }

    @Unique
    private static long ae2e$getTaskValue(Map.Entry<?, ?> task) {
        try {
            Object progress = task.getValue();
            return AE2E$TASK_PROGRESS_VALUE_FIELD.getLong(progress);
        } catch (Exception e) {
            return 1;
        }
    }

    @Unique
    private static void ae2e$setTaskValue(Map.Entry<?, ?> task, long value) {
        try {
            Object progress = task.getValue();
            AE2E$TASK_PROGRESS_VALUE_FIELD.setLong(progress, value);
        } catch (Exception e) {
            // 失败时交给原始代码继续处理，避免崩溃
        }
    }

    @Unique
    private static void ae2e$multiplyKeyCounter(KeyCounter counter, long multiplier) {
        if (multiplier <= 0) {
            return;
        }
        List<Object2LongMap.Entry<AEKey>> entries = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            entries.add(entry);
        }
        for (Object2LongMap.Entry<AEKey> entry : entries) {
            long extra = ae2e$safeMultiply(entry.getLongValue(), multiplier);
            if (extra > 0) {
                counter.add(entry.getKey(), extra);
            }
        }
    }

    @Unique
    private static long ae2e$safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
