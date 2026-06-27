package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiButtonTech extends GuiButton {

    public GuiButtonTech(int buttonId, int x, int y, int width, int height, String text) {
        super(buttonId, x, y, width, height, text);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

        // 背景色：默认深蓝,悬停变亮,禁用变暗
        int bgColor = this.enabled ? 0xFF0d1b2a : 0xFF1a1a2e;
        if (this.hovered && this.enabled) {
            bgColor = 0xFF1b3a5a;
        }
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

        // 边框：启用时青色,禁用时灰色
        int borderColor = this.enabled ? 0xFF00d4ff : 0xFF555555;
        if (this.hovered && this.enabled) {
            borderColor = 0xFF66e5ff;
        }
        // 上边框
        drawRect(this.x, this.y, this.x + this.width, this.y + 1, borderColor);
        // 下边框
        drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, borderColor);
        // 左边框
        drawRect(this.x, this.y, this.x + 1, this.y + this.height, borderColor);
        // 右边框
        drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, borderColor);

        // 文字
        int textColor = this.enabled ? 0xFFe0e0e0 : 0xFF888888;
        if (this.hovered && this.enabled) {
            textColor = 0xFFffffff;
        }
        mc.fontRenderer.drawString(this.displayString,
            this.x + (this.width - mc.fontRenderer.getStringWidth(this.displayString)) / 2,
            this.y + (this.height - 8) / 2,
            textColor);
    }
}
