package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import cofh.thermalexpansion.util.managers.machine.CentrifugeManager;
import cofh.thermalexpansion.util.managers.machine.CentrifugeManager.CentrifugeRecipe;
import cofh.thermalexpansion.util.managers.machine.ChargerManager;
import cofh.thermalexpansion.util.managers.machine.ChargerManager.ChargerRecipe;
import cofh.thermalexpansion.util.managers.machine.CompactorManager;
import cofh.thermalexpansion.util.managers.machine.CompactorManager.CompactorRecipe;
import cofh.thermalexpansion.util.managers.machine.CompactorManager.Mode;
import cofh.thermalexpansion.util.managers.machine.EnchanterManager;
import cofh.thermalexpansion.util.managers.machine.EnchanterManager.EnchanterRecipe;
import cofh.thermalexpansion.util.managers.machine.FurnaceManager;
import cofh.thermalexpansion.util.managers.machine.FurnaceManager.FurnaceRecipe;
import cofh.thermalexpansion.util.managers.machine.InsolatorManager;
import cofh.thermalexpansion.util.managers.machine.InsolatorManager.InsolatorRecipe;
import cofh.thermalexpansion.util.managers.machine.PulverizerManager;
import cofh.thermalexpansion.util.managers.machine.PulverizerManager.PulverizerRecipe;
import cofh.thermalexpansion.util.managers.machine.SawmillManager;
import cofh.thermalexpansion.util.managers.machine.SawmillManager.SawmillRecipe;
import cofh.thermalexpansion.util.managers.machine.SmelterManager;
import cofh.thermalexpansion.util.managers.machine.SmelterManager.SmelterRecipe;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * Thermal Expansion 机器配方 dump（粉碎机/红石炉/锯木机/感应炉/离心机/压缩机/附魔器/充能器）.
 * <p>只收物品进物品出的配方:含流体输入或流体输出的配方整条跳过——StackRef 无法表达流体,
 * 残缺条目会伪造出不含流体的合成路径.按此口径不采集的机器与配方:</p>
 * <ul>
 *   <li>岩浆坩埚(CrucibleManager)/分馏塔(RefineryManager)/流体转置机(TransposerManager)/
 *   造石机(ExtruderManager)/沉淀机(PrecipitatorManager)/酿造机(BrewerManager):全部配方含流体;</li>
 *   <li>植生培育机(InsolatorManager):全部配方消耗水(流体输入),整表不采集;</li>
 *   <li>红石炉热解配方:产木馏油(流体),逐条跳过;</li>
 *   <li>离心机:产流体的配方逐条跳过,怪物离心配方恒产经验流体,整表不采集.</li>
 * </ul>
 * <p>概率副产物只取 chance &gt;= 100 的进 outputs,其余以文本记入 extras(byproducts)。
 * count 直接取 ItemStack 自身数量(一次执行实际消耗/产出量),无独立数量字段需覆盖。
 * 输入槽为单候选:TE 配方对象内以具体 ItemStack 查表,矿词归并由 OreValidator 在查询期完成,
 * 此处记录的是配方内实际存放的代表物品.
 * 本类直接引用 TE 类,仅由 {@link MachineDumps} 在 mod 存在时经 Class.forName 加载.</p>
 */
public final class ThermalExpansionDump {

