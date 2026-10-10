/*
 * Derived from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleDefaults;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleLayoutPolicy;

/**
 * 阶段 1 的可测契约：chrome 常量、面板几何、槽位原语与 9-slice 资源的不变量。
 *
 * <p>渲染方法本身依赖 GL 上下文，不在单元测试里调用；这里覆盖的是纯算术与资源层面的事实
 * （也与 {@link NinePatch} 的平铺假设一一对应）。</p>
 */
class ModulePanelTest {

    /** 最小的具体面板：只实现身份/尺寸/槽位布局。 */
    private static final class TestPanel extends ModulePanel {
        private boolean closableOverride = true;
        private final Slot slot = new Slot(new InventoryBasic("test", false, 1), 0, 0, 0);

        TestPanel() {
            registerSlot(slot);
        }

        @Override
        public String id() {
            return "test";
        }

        @Override
        public String title() {
            return "Test";
        }

        @Override
        public int defaultWidth() {
            return 100;
        }

        @Override
        public int defaultHeight() {
            return 80;
        }

        @Override
        public boolean closable() {
            return closableOverride;
        }

        @Override
        public void layoutSlots() {
            placeSlot(slot, contentLeft() + 2, contentTop() + 2);
        }
    }

    private static TestPanel panel() {
        TestPanel panel = new TestPanel();
        panel.x = 10;
        panel.y = 20;
        panel.width = 200;
        panel.height = 120;
        return panel;
    }

    @Test
    void chromeConstantsMatchMestSpecification() {
        assertEquals(17, ChromeLayout.TITLE_BAR_HEIGHT);
        assertEquals(7, ChromeLayout.CONTENT_PADDING);
        assertEquals(10, ChromeLayout.RESIZE_HANDLE);
        assertEquals(8, ChromeLayout.TITLE_LEFT_INSET);
        assertEquals(12, ChromeLayout.PIN_SIZE);
        assertEquals(2, ChromeLayout.CHROME_BUTTON_GAP);
        assertEquals(256, ChromeLayout.TEXTURE_SIZE);
        assertEquals(4, ChromeLayout.BACKGROUND_BORDER);
        assertEquals(248, ChromeLayout.BACKGROUND_TILE);
        assertEquals(ChromeLayout.TEXTURE_SIZE - 2 * ChromeLayout.BACKGROUND_BORDER,
                ChromeLayout.BACKGROUND_TILE);
        // 调色板改成 AE2 19.2.17 的官方色值（ae2modern/states.png 与 background.png 同族）
        assertEquals(0xFFCBCCD4, ChromeLayout.COLOR_PANEL);
        assertEquals(0xFF696D88, ChromeLayout.COLOR_DARK);
        assertEquals(0xFF413F54, ChromeLayout.COLOR_TITLE_TEXT);
        assertEquals(0x66777B8C, ChromeLayout.SECTION_RULE_COLOR);
        assertEquals(0xCCACE9FF, ChromeLayout.SECTION_RULE_HOVER_COLOR);
        assertEquals(0x55000000, ChromeLayout.DROP_SHADOW_COLOR);
        assertEquals("ae2enhanced", ChromeLayout.BACKGROUND_TEXTURE.getNamespace());
        assertEquals("textures/gui/ae2modern/background.png", ChromeLayout.BACKGROUND_TEXTURE.getPath(),
                "窗口框改用 AE2 19.2.17 官方贴图（CC BY-NC-SA 3.0）");
        assertEquals(ChromeLayout.TITLE_BAR_HEIGHT, ModulePanel.TITLE_BAR_HEIGHT);
        assertEquals(ChromeLayout.CONTENT_PADDING, ModulePanel.CONTENT_PADDING);
        assertEquals(-9999, ModulePanel.HIDDEN_SLOT_POSITION);
    }

    @Test
    void contentRectangleFollowsTitleBarPaddingAndInsets() {
        TestPanel panel = panel();
        assertEquals(17, panel.contentLeft());
        assertEquals(37, panel.contentTop());
        assertEquals(186, panel.contentWidth());
        assertEquals(96, panel.contentHeight());

        panel.contentRightInset = 20;
        assertEquals(166, panel.contentWidth());

        panel.contentOffsetX = 3;
        panel.contentOffsetY = 5;
        assertEquals(20, panel.contentLeft());
        assertEquals(42, panel.contentTop());
    }

