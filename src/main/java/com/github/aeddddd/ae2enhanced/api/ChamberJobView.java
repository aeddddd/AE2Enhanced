package com.github.aeddddd.ae2enhanced.api;

import net.minecraft.item.ItemStack;

/** 奇点处理仓活动任务的只读快照，进度与时间单位均为游戏 tick。 */
public final class ChamberJobView {
    private final String recipeId;
    private final ItemStack output;
    private final long batches;
    private final long progressTicks;
    private final long requiredTicks;

    public ChamberJobView(String recipeId, ItemStack output, long batches, long progressTicks, long requiredTicks) {
        this.recipeId = recipeId == null ? "" : recipeId;
        this.output = output == null ? ItemStack.EMPTY : output.copy();
        this.batches = Math.max(0L, batches);
        this.requiredTicks = Math.max(0L, requiredTicks);
        this.progressTicks = Math.max(0L, Math.min(progressTicks, this.requiredTicks));
    }

    public String getRecipeId() {
        return recipeId;
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public long getBatches() {
        return batches;
    }

    public long getProgressTicks() {
        return progressTicks;
    }

    public long getRequiredTicks() {
        return requiredTicks;
    }

    public long getRemainingTicks() {
        return requiredTicks - progressTicks;
    }
}
