package com.github.aeddddd.ae2enhanced.client.gui.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.client.gui.widgets.GuiScrollbar;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import org.junit.jupiter.api.Test;

/**
 * 阶段 2b：repo 面板的可变列/行推导与滚动范围（纯几何，可在无头环境验证）。
 *
 * <p>这些公式是"面板尺寸 ↔ 可见物品数 ↔ 缓存容量 ↔ 分页 limit"这条链的唯一定义，
 * 一旦漂移就会出现空白格或静默截断，因此单独锁定。</p>
 */
class RepoPanelTest {

    private static int panelWidthFor(int columns) {
        return columns * RepoPanel.SLOT_SIZE + RepoPanel.SCROLLBAR_GAP + RepoPanel.SCROLLBAR_WIDTH
                + 2 * ModulePanel.CONTENT_PADDING;
    }

    private static int contentWidthOf(int panelWidth) {
        return panelWidth - 2 * ModulePanel.CONTENT_PADDING;
    }

    private static int panelHeightFor(int rows) {
        return ModulePanel.TITLE_BAR_HEIGHT + RepoPanel.SEARCH_ROW_HEIGHT + rows * RepoPanel.SLOT_SIZE
                + ModulePanel.CONTENT_PADDING;
    }

    private static int contentHeightOf(int panelHeight) {
        return panelHeight - ModulePanel.TITLE_BAR_HEIGHT - ModulePanel.CONTENT_PADDING;
    }

    @Test
    void columnsRoundTripBackToThePanelWidth() {
        for (int columns = RepoPanel.MIN_COLUMNS; columns <= 26; columns++) {
            int panelWidth = panelWidthFor(columns);
            assertEquals(columns, RepoPanel.columnsFor(contentWidthOf(panelWidth), 26),
                    "columns for panel width " + panelWidth);
        }
    }

    @Test
    void rowsRoundTripBackToThePanelHeight() {
        for (int rows = RepoPanel.MIN_ROWS; rows <= 12; rows++) {
            int panelHeight = panelHeightFor(rows);
            assertEquals(rows, RepoPanel.rowsFor(contentHeightOf(panelHeight)),
                    "rows for panel height " + panelHeight);
        }
    }

    @Test
    void columnsAreClampedIntoRange() {
        assertEquals(RepoPanel.MIN_COLUMNS, RepoPanel.columnsFor(0, 26));
        assertEquals(RepoPanel.MIN_COLUMNS, RepoPanel.clampColumns(2, 26));
        assertEquals(RepoPanel.MIN_COLUMNS, RepoPanel.clampColumns(500, 4));
        assertEquals(26, RepoPanel.clampColumns(500, 26));
        assertEquals(9, RepoPanel.clampColumns(500, 9));
    }

    @Test
    void rowsNeverExceedTheContentOrDropBelowTheFloor() {
        assertEquals(RepoPanel.MIN_ROWS, RepoPanel.rowsFor(0));
        assertEquals(RepoPanel.MIN_ROWS, RepoPanel.rowsFor(RepoPanel.SEARCH_ROW_HEIGHT));
        // 内容高度 18(搜索行) + 3*18 = 72 → 恰好 3 行；多出的 1 像素不该算作一行
        assertEquals(3, RepoPanel.rowsFor(RepoPanel.SEARCH_ROW_HEIGHT + 3 * 18 + 1));
    }

    @Test
    void scrollRangeAccountsForThePinnedCraftingRow() {
        assertEquals(0, RepoPanel.maxScrollRows(0, 9, 5, false));
        assertEquals(0, RepoPanel.maxScrollRows(45, 9, 5, false));
        assertEquals(1, RepoPanel.maxScrollRows(46, 9, 5, false));
        // 合成置顶占掉首行 → 可见正常物品只有 4 行
        assertEquals(1, RepoPanel.maxScrollRows(45, 9, 5, true));
        assertEquals(0, RepoPanel.maxScrollRows(36, 9, 5, true));
        // 防御：列数为 0 时不得除零（列数按 1 兜底，于是 10 个物品占 10 行）
        assertEquals(10 - 5, RepoPanel.maxScrollRows(10, 0, 5, false));
    }

