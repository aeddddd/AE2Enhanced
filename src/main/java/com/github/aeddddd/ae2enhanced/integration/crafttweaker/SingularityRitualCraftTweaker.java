package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipeRegistry;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * CraftTweaker 集成：允许通过 ZenScript 添加/移除微型奇点仪式配方.
 *
 * 用法示例：
 * <pre>
 *   mods.ae2enhanced.SingularityRitual.addRecipe("my_recipe",
 *       [&lt;minecraft:diamond&gt; * 64, &lt;minecraft:emerald&gt; * 32],
 *       &lt;minecraft:nether_star&gt;,
 *       &lt;appliedenergistics2:controller&gt;,
 *       12000);
 *   mods.ae2enhanced.SingularityRitual.removeRecipe("my_recipe");
 * </pre>
 */
@ZenRegister
@ZenClass("mods.ae2enhanced.SingularityRitual")
public class SingularityRitualCraftTweaker {

    @ZenMethod
    public static void addRecipe(String id, IItemStack[] droppedInputs, IItemStack heldItem, IItemStack targetBlock, int lifetimeTicks) {
        CraftTweakerAPI.apply(new AddRecipeAction(id, droppedInputs, heldItem, targetBlock, lifetimeTicks));
    }

    @ZenMethod
    public static void removeRecipe(String id) {
        CraftTweakerAPI.apply(new RemoveRecipeAction(id));
    }

    public static class AddRecipeAction implements IAction {
        private final String id;
        private final IItemStack[] droppedInputs;
        private final IItemStack heldItem;
        private final IItemStack targetBlock;
        private final int lifetimeTicks;

        public AddRecipeAction(String id, IItemStack[] droppedInputs, IItemStack heldItem, IItemStack targetBlock, int lifetimeTicks) {
            this.id = id;
            this.droppedInputs = droppedInputs;
            this.heldItem = heldItem;
            this.targetBlock = targetBlock;
            this.lifetimeTicks = lifetimeTicks;
        }

        @Override
        public void apply() {
            List<ItemStack> inputs = new ArrayList<>();
            for (IItemStack stack : droppedInputs) {
                if (stack == null) continue;
                ItemStack internal = (ItemStack) stack.getInternal();
                if (!internal.isEmpty()) {
                    inputs.add(internal.copy());
                }
            }

            ItemStack held = heldItem != null ? ((ItemStack) heldItem.getInternal()).copy() : ItemStack.EMPTY;
            // targetBlock 从 IItemStack 提取 Block
            Block block = null;
            if (targetBlock != null) {
                ItemStack blockStack = (ItemStack) targetBlock.getInternal();
                if (!blockStack.isEmpty() && blockStack.getItem() != null) {
                    block = Block.getBlockFromItem(blockStack.getItem());
                    if (block == null || net.minecraft.init.Blocks.AIR == block) {
                        block = null;
                    }
                }
            }

            SingularityRecipeRegistry.register(new SingularityRecipe(id, inputs, held, block, lifetimeTicks));
        }

        @Override
        public String describe() {
            return "Adding Singularity Ritual recipe: " + id;
        }
    }

    public static class RemoveRecipeAction implements IAction {
        private final String id;

        public RemoveRecipeAction(String id) {
            this.id = id;
        }

        @Override
        public void apply() {
            SingularityRecipeRegistry.queueRemoval(id);
        }

        @Override
        public String describe() {
            return "Queueing Singularity Ritual recipe removal: " + id;
        }
    }
}
