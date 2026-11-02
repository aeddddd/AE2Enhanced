package com.github.aeddddd.ae2enhanced.centralinterface.recipe;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 一条真实配方与其完整、确定性物品产物的绑定。
 *
 * <p>虚拟合成只能以本绑定的产物执行；样板输出必须与实际产物按
 * Item、Metadata、NBT、Count 完全一致（顺序无关，重复项按数量区分）。</p>
 */
public final class RecipeBinding {
    private final Object recipe;
    private final IAEItemStack[] outputs;

    private RecipeBinding(Object recipe, IAEItemStack[] outputs) {
        this.recipe = recipe;
        this.outputs = outputs;
    }

    public static RecipeBinding of(Object recipe, IAEItemStack[] expectedOutputs, ItemStack... actualOutputs) {
        return of(recipe, expectedOutputs, actualOutputs == null ? null : Arrays.asList(actualOutputs));
    }

    public static RecipeBinding of(Object recipe, IAEItemStack[] expectedOutputs, List<ItemStack> actualOutputs) {
        if (recipe == null || !outputsMatch(expectedOutputs, actualOutputs)) {
            return null;
        }
        IAEItemStack[] encoded = new IAEItemStack[actualOutputs.size()];
        for (int i = 0; i < actualOutputs.size(); i++) {
            encoded[i] = AEItemStack.fromItemStack(actualOutputs.get(i).copy());
            if (encoded[i] == null) return null;
        }
        return new RecipeBinding(recipe, encoded);
    }

    public static boolean outputsMatch(IAEItemStack[] expectedOutputs, List<ItemStack> actualOutputs) {
        if (expectedOutputs == null || actualOutputs == null
                || expectedOutputs.length == 0 || expectedOutputs.length != actualOutputs.size()) {
            return false;
        }
        boolean[] matched = new boolean[actualOutputs.size()];
        for (IAEItemStack expected : expectedOutputs) {
            if (expected == null || expected.getStackSize() <= 0 || expected.getStackSize() > Integer.MAX_VALUE) {
                return false;
            }
            ItemStack expectedStack = expected.createItemStack();
            if (expectedStack.isEmpty() || expectedStack.getCount() != expected.getStackSize()) return false;
            boolean found = false;
            for (int i = 0; i < actualOutputs.size(); i++) {
                if (!matched[i] && exactMatch(expectedStack, actualOutputs.get(i))) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    public static boolean exactMatch(ItemStack actual, ItemStack expected) {
        return actual != null && expected != null && !actual.isEmpty() && !expected.isEmpty()
                && actual.getItem() == expected.getItem()
                && actual.getMetadata() == expected.getMetadata()
                && actual.getCount() == expected.getCount()
                && ItemStack.areItemStackTagsEqual(actual, expected);
    }

    public static List<ItemStack> singleOutput(ItemStack output) {
        if (output == null || output.isEmpty()) return Collections.emptyList();
        List<ItemStack> result = new ArrayList<>(1);
        result.add(output);
        return result;
    }

    public Object getRecipe() {
        return recipe;
    }

    public IAEItemStack[] getOutputs() {
        return outputs.clone();
    }
}
