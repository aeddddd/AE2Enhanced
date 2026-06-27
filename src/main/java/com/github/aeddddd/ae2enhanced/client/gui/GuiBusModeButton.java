package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.client.gui.widgets.ITooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/**
 * E1a：通用总线模式切换按钮.
 * 使用 AE2 states.png 纹理风格,点击循环切换.
 */
public class GuiBusModeButton extends GuiButton implements ITooltip {

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2", "textures/guis/states.png");

    private static final String[] ABBREVS = { "SEQ", "RR", "RND" };
    private static final String[] NAME_KEYS = {
            "gui.ae2enhanced.bus_mode.sequential",
            "gui.ae2enhanced.bus_mode.round_robin",
            "gui.ae2enhanced.bus_mode.random"
    };

    private int modeIndex = 0;

    public GuiBusModeButton(int x, int y) {
        super(0, x, y, 16, 16, "");
    }

    public void setMode(int index) {
        if (index >= 0 && index < ABBREVS.length) {
            this.modeIndex = index;
        }
    }

    public void cycleMode() {
        this.modeIndex = (this.modeIndex + 1) % ABBREVS.length;
    }

    public int getModeIndex() {
        return this.modeIndex;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        int state = this.getHoverState(this.hovered);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(STATES_TEXTURE);
        // 绘制 AE2 风格的灰色框背景
        this.drawTexturedModalRect(this.x, this.y, 240, 240, 16, 16);

        String text = ABBREVS[this.modeIndex];
        int textColor = (state == 2) ? 0xFFFFA0 : 0xFFFFFF;
        mc.fontRenderer.drawStringWithShadow(text,
                this.x + (this.width - mc.fontRenderer.getStringWidth(text)) / 2,
                this.y + 4, textColor);
    }

    @Override
    public String getMessage() {
        return I18n.format(NAME_KEYS[this.modeIndex]);
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }
}
