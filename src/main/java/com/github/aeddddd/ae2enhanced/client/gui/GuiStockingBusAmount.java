package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketStockingBusConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * StockingBus 的数量快速输入 GUI.
 * 中键点击 config slot 时弹出,支持直接输入数字或数学表达式(如 64*4).
 */
public class GuiStockingBusAmount extends GuiScreen {

    private final int slot;
    private final long currentAmount;
    private final GuiScreen prevGui;
    private GuiTextField amountField;
    private GuiButton confirmButton;

    public GuiStockingBusAmount(int slot, long currentAmount, GuiScreen prevGui) {
        this.slot = slot;
        this.currentAmount = currentAmount;
        this.prevGui = prevGui;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.amountField = new GuiTextField(0, this.fontRenderer, centerX - 60, centerY - 12, 120, 20);
        this.amountField.setText(String.valueOf(this.currentAmount));
        this.amountField.setMaxStringLength(16);
        this.amountField.setFocused(true);
        this.amountField.setCanLoseFocus(false);

        this.confirmButton = new GuiButton(1, centerX - 50, centerY + 20, 100, 20, I18n.format("gui.done"));
        this.buttonList.add(this.confirmButton);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == this.confirmButton) {
            confirm();
        } else {
            super.actionPerformed(button);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            confirm();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.prevGui);
            return;
        }
        if (this.amountField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.amountField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.amountField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        String title = I18n.format("gui.ae2enhanced.stocking_bus.set_amount");
        int titleWidth = this.fontRenderer.getStringWidth(title);
        this.fontRenderer.drawStringWithShadow(title, (this.width - titleWidth) / 2, this.height / 2 - 40, 0xFFFFFF);

        this.amountField.drawTextBox();
    }

    private void confirm() {
        String text = this.amountField.getText().trim();
        if (text.isEmpty()) {
            this.mc.displayGuiScreen(this.prevGui);
            return;
        }

        long amount = parseAmount(text);
        if (amount < 0) amount = 0;

        AE2Enhanced.network.sendToServer(new PacketStockingBusConfig(this.slot, amount));
        this.mc.displayGuiScreen(this.prevGui);
    }

    /**
     * 解析输入文本.优先尝试 AE2 的 MathExpressionParser(支持 64*4+1 等表达式),
     * 失败时回退到纯 Long 解析.
     */
    private static long parseAmount(String text) {
        try {
            Class<?> parserClass = Class.forName("appeng.client.gui.MathExpressionParser");
            java.lang.reflect.Method parseMethod = parserClass.getMethod("parse", String.class);
            double result = (Double) parseMethod.invoke(null, text);
            if (Double.isNaN(result) || result <= 0) {
                return 0;
            }
            java.lang.reflect.Method roundMethod = parserClass.getMethod("round", double.class, int.class);
            double rounded = (Double) roundMethod.invoke(null, result, 0);
            return (long) rounded;
        } catch (Exception ignored) {
            // 回退到纯数字解析
        }

        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
