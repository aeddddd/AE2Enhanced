package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.oredict.OreDictionary;

import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.IThaumcraftRecipe;
import thaumcraft.api.crafting.InfusionRecipe;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * Thaumcraft 配方 dump（坩埚/注魔/奥术工作台）.
 * <p>奥术配方(IArcaneRecipe)在 TC6 只注册于 TC 自有配方表,不在 Forge 注册表,
 * 必须在此采集;源质/vis 成本记入 extras。本类直接引用 Thaumcraft 类,
 * 仅由 {@link MachineDumps} 在 mod 存在时经 Class.forName 加载.</p>
 */
public final class ThaumcraftDump {

    private ThaumcraftDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        try {
            for (IThaumcraftRecipe recipe : ThaumcraftApi.getCraftingRecipes().values()) {
                try {
                    MachineEntry entry = convert(recipe);
                    if (entry != null) {
                        snapshot.machines.add(entry);
                    }
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] thaumcraft dump 失败: {}", t.toString());
        }
    }

    private static MachineEntry convert(IThaumcraftRecipe recipe) {
        if (recipe instanceof CrucibleRecipe) {
            return crucible((CrucibleRecipe) recipe);
        }
        if (recipe instanceof InfusionRecipe) {
            return infusion((InfusionRecipe) recipe);
        }
        if (recipe instanceof IArcaneRecipe) {
            return arcane((IArcaneRecipe) recipe);
        }
        return null; // 其他类型(如研究假配方)跳过
    }

    /** 坩埚:催化剂物品 + 源质 → 输出. */
    private static MachineEntry crucible(CrucibleRecipe recipe) {
        StackRef output = RecipeHarvester.stackRef(recipe.getRecipeOutput());
        if (output == null) {
            return null;
        }
        MachineEntry entry = newEntry("crucible");
        entry.inputs.add(RecipeHarvester.ingredientRefs(recipe.getCatalyst()));
        entry.outputs.add(output);
        if (recipe.getAspects() != null) {
            entry.extras.put("aspects", recipe.getAspects().toString());
        }
        return entry;
    }

    /** 注魔:中心物品 + 成分物品 + 源质 → 输出(输出可为矿词字符串,展开为首候选). */
    private static MachineEntry infusion(InfusionRecipe recipe) {
        StackRef output = infusionOutput(recipe.recipeOutput);
        if (output == null) {
            return null;
        }
        MachineEntry entry = newEntry("infusion");
        entry.inputs.add(RecipeHarvester.ingredientRefs(recipe.getRecipeInput()));
        for (Ingredient component : recipe.getComponents()) {
            entry.inputs.add(RecipeHarvester.ingredientRefs(component));
        }
        entry.outputs.add(output);
        entry.extras.put("instability", String.valueOf(recipe.instability));
        if (recipe.aspects != null) {
            entry.extras.put("aspects", recipe.aspects.toString());
        }
        return entry;
    }

    /** 注魔输出:ItemStack 直接取;String 为矿词名,取首候选(仅作类型标识). */
    private static StackRef infusionOutput(Object output) {
        if (output instanceof ItemStack) {
            return RecipeHarvester.stackRef((ItemStack) output);
        }
        if (output instanceof String) {
            for (ItemStack stack : OreDictionary.getOres((String) output, false)) {
                StackRef ref = RecipeHarvester.stackRef(stack);
                if (ref != null) {
                    return ref;
                }
            }
        }
        return null;
    }

    /** 奥术工作台:按 IRecipe 口径提取(vis 成本记 extras). */
    private static MachineEntry arcane(IArcaneRecipe recipe) {
        IRecipe asRecipe = recipe;
        StackRef output = RecipeHarvester.stackRef(asRecipe.getRecipeOutput());
        if (output == null) {
            return null;
        }
        MachineEntry entry = newEntry("arcane");
        for (Ingredient ingredient : asRecipe.getIngredients()) {
            entry.inputs.add(RecipeHarvester.ingredientRefs(ingredient));
        }
        entry.outputs.add(output);
        entry.extras.put("vis", String.valueOf(recipe.getVis()));
        return entry;
    }

    private static MachineEntry newEntry(String type) {
        MachineEntry entry = new MachineEntry();
        entry.mod = "thaumcraft";
        entry.machine = type;
        entry.type = type;
        return entry;
    }
}
