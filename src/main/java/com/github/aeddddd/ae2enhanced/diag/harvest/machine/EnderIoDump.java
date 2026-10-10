package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import crazypants.enderio.base.recipe.IMachineRecipe;
import crazypants.enderio.base.recipe.IManyToOneRecipe;
import crazypants.enderio.base.recipe.IRecipe;
import crazypants.enderio.base.recipe.IRecipeInput;
import crazypants.enderio.base.recipe.MachineRecipeRegistry;
import crazypants.enderio.base.recipe.RecipeOutput;
import crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager;
import crazypants.enderio.base.recipe.sagmill.SagMillRecipeManager;
import crazypants.enderio.base.recipe.slicensplice.SliceAndSpliceRecipeManager;
import crazypants.enderio.base.recipe.soul.ISoulBinderRecipe;
import crazypants.enderio.base.recipe.vat.VatRecipeManager;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * EnderIO 机器配方 dump(SAG Mill/合金炉/切片机/灵魂绑定机/Vat).
 * <p>配方源:各 {@code *RecipeManager} 单例的终态列表(配方 XML/CraftTweaker 执行后的结果);
 * 灵魂绑定配方只注册在 {@link MachineRecipeRegistry} 的 soulbinder 槽,从注册表取。
 * 含流体成分或流体产物的配方整条跳过(StackRef 无法表达流体,Vat 配方恒含流体因此不产出条目);
 * SAG Mill 的概率副产物只取概率 &gt;= 1 的确定产物进 outputs,概率记入 extras。
 * 本类直接引用 EnderIO 类,仅由 {@link MachineDumps} 在 mod 存在时经 Class.forName 加载.</p>
 */
public final class EnderIoDump {

    private static final String SAG_MILL = "sag_mill";
    private static final String ALLOY_SMELTER = "alloy_smelter";
    private static final String SLICE_AND_SPLICE = "slice_and_splice";
    private static final String SOUL_BINDER = "soul_binder";
    private static final String VAT = "vat";

