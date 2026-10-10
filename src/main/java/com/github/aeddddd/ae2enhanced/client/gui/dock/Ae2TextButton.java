package com.github.aeddddd.ae2enhanced.client.gui.dock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * AE2 风格的文字按钮（浅色凸起底 + 1px 深色描边 + 居中文字）。
 *
 * <p>只替换绘制，不改任何触发逻辑：{@code id} / {@code enabled} / 命中区与 {@link GuiButton} 完全一致，
 * 因此布局编辑器的按钮行为（撤销/预设/完成/取消…）保持不变。</p>
 */
public class Ae2TextButton extends GuiButton {

    private static final int COLOR_TEXT = 0xFF413F54;
    private static final int COLOR_TEXT_DISABLED = 0xFF9AA0AE;

    public Ae2TextButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        NinePatch.drawButtonBase(this.x, this.y, this.width, this.height,
                this.hovered && this.enabled, 0.0F);
        this.mouseDragged(mc, mouseX, mouseY);
        String label = this.displayString == null ? "" : this.displayString;
        int color = this.enabled ? COLOR_TEXT : COLOR_TEXT_DISABLED;
        mc.fontRenderer.drawString(label,
                this.x + (this.width - mc.fontRenderer.getStringWidth(label)) / 2,
                this.y + (this.height - 8) / 2, color, false);
    }
}
