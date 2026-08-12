package com.github.aeddddd.ae2enhanced.chamber;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 奇点处理仓配方：若干输入组 + 单输出 + 处理时间.
 *
 * <p><b>输入组</b>：每组是一个"替代列表 + 单批数量",组内任一物品都可满足该组需求
 * （矿辞/变体语义,与 AE2 压印器 getInputs() 的 anyMatch 语义一致）.
 * 调度时按组内顺序从可用替代中抽取.</p>
 */
public class ChamberRecipe {

    /** 输入组：替代 key 列表 + 显示模板 + 单批消耗数量 */
    public static class InputGroup {
        private final List<String> keys;
        private final List<ItemStack> templates;
        private final long count;

        InputGroup(List<String> keys, List<ItemStack> templates, long count) {
            this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
            this.templates = Collections.unmodifiableList(new ArrayList<>(templates));
            this.count = count;
        }

        public List<String> getKeys() {
            return keys;
        }

        public List<ItemStack> getTemplates() {
            return templates;
        }

        public long getCount() {
            return count;
        }
    }

    private final String id;
    private final List<InputGroup> inputGroups;
    private final ItemStack output;
    private final int timeTicks;

    private ChamberRecipe(String id, List<InputGroup> inputGroups, ItemStack output, int timeTicks) {
        this.id = id;
        this.inputGroups = Collections.unmodifiableList(new ArrayList<>(inputGroups));
        this.output = output.copy();
        this.timeTicks = Math.max(1, timeTicks);
    }

    public String getId() {
        return id;
    }

    public List<InputGroup> getInputGroups() {
        return inputGroups;
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public int getTimeTicks() {
        return timeTicks;
    }

    /**
     * 计算 available 中的材料最多可支持本配方的批次数.
     * 每组需求按组内所有替代的数量总和计算.
     */
    public long maxBatches(Map<String, Long> available) {
        long batches = Long.MAX_VALUE;
        for (InputGroup group : inputGroups) {
            if (group.count <= 0) {
                continue;
            }
            long sum = 0;
            for (String key : group.keys) {
                sum = safeAdd(sum, available.getOrDefault(key, 0L));
            }
            batches = Math.min(batches, sum / group.count);
        }
        return batches == Long.MAX_VALUE ? 0 : batches;
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    // ---- 构建器 ----

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private final List<InputGroup> inputGroups = new ArrayList<>();
        private ItemStack output = ItemStack.EMPTY;
        private int timeTicks = 20;

        Builder(String id) {
            this.id = id;
        }

        /** 单物品输入组. */
        public Builder input(ItemStack stack, long count) {
            return inputRawKey(LongItemStore.keyOf(stack), count, stack);
        }

        /** 单 key 输入组（NBT 感知配方使用,避免模板重建丢 NBT）. */
        public Builder inputRawKey(String key, long count, ItemStack template) {
            List<String> keys = new ArrayList<>();
            keys.add(key);
            List<ItemStack> templates = new ArrayList<>();
            if (!template.isEmpty()) {
                ItemStack t = template.copy();
                t.setCount(1);
                templates.add(t);
            }
            inputGroups.add(new InputGroup(keys, templates, count));
            return this;
        }

        /** 替代输入组：组内任一物品满足需求（矿辞/变体语义）. */
        public Builder inputAlternatives(List<ItemStack> alternatives, long count) {
            List<String> keys = new ArrayList<>();
            List<ItemStack> templates = new ArrayList<>();
            for (ItemStack alt : alternatives) {
                if (alt.isEmpty()) {
                    continue;
                }
                String key = LongItemStore.keyOf(alt);
                if (keys.contains(key)) {
                    continue;
                }
                keys.add(key);
                ItemStack t = alt.copy();
                t.setCount(1);
                templates.add(t);
            }
            if (!keys.isEmpty()) {
                inputGroups.add(new InputGroup(keys, templates, count));
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
            return new ChamberRecipe(id, inputGroups, output, timeTicks);
        }
    }
}
