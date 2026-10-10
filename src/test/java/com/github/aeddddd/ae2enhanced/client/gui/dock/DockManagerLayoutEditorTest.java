package com.github.aeddddd.ae2enhanced.client.gui.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutTrees;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutCodec;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutPersistence;

/**
 * 阶段 4b：布局编辑器的状态机（begin/cancel/commit）、预设银行、恢复默认、导入导出、
 * 调色板拖入与编辑画布内边距。
 *
 * <p>全屏编辑器本身只做渲染与输入转发，真正的语义都在 {@code DockManager} 里，因此这里能覆盖
 * 编辑器的绝大部分行为而不需要 MC。</p>
 */
class DockManagerLayoutEditorTest {

    private static Path tempDir;

    @BeforeAll
    static void boot() throws IOException {
        tempDir = Files.createTempDirectory("ae2e-layout-editor");
    }

    @AfterAll
    static void cleanup() {
        try {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    /** 最小可用的面板桩：只提供 id/尺寸与空闲的手势响应。 */
    private static final class TestPanel extends ModulePanel {
        private final String moduleId;
        private final int width;
        private final int height;

        private TestPanel(String moduleId, int width, int height) {
            this.moduleId = moduleId;
            this.width = width;
            this.height = height;
        }

        @Override
        public String id() {
            return moduleId;
        }

        @Override
        public String title() {
            return moduleId;
        }

        @Override
        public int minWidth() {
            return Math.max(40, width - 40);
        }

        @Override
        public int minHeight() {
            return Math.max(30, height - 20);
        }

        @Override
        public int defaultWidth() {
            return width;
        }

        @Override
        public int defaultHeight() {
            return height;
        }

        @Override
        public void layoutSlots() {
        }
    }

    private static final class TestHost implements DockHost {
        private final Path configDir;

        private TestHost(Path configDir) {
            this.configDir = configDir;
        }

        @Override
        public int screenWidth() {
            return 640;
        }

        @Override
        public int screenHeight() {
            return 480;
        }

        @Override
        public DockLayoutPersistence createPersistence(DockLayoutCodec codec) {
            // 编辑器语义与落盘无关：本测试只验证内存状态机
            return null;
        }

        @Override
        public Path configDir() {
            return configDir;
        }
    }

    private static List<ModulePanel> newPanels() {
        return new ArrayList<ModulePanel>(Arrays.<ModulePanel>asList(
                new TestPanel("alpha", 160, 110),
                new TestPanel("beta", 180, 140),
                new TestPanel("gamma", 150, 120)));
    }

    private static DockManager plainManager() {
        List<ModulePanel> panels = newPanels();
        DockManager manager = new DockManager(new TestHost(null));
        manager.init(panels);
        return manager;
    }

    private static DockManager managerWithConfig() {
        List<ModulePanel> panels = newPanels();
        DockManager manager = new DockManager(new TestHost(tempDir.resolve("cfg-" + System.nanoTime())));
        manager.init(panels);
        return manager;
    }

    private static DockRect boundsOf(DockWorkspace workspace, String moduleId) {
        for (FloatingRoot root : workspace.roots()) {
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                if (leaf.moduleId().equals(moduleId)) {
                    return root.bounds();
                }
            }
        }
        return null;
    }

    private static void assertInsets(int[] expected, int[] actual) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    }

