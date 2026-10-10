package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.brandon3055.draconicevolution.api.fusioncrafting.IFusionRecipe;
import com.brandon3055.draconicevolution.lib.RecipeManager;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * Draconic Evolution 融合合成(Fusion Crafting)配方 dump（融合合成核心）.
 * <p>配方来源为 DE 自身的 {@link RecipeManager#FUSION_REGISTRY}（与 DE 自带 JEI 插件同口径,
 * FusionRecipeAPI 只是它的门面）:催化剂 1 个占 inputs 首个槽,注入器成分逐个槽,输出 1 个;
 * 单注入器耗能/总耗能/阶级记 extras.本类直接引用 DE 类,仅由 {@link MachineDumps} 在 mod
 * 存在时经 Class.forName 加载.</p>
 * <p>跳过口径:催化剂或输出无法静态解析的配方——升级类配方(FusionUpgradeRecipe)的催化剂随
 * 被升级工具而定、输出是该工具加工后的副本,注册表内不存在静态催化剂/输出——整条跳过;
 * 成分无法解析（矿词名无成员/未知成分类型,matches() 永不成立）的配方同样整条跳过,
 * 不产出残缺条目.</p>
 */
public final class DraconicEvolutionDump {

    private DraconicEvolutionDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        int skipped = 0;
        try {
            for (IFusionRecipe recipe : RecipeManager.FUSION_REGISTRY.getRecipes()) {
                try {
                    MachineEntry entry = convert(recipe);
                    if (entry == null) {
                        skipped++;
                    } else {
                        snapshot.machines.add(entry);
                    }
                } catch (Throwable t) {
                    skipped++; // 单条失败跳过
                }
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] draconicevolution fusion dump 失败: {}", t.toString());
        }
        if (skipped > 0) {
            // 升级类配方占多数,便于核对覆盖率
            AE2Enhanced.LOGGER.debug("[harvest] draconicevolution 融合配方跳过 {} 条", skipped);
        }
    }

    /** 单条配方:催化剂 + 注入器成分 → 输出;无法静态解析返回 null. */
    @SuppressWarnings("rawtypes")
    private static MachineEntry convert(IFusionRecipe recipe) {
        List<StackRef> catalyst = catalystRefs(recipe.getRecipeCatalyst());
        if (catalyst.isEmpty()) {
            return null; // 升级类配方(FusionUpgradeRecipe):催化剂是被升级的工具本身,注册表内为 EMPTY
        }
        // 输出取法与 DE 自带 JEI 插件一致(传空栈);升级类配方此处返回空栈,已被上面的催化剂判空挡掉
        StackRef output = RecipeHarvester.stackRef(recipe.getRecipeOutput(ItemStack.EMPTY));
        if (output == null) {
            return null;
        }
        MachineEntry entry = newEntry();
        entry.inputs.add(catalyst);
        for (Object ingredient : recipe.getRecipeIngredients()) {
            List<StackRef> refs = ingredientRefs(ingredient);
            if (refs.isEmpty()) {
                return null; // 成分不可解析的配方永不匹配,不投影
            }
            entry.inputs.add(refs);
        }
        entry.outputs.add(output);
        entry.extras.put("tier", String.valueOf(recipe.getRecipeTier()));
        // 核心逐注入器充能 getIngredientEnergyCost():总耗能 = 单注入器耗能 × 注入器数(DE JEI 即按此口径展示)
        long perInjector = recipe.getIngredientEnergyCost();
        entry.extras.put("energy", String.valueOf(perInjector));
        entry.extras.put("energyTotal", BigInteger.valueOf(perInjector)
                .multiply(BigInteger.valueOf(entry.inputs.size() - 1)).toString());
        return entry;
    }

    /** 催化剂 → 单候选列表(数量为一次执行实际消耗量,即配方声明的催化剂栈量). */
    private static List<StackRef> catalystRefs(ItemStack stack) {
        List<StackRef> refs = new ArrayList<>();
        StackRef ref = RecipeHarvester.stackRef(stack);
        if (ref != null) {
            refs.add(ref);
        }
        return refs;
    }

    /** 注入器成分 → 候选列表(矿词成分展开为全部成员);数量归一到 1,DE 每个注入器每次只消耗 1 个. */
    private static List<StackRef> ingredientRefs(Object ingredient) {
        List<StackRef> refs = new ArrayList<>();
        if (ingredient instanceof ItemStack) {
            addRef(refs, RecipeHarvester.stackRef((ItemStack) ingredient));
        } else if (ingredient instanceof String) {
            for (ItemStack stack : OreDictionary.getOres((String) ingredient, false)) {
                addRef(refs, RecipeHarvester.stackRef(stack));
            }
        } else if (ingredient instanceof Item) {
            addRef(refs, RecipeHarvester.stackRef(new ItemStack((Item) ingredient)));
        } else if (ingredient instanceof Block) {
            addRef(refs, RecipeHarvester.stackRef(new ItemStack((Block) ingredient)));
        }
        // 其余成分类型(如流体)无法用 StackRef 表达,返回空列表由调用方整条跳过
        return refs;
    }

    private static void addRef(List<StackRef> refs, StackRef ref) {
        if (ref != null) {
            ref.count = 1;
            refs.add(ref);
        }
    }

    private static MachineEntry newEntry() {
        MachineEntry entry = new MachineEntry();
        entry.mod = "draconicevolution";
        entry.machine = "fusion";
        entry.type = "fusion";
        return entry;
    }
}
