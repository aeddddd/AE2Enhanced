package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * 1.12.2 版处理样板构造器（对应 1.20.1 的 ProcessingPatternBuilder）.
 * <p>构造匿名 {@link ICraftingPatternDetails}:非工作台样板,精确输入,
 * 凝聚输入/输出按类型合并.1.12.2 无 multiplier 概念,每槽数量即每次合成消耗.</p>
 */
public class ProcessingPatternBuilder {

    private final IAEItemStack[] outputs;
    private final List<IAEItemStack> inputs = new ArrayList<>();

    public ProcessingPatternBuilder(IAEItemStack... outputs) {
        this.outputs = outputs.clone();
    }

    /**
     * 添加精确输入（每个 possibleInputs 取第一个为主输入,数量 = 单次消耗）.
     */
    public ProcessingPatternBuilder addPreciseInput(long count, IAEItemStack... possibleInputs) {
        IAEItemStack in = possibleInputs[0].copy();
        in.setStackSize(count);
        this.inputs.add(in);
        return this;
    }

    public ICraftingPatternDetails build() {
        final IAEItemStack[] condensedInputs = condense(this.inputs);
        final IAEItemStack[] condensedOutputs = condense(java.util.Arrays.asList(this.outputs));
        return new ICraftingPatternDetails() {
            @Override
            public ItemStack getPattern() {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean isValidItemForSlot(int slot, ItemStack stack, World world) {
                return false;
            }

            @Override
            public boolean isCraftable() {
                return false;
            }

            @Override
            public IAEItemStack[] getInputs() {
                return condensedInputs.clone();
            }

            @Override
            public IAEItemStack[] getCondensedInputs() {
                return condensedInputs.clone();
            }

            @Override
            public IAEItemStack[] getCondensedOutputs() {
                return condensedOutputs.clone();
            }

            @Override
            public IAEItemStack[] getOutputs() {
                return condensedOutputs.clone();
            }

            @Override
            public boolean canSubstitute() {
                return false;
            }

            @Override
            public ItemStack getOutput(InventoryCrafting ic, World w) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public void setPriority(int p) {
            }
        };
    }

    static IAEItemStack[] condense(List<IAEItemStack> stacks) {
        Map<IAEItemStack, IAEItemStack> merged = new LinkedHashMap<>();
        for (IAEItemStack stack : stacks) {
            IAEItemStack key = stack.copy();
            key.reset();
            IAEItemStack existing = merged.get(key);
            if (existing == null) {
                merged.put(key, stack.copy());
            } else {
                existing.incStackSize(stack.getStackSize());
            }
        }
        return merged.values().toArray(new IAEItemStack[0]);
    }
}
