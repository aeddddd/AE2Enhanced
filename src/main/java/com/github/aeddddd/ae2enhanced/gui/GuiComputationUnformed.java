package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerComputationUnformed;
import com.github.aeddddd.ae2enhanced.network.PacketRequestAssembly;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Supercausal Computation Core unformed state GUI.
 * Follows the same logic as GuiHyperdimensionalUnformed.
 */
public class GuiComputationUnformed extends GuiContainer {

    private static final int PANEL_BG = 0xFF1a1a2e;
    private static final int PANEL_LIGHT = 0xFF16213e;
    private static final int BORDER_DIM = 0xFF0f3460;
    private static final int ACCENT = 0xFF00d4ff;
    private static final int ACCENT_SOFT = 0xFF0f4c75;
    private static final int TEXT_MAIN = 0xFFe0e0e0;
    private static final int TEXT_ERROR = 0xFFff5555;
    private static final int TEXT_SUCCESS = 0xFF55ff88;
    private static final int TEXT_WARN = 0xFFffaa55;

    private final TileComputationCore tile;
    private GuiButtonTech assembleButton;
    private Map<Block, Integer> missingMap = new HashMap<>();
    private int refreshTicks = 0;

    public GuiComputationUnformed(InventoryPlayer playerInv, TileComputationCore tile) {
        super(new ContainerComputationUnformed(playerInv, tile));
        this.tile = tile;
        this.xSize = 280;
        this.ySize = 260;
    }

    private void refreshMissingMap() {
        SupercausalStructure.ValidationResult result = SupercausalStructure.validate(mc.world, tile.getPos());
        this.missingMap = result.missing;
    }

    @Override
    public void initGui() {
        super.initGui();
        int centerX = guiLeft + xSize / 2;
        assembleButton = new GuiButtonTech(0, centerX - 80, guiTop + 150, 160, 24, getAssembleButtonText());
        buttonList.add(assembleButton);
        refreshMissingMap();
        updateButtonState();
    }

    private String getAssembleButtonText() {
        if (mc.player.isCreative()) {
            return I18n.format("gui.ae2enhanced.assemble.creative");
        }
        return I18n.format("gui.ae2enhanced.assemble.survival");
    }

