package com.github.aeddddd.ae2enhanced.client.gui.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;

/**
 * 阶段 3a：新增模块面板的网格几何（纯静态推导，可在无头环境验证）。
 *
 * <p>锁住的是一类真实 bug：网格尺寸与面板尺寸推导不一致 → 槽位被摆到面板外面去。
 * 每个模块的"内容尺寸 → 面板尺寸"都必须能原样推回来。</p>
 */
class ModuleGeometryTest {

    @Test
    void craftingGridPlusOutputFitsItsPanel() {
        // 3×3 矩阵 + 12px 间隔 + 输出列
        assertEquals(3 * 18 + 12 + 18, CraftingPanel.contentWidthForGrid());
        assertEquals(3 * 18, CraftingPanel.contentHeightForGrid());
        assertEquals(3 * 18 + 12, CraftingPanel.outputCellOffsetX());
        // 输出槽与矩阵中间一行对齐 → 偏移必须落在网格内部
        assertTrue(CraftingPanel.outputCellOffsetX() + 18 <= CraftingPanel.contentWidthForGrid());

        assertEquals(CraftingPanel.contentWidthForGrid() + 2 * ModulePanel.CONTENT_PADDING,
                CraftingPanel.panelWidthForGrid());
        assertEquals(ModulePanel.TITLE_BAR_HEIGHT + CraftingPanel.contentHeightForGrid()
                + ModulePanel.CONTENT_PADDING, CraftingPanel.panelHeightForGrid());
    }

    @Test
    void storageGridIsNineByThree() {
        assertEquals(9 * 18, StoragePanel.contentWidthForGrid());
        assertEquals(3 * 18, StoragePanel.contentHeightForGrid());
        assertEquals(StoragePanel.contentWidthForGrid() + 2 * ModulePanel.CONTENT_PADDING,
                StoragePanel.panelWidthForGrid());
        assertEquals(ModulePanel.TITLE_BAR_HEIGHT + StoragePanel.contentHeightForGrid()
                + ModulePanel.CONTENT_PADDING, StoragePanel.panelHeightForGrid());
    }

    @Test
    void upgradeGridIsASingleRow() {
        assertEquals(9 * 18, UpgradePanel.contentWidthForGrid());
        assertEquals(18, UpgradePanel.contentHeightForGrid());
        assertEquals(UpgradePanel.contentWidthForGrid() + 2 * ModulePanel.CONTENT_PADDING,
                UpgradePanel.panelWidthForGrid());
        assertEquals(ModulePanel.TITLE_BAR_HEIGHT + UpgradePanel.contentHeightForGrid()
                + ModulePanel.CONTENT_PADDING, UpgradePanel.panelHeightForGrid());
    }

    @Test
    void allGridPanelsRespectTheSlotHostContract() {
        // 三个新面板都必须经 SlotHostPanel 承接槽位（可见性 → slotPolicy 登记）
        assertTrue(SlotHostPanel.class.isAssignableFrom(CraftingPanel.class));
        assertTrue(SlotHostPanel.class.isAssignableFrom(StoragePanel.class));
        assertTrue(SlotHostPanel.class.isAssignableFrom(UpgradePanel.class));
        assertTrue(SlotHostPanel.class.isAssignableFrom(PlayerInvPanel.class));
        assertTrue(SlotHostPanel.class.isAssignableFrom(ViewCellPanel.class));
        // repo 的槽位是运行时增删的 ME 槽，不走这个基类
        assertTrue(ModulePanel.class.isAssignableFrom(RepoPanel.class));
    }
}
