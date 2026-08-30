package com.github.aeddddd.ae2enhanced.diag.harvest;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.CraftEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.FurnaceEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.machine.MachineDumps;

/**
 * 整合包配方采集器（{@code /ae2e harvest}).
 * <p>采集的是全部 mod 加载 + CraftTweaker 执行完毕后的<b>终态注册表</b>:
 * 合成台/熔炉配方、矿词表、mod 版本清单;机器配方经 {@link MachineDumps}
 * 按 mod 存在性门控 dump。输出 logs/ae2enhanced/harvest-时间戳.json。</p>
 */
public final class RecipeHarvester {

    private RecipeHarvester() {
    }

    /**
     * 执行采集并写出快照.
     *
     * @return 快照文件;失败返回 null
     */
    public static File harvest() {
        HarvestSnapshot snapshot = new HarvestSnapshot();
        snapshot.timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());

        for (ModContainer mod : Loader.instance().getModList()) {
            snapshot.mods.put(mod.getModId(), mod.getVersion());
        }

        collectOreDict(snapshot);
        collectCrafting(snapshot);
        collectFurnace(snapshot);
        MachineDumps.dumpAll(snapshot);
        snapshot.stats.machineTotal = snapshot.machines.size();

        File file = new File("logs/ae2enhanced/harvest-" + snapshot.timestamp + ".json");
        try {
            snapshot.save(file);
            AE2Enhanced.LOGGER.info("[harvest] 快照已写出: {} (合成台 {}, 熔炉 {}, 机器 {}, 矿词 {})",
                    file.getPath(), snapshot.crafting.size(), snapshot.furnace.size(),
                    snapshot.machines.size(), snapshot.oreDict.size());
            return file;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[harvest] 快照写出失败", e);
            return null;
        }
    }

    private static void collectOreDict(HarvestSnapshot snapshot) {
        for (String name : OreDictionary.getOreNames()) {
            List<StackRef> members = new ArrayList<>();
            for (ItemStack stack : OreDictionary.getOres(name, false)) {
                StackRef ref = stackRef(stack);
                if (ref != null) {
                    members.add(ref);
                }
            }
            if (!members.isEmpty()) {
                snapshot.oreDict.put(name, members);
            }
        }
    }

    private static void collectCrafting(HarvestSnapshot snapshot) {
        for (IRecipe recipe : ForgeRegistries.RECIPES) {
            snapshot.stats.craftingTotal++;
            CraftEntry entry = new CraftEntry();
            entry.name = recipe.getRegistryName() != null ? recipe.getRegistryName().toString() : null;
            try {
                entry.output = stackRef(recipe.getRecipeOutput());
                if (entry.output == null) {
                    snapshot.stats.craftingUnknown++;
                    continue; // 动态配方(JEI 描述型/拼装型)无静态输出,不入快照
                }
                if (recipe instanceof ShapedOreRecipe) {
                    entry.type = "shaped_ore";
                    entry.width = ((ShapedOreRecipe) recipe).getWidth();
                    entry.height = ((ShapedOreRecipe) recipe).getHeight();
                } else if (recipe instanceof ShapelessOreRecipe) {
                    entry.type = "shapeless_ore";
                } else if (recipe instanceof ShapedRecipes) {
                    entry.type = "shaped";
                    entry.width = ((ShapedRecipes) recipe).getWidth();
                    entry.height = ((ShapedRecipes) recipe).getHeight();
                } else if (recipe instanceof ShapelessRecipes) {
                    entry.type = "shapeless";
                } else if (recipe.isDynamic()) {
                    entry.type = "dynamic:" + recipe.getClass().getName();
                } else {
                    entry.type = "unknown:" + recipe.getClass().getName();
                }
                List<Ingredient> ingredients = recipe.getIngredients();
                for (Ingredient ingredient : ingredients) {
                    entry.slots.add(ingredientRefs(ingredient));
                }
                entry.returnedSlots = sampleReturnedSlots(recipe, ingredients);
                snapshot.crafting.add(entry);
            } catch (Throwable t) {
                // 非标 IRecipe 实现(提取 ingredients 抛异常)记 unknown,不中断采集
                snapshot.stats.craftingUnknown++;
                AE2Enhanced.LOGGER.debug("[harvest] 配方提取失败: {} ({})", entry.name, t.toString());
            }
        }
    }

    private static void collectFurnace(HarvestSnapshot snapshot) {
        for (Map.Entry<ItemStack, ItemStack> e : FurnaceRecipes.instance().getSmeltingList().entrySet()) {
            StackRef in = stackRef(e.getKey());
            StackRef out = stackRef(e.getValue());
            if (in != null && out != null) {
                FurnaceEntry entry = new FurnaceEntry();
                entry.input = in;
                entry.output = out;
                snapshot.furnace.add(entry);
                snapshot.stats.furnaceTotal++;
            }
        }
    }

    /** Ingredient → 候选列表(矿词成分已展开;空槽 = 空列表). */
    public static List<StackRef> ingredientRefs(Ingredient ingredient) {
        List<StackRef> refs = new ArrayList<>();
        if (ingredient == null || ingredient == Ingredient.EMPTY) {
            return refs;
        }
        for (ItemStack stack : ingredient.getMatchingStacks()) {
            StackRef ref = stackRef(stack);
            if (ref != null) {
                refs.add(ref);
            }
        }
        return refs;
    }

    /**
     * 逐槽返还实采:以各成分首候选填满合成网格后调 {@code getRemainingItems},
     * 返还非空的槽位下标入列表(容器物/CrT .reuse()/工具损耗返还均由此覆盖——
     * 如 xReliquary Alkahestry 宝典,若误建模为消耗会把 dup 求解卡死在催化剂库存上).
     * 网格按成分序紧凑填充,容器语义按槽物品决定,位置无关;异常返回空列表.
     */
    private static List<Integer> sampleReturnedSlots(IRecipe recipe, List<Ingredient> ingredients) {
        List<Integer> returned = new ArrayList<>();
        try {
            net.minecraft.inventory.InventoryCrafting inv = new net.minecraft.inventory.InventoryCrafting(
                    new appeng.container.ContainerNull(), 3, 3);
            int limit = Math.min(ingredients.size(), inv.getSizeInventory());
            for (int i = 0; i < limit; i++) {
                Ingredient ingredient = ingredients.get(i);
                if (ingredient == null || ingredient == Ingredient.EMPTY) {
                    continue;
                }
                ItemStack[] matching = ingredient.getMatchingStacks();
                if (matching.length > 0 && !matching[0].isEmpty()) {
                    ItemStack fill = matching[0].copy();
                    fill.setCount(1);
                    inv.setInventorySlotContents(i, fill);
                }
            }
            net.minecraft.util.NonNullList<ItemStack> remaining = recipe.getRemainingItems(inv);
            for (int i = 0; i < Math.min(remaining.size(), limit); i++) {
                if (!remaining.get(i).isEmpty() && !inv.getStackInSlot(i).isEmpty()) {
                    returned.add(i);
                }
            }
        } catch (Throwable t) {
            // 采样失败按"全部消耗"处理(保守)
        }
        return returned;
    }

    /** ItemStack → StackRef(空栈/未注册物品返回 null;NBT 以 SNBT 摘要保留). */
    public static StackRef stackRef(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return null;
        }
        StackRef ref = new StackRef();
        ref.id = stack.getItem().getRegistryName().toString();
        ref.meta = stack.getMetadata();
        ref.count = stack.getCount();
        if (stack.hasTagCompound() && stack.getTagCompound() != null) {
            ref.nbt = stack.getTagCompound().toString();
        }
        return ref;
    }
}
