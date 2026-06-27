package com.github.aeddddd.ae2enhanced.client;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ItemRepo;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IIngredientListOverlay;
import mezz.jei.api.IBookmarkOverlay;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * F7：在 AE2 终端打开时,对 JEI 物品列表或收藏栏中的物品按下配置键位,
 * 自动将该物品的显示名称填入终端搜索栏.
 *
 * <p>扩展设计：
 * <ul>
 *   <li>支持 GuiMEMonitorable 及其所有子类(合成终端、无线终端等)</li>
 *   <li>同时检测 JEI 物品列表和收藏栏(Bookmark Overlay)</li>
 *   <li>键位通过 Forge KeyBinding 注册,可在 Controls 菜单修改</li>
 *   <li>默认不自动聚焦搜索栏,避免打断用户浏览物品</li>
 * </ul>
 */
public class JEISearchKeyHandler {

    private static IJeiRuntime jeiRuntime = null;

    public static void setJeiRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    public static IJeiRuntime getJeiRuntime() {
        return jeiRuntime;
    }

    /**
     * 执行 JEI 搜索：将 JEI 悬停物品的名称填入终端搜索栏.
     * 由 GuiOmniTerm.keyTyped() 和 MixinGuiMEMonitorableKeyHandler 调用.
     */
    public static boolean performSearch(GuiMEMonitorable gui) {
        return performSearch(gui, 0, 0);
    }

    /**
     * 执行 JEI 搜索,携带鼠标 GUI 坐标以支持 fallback 检测(HEI 收藏栏等).
     * @return true 表示成功设置了搜索文本并应拦截原按键事件；false 表示未执行搜索
     */
    public static boolean performSearch(GuiMEMonitorable gui, int mouseX, int mouseY) {
        MEGuiTextField searchField;
        try {
            Field searchFieldField = GuiMEMonitorable.class.getDeclaredField("searchField");
            searchFieldField.setAccessible(true);
            searchField = (MEGuiTextField) searchFieldField.get(gui);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get searchField via reflection", e);
            return false;
        }
        if (searchField == null) return false;
        if (searchField.isFocused()) return false; // 搜索栏已聚焦时不触发,避免打断输入

        // 获取 JEI 悬停物品：先查物品列表,再查收藏栏
        if (jeiRuntime == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] JEI runtime is null, skipping search");
            return false;
        }
        Object ingredient = null;

        IIngredientListOverlay listOverlay = jeiRuntime.getIngredientListOverlay();
        if (listOverlay != null) {
            ingredient = listOverlay.getIngredientUnderMouse();
            if (ingredient != null) {
                AE2Enhanced.LOGGER.debug("[AE2E] Found ingredient in IngredientListOverlay");
            }
        }
        if (ingredient == null) {
            IBookmarkOverlay bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
            if (bookmarkOverlay != null) {
                ingredient = bookmarkOverlay.getIngredientUnderMouse();
                if (ingredient != null) {
                    AE2Enhanced.LOGGER.debug("[AE2E] Found ingredient in BookmarkOverlay (no-arg)");
                } else if (mouseX != 0 || mouseY != 0) {
                    // Fallback: HEI 的 BookmarkOverlay 在某些情况下无参方法返回 null,
                    // 但 getIngredientUnderMouse(int, int) 可以正常工作
                    try {
                        java.lang.reflect.Method method = bookmarkOverlay.getClass()
                                .getMethod("getIngredientUnderMouse", int.class, int.class);
                        Object clickedIngredient = method.invoke(bookmarkOverlay, mouseX, mouseY);
                        if (clickedIngredient != null) {
                            java.lang.reflect.Method getValue = clickedIngredient.getClass()
                                    .getMethod("getValue");
                            ingredient = getValue.invoke(clickedIngredient);
                            if (ingredient != null) {
                                AE2Enhanced.LOGGER.debug("[AE2E] Found ingredient in BookmarkOverlay via fallback (int,int) at ({}, {})", mouseX, mouseY);
                            }
                        }
                    } catch (Exception e) {
                        AE2Enhanced.LOGGER.debug("[AE2E] BookmarkOverlay fallback reflection failed", e);
                    }
                }
            }
        }
        if (ingredient == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] No JEI ingredient found under mouse at ({}, {})", mouseX, mouseY);
            return false;
        }

        String searchText = extractSearchText(ingredient);
        if (searchText == null || searchText.isEmpty()) return false;

        // 设置搜索栏文本并立即刷新 repo
        try {
            searchField.setText(searchText);

            // 更新 repo 搜索字符串(立即生效,无需等待 drawScreen)
            Field repoField = GuiMEMonitorable.class.getDeclaredField("repo");
            repoField.setAccessible(true);
            ItemRepo repo = (ItemRepo) repoField.get(gui);
            if (repo != null) {
                repo.setSearchString(searchText);
            }

            // 更新 static memoryText(使关闭再打开 GUI 时保留搜索词)
            Field memoryTextField = GuiMEMonitorable.class.getDeclaredField("memoryText");
            memoryTextField.setAccessible(true);
            memoryTextField.set(null, searchText);

            // 调整滚动条以匹配新的搜索结果数量
            Method setScrollBarMethod = GuiMEMonitorable.class.getDeclaredMethod("setScrollBar");
            setScrollBarMethod.setAccessible(true);
            setScrollBarMethod.invoke(gui);

            AE2Enhanced.LOGGER.debug("[AE2E] JEI search key pressed: set terminal search to '{}'", searchText);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to set terminal search text from JEI hover", e);
            return false;
        }
        return true;
    }

    private static String extractSearchText(Object ingredient) {
        String raw = null;

        // HEI 收藏栏返回的是 BookmarkItem 包装类,需要先解包出内部 ingredient
        Object actual = unwrapBookmarkItem(ingredient);

        if (actual instanceof ItemStack) {
            raw = ((ItemStack) actual).getDisplayName();
        } else if (actual instanceof net.minecraftforge.fluids.FluidStack) {
            raw = ((net.minecraftforge.fluids.FluidStack) actual).getLocalizedName();
        } else {
            // 对未知类型尝试通用 getDisplayName
            try {
                java.lang.reflect.Method m = actual.getClass().getMethod("getDisplayName");
                Object result = m.invoke(actual);
                if (result instanceof String) raw = (String) result;
            } catch (Exception ignored) {
            }
        }
        if (raw == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not extract search text from ingredient type: {}",
                    ingredient != null ? ingredient.getClass().getName() : "null");
            return null;
        }
        // 去除 Minecraft 颜色代码(§[0-9a-fk-or])
        return TextFormatting.getTextWithoutFormattingCodes(raw);
    }

    /**
     * 解包 HEI 的 BookmarkItem 包装类.HEI 收藏栏的 getIngredientUnderMouse()
     * 返回的是 mezz.jei.bookmarks.BookmarkItem,其内部 ingredient 字段才是真正的 ItemStack.
     */
    private static Object unwrapBookmarkItem(Object ingredient) {
        if (ingredient == null) return null;
        if ("mezz.jei.bookmarks.BookmarkItem".equals(ingredient.getClass().getName())) {
            try {
                java.lang.reflect.Field field = ingredient.getClass().getField("ingredient");
                return field.get(ingredient);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to unwrap BookmarkItem", e);
            }
        }
        return ingredient;
    }
}
