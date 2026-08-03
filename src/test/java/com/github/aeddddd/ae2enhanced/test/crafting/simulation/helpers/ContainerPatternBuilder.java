package com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * 容器物样板构建器(测试):包装一个既有样板,为指定输入附加容器返还语义
 * ({@link IPatternDetails.IInput#getRemainingKey}),模拟工作台配方的剩余物
 * (如 4 蜂蜜瓶 → 蜂蜜块 + 4 玻璃瓶).
 */
public final class ContainerPatternBuilder {

    private ContainerPatternBuilder() {
    }

    /**
     * 包装 base 样板:消耗 inputKey 输入时返还 containerKey(数量与消耗量一致).
     */
    public static IPatternDetails withContainer(IPatternDetails base, AEKey inputKey,
            AEKey containerKey) {
        return new Wrapper(base, inputKey, containerKey);
    }

    private record Wrapper(IPatternDetails base, AEKey inputKey, AEKey containerKey)
            implements IPatternDetails {
        @Override
        public AEItemKey getDefinition() {
            return base.getDefinition();
        }

        @Override
        public IInput[] getInputs() {
            var inputs = base.getInputs();
            IInput[] out = new IInput[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                var possible = inputs[i].getPossibleInputs();
                if (possible.length > 0 && inputKey.equals(possible[0].what())) {
                    out[i] = new ContainerInput(inputs[i], containerKey);
                } else {
                    out[i] = inputs[i];
                }
            }
            return out;
        }

        @Override
        public GenericStack[] getOutputs() {
            return base.getOutputs();
        }
    }

    private record ContainerInput(IPatternDetails.IInput delegate, AEKey containerKey)
            implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return delegate.getPossibleInputs();
        }

        @Override
        public long getMultiplier() {
            return delegate.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return delegate.isValid(input, level);
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            var possible = delegate.getPossibleInputs();
            if (possible.length > 0 && template.equals(possible[0].what())) {
                return containerKey;
            }
            return delegate.getRemainingKey(template);
        }
    }
}
