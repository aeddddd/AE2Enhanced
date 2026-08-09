package com.github.aeddddd.ae2enhanced.chamber;

import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 奇点处理仓配方：多输入（按 key 计数,支持 long 批量）+ 单输出 + 处理时间.
 *
 * <p>压印板不作为机器槽位存在：处理仓定位后期,链式合并配方直接
 * "原料 -> 处理器",压印板约束被内化（等效于机器自带虚拟压印板）.</p>
 */
public class ChamberRecipe {

    private final String id;
    /** key -> 单批消耗数量 */
    private final Map<String, Long> inputs;
    /** key -> 显示用模板物品 */
    private final Map<String, ItemStack> inputTemplates;
    private final ItemStack output;
    private final int timeTicks;

    private ChamberRecipe(String id, Map<String, Long> inputs, Map<String, ItemStack> inputTemplates,
                          ItemStack output, int timeTicks) {
        this.id = id;
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        this.inputTemplates = Collections.unmodifiableMap(new LinkedHashMap<>(inputTemplates));
        this.output = output.copy();
        this.timeTicks = Math.max(1, timeTicks);
    }

    public String getId() {
        return id;
    }

    public Map<String, Long> getInputs() {
        return inputs;
    }

    public Map<String, ItemStack> getInputTemplates() {
        return inputTemplates;
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public int getTimeTicks() {
        return timeTicks;
    }

    /**
     * 计算 available 中的材料最多可支持本配方的批次数.
     */
    public long maxBatches(Map<String, Long> available) {
        long batches = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : inputs.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            long have = available.getOrDefault(entry.getKey(), 0L);
            batches = Math.min(batches, have / entry.getValue());
        }
        return batches == Long.MAX_VALUE ? 0 : batches;
    }

    // ---- 构建器 ----

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private final Map<String, Long> inputs = new LinkedHashMap<>();
        private final Map<String, ItemStack> inputTemplates = new LinkedHashMap<>();
        private ItemStack output = ItemStack.EMPTY;
        private int timeTicks = 20;

        Builder(String id) {
            this.id = id;
        }

        public Builder input(ItemStack stack, long count) {
            return inputRawKey(LongItemStore.keyOf(stack), count, stack);
        }

        /**
         * 以原始 key 登记输入（NBT 感知配方必须使用此方法,
         * 避免模板重建时丢失 NBT 导致 key 不一致）.
         */
        public Builder inputRawKey(String key, long count, ItemStack template) {
            inputs.merge(key, count, Long::sum);
            if (!inputTemplates.containsKey(key) && !template.isEmpty()) {
                ItemStack t = template.copy();
                t.setCount(1);
                inputTemplates.put(key, t);
            }
            return this;
        }

        public Builder output(ItemStack stack) {
            this.output = stack.copy();
            return this;
        }

        public Builder time(int ticks) {
            this.timeTicks = ticks;
            return this;
        }

        public ChamberRecipe build() {
            return new ChamberRecipe(id, inputs, inputTemplates, output, timeTicks);
        }
    }
}
