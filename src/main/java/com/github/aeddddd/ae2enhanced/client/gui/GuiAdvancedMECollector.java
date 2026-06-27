package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.container.slot.SlotFake;
import appeng.core.localization.GuiText;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import com.github.aeddddd.ae2enhanced.container.ContainerAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.network.packet.PacketCollectorConfig;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 先进 ME 收集器的 GUI.
 *
 * <p>显示当前收集范围,并提供 +/- 按钮调整范围.</p>
 */
public class GuiAdvancedMECollector extends GuiUpgradeable {

    private final ContainerAdvancedMECollector container;
    private GuiButton increaseRangeButton;
    private GuiButton decreaseRangeButton;

    public GuiAdvancedMECollector(InventoryPlayer inventoryPlayer, ContainerAdvancedMECollector container) {
        super(container);
        this.container = container;
        this.ySize = 251;
    }

    @Override
    protected void addButtons() {
        super.addButtons();
        this.increaseRangeButton = new GuiButton(100, this.guiLeft + 150, this.guiTop + 6, 20, 12, "+");
        this.decreaseRangeButton = new GuiButton(101, this.guiLeft + 128, this.guiTop + 6, 20, 12, "-");
        this.buttonList.add(this.increaseRangeButton);
        this.buttonList.add(this.decreaseRangeButton);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(I18n.format("gui.ae2enhanced.advanced_me_collector.name")), 8, 6, 0x404040);
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

        // 范围显示
        String rangeText = String.format("%d³", this.container.sideLength);
        int rangeTextWidth = this.fontRenderer.getStringWidth(rangeText);
        this.fontRenderer.drawString(rangeText, 120 - rangeTextWidth, 19, 0x404040);
    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();
        if (this.increaseRangeButton != null) {
            this.increaseRangeButton.visible = true;
        }
        if (this.decreaseRangeButton != null) {
            this.decreaseRangeButton.visible = true;
        }
        // 自定义方块,schedulingMode 不显示
        if (this.schedulingMode != null) {
            this.schedulingMode.setVisibility(false);
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        super.actionPerformed(btn);
        if (btn == this.increaseRangeButton) {
            AE2Enhanced.network.sendToServer(new PacketCollectorConfig(this.container.range + 1));
        } else if (btn == this.decreaseRangeButton) {
            AE2Enhanced.network.sendToServer(new PacketCollectorConfig(this.container.range - 1));
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