    @Test
    void hostedLeavesDropTheTitleBarAndBottomPadDependsOnTheWindow() {
        TestPanel panel = panel();
        panel.hosted = true;
        assertFalse(panel.drawsTitleBar());
        assertEquals(27, panel.contentTop());
        assertEquals(106, panel.contentHeight());

        panel.hosted = false;
        assertTrue(panel.drawsTitleBar());
        assertEquals(96, panel.contentHeight());

        // 只有窗口内最底部的那片才保留 AE2 的 7px 下内边距；上方的叶子贴紧兄弟面板
        panel.spliced = true;
        panel.splicedWindow = new DockRect(0, 0, 200, 200);
        assertEquals(103, panel.contentHeight(), "inner leaf drops the bottom pad");
        panel.splicedWindow = new DockRect(0, 0, 200, 100);
        assertEquals(96, panel.contentHeight(), "bottom-most leaf keeps the bottom pad");
    }

    @Test
    void preferredContentSizeAndSlack() {
        TestPanel panel = panel();
        assertEquals(86, panel.preferredContentWidth());
        assertEquals(56, panel.preferredContentHeight());
        assertEquals(100, panel.contentSlackX());
        assertEquals(40, panel.contentSlackY());
    }

    @Test
    void clampSizeEnforcesMinimums() {
        TestPanel panel = new TestPanel();
        panel.width = 10;
        panel.height = 1;
        panel.clampSize();
        assertEquals(60, panel.width);
        assertEquals(ModulePanel.TITLE_BAR_HEIGHT + ModulePanel.CONTENT_PADDING + 18, panel.height);
        assertEquals(42, panel.height);
    }

    @Test
    void hitTestsRespectTitleBarAndResizeHandle() {
        TestPanel panel = panel();
        assertTrue(panel.contains(10, 20));
        assertTrue(panel.contains(209.9D, 139.9D));
        assertFalse(panel.contains(210, 20));
        assertFalse(panel.contains(10, 140));

        assertTrue(panel.inTitleBar(11, 21));
        assertFalse(panel.inTitleBar(11, 20 + ModulePanel.TITLE_BAR_HEIGHT));
        assertTrue(panel.inResizeHandle(200, 130));
        assertFalse(panel.inResizeHandle(180, 130));

        panel.hosted = true;
        assertFalse(panel.inTitleBar(11, 21));
    }

    @Test
    void chromeButtonGeometryAndHitTests() {
        TestPanel panel = panel();
        assertEquals(194, panel.pinButtonX());
        assertEquals(23, panel.pinButtonY());
        // 阶段 3b 起可关闭的面板常驻一颗关闭按钮：预留 = 基础 28 + 关闭(12+2)
        assertEquals(42, panel.titleRightInset());
        assertEquals(0, panel.chromeButtonsReserve());
        assertFalse(panel.pinVisible());
        assertFalse(panel.inPinButton(194, 23));
        assertTrue(panel.closeVisible());
        assertTrue(panel.inCloseButton(panel.closeButtonX() + 1, panel.closeButtonY() + 1));
        assertTrue(panel.inTitleBarControls(panel.closeButtonX() + 1, panel.closeButtonY() + 1));
        assertFalse(panel.inTitleBar(panel.closeButtonX() + 1, panel.closeButtonY() + 1),
                "关闭按钮不能启动窗口拖动");
        panel.setPinControl(true, false);
        assertTrue(panel.pinVisible());
        assertFalse(panel.inCloseButton(195, 25), "关闭按钮给 dock 的 pin 按钮让出了位置");
        assertTrue(panel.inCloseButton(panel.closeButtonX() + 1, panel.closeButtonY() + 1));
        assertFalse(panel.inPinButton(panel.closeButtonX() + 1, panel.closeButtonY() + 1));
        assertFalse(panel.pinned());
        assertTrue(panel.inPinButton(195, 24));
        assertFalse(panel.inPinButton(206, 24));
        assertTrue(panel.inTitleBarControls(195, 24));
        assertFalse(panel.inTitleBar(195, 24), "buttons must not start a window drag");
        assertEquals(16, panel.chromeButtonsReserve());
        assertEquals(54, panel.titleRightInset());
        // 浮动按钮的预留位置只看 pin 是否可见（MEST 的 floatButtonX 语义）
        assertEquals(180, panel.floatButtonX());
        assertFalse(panel.floatVisible());

        panel.setFloatControl(true, false, 0);
        assertTrue(panel.floatVisible());
        assertEquals(180, panel.floatButtonX());
        assertTrue(panel.inFloatButton(181, 24));
        assertEquals(30, panel.chromeButtonsReserve());
        assertEquals(68, panel.titleRightInset());

        panel.setPinControl(true, true);
        assertTrue(panel.pinned());

        panel.contentEditing = true;
        assertTrue(panel.contentChromeVisible());
        assertFalse(panel.pinVisible());
        assertFalse(panel.floatVisible());
        assertEquals(34, panel.titleRightInset());
        assertEquals(panel.pinButtonX() - ChromeLayout.PIN_SIZE - ChromeLayout.CHROME_BUTTON_GAP,
                panel.resetButtonX());
        assertTrue(panel.inResetButton(panel.resetButtonX(), panel.resetButtonY()));
        assertTrue(panel.inCloseButton(panel.closeButtonX(), panel.closeButtonY()));
    }

