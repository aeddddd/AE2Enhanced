package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import net.minecraft.item.ItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.ArrayList;
import java.util.List;

/** CraftTweaker 奇点处理仓配方入口。每个 IIngredient 数组元素构成一个替代输入组。 */
@ZenRegister
@ZenClass("mods.ae2enhanced.SingularityChamber")
public class SingularityChamberCraftTweaker {

    @ZenMethod
    public static void addRecipe(IItemStack output, IItemStack[] inputs, int timeTicks) {
        addRecipe("ct_" + (output == null ? "invalid" : output.getName()), output, inputs, timeTicks);
    }

    @ZenMethod
    public static void addRecipe(String id, IItemStack output, IItemStack[] inputs, int timeTicks) {
        CraftTweakerAPI.apply(new AddRecipeAction(id, output, inputs, timeTicks));
    }

    @ZenMethod
    public static void addRecipeAlternatives(IItemStack output, IIngredient[] inputs, int timeTicks) {
        addRecipeAlternatives("ct_" + (output == null ? "invalid" : output.getName()), output, inputs, timeTicks);
    }

    @ZenMethod
    public static void addRecipeAlternatives(String id, IItemStack output, IIngredient[] inputs, int timeTicks) {
        CraftTweakerAPI.apply(new AddRecipeAction(id, output, inputs, timeTicks));
    }

    @ZenMethod
    public static void removeRecipe(String id) {
        CraftTweakerAPI.apply(new RemoveRecipeAction(id));
    }

    @ZenMethod
    public static String[] getRecipeIds() {
        List<String> ids = new ArrayList<>();
        for (ChamberRecipe recipe : ChamberRecipeIndex.allRecipes()) {
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
        private final IIngredient[] inputs;
        private final int timeTicks;

        public AddRecipeAction(String id, IItemStack output, IIngredient[] inputs, int timeTicks) {
            this.id = id;
            this.output = output;
            this.inputs = inputs;
            this.timeTicks = timeTicks;
        }

        @Override
        public boolean validate() {
            if (id == null || id.trim().isEmpty() || !validStack(output) || timeTicks <= 0
                    || inputs == null || inputs.length == 0) {
                return false;
            }
            for (IIngredient input : inputs) {
                if (input == null || input.getAmount() <= 0 || input.getItems() == null || input.getItems().isEmpty()) {
                    return false;
                }
                for (IItemStack alternative : input.getItems()) {
                    if (!validStack(alternative)) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public String describeInvalid() {
            return "SingularityChamber.addRecipe requires id/output, positive time and non-empty positive ingredient groups";
        }

        @Override
        public void apply() {
            ItemStack outStack = ((ItemStack) output.getInternal()).copy();
            ChamberRecipe.Builder builder = ChamberRecipe.builder(id).output(outStack).time(timeTicks);
            for (IIngredient ingredient : inputs) {
                List<ItemStack> alternatives = new ArrayList<>();
                for (IItemStack stack : ingredient.getItems()) {
                    alternatives.add(((ItemStack) stack.getInternal()).copy());
                }
                builder.inputAlternatives(alternatives, ingredient.getAmount());
            }
            ChamberRecipeIndex.addCustomRecipe(builder.build());
        }

        @Override
        public String describe() {
            return "Adding Singularity Chamber recipe " + id;
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
            return "SingularityChamber.removeRecipe requires a non-empty id";
        }

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
    }
}
