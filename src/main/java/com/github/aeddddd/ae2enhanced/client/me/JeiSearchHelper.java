package com.github.aeddddd.ae2enhanced.client.me;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import mezz.jei.api.IJeiRuntime;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HEI/JEI 搜索辅助类：通过反射调用 HEI 内部 IngredientFilter 的 getFilteredIngredients(String)，
 * 把 HEI 的搜索结果（含 JECH/HECH 拼音、tooltip、mod 前缀等语义）转换为 AE2E 的 ItemDescriptor 列表。
 *
 * <p>该类仅在客户端使用，所有 JEI 相关类均通过字符串/反射访问，避免运行时缺少 JEI 导致类加载失败。
 */
public final class JeiSearchHelper {

    private JeiSearchHelper() {}

    private static final int MAX_RESULTS = 5000;

    private static Method getFilteredIngredientsMethod = null;
    private static Boolean available = null;

    /**
     * 判断当前是否可以通过 HEI/JEI 进行高级搜索。
     */
    public static boolean isAvailable() {
        if (available == null) {
            available = init();
        }
        return available;
    }

    private static boolean init() {
        IJeiRuntime runtime = JEISearchKeyHandler.getJeiRuntime();
        if (runtime == null) {
            return false;
        }
        Object filter = runtime.getIngredientFilter();
        if (filter == null) {
            return false;
        }
        try {
            Method m = filter.getClass().getMethod("getFilteredIngredients", String.class);
            m.setAccessible(true);
            getFilteredIngredientsMethod = m;
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] HEI/JEI IngredientFilter.getFilteredIngredients(String) not found, falling back to server-side search", e);
            return false;
        }
    }

    /**
     * 使用 HEI/JEI 过滤器搜索指定文本，返回匹配的物品描述符列表。
     *
     * @param query 搜索文本（已去除 AE2E 自己的前缀）
     * @return 匹配描述符列表；如果 HEI 不可用、结果过多或发生异常，则返回 null（调用方应回退到服务端字面搜索）
     */
    @SuppressWarnings("unchecked")
    public static List<ItemDescriptor> getMatchingDescriptors(String query) {
        if (!isAvailable() || query == null || query.isEmpty()) {
            return null;
        }

        Object filter = JEISearchKeyHandler.getJeiRuntime().getIngredientFilter();
        if (filter == null || getFilteredIngredientsMethod == null) {
            return null;
        }

        List<Object> ingredients;
        try {
            ingredients = (List<Object>) getFilteredIngredientsMethod.invoke(filter, query);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to query HEI/JEI filter", e);
            return null;
        }

        if (ingredients == null || ingredients.isEmpty()) {
            return Collections.emptyList();
        }

        // 结果过多时放弃 HEI 过滤，避免巨型数据包和客户端卡顿
        if (ingredients.size() > MAX_RESULTS) {
            AE2Enhanced.LOGGER.debug("[AE2E] HEI/JEI filter returned {} results, exceeding max {}, fallback to server search", ingredients.size(), MAX_RESULTS);
            return null;
        }

        List<ItemDescriptor> result = new ArrayList<>(Math.min(ingredients.size(), 1024));
        for (Object ingredient : ingredients) {
            if (!(ingredient instanceof ItemStack)) {
                continue;
            }
            ItemStack stack = (ItemStack) ingredient;
            if (stack.isEmpty()) {
                continue;
            }
            try {
                result.add(new ItemDescriptor(stack));
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to create ItemDescriptor for HEI result {}", stack, e);
            }
        }
        return result;
    }
}
