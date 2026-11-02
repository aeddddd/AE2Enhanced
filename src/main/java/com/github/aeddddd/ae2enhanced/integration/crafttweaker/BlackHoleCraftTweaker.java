package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.chamber.RecipeCatalog;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import net.minecraft.item.ItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.LinkedHashMap;
import java.util.Map;

/** CraftTweaker 黑洞配方入口；输入按物品、metadata 与 NBT 精确匹配。 */
@ZenRegister
@ZenClass("mods.ae2enhanced.BlackHole")
public class BlackHoleCraftTweaker {

    @ZenMethod
    public static void addRecipe(IItemStack output, IItemStack[] inputs) {
        addRecipe("ct_" + (output == null ? "invalid" : output.getName()), output, inputs);
    }

    @ZenMethod
    public static void addRecipe(String id, IItemStack output, IItemStack[] inputs) {
        CraftTweakerAPI.apply(new AddRecipeAction(id, output, inputs));
    }

    @ZenMethod
    public static void removeRecipe(String id) {
        CraftTweakerAPI.apply(new RemoveRecipeAction(id));
    }

    @ZenMethod
    public static String[] getRecipeIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (BlackHoleRecipe recipe : BlackHoleRecipeRegistry.getRecipes()) {
            ids.add(recipe.getId());
        }
        return ids.toArray(new String[0]);
    }

    private static boolean validStack(IItemStack stack) {
        if (stack == null || !(stack.getInternal() instanceof ItemStack)) {
            return false;
        }
        return !((ItemStack) stack.getInternal()).isEmpty();
    }

    public static class AddRecipeAction implements IAction {
        private final String id;
        private final IItemStack output;
        private final IItemStack[] inputs;

        public AddRecipeAction(String id, IItemStack output, IItemStack[] inputs) {
            this.id = id;
            this.output = output;
            this.inputs = inputs;
        }

        @Override
        public boolean validate() {
            if (id == null || id.trim().isEmpty() || !validStack(output) || inputs == null || inputs.length == 0) {
                return false;
            }
            for (IItemStack input : inputs) {
                if (!validStack(input) || input.getAmount() <= 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String describeInvalid() {
            return "BlackHole.addRecipe requires a non-empty id/output and at least one non-empty positive input";
        }

        @Override
        public void apply() {
            Map<String, Integer> map = new LinkedHashMap<>();
            for (IItemStack stack : inputs) {
                ItemStack internal = (ItemStack) stack.getInternal();
                map.merge(BlackHoleRecipe.keyOf(internal), stack.getAmount(), Integer::sum);
            }
            ItemStack outStack = ((ItemStack) output.getInternal()).copy();
            RecipeCatalog.addScripted(new BlackHoleRecipe(id, map, outStack));
        }

        @Override
        public String describe() {
            return "Adding Black Hole recipe " + id;
        }
    }

    public static class RemoveRecipeAction implements IAction {
        private final String id;

        public RemoveRecipeAction(String id) {
            this.id = id;
        }

        @Override
        public boolean validate() {
            return id != null && !id.trim().isEmpty();
        }

        @Override
        public String describeInvalid() {
            return "BlackHole.removeRecipe requires a non-empty id";
        }

        @Override
        public void apply() {
            RecipeCatalog.removeScriptedBlackHole(id);
        }

        @Override
        public String describe() {
            return "Queueing Black Hole recipe removal: " + id;
        }
    }
}