    /** 带说明的版本：断言内边距并给出可读差异。 */
    private static void assertInsets(int[] expected, int[] actual, String message) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual), message);
    }

    // ------------------------------------------------------------------
    // begin / cancel / commit
    // ------------------------------------------------------------------

    @Test
    void cancelRestoresTheWorkspaceAndDropsEditorUndoPoints() {
        DockManager manager = plainManager();
        DockWorkspace before = manager.workspaceSnapshot();
        boolean undoBefore = manager.canUndoLayout();

        manager.beginLayoutEditing();
        assertTrue(manager.isEditingLayout());
        manager.setModuleVisible("gamma", false);
        assertFalse(manager.isModuleVisible("gamma"));

        manager.cancelLayoutEditing();

        assertFalse(manager.isEditingLayout());
        assertEquals(before, manager.workspaceSnapshot(), "取消后工作区回到进入编辑器的状态");
        assertTrue(manager.isModuleVisible("gamma"));
        assertEquals(undoBefore, manager.canUndoLayout(), "编辑期间产生的撤销点被丢弃");
        assertInsets(new int[] { 0, 0, 0, 0 }, manager.editorCanvasInsets());
    }

    @Test
    void commitKeepsTheEditsAndClearsTheCanvasInsets() {
        DockManager manager = plainManager();
        manager.beginLayoutEditing();
        manager.setModuleVisible("gamma", false);
        manager.commitLayoutEditing();

        assertFalse(manager.isEditingLayout());
        assertFalse(manager.isModuleVisible("gamma"), "提交后编辑生效");
        assertInsets(new int[] { 0, 0, 0, 0 }, manager.editorCanvasInsets());
    }

    @Test
    void commitCentersTheVisibleWindowsWhenRequested() {
        DockManager manager = plainManager();
        // 先把 beta 拖到视口右下角（受视口钳制，必然远离中心）
        assertTrue(manager.mouseClicked(24 + 30, 24 + 8, 0));
        assertTrue(manager.mouseDragged(600, 470));
        assertTrue(manager.mouseReleased(600, 470, 0));
        DockRect dragged = boundsOf(manager.workspaceSnapshot(), "beta");
        assertTrue(dragged.x() > 200 && dragged.y() > 200, "前置状态：beta 已在右下: " + dragged);

        manager.beginLayoutEditing();
        manager.setCenterOnReturn(true);
        manager.commitLayoutEditing();

        DockRect centered = boundsOf(manager.workspaceSnapshot(), "beta");
        assertTrue(centered.x() < dragged.x() || centered.y() < dragged.y(),
                "提交后会把可见窗口拉回视口中心附近");
        manager.setCenterOnReturn(true);
    }

    @Test
    void commitSkipsCenteringWhenThePreferenceIsOff() {
        DockManager manager = plainManager();
        assertTrue(manager.mouseClicked(24 + 30, 24 + 8, 0));
        manager.mouseDragged(600, 470);
        manager.mouseReleased(600, 470, 0);
        DockRect dragged = boundsOf(manager.workspaceSnapshot(), "beta");

        manager.beginLayoutEditing();
        manager.setCenterOnReturn(false);
        try {
            manager.commitLayoutEditing();
            assertEquals(dragged, boundsOf(manager.workspaceSnapshot(), "beta"), "关掉居中就不动窗口");
        } finally {
            manager.setCenterOnReturn(true);
        }
    }

    @Test
    void editorInsetsClampDraggedWindowsIntoTheCanvas() {
        DockManager manager = plainManager();
        manager.beginLayoutEditing();
        manager.setEditorCanvasInsets(100, 40, 60, 30);
        assertInsets(new int[] { 100, 40, 60, 30 }, manager.editorCanvasInsets());

        // 内边距生效后所有根都被钳到画布内；这里抓最上层的那个窗口来拖
        ModulePanel top = manager.topLeafAt(120, 48);
        assertNotNull(top, "画布左上角应有窗口");
        assertTrue(manager.mouseClicked(120, 48, 0), "标题栏按下进入拖动");
        assertTrue(manager.mouseDragged(-500, -500));
        assertTrue(manager.mouseReleased(-500, -500, 0));

        DockRect dragged = boundsOf(manager.workspaceSnapshot(), top.id());
        assertEquals(100, dragged.x(), "窗口不会被拖到侧栏底下");
        assertEquals(40, dragged.y(), "窗口不会被拖到工具栏底下");

        manager.clearEditorCanvasInsets();
        assertInsets(new int[] { 0, 0, 0, 0 }, manager.editorCanvasInsets());
        manager.cancelLayoutEditing();
    }

    @Test
    void insetsOnlyApplyWhileEditing() {
        DockManager manager = plainManager();
        manager.setEditorCanvasInsets(100, 40, 60, 30);
        assertInsets(new int[] { 0, 0, 0, 0 }, manager.editorCanvasInsets(), "非编辑态设置内边距无效");
    }

    // ------------------------------------------------------------------
    // 预设银行
    // ------------------------------------------------------------------

    @Test
    void presetSwitchAndCancelRestoreTheActiveSlot() {
        DockManager manager = managerWithConfig();
        assertTrue(manager.hasPresets());
        assertEquals(3, manager.presetCount());
        assertEquals(0, manager.activePreset());

        manager.beginLayoutEditing();
        manager.selectPreset(1);
        assertEquals(1, manager.activePreset());
        manager.setModuleVisible("gamma", false);

        manager.cancelLayoutEditing();

        assertEquals(0, manager.activePreset(), "取消回到进入时的活动槽");
        assertTrue(manager.isModuleVisible("gamma"));
    }

    @Test
    void presetNamesAreSanitizedAndFallBackToTheSlotNumber() {
        DockManager manager = managerWithConfig();
        assertEquals("1", manager.presetName(0));

        manager.setPresetName(0, "  my   layout  ");
        assertEquals("my   layout", manager.presetName(0));

        manager.setPresetName(0, "   ");
        assertEquals("1", manager.presetName(0), "空名字回落到槽号");
    }

    @Test
    void cyclePresetWrapsAround() {
        DockManager manager = managerWithConfig();
        manager.cyclePreset();
        assertEquals(1, manager.activePreset());
        manager.cyclePreset();
        manager.cyclePreset();
        assertEquals(0, manager.activePreset(), "第三个槽再切一次回到 0");
    }

    @Test
    void presetsAreUnavailableWithoutAConfigDirectory() {
        DockManager manager = plainManager();
        assertFalse(manager.hasPresets(), "无配置目录 → 预设功能整体降级");
        // 降级路径下的调用必须安全（编辑器会照常调用它们）
        manager.selectPreset(2);
        assertEquals(0, manager.activePreset());
        manager.setPresetName(0, "nope");
        assertEquals("1", manager.presetName(0));
    }

    // ------------------------------------------------------------------
    // 恢复默认 / 导入导出
    // ------------------------------------------------------------------

    @Test
    void editModeUndoRollsBackTheResetInsideTheEditor() {
        DockManager manager = plainManager();
        // 制造一个"非默认"的编辑前状态，确保 RESET 真的会改变布局
        manager.setModuleVisible("gamma", false);
        DockWorkspace beforeReset = manager.workspaceSnapshot();

        manager.beginLayoutEditing();
        manager.resetLayout();
        assertTrue(manager.isModuleVisible("gamma"), "编辑器内 RESET 应把隐藏模块拉回（布局已改变）");
        assertTrue(manager.canUndoLayout(), "RESET 必须留下撤销点，否则编辑器里的撤销是空操作");

        manager.undoLayout();
        assertEquals(beforeReset, manager.workspaceSnapshot(), "编辑态 undo 应回滚到 RESET 之前的状态");
    }

    /**
     * 实机 A22 序列的复刻：进入编辑器 → RESET → 撤销 → 再 RESET → CANCEL。
     * 有预设银行（configDir 非空）时走的是"整包预设还原"分支，与无预设分支是两条不同的代码路径。
     */
    @Test
    void cancelRollsBackEditsEvenWithAPresetBank() {
        DockManager manager = managerWithConfig();
        manager.setModuleVisible("gamma", false);
        DockWorkspace beforeEdit = manager.workspaceSnapshot();
        String beforeJson = manager.exportLayoutJson();

        manager.beginLayoutEditing();
        manager.resetLayout();
        assertFalse(beforeEdit.equals(manager.workspaceSnapshot()), "前置：RESET 必须改变布局");
        manager.undoLayout();
        assertEquals(beforeEdit, manager.workspaceSnapshot(), "前置：编辑态撤销回到 RESET 之前");
        manager.resetLayout();
        manager.cancelLayoutEditing();

        assertEquals(beforeEdit, manager.workspaceSnapshot(),
                "CANCEL 后工作区回到进入编辑器之前");
        assertEquals(beforeJson, manager.exportLayoutJson(),
                "CANCEL 后导出的布局文档（含预设名）与进入编辑器之前逐字节一致");
    }

    @Test
    void resetLayoutReturnsToTheHostDefault() {
        DockManager manager = plainManager();
        DockWorkspace defaults = manager.workspaceSnapshot();
        manager.setModuleVisible("gamma", false);
        manager.resetLayout();

        assertTrue(manager.isModuleVisible("gamma"), "恢复默认会把隐藏的模块拉回来");
        assertEquals(boundsOf(defaults, "alpha"), boundsOf(manager.workspaceSnapshot(), "alpha"));
    }

    @Test
    void exportImportRoundTripAndInvalidImportIsRejected() {
        DockManager manager = managerWithConfig();
        manager.setModuleVisible("gamma", false);
        String json = manager.exportLayoutJson();
        assertNotNull(json);
        assertTrue(json.contains("roots"), "导出的是布局文档");

        manager.setModuleVisible("gamma", true);
        assertTrue(manager.importLayoutJson(json).ok(), "自己导出的文档必须能导回来");
        assertFalse(manager.isModuleVisible("gamma"));

        DockWorkspace beforeInvalid = manager.workspaceSnapshot();
        DockManager.LayoutShareResult bad = manager.importLayoutJson("{ not a layout");
        assertFalse(bad.ok());
        assertEquals("gui.ae2enhanced.spliced_editor.import.invalid", bad.langKey());
        assertEquals(beforeInvalid, manager.workspaceSnapshot(), "非法导入不改变任何状态");

        assertEquals("gui.ae2enhanced.spliced_editor.import.empty",
                manager.importLayoutJson("   ").langKey());
        assertEquals("gui.ae2enhanced.spliced_editor.import.empty",
                manager.importLayoutJson(null).langKey());
    }

    @Test
    void exportToShareFolderWritesEverySlot() {
        DockManager manager = managerWithConfig();
        DockManager.LayoutShareResult result = manager.exportToShareFolder();
        assertTrue(result.ok(), "分享导出应当成功");
        assertNotNull(result.path());
        assertTrue(Files.isRegularFile(result.path().resolve("1").resolve("layout.json")));
        assertTrue(Files.isRegularFile(result.path().resolve("2").resolve("layout.json")));
        assertTrue(Files.isRegularFile(result.path().resolve("3").resolve("layout.json")));
    }

    @Test
    void exportWithoutConfigDirectoryFailsGracefully() {
        DockManager manager = plainManager();
        assertFalse(manager.exportToShareFolder().ok());
        assertEquals("gui.ae2enhanced.spliced_editor.export.fail", manager.exportToShareFolder().langKey());
    }

    // ------------------------------------------------------------------
    // 调色板拖入
    // ------------------------------------------------------------------

    @Test
    void externalDragRevealsAHiddenModuleAndMovesIt() {
        DockManager manager = plainManager();
        manager.beginLayoutEditing();
        manager.setModuleVisible("gamma", false);

        ModulePanel gamma = manager.panels().get(2);
        assertTrue(manager.startExternalDrag(gamma, 20, 20), "隐藏模块也能被拖进来");
        assertTrue(manager.isModuleVisible("gamma"), "拖入时先显形");
        DockRect before = boundsOf(manager.workspaceSnapshot(), "gamma");

        assertTrue(manager.mouseDragged(400, 300), "拖拽被 dock 接管");
        assertTrue(manager.mouseReleased(400, 300, 0), "松手后落位");

        DockRect after = boundsOf(manager.workspaceSnapshot(), "gamma");
        assertNotNull(after);
        assertFalse(after.equals(before), "窗口位置已改变");
        manager.cancelLayoutEditing();
    }

    @Test
    void externalDragIsRefusedWhileAGestureIsRunning() {
        DockManager manager = plainManager();
        manager.beginLayoutEditing();
        ModulePanel alpha = manager.panels().get(0);
        ModulePanel beta = manager.panels().get(1);

        assertTrue(manager.startExternalDrag(alpha, 60, 20));
        assertFalse(manager.startExternalDrag(beta, 60, 20), "已有手势在跑时拒绝第二次调色板拖入");
        manager.mouseReleased(60, 20, 0);
        manager.cancelLayoutEditing();
    }
}
