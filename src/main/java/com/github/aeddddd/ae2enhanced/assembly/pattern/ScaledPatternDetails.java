package com.github.aeddddd.ae2enhanced.assembly.pattern;

import java.util.Arrays;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * 包装一个 {@link IPatternDetails}，将其所有输入/输出数量放大指定倍数，用于 Long 级并行合成。
 */
public class ScaledPatternDetails implements IPatternDetails {

    private final IPatternDetails base;
    private final long multiplier;
    private final IInput[] scaledInputs;
    private final GenericStack[] scaledOutputs;
    private final GenericStack primaryOutput;

    public ScaledPatternDetails(IPatternDetails base, long multiplier) {
        this.base = base;
        this.multiplier = Math.max(1, multiplier);
        this.scaledInputs = scaleInputs(base.getInputs(), this.multiplier);
        this.scaledOutputs = scaleStacks(base.getOutputs(), this.multiplier);
        GenericStack primary = base.getPrimaryOutput();
        this.primaryOutput = primary != null ? scale(primary, this.multiplier) : null;
    }

    @Override
    public AEItemKey getDefinition() {
        return base.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return scaledInputs;
    }

    @Override
    public GenericStack getPrimaryOutput() {
        return primaryOutput;
    }

    @Override
    public GenericStack[] getOutputs() {
        return scaledOutputs;
    }

    public long getMultiplier() {
        return multiplier;
    }

    private static IInput[] scaleInputs(IInput[] inputs, long multiplier) {
        IInput[] result = new IInput[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            result[i] = new ScaledInput(inputs[i], multiplier);
        }
        return result;
    }

    private static GenericStack[] scaleStacks(GenericStack[] stacks, long multiplier) {
        return Arrays.stream(stacks)
                .map(s -> scale(s, multiplier))
                .toArray(GenericStack[]::new);
    }

    private static GenericStack scale(GenericStack stack, long multiplier) {
        long amount = safeMultiply(stack.amount(), multiplier);
        return new GenericStack(stack.what(), amount);
    }

    private static long safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static class ScaledInput implements IInput {

        private final IInput base;
        private final long multiplier;
        private final GenericStack[] possibleInputs;

        ScaledInput(IInput base, long multiplier) {
            this.base = base;
            this.multiplier = multiplier;
            this.possibleInputs = scaleStacks(base.getPossibleInputs(), multiplier);
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs;
        }

        @Override
        public long getMultiplier() {
            return safeMultiply(base.getMultiplier(), multiplier);
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return base.isValid(input, level);
        }

        @Override
        public AEKey getRemainingKey(AEKey input) {
            return base.getRemainingKey(input);
        }
    }
}