    @Test
    void nonClosablePanelHasNoCloseButtonAndNoReserve() {
        TestPanel panel = panel();
        panel.closableOverride = false;
        assertFalse(panel.closeVisible());
        assertFalse(panel.inCloseButton(panel.closeButtonX() + 1, panel.closeButtonY() + 1));
        assertEquals(28, panel.titleRightInset(), "不可关闭→不加关闭预留");
    }

    @Test
    void slotPrimitivesWrite112PositionsAndSkipHiddenSlots() {
        TestPanel panel = panel();
        Slot slot = null;
        for (Slot owned : panel.ownedSlots()) {
            slot = owned;
        }
        assertNotNull(slot);
        assertTrue(panel.ownsSlot(slot));
        assertFalse(panel.ownsSlot(new Slot(new InventoryBasic("other", false, 1), 0, 0, 0)));

        panel.layoutSlots();
        assertEquals(19, ModulePanel.slotScreenX(slot));
        assertEquals(39, ModulePanel.slotScreenY(slot));
        assertEquals(19, slot.xPos);
        assertEquals(39, slot.yPos);

        List<Slot> seen = new ArrayList<Slot>();
        panel.renderSlots(0, 0, 0.0F, (drawn, mouseX, mouseY, partialTicks, zLevel) -> {
            seen.add(drawn);
            assertEquals(0.0F, zLevel);
        }, 0.0F);
        assertEquals(1, seen.size());

        ModulePanel.hideSlot(slot);
        assertEquals(-9999, slot.xPos);
        assertEquals(-9999, slot.yPos);
        seen.clear();
        panel.renderSlots(0, 0, 0.0F, (drawn, mouseX, mouseY, partialTicks, zLevel) -> seen.add(drawn), 0.0F);
        assertTrue(seen.isEmpty(), "hidden slots must not be drawn");

        // 插槽绘制回调为 null 时不得 NPE
        panel.renderSlots(0, 0, 0.0F, null, 0.0F);
    }

    @Test
    void inputHooksAndFrameworkSwitchesDefaultToInert() {
        TestPanel panel = panel();
        assertFalse(panel.mouseClicked(15, 25, 0));
        assertFalse(panel.mouseScrolled(15, 25, 1.0D));
        assertFalse(panel.scrollbarPressed(15, 25));
        assertFalse(panel.scrollbarDragged(15, 25));
        assertFalse(panel.scrollbarDragging());
        panel.scrollbarReleased();

        assertTrue(panel.canSplice());
        assertTrue(panel.canCombine());
        assertTrue(panel.visible);
        assertTrue(panel.rightmostInWindow);
        assertFalse(panel.expandsVertically());
        assertFalse(panel.fillsContentArea());
        assertEquals(0, panel.preferredContentRightInset());
        assertSame(ModuleDefaults.STANDARD, panel.defaults());
        assertEquals(ModuleLayoutPolicy.defaults().visible(), panel.defaults().visible());
    }

    @Test
    void bundledNinePatchTextureIsAValid256pxSlice() throws Exception {
        InputStream in = ModulePanelTest.class
                .getResourceAsStream("/assets/ae2enhanced/textures/gui/dock/background.png");
        assertNotNull(in, "9-slice texture must ship with the mod");
        BufferedImage image;
        try {
            image = ImageIO.read(in);
        } finally {
            in.close();
        }
        assertNotNull(image);
        assertEquals(ChromeLayout.TEXTURE_SIZE, image.getWidth());
        assertEquals(ChromeLayout.TEXTURE_SIZE, image.getHeight());

        int border = ChromeLayout.BACKGROUND_BORDER;
        int tex = ChromeLayout.TEXTURE_SIZE;
        // 中部必须单色、各边框条必须沿自己的轴向恒定，否则 9-slice 平铺会露出接缝
        int middle = image.getRGB(border + 1, border + 1);
        for (int y = border; y < tex - border; y++) {
            for (int x = border; x < tex - border; x++) {
                assertEquals(middle, image.getRGB(x, y), "middle must be uniform at " + x + "," + y);
            }
        }
        for (int y = 0; y < border; y++) {
            int first = image.getRGB(border, y);
            for (int x = border; x < tex - border; x++) {
                assertEquals(first, image.getRGB(x, y), "top/bottom band must be constant at y=" + y);
            }
        }
        for (int y = tex - border; y < tex; y++) {
            int first = image.getRGB(border, y);
            for (int x = border; x < tex - border; x++) {
                assertEquals(first, image.getRGB(x, y), "bottom band must be constant at y=" + y);
            }
        }
        for (int x = 0; x < border; x++) {
            int first = image.getRGB(x, border);
            for (int y = border; y < tex - border; y++) {
                assertEquals(first, image.getRGB(x, y), "left/right band must be constant at x=" + x);
            }
        }
        for (int x = tex - border; x < tex; x++) {
            int first = image.getRGB(x, border);
            for (int y = border; y < tex - border; y++) {
                assertEquals(first, image.getRGB(x, y), "right band must be constant at x=" + x);
            }
        }
        // 每个绘制区都必须完全不透明，否则面板会漏出底下的画面
        for (int y = 0; y < tex; y++) {
            for (int x = 0; x < tex; x++) {
                assertEquals(0xFF000000, image.getRGB(x, y) & 0xFF000000,
                        "9-slice pixel must be opaque at " + x + "," + y);
            }
        }
    }

