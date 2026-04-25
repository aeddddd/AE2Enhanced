package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CraftTweaker 集成：允许通过 ZenScript 添加/移除黑洞合成配方。
 *
 * 用法示例：
 * <pre>
 *   mods.ae2enhanced.BlackHole.addRecipe(&lt;minecraft:obsidian&gt;, [&lt;minecraft:stone&gt; * 8, &lt;minecraft:diamond&gt;]);
 *   mods.ae2enhanced.BlackHole.removeRecipe("test_obsidian");
 * </pre>
 */
@ZenRegister
@ZenClass("mods.ae2enhanced.BlackHole")
public class BlackHoleCraftTweaker {

    /** 延迟移除队列：CraftTweaker 可能在 init() 之前执行脚本，此时配方尚未注册 */
    private static final Set<String> PENDING_REMOVALS = new HashSet<>();

    @ZenMethod
    public static void addRecipe(IItemStack output, IItemStack[] inputs) {
        CraftTweakerAPI.apply(new AddRecipeAction(output, inputs));
    }

    @ZenMethod
    public static void removeRecipe(String id) {
        CraftTweakerAPI.apply(new RemoveRecipeAction(id));
    }

    /**
     * 由 AE2Enhanced.init() 在配方注册完成后调用，执行延迟移除。
     */
    public static void applyPendingRemovals() {
        if (PENDING_REMOVALS.isEmpty()) return;
        for (String id : new HashSet<>(PENDING_REMOVALS)) {
            boolean removed = BlackHoleRecipeRegistry.removeById(id);
            AE2Enhanced.LOGGER.info("[AE2E CT] applyPendingRemovals('{}'): {}", id, removed ? "removed" : "not found");
        }
        PENDING_REMOVALS.clear();
    }

    public static class AddRecipeAction implements IAction {
        private final IItemStack output;
        private final IItemStack[] inputs;

        public AddRecipeAction(IItemStack output, IItemStack[] inputs) {
            this.output = output;
            this.inputs = inputs;
        }

        @Override
        public void apply() {
            Map<String, Integer> map = new HashMap<>();
            for (IItemStack stack : inputs) {
                net.minecraft.item.ItemStack internal = (net.minecraft.item.ItemStack) stack.getInternal();
                String key = BlackHoleRecipe.keyOf(internal);
                map.merge(key, stack.getAmount(), Integer::sum);
            }
            net.minecraft.item.ItemStack outStack = (net.minecraft.item.ItemStack) output.getInternal();
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "ct_" + output.getName(),
                    map,
                    outStack.copy()
            ));
        }

        @Override
        public String describe() {
            return "Adding Black Hole recipe for " + output.getName();
        }
    }

    public static class RemoveRecipeAction implements IAction {
        private final String id;

        public RemoveRecipeAction(String id) {
            this.id = id;
        }

        @Override
        public void apply() {
            // CraftTweaker 可能在 init() 之前执行，此时配方尚未注册。
            // 加入延迟队列，由 AE2Enhanced.init() 注册完成后统一移除。
            PENDING_REMOVALS.add(id);
            AE2Enhanced.LOGGER.info("[AE2E CT] removeRecipe('{}'): queued for delayed removal (recipes may not be registered yet)", id);
        }

        @Override
        public String describe() {
            return "Queueing Black Hole recipe removal: " + id;
        }
    }
}
