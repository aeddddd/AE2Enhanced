/*
 * 移植自 Applied Energistics 2 (15.3.4 / 1.20.1)
 * 源文件:src/test/java/appeng/crafting/simulation/helpers/ProcessingPatternBuilder.java
 * 仅调整包名,逻辑保持一致,用于在测试中直接构造匿名 IPatternDetails.
 */
package com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

public class ProcessingPatternBuilder {
    private final List<GenericStack> outputs;
    private final List<IPatternDetails.IInput> inputs = new ArrayList<>();

    public ProcessingPatternBuilder(GenericStack... outputs) {
        this.outputs = List.of(outputs);
    }

    public ProcessingPatternBuilder addPreciseInput(long multiplier, GenericStack... possibleInputs) {
        return addPreciseInput(multiplier, false, possibleInputs);
    }

    public ProcessingPatternBuilder addPreciseInput(long multiplier, boolean containerItems,
            GenericStack... possibleInputs) {
        inputs.add(new IPatternDetails.IInput() {
            @Override
            public GenericStack[] getPossibleInputs() {
                return possibleInputs;
            }

            @Override
            public long getMultiplier() {
                return multiplier;
            }

            @Override
            public boolean isValid(AEKey input, Level level) {
                for (var possibleInput : possibleInputs) {
                    if (possibleInput.what().equals(input)) {
                        return true;
                    }
                }
                return false;
            }

            @Nullable
            @Override
            public AEKey getRemainingKey(AEKey template) {
                // AE2 15.4.10 适配:NeoForge 官方映射为 getCraftingRemainingItem,返回 ItemStack 而非 Item
                if (containerItems && template instanceof AEItemKey itemKey) {
                    var remainder = itemKey.getItem().getCraftingRemainingItem(itemKey.getReadOnlyStack());
                    if (!remainder.isEmpty()) {
                        return AEItemKey.of(remainder);
                    }
                }
                return null;
            }
        });
        return this;
    }

    public ProcessingPatternBuilder addDamageableInput(Item item) {
        var possibleInputs = new GenericStack[] { GenericStack.fromItemStack(new ItemStack(item)) };
        inputs.add(new IPatternDetails.IInput() {
            @Override
            public GenericStack[] getPossibleInputs() {
                return possibleInputs;
            }

            @Override
            public long getMultiplier() {
                return 1;
            }

            @Override
            public boolean isValid(AEKey input, Level level) {
                if (input instanceof AEItemKey itemKey) {
                    return itemKey.getItem() == item;
                }
                return false;
            }

            @Nullable
            @Override
            public AEKey getRemainingKey(AEKey template) {
                if (template instanceof AEItemKey itemKey) {
                    ItemStack stack = itemKey.toStack();
                    stack.setDamageValue(stack.getDamageValue() - 1);
                    if (stack.getDamageValue() >= stack.getMaxDamage()) {
                        return null;
                    }
                    return AEItemKey.of(stack);
                }
                return null;
            }
        });
        return this;
    }

    public IPatternDetails build() {
        return new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() {
                throw new UnsupportedOperationException();
            }

            @Override
            public IInput[] getInputs() {
                return inputs.toArray(IInput[]::new);
            }

            @Override
            public GenericStack[] getOutputs() {
                // AE2 15.4.10 适配:getOutputs 返回数组而非 List
                return outputs.toArray(GenericStack[]::new);
            }
        };
    }
}
