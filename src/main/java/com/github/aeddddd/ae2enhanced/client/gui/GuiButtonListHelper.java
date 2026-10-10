package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.util.reflection.ReflectionHelper;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.lang.reflect.Field;
import java.util.List;

/**
 * {@code GuiScreen.buttonList} 的反射访问.
 *
 * <p>原本由 accessor mixin(IGuiScreenAccessor)提供, 但生产环境下该字段是 SRG 名
 * {@code field_146292_n}, accessor 需要经 refmap 映射; 当前 Mixin AP 0.8.5 生成的 refmap 键与
 * 运行期 MixinBooter 的查找键不一致, 导致 accessor mixin 应用失败
 * (日志: InvalidAccessorException No candidates were found matching buttonList),
 * 而失败的 accessor mixin 仍把接口合并进 GuiScreen, 于是 {@code (IGuiScreenAccessor) gui}
 * 调用抛 AbstractMethodError; 该错误发生在 {@code initGui} 内时会中断 GUI 初始化,
 * 使 AE2 的 GuiCraftingStatus 在 cpuScrollbar 未创建的状态下被渲染并崩溃
 * (见 2026-09-25 玩家日志). 反射按"MCP 名 → SRG 名"顺序探测, 两种环境均可用.</p>
 */
public final class GuiButtonListHelper {

    private static final Field BUTTON_LIST =
            ReflectionHelper.findAnyFieldInHierarchy(GuiScreen.class, "buttonList", "field_146292_n");

    private GuiButtonListHelper() {}

    /** 读取按钮列表, 不可用时返回 null. */
    @SuppressWarnings("unchecked")
    public static List<GuiButton> getButtonList(GuiScreen screen) {
        if (BUTTON_LIST == null) {
            return null;
        }
        try {
            return (List<GuiButton>) BUTTON_LIST.get(screen);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 向 GUI 添加按钮, 返回 false 表示按钮列表不可用(调用方应静默跳过). */
    public static boolean addButton(GuiScreen screen, GuiButton button) {
        List<GuiButton> list = getButtonList(screen);
        if (list == null) {
            return false;
        }
        list.add(button);
        return true;
    }
}
