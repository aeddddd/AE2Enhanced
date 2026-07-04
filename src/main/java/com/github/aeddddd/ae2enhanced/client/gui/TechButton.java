package com.github.aeddddd.ae2enhanced.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 科技风格按钮。
 */
public class TechButton extends Button {

    public TechButton(int x, int y, int width, int height, Component text, OnPress onPress) {
        super(x, y, width, height, text, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        boolean hovered = mouseX >= this.getX() && mouseY >= this.getY()
                && mouseX < this.getX() + this.getWidth() && mouseY < this.getY() + this.getHeight();

        int bgColor = this.active ? 0xFF0d1b2a : 0xFF1a1a2e;
        if (hovered && this.active) {
            bgColor = 0xFF1b3a5a;
        }
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), bgColor);

        int borderColor = this.active ? 0xFF00d4ff : 0xFF555555;
        if (hovered && this.active) {
            borderColor = 0xFF66e5ff;
        }
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + 1, borderColor);
        graphics.fill(this.getX(), this.getY() + this.getHeight() - 1, this.getX() + this.getWidth(), this.getY() + this.getHeight(), borderColor);
        graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.getHeight(), borderColor);
        graphics.fill(this.getX() + this.getWidth() - 1, this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), borderColor);

        int textColor = this.active ? 0xFFe0e0e0 : 0xFF888888;
        if (hovered && this.active) {
            textColor = 0xFFffffff;
        }
        int textWidth = this.getWidth() - 6;
        graphics.drawString(Minecraft.getInstance().font, this.getMessage(), this.getX() + (this.getWidth() - textWidth) / 2, this.getY() + (this.getHeight() - 8) / 2, textColor);
    }
}