    @Test
    void rendererTilingCoversEveryPanelSizeExactlyOnce() {
        final int border = ChromeLayout.BACKGROUND_BORDER;
        final int tile = ChromeLayout.BACKGROUND_TILE;
        final int tex = ChromeLayout.TEXTURE_SIZE;
        final int panelX = 30;
        final int panelY = 40;
        int[] sizes = { 8, 9, 16, 100, 200, 255, 256, 257, 500, 512, 1000 };
        for (final int width : sizes) {
            for (final int height : sizes) {
                final int[][] coverage = new int[height][width];
                final int[] sliceCount = { 0 };
                NinePatch.forEachSlice(panelX, panelY, width, height,
                        (u, v, destX, destY, sliceW, sliceH) -> {
                            sliceCount[0]++;
                            assertTrue(destX >= panelX && destY >= panelY,
                                    "slice must start inside the panel");
                            assertTrue(destX + sliceW <= panelX + width && destY + sliceH <= panelY + height,
                                    "slice must stay inside the panel");
                            assertTrue(sliceW >= 1 && sliceW <= tile && sliceH >= 1 && sliceH <= tile,
                                    "slice size must be 1..tile");
                            // 源矩形必须与目标位置一一对应，否则九个切片会被贴错位置
                            assertEquals(destX == panelX ? 0.0F
                                    : (destX == panelX + width - border ? tex - border : border), u,
                                    "u for destX=" + destX + " in " + width + "x" + height);
                            assertEquals(destY == panelY ? 0.0F
                                    : (destY == panelY + height - border ? tex - border : border), v,
                                    "v for destY=" + destY + " in " + width + "x" + height);
                            if (u == 0.0F || u == tex - border) {
                                assertEquals(border, sliceW, "vertical band slice must be one border wide");
                            }
                            if (v == 0.0F || v == tex - border) {
                                assertEquals(border, sliceH, "horizontal band slice must be one border tall");
                            }
                            for (int dy = 0; dy < sliceH; dy++) {
                                for (int dx = 0; dx < sliceW; dx++) {
                                    coverage[destY - panelY + dy][destX - panelX + dx]++;
                                }
                            }
                        });
                assertTrue(sliceCount[0] >= 4, "at least the four corners");
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        assertEquals(1, coverage[y][x],
                                "gap/overlap at " + x + "," + y + " for " + width + "x" + height);
                    }
                }
            }
        }
    }

    @Test
    void rendererTilingSkipsPanelsThinnerThanTwoBorders() {
        int[] tooSmall = { 0, 1, 7 };
        for (int width : tooSmall) {
            List<String> slices = new ArrayList<String>();
            NinePatch.forEachSlice(0, 0, width, 100,
                    (u, v, destX, destY, sliceW, sliceH) -> slices.add(destX + "," + destY));
            assertTrue(slices.isEmpty(), "width " + width + " must not produce slice draws");
        }
        for (int height : tooSmall) {
            List<String> slices = new ArrayList<String>();
            NinePatch.forEachSlice(0, 0, 100, height,
                    (u, v, destX, destY, sliceW, sliceH) -> slices.add(destX + "," + destY));
            assertTrue(slices.isEmpty(), "height " + height + " must not produce slice draws");
        }
        // 最小可绘制尺寸：8x8 只有四个角
        List<String> corners = new ArrayList<String>();
        NinePatch.forEachSlice(0, 0, 8, 8,
                (u, v, destX, destY, sliceW, sliceH) -> corners.add(destX + "," + destY));
        assertEquals(4, corners.size());
    }
}
