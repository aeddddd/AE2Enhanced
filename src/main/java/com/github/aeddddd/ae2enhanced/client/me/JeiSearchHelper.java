package com.github.aeddddd.ae2enhanced.client.me;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import mezz.jei.api.IJeiRuntime;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
     * 读取 HEI/JEI 搜索框当前文本（不可用时返回 {@code null}）。
     *
     * <p>AE2 的 {@code SearchBoxMode.JEI_*} 语义就是"终端搜索框与 JEI 搜索同步"：旧终端在
     * {@code GuiMEMonitorable.initGui} 里用 {@code Integrations.jei().getSearchText()} 把 JEI 的
     * 搜索词写进 {@code memoryText}，KEEP 模式下再套用到搜索框与 repo。本方法给可拼接终端
     * 提供同一条读取路径（走反射，JEI 不在场时返回 null 而不是抛错）。</p>
     */
    public static String getJeiFilterText() {
        if (!isAvailable()) {
            return null;
        }
        IJeiRuntime runtime = JEISearchKeyHandler.getJeiRuntime();
        if (runtime == null) {
            return null;
        }
        try {
            Object filter = runtime.getIngredientFilter();
            if (filter == null) {
                return null;
            }
            Method m = filter.getClass().getMethod("getFilterText");
            m.setAccessible(true);
            Object text = m.invoke(filter);
            return text instanceof String ? (String) text : null;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to read HEI/JEI filter text", e);
            return null;
        }
    }

    /**
     * A4 取证：当前打开的容器在 JEI 里有没有可用的配方转移处理器（返回可读描述）。
     *
     * <p>JEI 按容器<b>精确类</b>查表（{@code RecipeRegistry.getRecipeTransferHandler}），
     * 所以这里用同一个 API 查一次，能直接暴露"忘了给某个容器注册处理器"这类问题。</p>
     */
    public static String describeTransferHandler(Object container) {
        if (container == null) {
            return "容器=null";
        }
        if (!isAvailable()) {
            return "JEI 未就绪（无法查转移处理器）";
        }
        IJeiRuntime runtime = JEISearchKeyHandler.getJeiRuntime();
        try {
            Object registry = runtime.getRecipeRegistry();
            Method getCategories = registry.getClass().getMethod("getRecipeCategories");
            Object categories = getCategories.invoke(registry);
            Object crafting = null;
            if (categories instanceof List) {
                for (Object category : (List<?>) categories) {
                    if (category == null) {
                        continue;
                    }
                    Object uid = category.getClass().getMethod("getUid").invoke(category);
                    if ("minecraft.crafting".equals(uid)) {
                        crafting = category;
                        break;
                    }
                }
            }
            if (crafting == null) {
                return "找不到 minecraft.crafting 类别";
            }
            Method lookup = registry.getClass().getMethod("getRecipeTransferHandler", Class.forName("net.minecraft.inventory.Container"), Class.forName("mezz.jei.api.recipe.IRecipeCategory"));
            Object handler = lookup.invoke(registry, container, crafting);
            return container.getClass().getSimpleName() + " × minecraft.crafting → "
                    + (handler == null ? "无处理器（[+] 会不可用）" : handler.getClass().getSimpleName() + "（可用）");
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to query JEI transfer handler", e);
            return "查询失败: " + e;
        }
    }

    /**
     * 判断当前是否可以通过 HEI/JEI 进行高级搜索。
     * 如果之前初始化失败，会再次尝试（因为 JEI 可能在游戏过程中才就绪）。
     */
    public static boolean isAvailable() {
        if (available == null || !available) {
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
            return null;
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
