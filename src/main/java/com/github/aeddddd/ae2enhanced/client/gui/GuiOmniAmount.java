package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSetSlotAmount;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Omni Terminal 数量输入子 GUI —— 风格完全复用 AE2 GuiCraftAmount
 *
 * 中键点击 ghost slot 后弹出,输入数量后发送 PacketSetSlotAmount 并返回父 GUI.
 */
public class GuiOmniAmount extends GuiScreen {

    private static final ResourceLocation BG = new ResourceLocation("appliedenergistics2", "textures/guis/craft_amt.png");

    private final GuiScreen parent;
    private final int invType;
    private final int slotIndex;
    private final int initialAmount;

    private GuiTextField amountField;
    private GuiButton confirmBtn;
    private GuiButton cancelBtn;
    private GuiButton plus1, plus10, plus64, plus1000;
    private GuiButton minus1, minus10, minus64, minus1000;

    public GuiOmniAmount(GuiScreen parent, int invType, int slotIndex, int initialAmount) {
        this.parent = parent;
        this.invType = invType;
        this.slotIndex = slotIndex;
        this.initialAmount = initialAmount;
    }

    @Override
    public void initGui() {
        super.initGui();
        int cx = (this.width - 176) / 2;
        int cy = (this.height - 107) / 2;

        this.plus1 = new GuiButton(0, cx + 20, cy + 26, 22, 20, "+1");
        this.buttonList.add(this.plus1);
        this.plus10 = new GuiButton(1, cx + 48, cy + 26, 28, 20, "+10");
        this.buttonList.add(this.plus10);
        this.plus64 = new GuiButton(2, cx + 82, cy + 26, 32, 20, "+64");
        this.buttonList.add(this.plus64);
        this.plus1000 = new GuiButton(3, cx + 120, cy + 26, 38, 20, "+1k");
        this.buttonList.add(this.plus1000);

        this.minus1 = new GuiButton(4, cx + 20, cy + 75, 22, 20, "-1");
        this.buttonList.add(this.minus1);
        this.minus10 = new GuiButton(5, cx + 48, cy + 75, 28, 20, "-10");
        this.buttonList.add(this.minus10);
        this.minus64 = new GuiButton(6, cx + 82, cy + 75, 32, 20, "-64");
        this.buttonList.add(this.minus64);
        this.minus1000 = new GuiButton(7, cx + 120, cy + 75, 38, 20, "-1k");
        this.buttonList.add(this.minus1000);

        this.confirmBtn = new GuiButton(8, cx + 128, cy + 51, 38, 20, "确认");
        this.buttonList.add(this.confirmBtn);

        this.cancelBtn = new GuiButton(9, cx + 10, cy + 51, 46, 20, "取消");
        this.buttonList.add(this.cancelBtn);

        this.amountField = new GuiTextField(0, this.fontRenderer, cx + 62, cy + 57, 59, this.fontRenderer.FONT_HEIGHT);
        this.amountField.setEnableBackgroundDrawing(false);
        this.amountField.setMaxStringLength(9);
        this.amountField.setTextColor(0xFFFFFF);
        this.amountField.setFocused(true);
        this.amountField.setText(String.valueOf(this.initialAmount));

        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int cx = (this.width - 176) / 2;
        int cy = (this.height - 107) / 2;

        // 绘制 AE2 craft_amt.png 背景
        this.mc.getTextureManager().bindTexture(BG);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawTexturedModalRect(cx, cy, 0, 0, 176, 107);

        // 绘制文本框
        this.amountField.drawTextBox();

        // 绘制标题
        this.fontRenderer.drawString("设置数量", cx + 8, cy + 6, 0x404040);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == this.confirmBtn) {
            this.confirm();
        } else if (button == this.cancelBtn) {
            this.mc.displayGuiScreen(this.parent);
        } else if (button == this.plus1) {
            this.addQty(1);
        } else if (button == this.plus10) {
            this.addQty(10);
        } else if (button == this.plus64) {
            this.addQty(64);
        } else if (button == this.plus1000) {
            this.addQty(1000);
        } else if (button == this.minus1) {
            this.addQty(-1);
        } else if (button == this.minus10) {
            this.addQty(-10);
        } else if (button == this.minus64) {
            this.addQty(-64);
        } else if (button == this.minus1000) {
            this.addQty(-1000);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            this.confirm();
            return;
        }
        if (this.amountField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void addQty(int delta) {
        String text = this.amountField.getText();
        int current;
        try {
            current = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            current = 1;
        }
        long result = (long) current + delta;
        if (result < 1) result = 1;
        if (result > 999999999) result = 999999999;
        this.amountField.setText(String.valueOf((int) result));
    }

    private void confirm() {
        int amount;
        try {
            amount = Integer.parseInt(this.amountField.getText().trim());
        } catch (NumberFormatException e) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (amount < 1) amount = 1;
        if (amount > 999999999) amount = 999999999;

        AE2Enhanced.network.sendToServer(new PacketSetSlotAmount(this.invType, this.slotIndex, amount));
        this.mc.displayGuiScreen(this.parent);
    }
}
