package com.github.aeddddd.ae2enhanced.integration.jei;

import java.awt.Rectangle;
import java.util.List;

import javax.annotation.Nullable;

import mezz.jei.api.gui.IGuiProperties;
import net.minecraft.client.gui.GuiScreen;

import com.github.aeddddd.ae2enhanced.client.gui.terminal.GuiSplicedTerm;

/**
 * 可拼接终端上报给 JEI/HEI 的"GUI 本体矩形"。
 *
 * <h3>为什么必须上报</h3>
 * <p>HEI 用 {@code IGuiProperties}（guiLeft/guiTop/guiXSize/guiYSize）决定三件事：</p>
 * <ol>
 *   <li><b>物品列表的位置</b>：{@code IngredientListOverlay.getDisplayArea} = {@code
 *       (guiLeft + guiXSize + 2, 2, screenWidth - x - 2, screenHeight - 4)}，即"GUI 右侧那一整条"；</li>
 *   <li><b>书签区（左侧栏）的位置</b>：{@code LeftAreaDispatcher.makeDisplayArea} 用
 *       {@code guiLeft - 4} 当宽度；</li>
 *   <li><b>搜索框/翻页/配置按钮</b>：全部由 {@code displayArea} 派生。</li>
 * </ol>
 * <p>默认实现（{@code GuiProperties::create(GuiContainer)}）读的是 {@code guiLeft/guiTop/xSize/ySize}。
 * 本终端是全屏对话框（{@code xSize = ySize = 0}，{@code guiLeft = guiTop = 0}），于是
 * {@code displayArea.x = 0 + 0 + 2 = 2} —— 物品列表被判定为"整屏可用"而铺满全屏，
 * 书签区宽度 {@code 0 - 4 < 0} 直接被挤没：这正是"HEI 布局全乱 + 按钮位置不对"的根因。
 * 仅靠 {@code IAdvancedGuiHandler.getGuiExtraAreas()}（我们已实现的
 * {@link GuiSplicedTerm#getJEIExclusionArea()}）只能让物品格<b>避开</b>矩形，
 * 改不了"列表从 x=2 开始铺"这件事。</p>
 *
 * <h3>本实现</h3>
 * <p>把"GUI 本体"报成<b>所有可见面板的并集</b>：物品列表自然落到并集右侧、书签落到并集左侧，
 * 两者都跟随面板拖拽（HEI 每帧都会重新取一次属性 + 排除区）。不重叠的窗口共享同一矩形的语义，
 * 与终端"面板可以放在任意位置"的模型一致。</p>
 *
 * <p>只在装了 JEI/HEI 时才会被加载（由 {@code AE2EnhancedJEIPlugin} 注册）。</p>
 */
public final class SplicedTermGuiProperties implements IGuiProperties {

    private final Class<? extends GuiScreen> guiClass;
    private final int guiLeft;
    private final int guiTop;
    private final int guiXSize;
    private final int guiYSize;
    private final int screenWidth;
    private final int screenHeight;

    private SplicedTermGuiProperties(Class<? extends GuiScreen> guiClass, int guiLeft, int guiTop,
            int guiXSize, int guiYSize, int screenWidth, int screenHeight) {
        this.guiClass = guiClass;
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.guiXSize = guiXSize;
        this.guiYSize = guiYSize;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * 由可见面板的并集构造属性；一个可见面板都没有（理论上不会发生）时返回 {@code null}，
     * 此时 HEI 会隐藏自己的浮层（比铺满屏幕安全）。
     */
    @Nullable
    public static IGuiProperties create(GuiSplicedTerm gui) {
        List<Rectangle> areas = gui.getJEIExclusionArea();
        if (areas.isEmpty()) {
            return null;
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (Rectangle area : areas) {
            left = Math.min(left, (int) area.getMinX());
            top = Math.min(top, (int) area.getMinY());
            right = Math.max(right, (int) area.getMaxX());
            bottom = Math.max(bottom, (int) area.getMaxY());
        }
        // 夹到屏幕内：面板可以被拖出屏幕，越界矩形会让 HEI 算出负宽度的展示区
        int x = Math.max(0, Math.min(left, Math.max(0, gui.width - 1)));
        int y = Math.max(0, Math.min(top, Math.max(0, gui.height - 1)));
        int width = Math.max(1, Math.min(right, gui.width) - x);
        int height = Math.max(1, Math.min(bottom, gui.height) - y);
        return new SplicedTermGuiProperties(gui.getClass(), x, y, width, height, gui.width, gui.height);
    }

    @Override
    public Class<? extends GuiScreen> getGuiClass() {
        return this.guiClass;
    }

    @Override
    public int getGuiLeft() {
        return this.guiLeft;
    }

    @Override
    public int getGuiTop() {
        return this.guiTop;
    }

    @Override
    public int getGuiXSize() {
        return this.guiXSize;
    }

    @Override
    public int getGuiYSize() {
        return this.guiYSize;
    }

    @Override
    public int getScreenWidth() {
        return this.screenWidth;
    }

    @Override
    public int getScreenHeight() {
        return this.screenHeight;
    }
}
