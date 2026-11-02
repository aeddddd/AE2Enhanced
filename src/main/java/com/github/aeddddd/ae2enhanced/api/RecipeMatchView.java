package com.github.aeddddd.ae2enhanced.api;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 配方及当前可执行批次数的只读视图。 */
public final class RecipeMatchView {
    private final String recipeId;
    private final ItemStack output;
    private final List<CountedStackView> inputs;
    private final long maxBatches;
    private final int timeTicks;

    public RecipeMatchView(String recipeId, ItemStack output, List<CountedStackView> inputs,
            long maxBatches, int timeTicks) {
        this.recipeId = recipeId == null ? "" : recipeId;
        this.output = output == null ? ItemStack.EMPTY : output.copy();
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.maxBatches = Math.max(0L, maxBatches);
        this.timeTicks = timeTicks;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public List<CountedStackView> getInputs() {
        return inputs;
    }

    public long getMaxBatches() {
        return maxBatches;
    }

    public int getTimeTicks() {
        return timeTicks;
    }
}
