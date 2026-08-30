package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import java.util.List;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;

import com.blakebr0.extendedcrafting.crafting.CombinationRecipe;
import com.blakebr0.extendedcrafting.crafting.CombinationRecipeManager;
import com.blakebr0.extendedcrafting.crafting.CompressorRecipe;
import com.blakebr0.extendedcrafting.crafting.CompressorRecipeManager;
import com.blakebr0.extendedcrafting.crafting.endercrafter.EnderCrafterRecipeManager;
import com.blakebr0.extendedcrafting.crafting.endercrafter.IEnderCraftingRecipe;
import com.blakebr0.extendedcrafting.crafting.table.TableRecipeManager;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * ExtendedCrafting 机器配方 dump（合成核心/扩展工作台/末影合成器/量子压缩机）.
 * <p>本类直接引用 ExtendedCrafting 类,仅由 {@link MachineDumps} 在 mod 存在时
 * 经 Class.forName 加载.</p>
 */
public final class ExtendedCraftingDump {

    private ExtendedCraftingDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        dumpCombination(snapshot);
        dumpTable(snapshot);
        dumpEnder(snapshot);
        dumpCompressor(snapshot);
    }

    /** 合成核心:中心输入 + 基座输入 → 输出(耗能记 extras). */
    private static void dumpCombination(HarvestSnapshot snapshot) {
        try {
            for (CombinationRecipe recipe : CombinationRecipeManager.getInstance().getRecipes()) {
                StackRef output = RecipeHarvester.stackRef(recipe.getOutput());
                if (output == null) {
                    continue;
                }
                MachineEntry entry = newEntry("combination");
                entry.inputs.add(RecipeHarvester.ingredientRefs(recipe.getInputIngredient()));
                for (Ingredient pedestal : recipe.getPedestalIngredients()) {
                    entry.inputs.add(RecipeHarvester.ingredientRefs(pedestal));
                }
                entry.outputs.add(output);
                entry.extras.put("cost", String.valueOf(recipe.getCost()));
                entry.extras.put("perTick", String.valueOf(recipe.getPerTick()));
                snapshot.machines.add(entry);
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] extendedcrafting combination dump 失败: {}", t.toString());
        }
    }

    /** 扩展工作台(3x3~9x9):按 IRecipe 口径提取. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void dumpTable(HarvestSnapshot snapshot) {
        try {
            // getRecipes 返回 List<ITieredRecipe>(元素即 IRecipe),泛型擦除按 IRecipe 迭代
            for (IRecipe recipe : (List<IRecipe>) (List) TableRecipeManager.getInstance().getRecipes()) {
                MachineEntry entry = fromIRecipe("table", recipe);
                if (entry != null) {
                    snapshot.machines.add(entry);
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] extendedcrafting table dump 失败: {}", t.toString());
        }
    }

    /** 末影合成器:按 IRecipe 口径提取(耗时记 extras). */
    private static void dumpEnder(HarvestSnapshot snapshot) {
        try {
            for (IRecipe recipe : EnderCrafterRecipeManager.getInstance().getRecipes()) {
                MachineEntry entry = fromIRecipe("ender", recipe);
                if (entry != null) {
                    if (recipe instanceof IEnderCraftingRecipe) {
                        entry.extras.put("seconds",
                                String.valueOf(((IEnderCraftingRecipe) recipe).getEnderCrafterTimeSeconds()));
                    }
                    snapshot.machines.add(entry);
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] extendedcrafting ender dump 失败: {}", t.toString());
        }
    }

    /** 量子压缩机:大宗单输入(→输入数量记 StackRef.count)+ 可选催化剂 → 输出. */
    private static void dumpCompressor(HarvestSnapshot snapshot) {
        try {
            for (CompressorRecipe recipe : CompressorRecipeManager.getInstance().getRecipes()) {
                StackRef output = RecipeHarvester.stackRef(recipe.getOutput());
                if (output == null) {
                    continue;
                }
                MachineEntry entry = newEntry("compressor");
                List<StackRef> inputs = RecipeHarvester.ingredientRefs(recipe.getInput());
                int inputCount = recipe.getInputCount();
                for (StackRef ref : inputs) {
                    ref.count = inputCount;
                }
                entry.inputs.add(inputs);
                if (recipe.getCatalyst() != null) {
                    List<StackRef> catalyst = RecipeHarvester.ingredientRefs(recipe.getCatalyst());
                    if (!catalyst.isEmpty()) {
                        entry.extras.put("catalystConsumed", String.valueOf(recipe.consumeCatalyst()));
                        if (recipe.consumeCatalyst()) {
                            entry.inputs.add(catalyst);
                        }
                    }
                }
                entry.outputs.add(output);
                snapshot.machines.add(entry);
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] extendedcrafting compressor dump 失败: {}", t.toString());
        }
    }

    private static MachineEntry fromIRecipe(String type, Object recipeObj) {
        IRecipe recipe = (IRecipe) recipeObj;
        try {
            StackRef output = RecipeHarvester.stackRef(recipe.getRecipeOutput());
            if (output == null) {
                return null;
            }
            MachineEntry entry = newEntry(type);
            for (Ingredient ingredient : recipe.getIngredients()) {
                entry.inputs.add(RecipeHarvester.ingredientRefs(ingredient));
            }
            entry.outputs.add(output);
            return entry;
        } catch (Throwable t) {
            return null; // 单条失败跳过
        }
    }

    private static MachineEntry newEntry(String type) {
        MachineEntry entry = new MachineEntry();
        entry.mod = "extendedcrafting";
        entry.machine = type;
        entry.type = type;
        return entry;
    }
}
