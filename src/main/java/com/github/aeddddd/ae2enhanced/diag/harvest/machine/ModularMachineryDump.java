package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.RecipeRegistry;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentRequirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineRegistry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * ModularMachinery CE 机器配方 dump（逐机器:物品成分输入/输出 + tick 数）.
 * <p>矿词成分展开为候选列表;概率成分(chance&lt;1)记入 extras;
 * 流体/能量成分不进 inputs/outputs(fixture 为物品域),仅计数标记。
 * 本类直接引用 MM CE 类,仅由 {@link MachineDumps} 在 mod 存在时经 Class.forName 加载.</p>
 */
public final class ModularMachineryDump {

    private ModularMachineryDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        try {
            for (DynamicMachine machine : MachineRegistry.getRegistry()) {
                String machineName = machine.getRegistryName() != null
                        ? machine.getRegistryName().toString() : "unknown";
                for (MachineRecipe recipe : RecipeRegistry.getRecipesFor(machine)) {
                    try {
                        MachineEntry entry = convert(machineName, recipe);
                        if (entry != null) {
                            snapshot.machines.add(entry);
                        }
                    } catch (Throwable t) {
                        // 单条失败跳过
                    }
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] modularmachinery dump 失败: {}", t.toString());
        }
    }

    private static MachineEntry convert(String machineName, MachineRecipe recipe) {
        MachineEntry entry = new MachineEntry();
        entry.mod = "modularmachinery";
        entry.machine = machineName;
        entry.type = "machine";
        entry.name = recipe.getRegistryName() != null ? recipe.getRegistryName().toString() : null;
        entry.extras.put("ticks", String.valueOf(recipe.getRecipeTotalTickTime()));

        int otherComponents = 0;
        List<String> chances = new ArrayList<>();
        for (ComponentRequirement<?, ?> requirement : recipe.getCraftingRequirements()) {
            if (requirement instanceof RequirementItem) {
                RequirementItem item = (RequirementItem) requirement;
                List<StackRef> refs = itemRefs(item);
                if (refs.isEmpty()) {
                    continue;
                }
                if (item.getActionType() == IOType.INPUT) {
                    entry.inputs.add(refs);
                } else {
                    // 输出成分:取首候选(矿词输出取代表),保留数量
                    entry.outputs.add(refs.get(0));
                }
                if (item.chance < 1.0f) {
                    chances.add(String.valueOf(item.chance));
                }
            } else {
                otherComponents++;
            }
        }
        if (otherComponents > 0) {
            entry.extras.put("otherComponents", String.valueOf(otherComponents));
        }
        if (!chances.isEmpty()) {
            entry.extras.put("chances", chances.toString());
        }
        if (entry.outputs.isEmpty()) {
            return null; // 无物品输出的配方(纯产能/耗能)不投影
        }
        return entry;
    }

    /** 物品成分 → 候选列表(矿词成分展开;数量覆盖到每个候选). */
    private static List<StackRef> itemRefs(RequirementItem item) {
        List<StackRef> refs = new ArrayList<>();
        if (item.oreDictName != null && !item.oreDictName.isEmpty()) {
            for (ItemStack stack : OreDictionary.getOres(item.oreDictName, false)) {
                StackRef ref = RecipeHarvester.stackRef(stack);
                if (ref != null) {
                    ref.count = item.oreDictItemAmount;
                    refs.add(ref);
                }
            }
            return refs;
        }
        StackRef ref = RecipeHarvester.stackRef(item.required);
        if (ref != null) {
            refs.add(ref);
        }
        return refs;
    }
}
