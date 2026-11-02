package com.github.aeddddd.ae2enhanced.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import appeng.client.gui.widgets.GuiTabButton;

import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;

/**
 * AE2 19.x（1.21.1）风格的页签按钮：底图用 {@code states.png} 的 {@code TAB_BUTTON_BACKGROUND}
 * （20×20，19.x 终端页签用的就是它），图标用 19.x 图标或物品渲染（编码区模式页签仍渲染合成台/熔炉物品）。
 *
 * <p>规格对齐 AE2 19.2.17 的 {@code TabButton}：底图 20×20 原尺寸、图标 16×16 画在
 * {@code (x + 2, y + 1)}。父类（{@link GuiTabButton}）的 1.12 精灵绘制被完全跳过。</p>
 */
public class ModernTabButton extends GuiTabButton {

    private final Ae2ModernArt.Icon icon;
    private final ItemStack item;
    private float paintZ;

    public ModernTabButton(int x, int y, Ae2ModernArt.Icon icon, String message, RenderItem itemRenderer) {
        super(x, y, 0, message, itemRenderer);
        this.icon = icon;
        this.item = null;
        this.width = 20;
        this.height = 20;
    }

    public ModernTabButton(int x, int y, ItemStack itemStack, String message, RenderItem itemRenderer) {
        super(x, y, itemStack, message, itemRenderer);
        this.icon = null;
        this.item = itemStack;
        this.width = 20;
        this.height = 20;
    }

    public ModernTabButton setPaintZ(float paintZ) {
        this.paintZ = paintZ;
        return this;
    }

    public float paintZ() {
        return paintZ;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        boolean pushed = com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.pushZLevel(this.paintZ);
        try {
            Ae2ModernArt.drawIcon(Ae2ModernArt.Icon.TAB_BUTTON_BACKGROUND, this.x, this.y, 0.0F);
            if (this.icon != null) {
                Ae2ModernArt.drawIcon(this.icon, this.x + 2, this.y + 1, 0.0F);
            } else if (this.item != null && !this.item.isEmpty()) {
                float previousZ = this.zLevel;
                RenderItem renderItem = mc.getRenderItem();
                float previousItemZ = renderItem.zLevel;
                this.zLevel = 0.0F;
                renderItem.zLevel = 0.0F;
                GlStateManager.enableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                renderItem.renderItemAndEffectIntoGUI(this.item, this.x + 2, this.y + (this.height - 16) / 2);
                RenderHelper.disableStandardItemLighting();
                // 终端后续按钮仍依赖深度测试表达各自窗口层级。
                GlStateManager.enableDepth();
                renderItem.zLevel = previousItemZ;
                this.zLevel = previousZ;
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.popZLevel(pushed);
        }
    }
}
