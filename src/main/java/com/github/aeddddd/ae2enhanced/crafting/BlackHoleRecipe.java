package com.github.aeddddd.ae2enhanced.crafting;

import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 黑洞合成配方.
 * 物品被投入黑洞事件视界后,若累计数量满足输入,则转化为输出产物.
 *
 * 输入 key 格式："registryName:meta",支持同一 Item 的不同 metadata 区分.
 */
public class BlackHoleRecipe {

    private final String id;
    private final Map<String, Integer> inputs;
    private final ItemStack output;

    public BlackHoleRecipe(String id, Map<String, Integer> inputs, ItemStack output) {
        this.id = id;
        // null 归一化：inputs 视为空 Map,output 视为 ItemStack.EMPTY
        this.inputs = inputs != null ? new HashMap<>(inputs) : new HashMap<>();
        this.output = output != null ? output.copy() : ItemStack.EMPTY;
    }

    public String getId() {
        return id;
    }

    public Map<String, Integer> getInputs() {
        return new HashMap<>(inputs);
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    /**
     * 检查 found 中是否包含所有输入且数量足够.
     */
    public boolean matches(Map<String, Integer> found) {
        for (Map.Entry<String, Integer> entry : inputs.entrySet()) {
            if (found.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算 found 中的材料最多可支持本配方的批次数.
     */
    public int maxBatches(Map<String, Integer> found) {
        int batches = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : inputs.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            batches = Math.min(batches, found.getOrDefault(entry.getKey(), 0) / entry.getValue());
        }
        return batches == Integer.MAX_VALUE ? 0 : batches;
    }

    /**
     * 生成 ItemStack 的 key："registryName:meta"，若存在 NBT 则追加 NBT 字符串以区分同 meta 的不同物品。
     */
    public static String keyOf(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) return "";
        String key = stack.getItem().getRegistryName().toString() + ":" + stack.getMetadata();
        if (stack.hasTagCompound()) {
            key += stack.getTagCompound().toString();
        }
        return key;
    }
}
