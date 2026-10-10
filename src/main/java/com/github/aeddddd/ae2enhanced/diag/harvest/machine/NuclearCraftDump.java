package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import nc.recipe.AbstractRecipeHandler;
import nc.recipe.NCRecipes;
import nc.recipe.ingredient.IItemIngredient;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * NuclearCraft 机器配方 dump（处理器/衰变发生器/裂变燃料等）.
 *
 * <p>NC 存在两条互不兼容的代码谱系,本适配器都要覆盖:</p>
 * <ul>
 * <li>编译依赖 libs/nuclearcraft-1.12.2-2o.9.7.jar（Overhauled）:{@code NCRecipes} 的静态机器字段
 * 类型为 {@code BasicRecipeHandler},配方取值为
 * {@code getItemIngredients/getFluidIngredients/getItemProducts/getFluidProducts/getExtras};</li>
 * <li>目标整合包为 NC 2.19a（非 Overhauled）:字段类型为 {@code ProcessorRecipeHandler},配方取值为
 * {@code itemIngredients/fluidIngredients/itemProducts/fluidProducts/extras},且机器字段名有差异
 * （{@code separator}/{@code isotope_separator}、{@code electrolyzer}/{@code electrolyser}）。</li>
 * </ul>
 *
 * <p>两谱系只有 {@code NCRecipes}/{@code AbstractRecipeHandler}/{@code IItemIngredient} 三个类型同名同形,
 * 故本类直接引用这三个类型,机器字段按注册名（含别名）反射读取,配方成分按候选方法名反射取用;
 * 单版缺失的字段/方法静默跳过（即该版本没有这台机器）,不中断整体采集。</p>
 *
 * <p>含流体成分或流体产物的配方整条跳过（StackRef 无法表达流体）;无物品产出的配方同样跳过。
 * 数量语义:成分展开用 {@code getInputStackList()},产出取 {@code getStack()},其物品栈 count
 * 即一次执行实际消耗/产出的数量（矿词成分展开为全部成员,数量相同）;概率产出取标称（最大）产量,
 * 概率记入 extras 供下游折算。配方 extras（时间/功率/热量倍率等）原样字符串化。</p>
 *
 * <p>本类直接引用 NuclearCraft 类,仅由 {@link MachineDumps} 在 mod 存在时经 Class.forName 加载。</p>
 */
public final class NuclearCraftDump {

    /** 机器注册名（同一行内为各版本字段名别名,首个命中者即 machine/type）。 */
    private static final String[][] MACHINES = {
            { "manufactory" },
            { "separator", "isotope_separator" },
            { "decay_hastener" },
            { "fuel_reprocessor" },
            { "alloy_furnace" },
            { "infuser" },
            { "melter" },
            { "supercooler" },
            { "electrolyzer", "electrolyser" },
            { "assembler" },
            { "ingot_former" },
            { "pressurizer" },
            { "chemical_reactor" },
            { "salt_mixer" },
            { "crystallizer" },
            { "enricher" },
            { "dissolver" },
            { "extractor" },
            { "centrifuge" },
            { "rock_crusher" },
            { "electric_furnace" },
            { "irradiator" },
            { "collector" },
            { "decay_generator" },
            { "fission" },
            { "fusion" },
            { "active_cooler" },
            { "salt_fission" },
            { "coolant_heater" },
            { "heat_exchanger" },
            { "turbine" },
            { "condenser" },
    };

    /** 配方取值方法缓存（按配方类;一个版本内配方类固定）。 */
    private static final Map<Class<?>, Accessors> ACCESSORS = new LinkedHashMap<>();