    private boolean hasEnoughMaterials() {
        if (missingMap.isEmpty()) return true;
        Map<Block, Integer> needed = new LinkedHashMap<>(missingMap);
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                Block block = entry.getKey();
                if (stack.getItem() == Item.getItemFromBlock(block)) {
                    int need = entry.getValue();
                    int have = stack.getCount();
                    if (have >= need) {
                        entry.setValue(0);
                    } else {
                        entry.setValue(need - have);
                    }
                    break;
                }
            }
        }
        for (int count : needed.values()) {
            if (count > 0) return false;
        }
        return true;
    }

    private void updateButtonState() {
        if (missingMap.isEmpty()) {
            assembleButton.enabled = true;
            assembleButton.displayString = getAssembleButtonText();
        } else {
            if (mc.player.isCreative()) {
                assembleButton.enabled = true;
            } else {
                boolean hasMaterials = hasEnoughMaterials();
                assembleButton.enabled = hasMaterials;
                assembleButton.displayString = hasMaterials
                        ? getAssembleButtonText()
                        : I18n.format("gui.ae2enhanced.assemble.insufficient");
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, PANEL_BG);
        drawRect(guiLeft + 10, guiTop + 40, guiLeft + xSize - 10, guiTop + 140, PANEL_LIGHT);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 2, ACCENT);

        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 1, BORDER_DIM);
        drawRect(guiLeft, guiTop + ySize - 1, guiLeft + xSize, guiTop + ySize, BORDER_DIM);
        drawRect(guiLeft, guiTop, guiLeft + 1, guiTop + ySize, BORDER_DIM);
        drawRect(guiLeft + xSize - 1, guiTop, guiLeft + xSize, guiTop + ySize, BORDER_DIM);

        int corner = 10;
        drawRect(guiLeft, guiTop, guiLeft + corner, guiTop + 2, ACCENT);
        drawRect(guiLeft, guiTop, guiLeft + 2, guiTop + corner, ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop, guiLeft + xSize, guiTop + 2, ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop, guiLeft + xSize, guiTop + corner, ACCENT);
        drawRect(guiLeft, guiTop + ySize - 2, guiLeft + corner, guiTop + ySize, ACCENT);
        drawRect(guiLeft, guiTop + ySize - corner, guiLeft + 2, guiTop + ySize, ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop + ySize - 2, guiLeft + xSize, guiTop + ySize, ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop + ySize - corner, guiLeft + xSize, guiTop + ySize, ACCENT);

        drawRect(guiLeft + 10, guiTop + 40, guiLeft + xSize - 10, guiTop + 41, BORDER_DIM);
        drawRect(guiLeft + 10, guiTop + 139, guiLeft + xSize - 10, guiTop + 140, BORDER_DIM);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.computation.unformed.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, (xSize - titleWidth) / 2, 12, ACCENT);

        String subtitle = I18n.format("tile.ae2enhanced.computation_core.name");
        int subWidth = fontRenderer.getStringWidth(subtitle);
        fontRenderer.drawString(subtitle, (xSize - subWidth) / 2, 28, 0xFF88ccdd);

        drawRect(16, 36, xSize - 16, 37, ACCENT_SOFT);

        if (missingMap.isEmpty()) {
            String ready = I18n.format("gui.ae2enhanced.unformed.ready");
            int rw = fontRenderer.getStringWidth(ready);
            fontRenderer.drawString(ready, (xSize - rw) / 2, 54, TEXT_SUCCESS);

            String hint = I18n.format("gui.ae2enhanced.unformed.hint");
            int hw = fontRenderer.getStringWidth(hint);
            fontRenderer.drawString(hint, (xSize - hw) / 2, 70, 0xFF88aaaa);
        } else {
            String missingTitle = I18n.format("gui.ae2enhanced.unformed.missing");
            fontRenderer.drawString(missingTitle, 26, 46, TEXT_WARN);

            fontRenderer.drawString(I18n.format("gui.ae2enhanced.unformed.header.material"), 36, 58, 0xFF88aabb);
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.unformed.header.quantity"), xSize - 90, 58, 0xFF88aabb);
            drawRect(30, 70, xSize - 30, 71, BORDER_DIM);

            int y = 76;
            for (Map.Entry<Block, Integer> entry : missingMap.entrySet()) {
                Block block = entry.getKey();
                int count = entry.getValue();
                ItemStack stack = new ItemStack(block, 1);
                String name = stack.getDisplayName();

                fontRenderer.drawString(name, 36, y, TEXT_MAIN);
                String countStr = "x" + count;
                fontRenderer.drawString(countStr, xSize - 36 - fontRenderer.getStringWidth(countStr), y, TEXT_ERROR);
                y += 16;
            }
        }

        if (missingMap.isEmpty()) {
            String status = I18n.format("gui.ae2enhanced.unformed.status.ready");
            int sw = fontRenderer.getStringWidth(status);
            fontRenderer.drawString(status, (xSize - sw) / 2, 134, TEXT_SUCCESS);
        } else {
            String status = I18n.format("gui.ae2enhanced.unformed.status.missing");
            int sw = fontRenderer.getStringWidth(status);
            fontRenderer.drawString(status, (xSize - sw) / 2, 134, TEXT_ERROR);
        }

        drawRect(16, 170, xSize - 16, 171, ACCENT_SOFT);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            AE2Enhanced.network.sendToServer(new PacketRequestAssembly(tile.getPos()));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (tile.isFormed()) {
            mc.player.closeScreen();
            return;
        }
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            refreshMissingMap();
            updateButtonState();
        }
    }
}
