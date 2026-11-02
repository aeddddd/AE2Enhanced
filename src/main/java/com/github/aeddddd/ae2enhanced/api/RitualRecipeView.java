package com.github.aeddddd.ae2enhanced.api;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 微型奇点仪式的只读视图。掉落物与手持物匹配物品和 metadata，忽略 NBT。 */
public final class RitualRecipeView {
    private final String recipeId;
    private final List<CountedStackView> droppedInputs;
    private final ItemStack heldItem;
    private final ResourceLocation targetBlock;
    private final ItemStack output;
    private final int lifetimeTicks;

    public RitualRecipeView(String recipeId, List<CountedStackView> droppedInputs, ItemStack heldItem,
            ResourceLocation targetBlock, ItemStack output, int lifetimeTicks) {
        this.recipeId = recipeId == null ? "" : recipeId;
        this.droppedInputs = Collections.unmodifiableList(new ArrayList<>(droppedInputs));
        this.heldItem = heldItem == null ? ItemStack.EMPTY : heldItem.copy();
        this.targetBlock = targetBlock;
        this.output = output == null ? ItemStack.EMPTY : output.copy();
        this.lifetimeTicks = Math.max(0, lifetimeTicks);
    }

    public String getRecipeId() {
        return recipeId;
    }

    public List<CountedStackView> getDroppedInputs() {
        return droppedInputs;
    }

    public ItemStack getHeldItem() {
        return heldItem.copy();
    }

    /** 无目标方块要求时返回 null。 */
    public ResourceLocation getTargetBlock() {
        return targetBlock;
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public double getLifetimeSeconds() {
        return lifetimeTicks / 20.0D;
    }
}
