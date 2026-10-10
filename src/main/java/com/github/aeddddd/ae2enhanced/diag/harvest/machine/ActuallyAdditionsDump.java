package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import java.util.List;

import net.minecraft.item.crafting.Ingredient;

import de.ellpeck.actuallyadditions.api.ActuallyAdditionsAPI;
import de.ellpeck.actuallyadditions.api.recipe.CompostRecipe;
import de.ellpeck.actuallyadditions.api.recipe.CrusherRecipe;
import de.ellpeck.actuallyadditions.api.recipe.EmpowererRecipe;
import de.ellpeck.actuallyadditions.api.recipe.LensConversionRecipe;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * ActuallyAdditions 机器配方 dump（破碎机/赋能器/原子重构机/堆肥桶）.
 * <p>配方来源为 {@link ActuallyAdditionsAPI} 的静态终态列表（AA 及其附属经
 * add*Recipe 追加,无其他运行期注册表）:CRUSHER_RECIPES / EMPOWERER_RECIPES /
 * RECONSTRUCTOR_LENS_CONVERSION_RECIPES / COMPOST_RECIPES。本类直接引用 AA 类,
 * 仅由 {@link MachineDumps} 在 mod 存在时经 Class.forName 加载.</p>
 * <p>跳过口径:无物品产出（如 EnchBookConversion 由 transformHook 动态生成附魔书）、
 * 输入成分为空、含流体输入输出的配方（咖啡机/燃油发电机,液体无法用 StackRef 表达）
 * 整条跳过;概率产出只取 100% 的进 outputs,其余记 extras。</p>
 */
public final class ActuallyAdditionsDump {