    private EnderIoDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        try {
            dumpSagMill(snapshot);
            dumpAlloySmelter(snapshot);
            dumpSliceAndSplice(snapshot);
            dumpSoulBinder(snapshot);
            dumpVat(snapshot);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] enderio dump 失败: {}", t.toString());
        }
    }

    /** SAG Mill:单物品输入(矿词展开为候选) → 确定产物 + 概率副产物(记 extras). */
    private static void dumpSagMill(HarvestSnapshot snapshot) {
        try {
            for (IRecipe recipe : SagMillRecipeManager.getInstance().getRecipes()) {
                addEntry(snapshot, SAG_MILL, recipe);
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] enderio sag_mill dump 失败: {}", t.toString());
        }
    }

    /** 合金炉:1~3 输入槽(可多候选) → 单输出;合成/去重衍生条目一并采集. */
    private static void dumpAlloySmelter(HarvestSnapshot snapshot) {
        try {
            for (IManyToOneRecipe recipe : AlloyRecipeManager.getInstance().getRecipes()) {
                // dedupe 标记条目仅作 JEI 去重提示(isInputForRecipe 恒 false → 机器不可达),不投影
                if (!recipe.isDedupeInput()) {
                    addEntry(snapshot, ALLOY_SMELTER, recipe);
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] enderio alloy_smelter dump 失败: {}", t.toString());
        }
    }

    /** 切片机:固定 6 输入槽 → 单输出. */
    private static void dumpSliceAndSplice(HarvestSnapshot snapshot) {
        try {
            for (IRecipe recipe : SliceAndSpliceRecipeManager.getInstance().getRecipes()) {
                addEntry(snapshot, SLICE_AND_SPLICE, recipe);
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] enderio slice_and_splice dump 失败: {}", t.toString());
        }
    }

    /** 灵魂绑定机:物品输入 + 灵魂瓶 → 输出(灵魂清单/耗能/经验等级记 extras). */
    private static void dumpSoulBinder(HarvestSnapshot snapshot) {
        try {
            for (IMachineRecipe recipe : MachineRecipeRegistry.instance
                    .getRecipesForMachine(MachineRecipeRegistry.SOULBINDER).values()) {
                try {
                    MachineEntry entry = soulBinder(recipe);
                    if (entry != null) {
                        snapshot.machines.add(entry);
                    }
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] enderio soul_binder dump 失败: {}", t.toString());
        }
    }

    /** Vat:全部配方都含流体输入与流体产物,经通用流体闸门整条跳过(恒 0 条). */
    private static void dumpVat(HarvestSnapshot snapshot) {
        try {
            for (IRecipe recipe : VatRecipeManager.getInstance().getRecipes()) {
                addEntry(snapshot, VAT, recipe);
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] enderio vat dump 失败: {}", t.toString());
        }
    }

    private static void addEntry(HarvestSnapshot snapshot, String machine, IRecipe recipe) {
        try {
            MachineEntry entry = convert(machine, recipe);
            if (entry != null) {
                snapshot.machines.add(entry);
            }
        } catch (Throwable t) {
            // 单条失败跳过
        }
    }

    /** 通用 IRecipe → MachineEntry(输入按槽取候选列表,数量沿用配方声明值). */
    private static MachineEntry convert(String machine, IRecipe recipe) {
        if (!recipe.isValid()) {
            return null; // 能量/成分/产物非法的配方机器不可执行,不投影
        }
        if (hasFluid(recipe)) {
            return null; // 含流体成分或流体产物:StackRef 无法表达,整条跳过
        }
        List<StackRef> outputs = new ArrayList<>();
        List<String> chances = new ArrayList<>();
        for (RecipeOutput output : recipe.getOutputs()) {
            StackRef ref = RecipeHarvester.stackRef(output.getOutput());
            if (ref == null) {
                continue;
            }
            if (output.getChance() >= 1.0f) {
                outputs.add(ref);
            } else {
                // 概率副产物不进 outputs(样板只放确定产物),概率随物品记 extras
                chances.add(ref.id + "@" + ref.meta + " chance=" + output.getChance());
            }
        }
        if (outputs.isEmpty()) {
            return null; // 无确定物品产物(纯副产物/纯流体)不投影
        }
        MachineEntry entry = newEntry(machine);
        for (List<ItemStack> slot : recipe.getInputStackAlternatives()) {
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            List<StackRef> refs = new ArrayList<>();
            for (ItemStack stack : slot) {
                StackRef ref = RecipeHarvester.stackRef(stack);
                if (ref != null) {
                    refs.add(ref);
                }
            }
            if (!refs.isEmpty()) {
                entry.inputs.add(refs);
            }
        }
        if (entry.inputs.isEmpty()) {
            return null;
        }
        entry.outputs.addAll(outputs);
        entry.extras.put("energy", String.valueOf(recipe.getEnergyRequired()));
        entry.extras.put("bonus", String.valueOf(recipe.getBonusType()));
        if (!chances.isEmpty()) {
            entry.extras.put("chances", chances.toString());
        }
        return entry;
    }

    /** 流体闸门:任一输入为流体成分或任一输出为流体产物即整条跳过. */
    private static boolean hasFluid(IRecipe recipe) {
        for (IRecipeInput input : recipe.getInputs()) {
            if (input == null || input.isFluid() || input.getFluidInput() != null) {
                return true;
            }
        }
        for (RecipeOutput output : recipe.getOutputs()) {
            if (output == null || output.isFluid() || output.getFluidOutput() != null) {
                return true;
            }
        }
        return false;
    }

    private static MachineEntry soulBinder(IMachineRecipe recipe) {
        if (!(recipe instanceof ISoulBinderRecipe)) {
            return null; // 非灵魂绑定实现不投影
        }
        ISoulBinderRecipe binder = (ISoulBinderRecipe) recipe;
        StackRef input = RecipeHarvester.stackRef(binder.getInputStack());
        StackRef output = RecipeHarvester.stackRef(binder.getOutputStack());
        if (input == null || output == null) {
            return null;
        }
        MachineEntry entry = newEntry(SOUL_BINDER);
        entry.name = recipe.getUid();
        List<StackRef> inputs = new ArrayList<>();
        inputs.add(input);
        entry.inputs.add(inputs);
        entry.outputs.add(output);
        entry.extras.put("energy", String.valueOf(binder.getEnergyRequired()));
        entry.extras.put("xpLevels", String.valueOf(binder.getExperienceLevelsRequired()));
        // 灵魂瓶是灵魂载体(参与匹配但原样返还,净消耗 0),无法用 count>=1 的 StackRef 表达消耗
        entry.extras.put("soulVial", "required_returned");
        if (binder.getSupportedSouls() != null) {
            entry.extras.put("souls", souls(binder.getSupportedSouls()));
        }
        return entry;
    }

    private static String souls(List<ResourceLocation> souls) {
        StringBuilder sb = new StringBuilder();
        for (ResourceLocation soul : souls) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(soul);
        }
        return sb.toString();
    }

    private static MachineEntry newEntry(String machine) {
        MachineEntry entry = new MachineEntry();
        entry.mod = "enderio";
        entry.machine = machine;
        entry.type = machine;
        return entry;
    }
}