    private ThermalExpansionDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        try {
            dumpPulverizer(snapshot);
            dumpFurnace(snapshot);
            dumpSawmill(snapshot);
            dumpSmelter(snapshot);
            dumpInsolator(snapshot);
            dumpCompactor(snapshot);
            dumpEnchanter(snapshot);
            dumpCentrifuge(snapshot);
            dumpCharger(snapshot);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion dump 失败: {}", t.toString());
        }
    }

    /** 粉碎机:单输入 → 主产出 + 概率副产出(耗能记 extras). */
    private static void dumpPulverizer(HarvestSnapshot snapshot) {
        try {
            for (PulverizerRecipe recipe : PulverizerManager.getRecipeList()) {
                try {
                    MachineEntry entry = newEntry("pulverizer");
                    addInput(entry, recipe.getInput());
                    addOutput(entry, recipe.getPrimaryOutput(), 100);
                    addOutput(entry, recipe.getSecondaryOutput(), recipe.getSecondaryOutputChance());
                    addEnergy(entry, recipe.getEnergy());
                    emit(snapshot, entry);
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion pulverizer dump 失败: {}", t.toString());
        }
    }

    /** 红石炉:烤制单输入 → 单产出;热解配方产木馏油(流体),整条跳过. */
    private static void dumpFurnace(HarvestSnapshot snapshot) {
        try {
            for (FurnaceRecipe recipe : FurnaceManager.getRecipeList(false)) {
                try {
                    MachineEntry entry = newEntry("redstone_furnace");
                    addInput(entry, recipe.getInput());
                    addOutput(entry, recipe.getOutput(), 100);
                    addEnergy(entry, recipe.getEnergy());
                    emit(snapshot, entry);
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion redstone_furnace dump 失败: {}", t.toString());
        }
        try {
            for (FurnaceRecipe recipe : FurnaceManager.getRecipeList(true)) {
                try {
                    if (recipe.getCreosote() > 0) {
                        continue; // 产木馏油(流体)的热解配方不采集
                    }
                    MachineEntry entry = newEntry("redstone_furnace");
                    addInput(entry, recipe.getInput());
                    addOutput(entry, recipe.getOutput(), 100);
                    addEnergy(entry, recipe.getEnergy());
                    entry.extras.put("pyrolysis", "true");
                    emit(snapshot, entry);
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion redstone_furnace pyrolysis dump 失败: {}", t.toString());
        }
    }

    /** 锯木机:单输入 → 主产出 + 概率副产出(耗能记 extras). */
    private static void dumpSawmill(HarvestSnapshot snapshot) {
        try {
            for (SawmillRecipe recipe : SawmillManager.getRecipeList()) {
                try {
                    MachineEntry entry = newEntry("sawmill");
                    addInput(entry, recipe.getInput());
                    addOutput(entry, recipe.getPrimaryOutput(), 100);
                    addOutput(entry, recipe.getSecondaryOutput(), recipe.getSecondaryOutputChance());
                    addEnergy(entry, recipe.getEnergy());
                    emit(snapshot, entry);
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion sawmill dump 失败: {}", t.toString());
        }
    }

    /** 感应炉:主输入 + 助熔剂输入 → 主产出 + 概率副产出(是否助熔配方记 extras). */
    private static void dumpSmelter(HarvestSnapshot snapshot) {
        try {
            for (SmelterRecipe recipe : SmelterManager.getRecipeList()) {
                try {
                    MachineEntry entry = newEntry("smelter");
                    addInput(entry, recipe.getPrimaryInput());
                    addInput(entry, recipe.getSecondaryInput());
                    addOutput(entry, recipe.getPrimaryOutput(), 100);
                    addOutput(entry, recipe.getSecondaryOutput(), recipe.getSecondaryOutputChance());
                    addEnergy(entry, recipe.getEnergy());
                    entry.extras.put("hasFlux", String.valueOf(recipe.hasFlux()));
                    emit(snapshot, entry);
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion smelter dump 失败: {}", t.toString());
        }
    }

    /** 植生培育机:配方恒消耗水(流体输入,getWater() &gt; 0),按流体口径整条跳过. */
    private static void dumpInsolator(HarvestSnapshot snapshot) {
        try {
            for (InsolatorRecipe recipe : InsolatorManager.getRecipeList()) {
                try {
                    if (recipe.getWater() > 0) {
                        continue;
                    }
                    MachineEntry entry = newEntry("insolator");
                    addInput(entry, recipe.getPrimaryInput());
                    addInput(entry, recipe.getSecondaryInput());
                    addOutput(entry, recipe.getPrimaryOutput(), 100);
                    addOutput(entry, recipe.getSecondaryOutput(), recipe.getSecondaryOutputChance());
                    addEnergy(entry, recipe.getEnergy());
                    emit(snapshot, entry);
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion insolator dump 失败: {}", t.toString());
        }
    }

    /** 压缩机:单输入 → 单产出(工作模式 PLATE/COIN/GEAR/ALL 记 extras). */
    private static void dumpCompactor(HarvestSnapshot snapshot) {
        try {
            for (Mode mode : Mode.values()) {
                try {
                    for (CompactorRecipe recipe : CompactorManager.getRecipeList(mode)) {
                        try {
                            MachineEntry entry = newEntry("compactor");
                            addInput(entry, recipe.getInput());
                            addOutput(entry, recipe.getOutput(), 100);
                            addEnergy(entry, recipe.getEnergy());
                            entry.extras.put("mode", mode.name());
                            emit(snapshot, entry);
                        } catch (Throwable t) {
                            // 单条失败跳过
                        }
                    }
                } catch (Throwable t) {
                    AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion compactor {} dump 失败: {}", mode, t.toString());
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion compactor dump 失败: {}", t.toString());
        }
    }

    /** 附魔器:原材料 + 附魔媒介 → 附魔产物(经验/耗能/附魔名记 extras). */
    private static void dumpEnchanter(HarvestSnapshot snapshot) {
        try {
            for (EnchanterRecipe recipe : EnchanterManager.getRecipeList()) {
                try {
                    MachineEntry entry = newEntry("enchanter");
                    addInput(entry, recipe.getPrimaryInput());
                    addInput(entry, recipe.getSecondaryInput());
                    addOutput(entry, recipe.getOutput(), 100);
                    addEnergy(entry, recipe.getEnergy());
                    entry.extras.put("experience", String.valueOf(recipe.getExperience()));
                    entry.extras.put("recipeType", recipe.getType().name());
                    if (recipe.getEnchantName() != null && !recipe.getEnchantName().isEmpty()) {
                        entry.extras.put("enchant", recipe.getEnchantName());
                    }
                    emit(snapshot, entry);
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion enchanter dump 失败: {}", t.toString());
        }
    }

    /** 离心机:单输入 → 多产出(各产出自带概率;产流体的配方整条跳过). */
    private static void dumpCentrifuge(HarvestSnapshot snapshot) {
        try {
            for (CentrifugeRecipe recipe : CentrifugeManager.getRecipeList()) {
                try {
                    if (recipe.getFluid() != null) {
                        continue; // 产流体的配方不采集
                    }
                    MachineEntry entry = newEntry("centrifuge");
                    addInput(entry, recipe.getInput());
                    List<ItemStack> outputs = recipe.getOutput();
                    List<Integer> chances = recipe.getChance();
                    for (int i = 0; i < outputs.size(); i++) {
                        addOutput(entry, outputs.get(i), i < chances.size() ? chances.get(i) : 100);
                    }
                    addEnergy(entry, recipe.getEnergy());
                    emit(snapshot, entry);
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion centrifuge dump 失败: {}", t.toString());
        }
    }

    /** 充能器:TE 本体不注册静态配方(附属/CrT 可加),列表通常为空. */
    private static void dumpCharger(HarvestSnapshot snapshot) {
        try {
            for (ChargerRecipe recipe : ChargerManager.getRecipeList()) {
                try {
                    MachineEntry entry = newEntry("charger");
                    addInput(entry, recipe.getInput());
                    addOutput(entry, recipe.getOutput(), 100);
                    addEnergy(entry, recipe.getEnergy());
                    emit(snapshot, entry);
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thermalexpansion charger dump 失败: {}", t.toString());
        }
    }

    private static MachineEntry newEntry(String machine) {
        MachineEntry entry = new MachineEntry();
        entry.mod = "thermalexpansion";
        entry.machine = machine;
        entry.type = machine;
        return entry;
    }

    /** 单物品输入槽 → 单候选列表(空栈不占槽). */
    private static void addInput(MachineEntry entry, ItemStack stack) {
        StackRef ref = RecipeHarvester.stackRef(stack);
        if (ref == null) {
            return;
        }
        List<StackRef> slot = new ArrayList<>();
        slot.add(ref);
        entry.inputs.add(slot);
    }

    /** 产出:chance &gt;= 100 进 outputs;其余概率副产物以 id#meta xcount chance=n 记入 extras. */
    private static void addOutput(MachineEntry entry, ItemStack stack, int chance) {
        StackRef ref = RecipeHarvester.stackRef(stack);
        if (ref == null) {
            return;
        }
        if (chance >= 100) {
            entry.outputs.add(ref);
            return;
        }
        String desc = describe(ref) + " chance=" + chance;
        String existing = entry.extras.get("byproducts");
        entry.extras.put("byproducts", existing == null ? desc : existing + ", " + desc);
    }

    private static void addEnergy(MachineEntry entry, int energy) {
        entry.extras.put("energy", String.valueOf(energy));
    }

    /** 无物品产出的配方(纯流体/纯能量)不投影. */
    private static void emit(HarvestSnapshot snapshot, MachineEntry entry) {
        if (!entry.outputs.isEmpty()) {
            snapshot.machines.add(entry);
        }
    }

    private static String describe(StackRef ref) {
        return ref.id + "#" + ref.meta + " x" + ref.count;
    }
}
