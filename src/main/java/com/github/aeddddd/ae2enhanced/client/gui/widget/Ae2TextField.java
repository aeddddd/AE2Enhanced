package com.github.aeddddd.ae2enhanced.client.gui.widget;

import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2Theme;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

public final class Ae2TextField extends GuiTextField {
    private boolean enabled = true;

    public Ae2TextField(int id, FontRenderer font, int x, int y, int width) {
        super(id, font, x, y, width, Ae2Theme.FIELD_HEIGHT);
        setEnableBackgroundDrawing(false);
        setTextColor(Ae2Theme.TEXT);
    }

    public boolean isMouseIn(int mouseX, int mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }

    public void selectAll() {
        setCursorPositionEnd();
        setSelectionPos(0);
    }

    public void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = Ae2Theme.FIELD_HEIGHT;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.enabled = enabled;
    }

    @Override
    public void drawTextBox() {
        Ae2ModernArt.drawTextField(this.x, this.y, this.width, this.height,
                this.isFocused(), this.enabled, 0.0F);
        super.drawTextBox();
    }
}
