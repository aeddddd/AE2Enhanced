package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/**
 * CraftTweaker 集成：允许通过 ZenScript 添加/移除奇点处理仓自定义配方.
 *
 * 用法示例：
 * <pre>
 *   mods.ae2enhanced.SingularityChamber.addRecipe(&lt;minecraft:diamond&gt; * 2, [&lt;minecraft:coal&gt; * 64], 100);
 *   mods.ae2enhanced.SingularityChamber.removeRecipe("ct_minecraft:diamond");
 * </pre>
 */
@ZenRegister
@ZenClass("mods.ae2enhanced.SingularityChamber")
public class SingularityChamberCraftTweaker {

    @ZenMethod
    public static void addRecipe(IItemStack output, IItemStack[] inputs, int timeTicks) {
        CraftTweakerAPI.apply(new IAction() {
            @Override
            public void apply() {
                net.minecraft.item.ItemStack outStack = (net.minecraft.item.ItemStack) output.getInternal();
                ChamberRecipe.Builder builder = ChamberRecipe.builder("ct_" + output.getName())
                        .output(outStack.copy())
                        .time(timeTicks);
                for (IItemStack stack : inputs) {
                    net.minecraft.item.ItemStack internal = (net.minecraft.item.ItemStack) stack.getInternal();
                    builder.input(internal, stack.getAmount());
                }
                ChamberRecipeIndex.addCustomRecipe(builder.build());
            }

            @Override
            public String describe() {
                return "Adding Singularity Chamber recipe for " + output.getDisplayName();
            }
        });
    }

    @ZenMethod
    public static void removeRecipe(String id) {
        CraftTweakerAPI.apply(new IAction() {
            @Override
            public void apply() {
                if (!ChamberRecipeIndex.removeCustomRecipe(id)) {
                    CraftTweakerAPI.logWarning("No Singularity Chamber recipe found with id " + id);
                }
            }

            @Override
            public String describe() {
                return "Removing Singularity Chamber recipe " + id;
            }
        });
    }
}
