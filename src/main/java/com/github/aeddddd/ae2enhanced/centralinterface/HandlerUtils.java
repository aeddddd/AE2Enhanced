package com.github.aeddddd.ae2enhanced.centralinterface;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
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
     * 收集合成网格中的非空物品（按槽位顺序）.
     *
     * <p>返回的是<b>防御性副本</b>（每个非空槽位 {@code stack.copy()}）：调用方普遍会从结果里
     * 逐个 {@code remove} 来匹配配方输入，副本可保证这种消费不会写回样板表 / 玩家网格本身。
     * 空槽位直接跳过，因此返回列表的长度通常小于网格槽位数。</p>
     *
     * @param ingredients 合成网格；不得为 null
     * @return 非空槽位的副本列表（可变，可能为空的非 null 列表）
     */
    public static List<ItemStack> collectNonEmpty(InventoryCrafting ingredients) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) list.add(stack.copy());
        }
        return list;
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
