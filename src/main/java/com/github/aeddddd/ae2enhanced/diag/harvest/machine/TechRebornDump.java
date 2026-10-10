package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import reborncore.api.praescriptum.ingredients.input.FluidStackInputIngredient;
import reborncore.api.praescriptum.ingredients.input.InputIngredient;
import reborncore.api.praescriptum.ingredients.input.ItemStackInputIngredient;
import reborncore.api.praescriptum.ingredients.input.OreDictionaryInputIngredient;
import reborncore.api.praescriptum.recipes.Recipe;
import reborncore.api.recipe.IBaseRecipeType;
import reborncore.api.recipe.RecipeHandler;
import reborncore.common.recipes.IRecipeInput;

import techreborn.api.recipe.Recipes;
import techreborn.api.recipe.machines.BlastFurnaceRecipe;
import techreborn.api.recipe.machines.IndustrialGrinderRecipe;
import techreborn.api.recipe.machines.IndustrialSawmillRecipe;
import techreborn.api.recipe.machines.ScrapboxRecipe;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * TechReborn 机器配方 dump（RebornCore 两条并行配方体系全覆盖）.
 * <p>旧版 crafter 体系:{@code reborncore.api.recipe.RecipeHandler.recipeList},由机器
 * tile 的 {@code RecipeCrafter} 按 recipeName（即 {@code Reference.X_RECIPE}）取用,
 * 覆盖高炉/真空冷冻机/工业电解机/聚爆压缩机/蒸馏塔/工业研磨机/工业锯木机/废料盒。</p>
 * <p>新版 praescriptum 体系:{@code techreborn.api.recipe.Recipes} 的静态处理器
 * （合金炉/装配机/离心机/化学反应器/压缩机/提取机/磨粉机/压板机/固体装罐机/拉丝机）,
 * 机器名取 {@code RecipeHandler.name}。</p>
 * <p>machine/type 统一取配方的机器名;输入成分逐项展开为候选列表（矿词展开全部成员）;
 * 流体无法用 StackRef 表达,凡工艺必需流体或含 FluidStack 成分/输出的配方整条跳过。
 * 废料盒(SCRAPBOX_RECIPE)为等概率随机产出,建模会成为"1 废料盒 → 任意物品"的
 * 万能源,整条跳过。
 * 本类直接引用 TechReborn/RebornCore 类,仅由 {@link MachineDumps} 在 mod 存在时经
 * Class.forName 加载。</p>
 */
public final class TechRebornDump {

