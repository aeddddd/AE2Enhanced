package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.widget.Ae2TextField;
import com.github.aeddddd.ae2enhanced.client.gui.widget.ModernGuiButton;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSetSlotAmount;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Omni Terminal 数量输入子 GUI —— 风格完全复用 AE2 GuiCraftAmount
 *
 * 中键点击 ghost slot 后弹出,输入数量后发送 PacketSetSlotAmount 并返回父 GUI.
 */
public class GuiOmniAmount extends GuiScreen
        implements com.github.aeddddd.ae2enhanced.client.gui.terminal.IOmniTerminalGui {

    private final GuiScreen parent;
    private final int invType;
    private final int slotIndex;
    private final int initialAmount;
    private final net.minecraft.item.ItemStack displayStack;

    private Ae2TextField amountField;
    private GuiButton confirmBtn;
    private GuiButton plus1, plus10, plus64, plus1000;
    private GuiButton minus1, minus10, minus64, minus1000;

    public GuiOmniAmount(GuiScreen parent, int invType, int slotIndex, int initialAmount) {
        this(parent, invType, slotIndex, initialAmount, net.minecraft.item.ItemStack.EMPTY);
    }

    public GuiOmniAmount(GuiScreen parent, int invType, int slotIndex, int initialAmount,
            net.minecraft.item.ItemStack displayStack) {
        this.parent = parent;
        this.invType = invType;
        this.slotIndex = slotIndex;
        this.initialAmount = initialAmount;
        this.displayStack = displayStack.copy();
    }

    @Override
    public void initGui() {
        super.initGui();
        int cx = (this.width - 176) / 2;
        int cy = (this.height - 107) / 2;

        this.plus1 = new ModernGuiButton(0, cx + 20, cy + 30, 22, 20, "+1");
        this.buttonList.add(this.plus1);
        this.plus10 = new ModernGuiButton(1, cx + 48, cy + 30, 28, 20, "+10");
        this.buttonList.add(this.plus10);
        this.plus64 = new ModernGuiButton(2, cx + 82, cy + 30, 32, 20, "+64");
        this.buttonList.add(this.plus64);
        this.plus1000 = new ModernGuiButton(3, cx + 120, cy + 30, 38, 20, "+1k");
        this.buttonList.add(this.plus1000);

        this.minus1 = new ModernGuiButton(4, cx + 20, cy + 72, 22, 20, "-1");
        this.buttonList.add(this.minus1);
        this.minus10 = new ModernGuiButton(5, cx + 48, cy + 72, 28, 20, "-10");
        this.buttonList.add(this.minus10);
        this.minus64 = new ModernGuiButton(6, cx + 82, cy + 72, 32, 20, "-64");
        this.buttonList.add(this.minus64);
        this.minus1000 = new ModernGuiButton(7, cx + 120, cy + 72, 38, 20, "-1k");
        this.buttonList.add(this.minus1000);

        this.confirmBtn = new ModernGuiButton(8, cx + 120, cy + 51, 38, 20,
                I18n.format("gui.ae2enhanced.omni_amount.confirm"))
                .setIcon(com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt.Icon.ENTER);
        this.buttonList.add(this.confirmBtn);

        this.amountField = new Ae2TextField(0, this.fontRenderer, cx + 48, cy + 55, 66);
        this.amountField.setMaxStringLength(9);
        this.amountField.setCanLoseFocus(false);
        this.amountField.setFocused(true);
        this.amountField.setText(String.valueOf(this.initialAmount));

        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int cx = (this.width - 176) / 2;
        int cy = (this.height - 107) / 2;

        // 绘制 AE2 19.x craft_amt.png 背景（176x107）
        com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.drawAmountDialog(cx, cy, 0.0F);

        this.amountField.drawTextBox();
        if (!this.displayStack.isEmpty()) {
            net.minecraft.client.renderer.GlStateManager.enableDepth();
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            this.mc.getRenderItem().renderItemAndEffectIntoGUI(this.displayStack, cx + 24, cy + 54);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            net.minecraft.client.renderer.GlStateManager.enableDepth();
        }

        // 绘制标题
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.omni_amount.title"), cx + 8, cy + 6,
                com.github.aeddddd.ae2enhanced.client.gui.dock.ChromeLayout.COLOR_TITLE_TEXT, false);

        super.drawScreen(mouseX, mouseY, partialTicks);
        if (mouseX >= this.confirmBtn.x && mouseX < this.confirmBtn.x + this.confirmBtn.width
                && mouseY >= this.confirmBtn.y && mouseY < this.confirmBtn.y + this.confirmBtn.height) {
            this.drawHoveringText(this.confirmBtn.displayString, mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.amountField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == this.confirmBtn) {
            this.confirm();
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
    public void updateScreen() {
        super.updateScreen();
        this.amountField.updateCursorCounter();
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

    @Override
    public void handlePageResult(com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult packet) {
        if (this.parent instanceof com.github.aeddddd.ae2enhanced.client.gui.terminal.IOmniTerminalGui) {
            ((com.github.aeddddd.ae2enhanced.client.gui.terminal.IOmniTerminalGui) this.parent)
                    .handlePageResult(packet);
        }
    }

    @Override
    public void handleUpdateNotify() {
        if (this.parent instanceof com.github.aeddddd.ae2enhanced.client.gui.terminal.IOmniTerminalGui) {
            ((com.github.aeddddd.ae2enhanced.client.gui.terminal.IOmniTerminalGui) this.parent)
                    .handleUpdateNotify();
        }
    }
}
