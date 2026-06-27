package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.container.slot.SlotFake;
import appeng.core.localization.GuiText;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import com.github.aeddddd.ae2enhanced.container.AbstractUniversalBusContainer;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUniversalBusConfig;
import com.github.aeddddd.ae2enhanced.part.PartUniversalBusBase;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 通用总线(输入/输出)GUI 抽象基类.
 *
 * 消除 GuiUniversalExportBus / GuiUniversalImportBus 中
 * 各自重复的 ~130 行代码.
 */
public abstract class GuiUniversalBus extends GuiUpgradeable {

    protected final AbstractUniversalBusContainer busContainer;
    protected GuiBusModeButton busModeButton;

    public GuiUniversalBus(AbstractUniversalBusContainer container) {
        super(container);
        this.busContainer = container;
        this.ySize = 251;
    }

    /** 子类提供显示名称,如 "Universal Export Bus". */
    protected abstract String getBusDisplayName();

    @Override
    protected void addButtons() {
        super.addButtons();
        // 移除 AE2 原生调度模式按钮,通用总线使用自己的 BusMode
        if (this.schedulingMode != null) {
            this.buttonList.remove(this.schedulingMode);
            this.schedulingMode = null;
        }
        this.busModeButton = new GuiBusModeButton(this.guiLeft - 18, this.guiTop + 88);
        this.buttonList.add(this.busModeButton);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(getBusDisplayName()), 8, 6, 0x404040);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 0x404040);

        if (this.redstoneMode != null) {
            this.redstoneMode.set(this.cvb.getRedStoneMode());
        }
        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(this.cvb.getFuzzyMode());
        }
        if (this.craftMode != null) {
            this.craftMode.set(this.cvb.getCraftingMode());
        }

        if (this.busModeButton != null) {
            this.busModeButton.setMode(this.busContainer.busModeOrdinal);
        }
    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();
        if (this.busModeButton != null) {
            this.busModeButton.visible = true;
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.handleButtonVisibility();
        this.bindTexture(this.getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 177, this.ySize);
        if (this.drawUpgrades()) {
            int upgradeHeight = 14 + this.cvb.availableUpgrades() * 18;
            this.drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35, upgradeHeight);
            this.drawTexturedModalRect(offsetX + 177, offsetY + upgradeHeight, 177, 151, 35, 7);
        }
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + 178, offsetY + this.ySize - 90, 178, this.ySize - 90, 68, 68);
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        super.actionPerformed(btn);
        if (btn == this.busModeButton) {
            boolean backwards = Mouse.isButtonDown(1);
            int newMode = this.busModeButton.getModeIndex() + (backwards ? -1 : 1);
            int modeCount = PartUniversalBusBase.BusMode.values().length;
            if (newMode < 0) newMode = modeCount - 1;
            if (newMode >= modeCount) newMode = 0;
            AE2Enhanced.network.sendToServer(new PacketUniversalBusConfig(newMode));
        }
    }

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        this.mapTargetSlot.clear();

        boolean isItem = ingredient instanceof ItemStack;
        boolean isFluid = ingredient instanceof FluidStack;
        boolean isGas = false;
        if (!isItem && !isFluid) {
            try {
                isGas = ingredient.getClass().getName().equals("mekanism.api.gas.GasStack");
            } catch (Exception ignored) {
            }
        }

        if (!isItem && !isFluid && !isGas) {
            return Collections.emptyList();
        }

        ArrayList<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!(slot instanceof SlotFake) || !((SlotFake) slot).isSlotEnabled()) continue;
            GhostIngredientTarget target = new GhostIngredientTarget(this.getGuiLeft(), this.getGuiTop(), slot);
            targets.add(target);
            this.mapTargetSlot.putIfAbsent(target, slot);
        }
        return targets;
    }
}
