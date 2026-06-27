package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 智能样板展开后的单个配方详情.
 * 实现 {@link ICraftingPatternDetails},使 AE2 网络将其视为独立的普通样板.
 *
 * <p>关键设计点：</p>
 * <ul>
 *   <li>{@link #getPattern()} 返回 parent 智能样板 ItemStack(用于 DualityInterface 的 identity 验证)</li>
 *   <li>{@link #equals(Object)} / {@link #hashCode()} 基于配方内容,确保 Set 中不重复</li>
 *   <li>processing 配方：{@link #isCraftable()} = false, {@link #canSubstitute()} = false</li>
 * </ul>
 */
public class SmartPatternSubDetails implements ICraftingPatternDetails {

    private final ItemStack parentPattern;
    private final SmartRecipe recipe;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] condensedOutputs;
    private final IAEItemStack pattern; // 用于 equals/hashCode
    private int priority = 0;

    public SmartPatternSubDetails(@Nonnull ItemStack parentPattern, @Nonnull SmartRecipe recipe) {
        this.parentPattern = parentPattern;
        this.recipe = recipe;
        this.condensedInputs = condenseStacks(recipe.getInputs());
        this.condensedOutputs = condenseStacks(recipe.getOutputs());
        this.pattern = AEItemStack.fromItemStack(parentPattern);
    }

    @Override
    @Nonnull
    public ItemStack getPattern() {
        return parentPattern;
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack item, World world) {
        // processing 配方不支持合成网格验证
        if (!recipe.isCrafting()) {
            return false;
        }
        // 对于 crafting 配方,检查 slotIndex 位置的输入是否匹配
        IAEItemStack[] inputs = recipe.getInputs();
        if (slotIndex < 0 || slotIndex >= inputs.length) {
            return false;
        }
        IAEItemStack expected = inputs[slotIndex];
        if (expected == null || item.isEmpty()) {
            return expected == null && item.isEmpty();
        }
        return expected.equals(AEItemStack.fromItemStack(item));
    }

    @Override
    public boolean isCraftable() {
        // 智能样板统一作为 processing 配方处理，避免 AE2 硬编码 3x3 合成网格限制
        return false;
    }

    @Override
    @Nonnull
    public IAEItemStack[] getInputs() {
        return recipe.getInputs();
    }

    @Override
    @Nonnull
    public IAEItemStack[] getCondensedInputs() {
        return condensedInputs;
    }

    @Override
    @Nonnull
    public IAEItemStack[] getCondensedOutputs() {
        return condensedOutputs;
    }

    @Override
    @Nonnull
    public IAEItemStack[] getOutputs() {
        return recipe.getOutputs();
    }

    @Override
    public boolean canSubstitute() {
        return false;
    }

    @Override
    @Nonnull
    public List<IAEItemStack> getSubstituteInputs(int slot) {
        return Collections.emptyList();
    }

    @Override
    @Nonnull
    public ItemStack getOutput(InventoryCrafting craftingGrid, World world) {
        // processing 配方不通过合成网格计算输出
        if (!recipe.isCrafting()) {
            return ItemStack.EMPTY;
        }
        // crafting 配方：返回主要输出(AE2 内部通常只在 crafting grid 验证后调用)
        IAEItemStack primary = recipe.getPrimaryOutput();
        return primary != null ? primary.createItemStack() : ItemStack.EMPTY;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Nonnull
    public SmartRecipe getRecipe() {
        return recipe;
    }

    /**
     * 压缩 IAEItemStack 数组：合并相同的物品,累加数量.
     */
    @Nonnull
    private static IAEItemStack[] condenseStacks(@Nonnull IAEItemStack[] stacks) {
        Map<IAEItemStack, IAEItemStack> map = new LinkedHashMap<>();
        for (IAEItemStack stack : stacks) {
            if (stack == null || stack.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack existing = null;
            for (IAEItemStack key : map.keySet()) {
                if (key.equals(stack)) {
                    existing = map.get(key);
                    break;
                }
            }
            if (existing != null) {
                existing.add(stack);
            } else {
                map.put(stack.copy(), stack.copy());
            }
        }
        return map.values().toArray(new IAEItemStack[0]);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SmartPatternSubDetails)) return false;
        SmartPatternSubDetails other = (SmartPatternSubDetails) obj;
        return recipe.isCrafting() == other.recipe.isCrafting()
            && Arrays.equals(recipe.getInputs(), other.recipe.getInputs())
            && Arrays.equals(recipe.getOutputs(), other.recipe.getOutputs());
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(recipe.isCrafting());
        result = 31 * result + Arrays.hashCode(recipe.getInputs());
        result = 31 * result + Arrays.hashCode(recipe.getOutputs());
        return result;
    }
}