    private NuclearCraftDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        try {
            for (String[] machine : MACHINES) {
                try {
                    dumpMachine(snapshot, machine);
                } catch (Throwable t) {
                    // 单台机器失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] nuclearcraft dump 失败: {}", t.toString());
        }
    }

    /** 单台机器:别名中首个命中的字段名即机器注册名,逐条配方转换. */
    private static void dumpMachine(HarvestSnapshot snapshot, String[] names) {
        for (String name : names) {
            AbstractRecipeHandler<?> handler = handler(name);
            if (handler == null) {
                continue; // 该版本无此机器(字段名不同或字段未初始化)
            }
            List<?> recipes = handler.getRecipeList();
            if (recipes != null) {
                for (Object recipe : recipes) {
                    try {
                        MachineEntry entry = convert(name, recipe);
                        if (entry != null) {
                            snapshot.machines.add(entry);
                        }
                    } catch (Throwable t) {
                        // 单条失败跳过
                    }
                }
            }
            return; // 别名命中即止,不同时采集两个别名
        }
    }

    /** 单条配方 → MachineEntry（含流体整条跳过;无物品产出跳过）. */
    private static MachineEntry convert(String machine, Object recipe) throws Exception {
        Accessors accessors = accessors(recipe.getClass());
        if (accessors == null) {
            return null; // 配方取值方法未识别(未知版本),跳过
        }
        if (!list(accessors.fluidIngredients.invoke(recipe)).isEmpty()
                || !list(accessors.fluidProducts.invoke(recipe)).isEmpty()) {
            return null; // 含流体成分或流体产物:StackRef 无法表达,整条跳过
        }
        MachineEntry entry = newEntry(machine);
        String chance = "";
        for (Object ingredient : list(accessors.itemProducts.invoke(recipe))) {
            if (!(ingredient instanceof IItemIngredient)) {
                continue;
            }
            IItemIngredient product = (IItemIngredient) ingredient;
            StackRef output = RecipeHarvester.stackRef(product.getStack());
            if (output == null) {
                continue; // 空槽
            }
            entry.outputs.add(output);
            int percent = chancePercent(product);
            if (percent < 100) {
                chance = chance + (chance.isEmpty() ? "" : ", ") + output.id + "=" + percent + "%";
            }
        }
        if (entry.outputs.isEmpty()) {
            return null; // 无物品产出:对合成规划无意义
        }
        for (Object ingredient : list(accessors.itemIngredients.invoke(recipe))) {
            entry.inputs.add(refs(ingredient));
        }
        if (accessors.extras != null) {
            entry.extras.put("extras", String.valueOf(accessors.extras.invoke(recipe)));
        }
        if (!chance.isEmpty()) {
            entry.extras.put("chance", chance);
        }
        return entry;
    }

    /** 单个物品成分 → 候选列表（矿词/多候选展开;空槽 = 空列表;栈 count 即单次用量）. */
    private static List<StackRef> refs(Object ingredient) {
        List<StackRef> refs = new ArrayList<>();
        if (!(ingredient instanceof IItemIngredient)) {
            return refs;
        }
        List<ItemStack> stacks = ((IItemIngredient) ingredient).getInputStackList();
        if (stacks == null) {
            return refs;
        }
        for (ItemStack stack : stacks) {
            StackRef ref = RecipeHarvester.stackRef(stack);
            if (ref != null) {
                refs.add(ref);
            }
        }
        return refs;
    }

    /** 概率成分的概率值（ChanceItemIngredient 的公开字段,两版同名）;非概率成分返回 100. */
    private static int chancePercent(IItemIngredient ingredient) {
        try {
            return ingredient.getClass().getField("chancePercent").getInt(ingredient);
        } catch (Throwable t) {
            return 100;
        }
    }

    /** 按注册名取静态机器处理器;该版本无此字段(或字段未初始化)返回 null. */
    private static AbstractRecipeHandler<?> handler(String name) {
        try {
            Object value = NCRecipes.class.getField(name).get(null);
            return value instanceof AbstractRecipeHandler ? (AbstractRecipeHandler<?>) value : null;
        } catch (Throwable t) {
            return null; // 两版机器字段名不同
        }
    }

    /** 配方取值方法集（配方类与版本一一对应,识别失败按"未知配方形状"处理）. */
    private static Accessors accessors(Class<?> recipeClass) {
        Accessors cached = ACCESSORS.get(recipeClass);
        if (cached == null) {
            cached = new Accessors(recipeClass);
            ACCESSORS.put(recipeClass, cached);
        }
        return cached.recognised() ? cached : null;
    }

    private static List<?> list(Object value) {
        return value instanceof List ? (List<?>) value : new ArrayList<Object>();
    }

    private static MachineEntry newEntry(String machine) {
        MachineEntry entry = new MachineEntry();
        entry.mod = "nuclearcraft";
        entry.machine = machine;
        entry.type = machine;
        return entry;
    }

    /** 配方取值方法（两谱系方法名不同,按候选名解析;一个版本只存在其中一个名字）. */
    private static final class Accessors {

        final Method itemIngredients;
        final Method fluidIngredients;
        final Method itemProducts;
        final Method fluidProducts;
        final Method extras;

        Accessors(Class<?> recipeClass) {
            this.itemIngredients = find(recipeClass, "getItemIngredients", "itemIngredients");
            this.fluidIngredients = find(recipeClass, "getFluidIngredients", "fluidIngredients");
            this.itemProducts = find(recipeClass, "getItemProducts", "itemProducts");
            this.fluidProducts = find(recipeClass, "getFluidProducts", "fluidProducts");
            this.extras = find(recipeClass, "getExtras", "extras");
        }

        /** 物品/流体四个取值方法齐全才认为配方形状可识别（缺流体方法时不敢断言"无流体"） */
        boolean recognised() {
            return this.itemIngredients != null && this.fluidIngredients != null
                    && this.itemProducts != null && this.fluidProducts != null;
        }

        private static Method find(Class<?> recipeClass, String... names) {
            for (String name : names) {
                try {
                    return recipeClass.getMethod(name);
                } catch (Throwable t) {
                    // 该版本无此方法名,试下一个
                }
            }
            return null;
        }
    }
}
