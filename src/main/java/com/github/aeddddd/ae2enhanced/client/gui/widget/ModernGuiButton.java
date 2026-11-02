package com.github.aeddddd.ae2enhanced.client.gui.widget;

import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class ModernGuiButton extends GuiButton {
    private boolean selected;
    private Ae2ModernArt.Icon icon;

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

    public ModernGuiButton setIcon(Ae2ModernArt.Icon icon) {
        this.icon = icon;
        return this;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        Ae2ModernArt.drawButton(this.x, this.y, this.width, this.height,
                (this.hovered || this.selected) && this.enabled, this.enabled, 0.0F);
        if (this.icon != null) {
            Ae2ModernArt.drawIcon(this.icon, this.x + (this.width - 16) / 2,
                    this.y + 2 + (this.hovered ? 1 : 0), 0.0F);
            return;
        }
        String text = mc.fontRenderer.trimStringToWidth(this.displayString, Math.max(0, this.width - 4));
        int textWidth = mc.fontRenderer.getStringWidth(text);
        int baseline = this.y + (this.height - mc.fontRenderer.FONT_HEIGHT) / 2 + 1;
        if (this.enabled && !this.hovered && !this.selected) {
            baseline--;
        }
        mc.fontRenderer.drawString(text, this.x + (this.width - textWidth) / 2, baseline,
                Ae2Theme.buttonTextColor(this.enabled, this.hovered || this.selected), false);
    }
}
