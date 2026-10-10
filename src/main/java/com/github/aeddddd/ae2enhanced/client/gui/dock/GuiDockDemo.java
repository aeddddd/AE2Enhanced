package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.AtomicFileDockLayoutStore;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutCodec;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutPersistence;

/**
 * 停靠引擎的开发演示屏（{@code /ae2edockdemo}）：5 个占位面板 + 模块显隐按钮 + 撤销/保存按钮，
 * 用来手工验证拖拽移动、四边吸附拼接、拆分、分隔条与握把缩放、置顶/浮动。
 *
 * <p>布局持久化到 {@code config/ae2enhanced/spliced_terminal/demo_layout.json}，损坏文件沿用
 * {@link AtomicFileDockLayoutStore} 的隔离语义。</p>
 */
@SideOnly(Side.CLIENT)
public class GuiDockDemo extends GuiScreen {
    private static final int BUTTON_TOP = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_GAP = 2;

    private static final int ID_TOGGLE_BASE = 100;
    private static final int ID_UNDO = 200;
    private static final int ID_SAVE = 201;

    private final List<ModulePanel> panels = new ArrayList<ModulePanel>();
    private DockManager manager;

    /** 宿主适配层：MC 单例只出现在这里。 */
    private final class DemoHost implements DockHost {
        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public DockLayoutPersistence createPersistence(DockLayoutCodec codec) {
            File file = new File(Loader.instance().getConfigDir(),
                    "ae2enhanced/spliced_terminal/demo_layout.json");
            return new AtomicFileDockLayoutStore(file.toPath(), codec);
        }

        @Override
        public boolean altDown() {
            return isAltKeyDown();
        }

        @Override
        public boolean controlDown() {
            return isCtrlKeyDown();
        }
    }

    @Override
    public void initGui() {
        if (manager == null) {
            panels.addAll(DummyPanel.createDefaultPanels());
            manager = new DockManager(new DemoHost());
            manager.init(panels);
        } else {
            manager.updateViewport(width, height);
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttonList.clear();
        int x = 6;
        int y = BUTTON_TOP;
        for (int index = 0; index < panels.size(); index++) {
            ModulePanel panel = panels.get(index);
            boolean visible = manager.policyFor(panel).visible();
            String label = (visible ? "\u00a7a" : "\u00a78") + panel.title();
            buttonList.add(new GuiButton(ID_TOGGLE_BASE + index, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, label));
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
        GuiButton undo = new GuiButton(ID_UNDO, 6 + BUTTON_WIDTH + BUTTON_GAP, BUTTON_TOP, 80, BUTTON_HEIGHT,
                I18n.format("gui.ae2enhanced.dock.demo.undo"));
        undo.enabled = manager.canUndoLayout();
        buttonList.add(undo);
        buttonList.add(new GuiButton(ID_SAVE, 6 + BUTTON_WIDTH + BUTTON_GAP, BUTTON_TOP + BUTTON_HEIGHT + BUTTON_GAP,
                80, BUTTON_HEIGHT, I18n.format("gui.ae2enhanced.dock.demo.save")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_UNDO) {
            manager.undoLayout();
            rebuildButtons();
            return;
        }
        if (button.id == ID_SAVE) {
            manager.save();
            return;
        }
        int index = button.id - ID_TOGGLE_BASE;
        if (index >= 0 && index < panels.size()) {
            ModulePanel panel = panels.get(index);
            if (manager.policyFor(panel).visible()) {
                manager.hideModule(panel);
                manager.save();
            } else {
                manager.revealModule(panel);
                manager.save();
            }
            rebuildButtons();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        manager.render(mouseX, mouseY, partialTicks, 0.0F, null);
        manager.renderForeground(mouseX, mouseY, partialTicks, 0.0F);
        // 分隔条只在 hover 时提示：MEST 把区段规则线留给布局编辑器，本演示没有编辑器，
        // 于是改成"光标压到分隔条才画一条高亮线"，让该功能可被发现。
        DockManager.DividerHit divider = manager.dividerAt(mouseX, mouseY);
        if (divider != null) {
            NinePatch.drawSectionRule(divider.bounds(), divider.axis(), true, 1.0F);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        String hint = I18n.format("gui.ae2enhanced.dock.demo.hint");
        fontRenderer.drawString(hint, 6, height - 24, 0xFFE0E0E0, true);
        fontRenderer.drawString("dock: mode=" + manager.activeModeName()
                + " panels=" + panels.size()
                + " undo=" + (manager.canUndoLayout() ? "yes" : "no"), 6, height - 12, 0xFF9FE8A0, true);
        String tooltip = manager.titleBarTooltipAt(mouseX, mouseY);
        if (tooltip != null) {
            drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // 两段式：先把光标下的窗口抬到最上层（待提交），再把它交给 dock 手势或原生控件。
        manager.beginPanelInteraction(mouseX, mouseY);
        boolean consumed = manager.mouseClicked(mouseX, mouseY, mouseButton);
        if (!consumed) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
        manager.commitPanelInteraction();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!manager.mouseDragged(mouseX, mouseY)) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        manager.mouseReleased(mouseX, mouseY, state);
        super.mouseReleased(mouseX, mouseY, state);
        rebuildButtons();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * width / this.mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / this.mc.displayHeight - 1;
            manager.mouseScrolled(mouseX, mouseY, wheel / 120.0D);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (manager.keyPressed(keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_Z && isCtrlKeyDown() && manager.canUndoLayout()) {
            manager.undoLayout();
            rebuildButtons();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        manager.finishPendingGesture();
        manager.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** 演示屏当前的面板列表（调试/测试用）。 */
    public List<ModulePanel> demoPanels() {
        return panels;
    }

    /** 演示屏的 dock 运行时（调试用）。 */
    public DockManager manager() {
        return manager;
    }
}