    @Test
    void defaultColumnsFeedTheRepoCapacity() {
        // 默认 18 列 × 5 行 = 90 个可见格 → 缓存 270，与旧 Omni 终端的默认页容量一致
        int slotsPerPage = RepoPanel.DEFAULT_COLUMNS * RepoPanel.DEFAULT_ROWS;
        assertEquals(OmniItemRepo.DEFAULT_SLOTS_PER_PAGE, slotsPerPage);
        assertEquals(270, OmniItemRepo.capacityFor(slotsPerPage));
    }

    @Test
    void configuredColumnLimitIsUsable() {
        int max = RepoPanel.maxRepoColumns();
        assertTrue(max >= RepoPanel.MIN_COLUMNS && max <= 26, "unexpected maxRepoColumns: " + max);
        assertEquals(26, AE2EnhancedConfig.Terminal.REPO_MAX_COLUMNS);
        assertEquals(9, AE2EnhancedConfig.Terminal.REPO_MIN_COLUMNS);
    }

    @Test
    void minAndDefaultSizesSatisfyTheirOwnDerivations() {
        // 只验证纯几何部分：内容宽高必须正好推出面板自己声明的列/行
        assertEquals(RepoPanel.MIN_COLUMNS, RepoPanel.columnsFor(contentWidthOf(panelWidthFor(RepoPanel.MIN_COLUMNS)), 26));
        assertEquals(RepoPanel.DEFAULT_COLUMNS,
                RepoPanel.columnsFor(contentWidthOf(panelWidthFor(RepoPanel.DEFAULT_COLUMNS)), 26));
        assertEquals(RepoPanel.MIN_ROWS, RepoPanel.rowsFor(contentHeightOf(panelHeightFor(RepoPanel.MIN_ROWS))));
        assertEquals(RepoPanel.DEFAULT_ROWS, RepoPanel.rowsFor(contentHeightOf(panelHeightFor(RepoPanel.DEFAULT_ROWS))));
    }

    @Test
    void titleControlsReserveCoversAllFiveControls() {
        // 合成状态 tab(25) + 间隔(1) + 4×设置按钮(16) + 3×按钮间隔(1) + 2px 余量 = 95
        assertEquals(95, RepoPanel.titleControlsWidth());
        assertEquals(4, RepoPanel.TITLE_SETTING_COUNT);
        assertEquals(16, RepoPanel.TITLE_CONTROL_SIZE);
        assertEquals(25, RepoPanel.CRAFT_STATUS_WIDTH);
    }

    @Test
    void titleControlsDoNotOverlapEachOther() {
        int craftLeft = RepoPanel.CRAFT_STATUS_WIDTH;
        int firstSetting = craftLeft + RepoPanel.TITLE_CONTROL_GAP;
        // 设置按钮依次排开，互不重叠且都落在控件条内
        for (int i = 0; i < RepoPanel.TITLE_SETTING_COUNT; i++) {
            int left = firstSetting + i * (RepoPanel.TITLE_CONTROL_SIZE + RepoPanel.TITLE_CONTROL_GAP);
            int right = left + RepoPanel.TITLE_CONTROL_SIZE;
            assertTrue(left >= craftLeft, "设置按钮不能压在合成状态 tab 上");
            assertTrue(right <= RepoPanel.titleControlsWidth(),
                    "设置按钮不能越出控件条：index=" + i + " right=" + right);
        }
    }

    @Test
    void scrollbarGeometryStaysInsideTheContentWidth() {
        assertEquals(12, RepoPanel.SCROLLBAR_WIDTH);
        assertEquals(12, new GuiScrollbar().getWidth());
        // 18 列的面板：格子带 324 + 间隔 2 + 滚动条 12 = 338 = 内容宽度
        assertEquals(18 * RepoPanel.SLOT_SIZE, contentWidthOf(panelWidthFor(18)) - RepoPanel.SCROLLBAR_GAP
                - RepoPanel.SCROLLBAR_WIDTH);
    }
}
