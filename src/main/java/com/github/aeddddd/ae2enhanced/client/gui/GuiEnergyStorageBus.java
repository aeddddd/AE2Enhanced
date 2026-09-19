package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerEnergyStorageBus;
import com.github.aeddddd.ae2enhanced.network.packet.PacketEnergyStorageBusAction;
import com.github.aeddddd.ae2enhanced.part.PartEnergyStorageBus;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * 能源存储总线 GUI,外观与交互完全复刻原版存储总线(GuiStorageBus):
 * 63 过滤槽、5 升级卡槽、访问模式 / 存储过滤按钮、分区 / 清空按钮、优先级页签.
 * 优先级设置复用 AE2 原生优先级 GUI(GuiBridge.GUI_PRIORITY).
 */
public class GuiEnergyStorageBus extends GuiUpgradeable {

    private final ContainerEnergyStorageBus container;
    private GuiImgButton rwMode;
    private GuiImgButton storageFilter;
    private GuiTabButton priority;
    private GuiImgButton partition;
    private GuiImgButton clear;

    public GuiEnergyStorageBus(InventoryPlayer inventoryPlayer, PartEnergyStorageBus te) {
        super(new ContainerEnergyStorageBus(inventoryPlayer, te));
        this.container = (ContainerEnergyStorageBus) this.cvb;
        // GuiUpgradeable 构造函数硬编码 ySize = 184,存储总线布局需要 251
        this.ySize = 251;
    }

    @Override
    protected void addButtons() {
        this.clear = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CLOSE);
        this.partition = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.ACTIONS, ActionItems.WRENCH);
        this.rwMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.storageFilter = new GuiImgButton(this.guiLeft - 18, this.guiTop + 68, Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.priority = new GuiTabButton(this.guiLeft + 154, this.guiTop, 66, GuiText.Priority.getLocal(), this.itemRender);
        this.buttonList.add(this.priority);
        this.buttonList.add(this.storageFilter);
        this.buttonList.add(this.rwMode);
        this.buttonList.add(this.partition);
        this.buttonList.add(this.clear);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(I18n.format("gui.ae2enhanced.energy_storage_bus.name")), 8, 6, 0x404040);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 0x404040);
        if (this.storageFilter != null) {
            this.storageFilter.set(this.container.getStorageFilter());
        }
        if (this.rwMode != null) {
            this.rwMode.set(this.container.getReadWriteMode());
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        super.actionPerformed(btn);
        boolean backwards = Mouse.isButtonDown(1);
        if (btn == this.partition) {
            AE2Enhanced.network.sendToServer(new PacketEnergyStorageBusAction(PacketEnergyStorageBusAction.ACTION_PARTITION));
        } else if (btn == this.clear) {
            AE2Enhanced.network.sendToServer(new PacketEnergyStorageBusAction(PacketEnergyStorageBusAction.ACTION_CLEAR));
        } else if (btn == this.priority) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
        } else if (btn == this.rwMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.rwMode.getSetting(), backwards));
        } else if (btn == this.storageFilter) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.storageFilter.getSetting(), backwards));
        }
    }
}
