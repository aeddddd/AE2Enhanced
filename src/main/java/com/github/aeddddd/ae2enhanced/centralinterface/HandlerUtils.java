package com.github.aeddddd.ae2enhanced.centralinterface;

import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Handler 层通用工具类.
 *
 * <p>提供通用物品匹配等，减少各 handler 的重复代码。</p>
 */
public final class HandlerUtils {

    private HandlerUtils() {
    }

    /**
     * 判断物品是否在给定的输入快照中。
     */
    public static boolean isInputMaterial(ItemStack stack, @Nullable List<ItemStack> inputs) {
        if (stack.isEmpty() || inputs == null || inputs.isEmpty()) {
            return false;
        }
        for (ItemStack input : inputs) {
            if (ItemStack.areItemsEqual(stack, input) && ItemStack.areItemStackTagsEqual(stack, input)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 宽松匹配：item + metadata 相同；若 expected 有 NBT 则要求 NBT 也相同。
     */
    public static boolean matchesLoosely(ItemStack actual, ItemStack expected) {
        if (!ItemStack.areItemsEqual(actual, expected)) {
            return false;
        }
        if (!expected.hasTagCompound()) {
            return true;
        }
        return ItemStack.areItemStackTagsEqual(actual, expected);
    }
}
