package com.github.aeddddd.ae2enhanced.centralinterface.recipe;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方输入校验：样板声明的输入是否真的能构成该配方.
 *
 * <p>样板声明的输入必须真的能构成目标配方，否则"输入随便填、输出取某真实配方产物"的样板
 * 就能虚拟产出真产物，而成本按配方折算 —— 用料与产物脱钩。</p>
 *
 * <p>匹配是<b>无序多重集</b>匹配：EC 工作台把样板按处理样板对待，槽位顺序与配方
 * {@code getIngredients()} 的顺序不保证一致，因此按"每个配方 ingredient 贪心认领一个未使用的实物"
 * 判定，要求数量与内容都吻合。</p>
 */
public final class IngredientMatcher {

    private IngredientMatcher() {
    }

    /**
     * 样板输入是否与配方的 ingredients 吻合.
     *
     * @param table  单份配方的输入（来自合成台）
     * @param recipe 目标配方
     * @return 吻合返回 true；{@code table} 为空、配方无有效 ingredient、或数量/内容不吻合返回 false
     */
    public static boolean matches(InventoryCrafting table, IRecipe recipe) {
        return recipe != null && matches(table, recipe.getIngredients());
    }

    /**
     * 样板输入是否与一组 ingredient 吻合（供配方对象不是 {@link IRecipe} 的 mod 直接传 ingredients）.
     *
     * @param table    单份配方的输入（来自合成台）
     * @param required 配方的 ingredient 列表
     * @return 吻合返回 true；{@code table} 为空、无有效 ingredient、或数量/内容不吻合返回 false
     */
    public static boolean matches(InventoryCrafting table, NonNullList<Ingredient> required) {
        if (table == null || required == null) {
            return false;
        }

        List<ItemStack> provided = new ArrayList<>();
        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (!stack.isEmpty()) {
                provided.add(stack);
            }
        }

        List<Ingredient> effective = new ArrayList<>();
        for (Ingredient ingredient : required) {
            if (ingredient != null && ingredient != Ingredient.EMPTY) {
                effective.add(ingredient);
            }
        }

        // 数量必须一致：少一件意味着样板声明不足，多一件意味着有未被计价的投入
        if (effective.size() != provided.size() || effective.isEmpty()) {
            return false;
        }

        boolean[] claimed = new boolean[provided.size()];
        for (Ingredient ingredient : effective) {
            boolean matched = false;
            for (int i = 0; i < provided.size(); i++) {
                if (claimed[i]) {
                    continue;
                }
                if (ingredient.apply(provided.get(i))) {
                    claimed[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }
}