    private ActuallyAdditionsDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        try {
            dumpCrusher(snapshot);
            dumpEmpowerer(snapshot);
            dumpReconstructor(snapshot);
            dumpCompost(snapshot);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] actuallyadditions dump 失败: {}", t.toString());
        }
    }

    /** 破碎机:单输入 → 主产出 + 概率第二产出(概率 <100% 记 extras). */
    private static void dumpCrusher(HarvestSnapshot snapshot) {
        try {
            for (CrusherRecipe recipe : ActuallyAdditionsAPI.CRUSHER_RECIPES) {
                try {
                    MachineEntry entry = crusher(recipe);
                    if (entry != null) {
                        snapshot.machines.add(entry);
                    }
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] actuallyadditions crusher dump 失败: {}", t.toString());
        }
    }

    private static MachineEntry crusher(CrusherRecipe recipe) {
        StackRef outputOne = RecipeHarvester.stackRef(recipe.getOutputOne());
        if (outputOne == null) {
            return null; // 无主产出(异常配方)不投影
        }
        List<StackRef> inputs = inputRefs(recipe.getInput());
        if (inputs.isEmpty()) {
            return null;
        }
        MachineEntry entry = newEntry("crusher");
        entry.inputs.add(inputs);
        entry.outputs.add(outputOne);
        StackRef outputTwo = RecipeHarvester.stackRef(recipe.getOutputTwo());
        if (outputTwo != null) {
            // getSecondChance 为百分数,TileEntityGrinder 用 rand.nextInt(100)+1 <= chance 判定
            int chance = recipe.getSecondChance();
            if (chance >= 100) {
                entry.outputs.add(outputTwo);
            } else {
                String desc = describe(outputTwo) + " chance=" + chance;
                String existing = entry.extras.get("byproducts");
                entry.extras.put("byproducts", existing == null ? desc : existing + ", " + desc);
            }
        }
        return entry;
    }

    /** 赋能器:中心输入 + 4 个基座成分 → 输出(耗能/耗时记 extras). */
    private static void dumpEmpowerer(HarvestSnapshot snapshot) {
        try {
            for (EmpowererRecipe recipe : ActuallyAdditionsAPI.EMPOWERER_RECIPES) {
                try {
                    MachineEntry entry = empowerer(recipe);
                    if (entry != null) {
                        snapshot.machines.add(entry);
                    }
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] actuallyadditions empowerer dump 失败: {}", t.toString());
        }
    }

    private static MachineEntry empowerer(EmpowererRecipe recipe) {
        StackRef output = RecipeHarvester.stackRef(recipe.getOutput());
        if (output == null) {
            return null;
        }
        List<StackRef> center = inputRefs(recipe.getInput());
        List<StackRef> standOne = inputRefs(recipe.getStandOne());
        List<StackRef> standTwo = inputRefs(recipe.getStandTwo());
        List<StackRef> standThree = inputRefs(recipe.getStandThree());
        List<StackRef> standFour = inputRefs(recipe.getStandFour());
        // 四个基座成分缺一不可(EmpowererRecipe.matches 要求 4 个全部匹配)
        if (center.isEmpty() || standOne.isEmpty() || standTwo.isEmpty()
                || standThree.isEmpty() || standFour.isEmpty()) {
            return null;
        }
        MachineEntry entry = newEntry("empowerer");
        entry.inputs.add(center);
        entry.inputs.add(standOne);
        entry.inputs.add(standTwo);
        entry.inputs.add(standThree);
        entry.inputs.add(standFour);
        entry.outputs.add(output);
        entry.extras.put("energyPerStand", String.valueOf(recipe.getEnergyPerStand()));
        entry.extras.put("ticks", String.valueOf(recipe.getTime()));
        return entry;
    }

    /** 原子重构机:单输入 → 单输出(耗能/透镜记 extras). */
    private static void dumpReconstructor(HarvestSnapshot snapshot) {
        try {
            for (LensConversionRecipe recipe : ActuallyAdditionsAPI.RECONSTRUCTOR_LENS_CONVERSION_RECIPES) {
                try {
                    MachineEntry entry = reconstructor(recipe);
                    if (entry != null) {
                        snapshot.machines.add(entry);
                    }
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] actuallyadditions reconstructor dump 失败: {}", t.toString());
        }
    }

    private static MachineEntry reconstructor(LensConversionRecipe recipe) {
        StackRef output = RecipeHarvester.stackRef(recipe.getOutput());
        if (output == null) {
            // EnchBookConversion 等输出为空(由 transformHook 动态生成)的配方不投影
            return null;
        }
        List<StackRef> inputs = inputRefs(recipe.getInput());
        if (inputs.isEmpty()) {
            return null;
        }
        MachineEntry entry = newEntry("atomic_reconstructor");
        entry.inputs.add(inputs);
        entry.outputs.add(output);
        entry.extras.put("energy", String.valueOf(recipe.getEnergyUsed()));
        Object lens = recipe.getType();
        if (lens != null) {
            entry.extras.put("lens", lens.getClass().getSimpleName());
        }
        return entry;
    }

    /** 堆肥桶:单输入 → 单输出(与输入 1:1). */
    private static void dumpCompost(HarvestSnapshot snapshot) {
        try {
            for (CompostRecipe recipe : ActuallyAdditionsAPI.COMPOST_RECIPES) {
                try {
                    MachineEntry entry = compost(recipe);
                    if (entry != null) {
                        snapshot.machines.add(entry);
                    }
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] actuallyadditions compost dump 失败: {}", t.toString());
        }
    }

    private static MachineEntry compost(CompostRecipe recipe) {
        StackRef output = RecipeHarvester.stackRef(recipe.getOutput());
        if (output == null) {
            return null;
        }
        List<StackRef> inputs = inputRefs(recipe.getInput());
        if (inputs.isEmpty()) {
            return null;
        }
        MachineEntry entry = newEntry("compost");
        entry.inputs.add(inputs);
        // TileEntityCompost 整堆转换:产出数量被输入堆大小覆盖(声明栈量不参与),即 1 输入 → 1 产出
        output.count = 1;
        entry.outputs.add(output);
        return entry;
    }

    /** 单槽成分 → 候选列表,数量归一到 1(上述机器每槽每次执行只消耗 1 个). */
    private static List<StackRef> inputRefs(Ingredient ingredient) {
        List<StackRef> refs = RecipeHarvester.ingredientRefs(ingredient);
        for (StackRef ref : refs) {
            ref.count = 1;
        }
        return refs;
    }

    /** StackRef → 单行摘要(extras 用). */
    private static String describe(StackRef ref) {
        return ref.id + "#" + ref.meta + " x" + ref.count;
    }

    private static MachineEntry newEntry(String type) {
        MachineEntry entry = new MachineEntry();
        entry.mod = "actuallyadditions";
        entry.machine = type;
        entry.type = type;
        return entry;
    }
}
