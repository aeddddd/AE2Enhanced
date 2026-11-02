package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingGateResult;
import com.github.aeddddd.ae2enhanced.craftingapi.SpecialCraftingPorts;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** 特殊 CPU 生命周期、NBT 与单趟配额管线的普通 Java 实现。 */
public final class SpecialCraftingCpuService {

    private SpecialCraftingCpuService() {
    }

    public static void submitted(CraftingCpuAdapter cpu, ICraftingJob job, Object link) {
        if (link == null || !SpecialCraftingPorts.get().isSpecial(job)) {
            return;
        }
        ISpecialCpuAccess access = cpu.access();
        CraftingCpuExecutionContext context = cpu.context();
        context.specialMarked(true);
        Map<ICraftingPatternDetails, Long> totals = new LinkedHashMap<>();
        for (Map.Entry<ICraftingPatternDetails, Object> entry : cpu.tasks().entrySet()) {
            totals.put(entry.getKey(), access.ae2e$taskValue(entry.getValue()));
        }
        SpecialCraftingPorts.get().snapshot(context, totals);
        AE2Enhanced.LOGGER.info("[特殊配方] 特殊计划已提交到计算核心集群: {}", job.getOutput());
    }

    public static void finished(CraftingCpuAdapter cpu) {
        if (cpu.context().specialMarked()) {
            cpu.context().specialMarked(false);
            SpecialCraftingPorts.get().clear(cpu.context());
        }
    }

    public static void writeNbt(CraftingCpuAdapter cpu, NBTTagCompound data) {
        CraftingCpuExecutionContext context = cpu.context();
        if (!context.specialMarked()) {
            return;
        }
        data.setBoolean("ae2eSpecial", true);
        Map<ICraftingPatternDetails, Long> totals = SpecialCraftingPorts.get().totals(context);
        if (totals == null || totals.isEmpty()) {
            return;
        }
        NBTTagList list = new NBTTagList();
        for (Map.Entry<ICraftingPatternDetails, Long> entry : totals.entrySet()) {
            ItemStack pattern = entry.getKey().getPattern();
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            NBTTagCompound item = new NBTTagCompound();
            pattern.writeToNBT(item);
            item.setLong("total", entry.getValue());
            list.appendTag(item);
        }
        if (!list.isEmpty()) {
            data.setTag("ae2eSpecialTotals", list);
        }
    }

    public static void readNbt(CraftingCpuAdapter cpu, NBTTagCompound data) {
        if (!data.getBoolean("ae2eSpecial")) {
            return;
        }
        CraftingCpuExecutionContext context = cpu.context();
        context.specialMarked(true);
        NBTTagList list = data.getTagList("ae2eSpecialTotals", 10);
        if (list.isEmpty()) {
            AE2Enhanced.LOGGER.info("[特殊配方] NBT 恢复特殊集群(无配额快照,退化为原生推送): {}",
                    cpu.finalOutput());
            return;
        }
        Map<ICraftingPatternDetails, Long> totals = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            ItemStack patternStack = new ItemStack(item);
            long total = item.getLong("total");
            if (patternStack.isEmpty() || total <= 0) {
                continue;
            }
            for (ICraftingPatternDetails details : cpu.tasks().keySet()) {
                if (ItemStack.areItemStacksEqual(details.getPattern(), patternStack)) {
                    totals.put(details, total);
                    break;
                }
            }
        }
        if (totals.isEmpty()) {
            AE2Enhanced.LOGGER.warn("[特殊配方] NBT 恢复特殊集群:配额快照匹配失败(样板已变更?),退化为原生推送: {}",
                    cpu.finalOutput());
            return;
        }
        SpecialCraftingPorts.get().snapshot(context, totals);
        AE2Enhanced.LOGGER.info("[特殊配方] NBT 恢复特殊集群:标记 + 配额快照({} 样板)已重建: {}",
                totals.size(), cpu.finalOutput());
    }

    public static CraftingGateResult inject(CraftingCpuAdapter cpu, IAEItemStack input,
            Actionable type, IActionSource source) {
        return SpecialCraftingPorts.get().handleInsert(cpu, input, type, source);
    }

    public static boolean canCraft(CraftingCpuAdapter cpu, ICraftingPatternDetails details,
            BooleanSupplier original) {
        CraftingCpuExecutionContext context = cpu.context();
        if (context.specialMarked()) {
            Set<ICraftingPatternDetails> vetoed = context.vetoedThisTick();
            if (vetoed == null) {
                if (!SpecialCraftingPorts.get().hasQuota(context, cpu.finalOutput())) {
                    vetoed = Collections.emptySet();
                } else {
                    Map<ICraftingPatternDetails, Long> remaining = context.remainingSnapshot();
                    if (remaining == null) {
                        remaining = new LinkedHashMap<>();
                        for (Map.Entry<ICraftingPatternDetails, Object> entry : cpu.tasks().entrySet()) {
                            remaining.put(entry.getKey(), cpu.access().ae2e$taskValue(entry.getValue()));
                        }
                        context.remainingSnapshot(remaining);
                    }
                    vetoed = SpecialCraftingPorts.get().vetoedSet(context, remaining, cpu.finalOutput());
                }
                context.vetoedThisTick(vetoed);
            }
            if (vetoed.contains(details)) {
                return false;
            }
        }
        return original.getAsBoolean();
    }

    public static void tick(CraftingCpuAdapter cpu, IActionSource source) {
        SpecialCraftingPorts.get().tickSettle(cpu, source);
    }

    public static boolean isSpecial(CraftingCpuAdapter cpu) {
        return SpecialCraftingPorts.get().isSpecial(cpu.context());
    }
}
