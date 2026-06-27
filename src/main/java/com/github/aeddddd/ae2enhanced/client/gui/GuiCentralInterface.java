package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.core.localization.GuiText;
import com.github.aeddddd.ae2enhanced.container.ContainerCentralInterface;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import net.minecraft.entity.player.InventoryPlayer;
/**
 * 中枢 ME 接口的 GUI.
 *
 * 复刻 AE2 GuiInterface 的布局和外观.
 */
public class GuiCentralInterface extends GuiUpgradeable {

    public GuiCentralInterface(InventoryPlayer inventoryPlayer, TileCentralMEInterface te) {
        super(new ContainerCentralInterface(inventoryPlayer, te));
        this.ySize = 256;
    }

    @Override
    protected void addButtons() {
        // 中枢 ME 接口模式固定,不显示任何配置按钮
    }

    @Override
    protected void handleButtonVisibility() {
        // 无按钮需要处理可见性
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Interface.getLocal()), 8, 6, 0x404040);
        this.fontRenderer.drawString(GuiText.Config.getLocal(), 8, 24, 0x404040);
        this.fontRenderer.drawString(GuiText.StoredItems.getLocal(), 8, 73, 0x404040);
        this.fontRenderer.drawString(GuiText.Patterns.getLocal(), 8, 86, 0x404040);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 0x404040);
    }

    @Override
    protected String getBackground() {
        int upgrades = ((ContainerCentralInterface) this.cvb).getPatternUpgrades();
        if (upgrades == 0) {
            return "guis/newinterface.png";
        }
        return "guis/newinterface" + upgrades + ".png";
    }
}
