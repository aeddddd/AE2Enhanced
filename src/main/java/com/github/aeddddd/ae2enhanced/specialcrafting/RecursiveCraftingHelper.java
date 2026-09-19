package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * 递归类合成(产物与原料含相同物品且净产出为正,如 A+2B=2A)的判定工具.
 * <p>1.12.2 的 ae2fc 流体样板以 FluidDrop 假物品形式存在,物品通道天然覆盖流体配方.</p>
 */
public final class RecursiveCraftingHelper {

    private RecursiveCraftingHelper() {
    }

    /**
     * 规范化键:类型相同即相等(数量/可合成标志清零),用于 Map/Set 键与等值比较.
     */
    public static IAEItemStack canon(IAEItemStack stack) {
        IAEItemStack copy = stack.copy();
        copy.reset();
        return copy;
    }

    /**
     * 每次合成消耗的 {@code what} 数量（凝聚输入合计）.
     */
    public static long selfInputPerCraft(ICraftingPatternDetails details, IAEItemStack what) {
        long in = 0;
        for (IAEItemStack input : details.getCondensedInputs()) {
            if (input != null && what.equals(input)) {
                in += input.getStackSize();
            }
        }
        return in;
    }

    /**
     * 每次合成产出的 {@code what} 数量.
     */
    public static long selfOutputPerCraft(ICraftingPatternDetails details, IAEItemStack what) {
        long out = 0;
        for (IAEItemStack output : details.getCondensedOutputs()) {
            if (output != null && what.equals(output)) {
                out += output.getStackSize();
            }
        }
        return out;
    }

    /**
     * 是否为 {@code what} 的净产出自引用样板:原料与产物均含 what,且每次合成净产出为正.
     */
    public static boolean isNetPositiveSelfRef(ICraftingPatternDetails details, IAEItemStack what) {
        long in = selfInputPerCraft(details, what);
        if (in <= 0) {
            return false;
        }
        return selfOutputPerCraft(details, what) > in;
    }
}