    private TechRebornDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        try {
            dumpCrafterRecipes(snapshot);
            dumpPraescriptumRecipes(snapshot);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] techreborn dump 失败: {}", t.toString());
        }
    }

    /** 旧版 crafter 体系:reborncore 静态 recipeList（tile 的 RecipeCrafter 消费）. */
    private static void dumpCrafterRecipes(HarvestSnapshot snapshot) {
        try {
            for (IBaseRecipeType recipe : RecipeHandler.recipeList) {
                try {
                    MachineEntry entry = fromCrafterRecipe(recipe);
                    if (entry != null) {
                        snapshot.machines.add(entry);
                    }
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] techreborn crafter dump 失败: {}", t.toString());
        }
    }

    /** 旧版配方:输入可为 ItemStack / 矿词名 / IRecipeInput;工艺必需流体的整条跳过. */
    private static MachineEntry fromCrafterRecipe(IBaseRecipeType recipe) {
        // 废料盒为等概率随机产出(ScrapboxRecipeCrafter.nextInt 抽取),建模进来会成为
        // "1 废料盒 → 任意物品"的万能源,整条跳过
        if (recipe instanceof ScrapboxRecipe) {
            return null;
        }
        String machine = recipe.getRecipeName();
        List<Object> inputs = recipe.getInputs();
        List<ItemStack> outputs = recipe.getOutputs();
        if (machine == null || inputs == null || inputs.isEmpty() || outputs == null || outputs.isEmpty()) {
            return null;
        }
        // 工业研磨机/工业锯木机的水/汞为必需输入(见 TileIndustrialGrinder.canCraft),无法表达则整条跳过
        if (hasRequiredFluid(recipe)) {
            return null;
        }
        MachineEntry entry = newEntry(machine);
        entry.extras.put("userName", String.valueOf(recipe.getUserFreindlyName()));
        entry.extras.put("tickTime", String.valueOf(recipe.tickTime()));
        entry.extras.put("euPerTick", String.valueOf(recipe.euPerTick()));
        if (recipe instanceof BlastFurnaceRecipe) {
            entry.extras.put("neededHeat", String.valueOf(((BlastFurnaceRecipe) recipe).neededHeat));
        }
        for (Object input : inputs) {
            List<StackRef> refs = crafterInputRefs(input);
            if (refs == null) {
                return null; // 无法表达的输入(流体等) → 整条跳过
            }
            entry.inputs.add(refs);
        }
        for (ItemStack output : outputs) {
            StackRef ref = RecipeHarvester.stackRef(output);
            if (ref == null) {
                return null; // 输出无法表达 → 整条跳过
            }
            entry.outputs.add(ref);
        }
        if (entry.outputs.isEmpty()) {
            return null;
        }
        return entry;
    }

    /**
     * 旧版输入对象 → 候选列表;无法用物品表达返回 null.
     * <p>数量语义按 {@code RecipeCrafter.useAllInputs}:ItemStack 与 IRecipeInput 按注册数量消耗,
     * 裸矿词名每次消耗 1。</p>
     */
    private static List<StackRef> crafterInputRefs(Object input) {
        if (input instanceof ItemStack) {
            StackRef ref = RecipeHarvester.stackRef((ItemStack) input);
            if (ref == null) {
                return null;
            }
            List<StackRef> refs = new ArrayList<>();
            refs.add(ref);
            return refs;
        }
        if (input instanceof String) {
            List<StackRef> refs = new ArrayList<>();
            for (ItemStack stack : OreDictionary.getOres((String) input, false)) {
                StackRef ref = RecipeHarvester.stackRef(stack);
                if (ref != null) {
                    ref.count = 1;
                    refs.add(ref);
                }
            }
            return refs.isEmpty() ? null : refs;
        }
        if (input instanceof IRecipeInput) {
            List<StackRef> refs = new ArrayList<>();
            for (ItemStack stack : ((IRecipeInput) input).getAllStacks()) {
                StackRef ref = RecipeHarvester.stackRef(stack);
                if (ref != null) {
                    // getAllStacks 已把 OreRecipeInput.size 写入 count
                    refs.add(ref);
                }
            }
            return refs.isEmpty() ? null : refs;
        }
        return null; // FluidStack 等非物品成分
    }

    /** 旧版配方是否要求流体(仅工业研磨机/工业锯木机带 fluidStack,为 null 时该配方无流体需求). */
    private static boolean hasRequiredFluid(IBaseRecipeType recipe) {
        if (recipe instanceof IndustrialGrinderRecipe) {
            return ((IndustrialGrinderRecipe) recipe).fluidStack != null;
        }
        if (recipe instanceof IndustrialSawmillRecipe) {
            return ((IndustrialSawmillRecipe) recipe).fluidStack != null;
        }
        return false;
    }

    /** 新版 praescriptum 体系:techreborn.api.recipe.Recipes 的静态处理器列表. */
    private static void dumpPraescriptumRecipes(HarvestSnapshot snapshot) {
        collectPraescriptum(snapshot, Recipes.alloySmelter);
        collectPraescriptum(snapshot, Recipes.assemblingMachine);
        collectPraescriptum(snapshot, Recipes.centrifuge);
        collectPraescriptum(snapshot, Recipes.chemicalReactor);
        collectPraescriptum(snapshot, Recipes.compressor);
        collectPraescriptum(snapshot, Recipes.extractor);
        collectPraescriptum(snapshot, Recipes.grinder);
        collectPraescriptum(snapshot, Recipes.plateBendingMachine);
        collectPraescriptum(snapshot, Recipes.recycler);
        collectPraescriptum(snapshot, Recipes.solidCanningMachine);
        collectPraescriptum(snapshot, Recipes.wireMill);
    }

    /** 单个 praescriptum 处理器（处理器类与旧版 RecipeHandler 同名,形参用全限定名区分）. */
    private static void collectPraescriptum(HarvestSnapshot snapshot,
            reborncore.api.praescriptum.recipes.RecipeHandler handler) {
        if (handler == null) {
            return; // 处理器未初始化(如 TechReborn 初始化未走到该机器)
        }
        String machine = handler.name;
        try {
            for (Recipe recipe : handler.getRecipes()) {
                try {
                    MachineEntry entry = fromPraescriptumRecipe(machine, recipe);
                    if (entry != null) {
                        snapshot.machines.add(entry);
                    }
                } catch (Throwable t) {
                    // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] techreborn {} dump 失败: {}", machine, t.toString());
        }
    }

    /** praescriptum 配方:物品成分输入/输出;含流体成分或流体输出的整条跳过. */
    private static MachineEntry fromPraescriptumRecipe(String machine, Recipe recipe) {
        List<InputIngredient<?>> ingredients = recipe.getInputIngredients();
        ItemStack[] outputs = recipe.getItemOutputs();
        if (ingredients == null || ingredients.isEmpty() || outputs == null || outputs.length == 0) {
            return null;
        }
        if (recipe.getFluidOutputs().length > 0) {
            return null; // 流体输出无法用 StackRef 表达
        }
        MachineEntry entry = newEntry(machine);
        entry.extras.put("energyCostPerTick", String.valueOf(recipe.getEnergyCostPerTick()));
        entry.extras.put("operationDuration", String.valueOf(recipe.getOperationDuration()));
        entry.extras.put("useNbt", String.valueOf(recipe.shouldUseNBT()));
        for (InputIngredient<?> ingredient : ingredients) {
            List<StackRef> refs = praescriptumInputRefs(ingredient);
            if (refs == null) {
                return null; // 无法表达的成分(流体/未知类型) → 整条跳过
            }
            entry.inputs.add(refs);
        }
        for (ItemStack output : outputs) {
            StackRef ref = RecipeHarvester.stackRef(output);
            if (ref == null) {
                return null;
            }
            entry.outputs.add(ref);
        }
        if (entry.outputs.isEmpty()) {
            return null;
        }
        return entry;
    }

    /** praescriptum 成分 → 候选列表(矿词展开全部成员);流体/未知类型返回 null. */
    private static List<StackRef> praescriptumInputRefs(InputIngredient<?> ingredient) {
        if (ingredient == null || ingredient instanceof FluidStackInputIngredient) {
            return null;
        }
        if (ingredient instanceof ItemStackInputIngredient) {
            StackRef ref = RecipeHarvester.stackRef((ItemStack) ingredient.ingredient);
            if (ref == null) {
                return null;
            }
            List<StackRef> refs = new ArrayList<>();
            refs.add(ref); // 数量取栈自身 count,与 InputIngredient.getCount() 一致
            return refs;
        }
        if (ingredient instanceof OreDictionaryInputIngredient) {
            OreDictionaryInputIngredient ore = (OreDictionaryInputIngredient) ingredient;
            if (ore.amount <= 0) {
                return null;
            }
            List<StackRef> refs = new ArrayList<>();
            for (ItemStack stack : OreDictionary.getOres(ore.ingredient, false)) {
                StackRef ref = RecipeHarvester.stackRef(stack);
                if (ref != null) {
                    ref.count = ore.amount;
                    refs.add(ref);
                }
            }
            return refs.isEmpty() ? null : refs;
        }
        return null; // 自定义成分类型不投影,避免产出残缺配方
    }

    private static MachineEntry newEntry(String machine) {
        MachineEntry entry = new MachineEntry();
        entry.mod = "techreborn";
        entry.machine = machine;
        entry.type = machine;
        return entry;
    }
}
