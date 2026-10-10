package com.github.aeddddd.ae2enhanced.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ChromeLayout;

/**
 * AE2 19.x 风格的普通文字按钮（{@code button.png} 九宫格底 + 居中文字）。
 *
 * <p>用于布局编辑器工具栏与预设页签——替换掉 vanilla {@code GuiButton} 的原生灰底，
 * 让编辑器与终端同一套观感。标签、命中、{@code actionPerformed} 行为与 vanilla 完全一致。</p>
 *
 * <p>页签用 {@link #setSelected(boolean)} 表达选中态：选中时在按钮面上补一圈高光边并把文字提亮。</p>
 */
public class ModernGuiButton extends GuiButton {

    private boolean selected;

    public ModernGuiButton(int id, int x, int y, int width, int height, String label) {
        super(id, x, y, width, height, label);
    }

    public ModernGuiButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        Ae2ModernArt.drawButton(this.x, this.y, this.width, this.height, this.hovered && this.enabled,
                this.enabled, 0.0F);
        if (this.selected) {
            boolean pushed = com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.pushZLevel(1.0F);
            int inset = 2;
            net.minecraft.client.gui.Gui.drawRect(this.x + inset, this.y + inset,
                    this.x + this.width - inset, this.y + inset + 1, ChromeLayout.COLOR_LIGHT);
            net.minecraft.client.gui.Gui.drawRect(this.x + inset, this.y + this.height - inset - 1,
                    this.x + this.width - inset, this.y + this.height - inset, ChromeLayout.COLOR_LIGHT);
            com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.popZLevel(pushed);
        }
        int textColor = !this.enabled ? ChromeLayout.COLOR_MUTED
                : (this.selected ? ChromeLayout.COLOR_TITLE_TEXT : ChromeLayout.COLOR_DARK);
        drawCenteredString(mc.fontRenderer, this.displayString, this.x + this.width / 2,
                this.y + (this.height - 8) / 2, textColor);
    }
}
