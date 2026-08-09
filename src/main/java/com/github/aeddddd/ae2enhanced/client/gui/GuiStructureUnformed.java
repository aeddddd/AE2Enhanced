package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRequestAssembly;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多方块结构未成型状态 GUI 抽象基类.
 *
 * 统一缺失材料检测、组装按钮状态、材料列表渲染等逻辑,
 * 消除 GuiAssemblyUnformed / GuiHyperdimensionalUnformed / GuiComputationUnformed
 * 中各自重复的 ~90 行代码.
 */
public abstract class GuiStructureUnformed extends GuiContainer {

    // 手绘纹理配色（浅色背景上的文字/分隔线颜色）
    private static final int TEXT_TITLE   = 0xFF211F2E;
    private static final int TEXT_SUB     = 0xFF3D4157;
    private static final int TEXT_BODY    = 0xFF1C1E2E;
    private static final int TEXT_OK      = 0xFF0E4A22;
    private static final int TEXT_WARN    = 0xFF7A4300;
    private static final int TEXT_ERROR   = 0xFF8A2418;
    private static final int DIVIDER      = 0xFF878FA5;

    protected final TileEntity tile;
    protected GuiButtonTech assembleButton;
    protected Map<Block, Integer> missingMap = new HashMap<>();
    protected int refreshTicks = 0;

    private final ResourceLocation texture;

    // 布局参数(相对 guiTop 或绝对坐标)
    private final int buttonYOffset;
    private final int statusYOffset;
    private final int inventoryDividerYOffset;
    private final int missingListStartY;
    private final int readyTextY;
    private final int hintTextY;
    private final int missingTitleY;
    private final int headerY;
    private final int headerDividerY;

    public GuiStructureUnformed(InventoryPlayer playerInv, TileEntity tile, Container container, int ySize,
                                String texturePath,
                                int buttonYOffset, int statusYOffset,
                                int inventoryDividerYOffset, int missingListStartY,
                                int readyTextY, int hintTextY, int missingTitleY,
                                int headerY, int headerDividerY) {
        super(container);
        this.tile = tile;
        this.texture = new ResourceLocation(AE2Enhanced.MOD_ID, texturePath);
        this.xSize = 280;
        this.ySize = ySize;
        this.buttonYOffset = buttonYOffset;
        this.statusYOffset = statusYOffset;
        this.inventoryDividerYOffset = inventoryDividerYOffset;
        this.missingListStartY = missingListStartY;
        this.readyTextY = readyTextY;
        this.hintTextY = hintTextY;
        this.missingTitleY = missingTitleY;
        this.headerY = headerY;
        this.headerDividerY = headerDividerY;
    }

    /** 子类提供缺失材料映射 */
    protected abstract Map<Block, Integer> getMissingMap();

    /** 子类提供标题本地化键 */
    protected abstract String getTitleKey();

    /** 子类提供副标题本地化键 */
    protected abstract String getSubtitleKey();

    /** 子类检测结构是否已成型(用于关闭 GUI) */
    protected abstract boolean isTileFormed();

    @Override
    public void initGui() {
        super.initGui();
        int centerX = guiLeft + xSize / 2;
        assembleButton = new GuiButtonTech(0, centerX - 80, guiTop + buttonYOffset, 160, 24, getAssembleButtonText());
        buttonList.add(assembleButton);
        refreshMissingMap();
        updateButtonState();
    }

    private void refreshMissingMap() {
        this.missingMap = getMissingMap();
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 背景遮罩 + 槽位 tooltips
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(texture);
        this.drawModalRectWithCustomSizedTexture(guiLeft, guiTop, 0, 0, xSize, ySize, 512, 512);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format(getTitleKey());
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, (xSize - titleWidth) / 2, 12, TEXT_TITLE);

        String subtitle = I18n.format(getSubtitleKey());
        int subWidth = fontRenderer.getStringWidth(subtitle);
        fontRenderer.drawString(subtitle, (xSize - subWidth) / 2, 28, TEXT_SUB);

        drawRect(16, 36, xSize - 16, 37, DIVIDER);

        if (missingMap.isEmpty()) {
            String ready = I18n.format("gui.ae2enhanced.unformed.ready");
            int rw = fontRenderer.getStringWidth(ready);
            fontRenderer.drawString(ready, (xSize - rw) / 2, readyTextY, TEXT_OK);

            String hint = I18n.format("gui.ae2enhanced.unformed.hint");
            int hw = fontRenderer.getStringWidth(hint);
            fontRenderer.drawString(hint, (xSize - hw) / 2, hintTextY, TEXT_SUB);
        } else {
            String missingTitle = I18n.format("gui.ae2enhanced.unformed.missing");
            fontRenderer.drawString(missingTitle, 26, missingTitleY, TEXT_WARN);

            fontRenderer.drawString(I18n.format("gui.ae2enhanced.unformed.header.material"), 36, headerY, TEXT_SUB);
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.unformed.header.quantity"), xSize - 90, headerY, TEXT_SUB);
            drawRect(30, headerDividerY, xSize - 30, headerDividerY + 1, DIVIDER);

            int y = missingListStartY;
            for (Map.Entry<Block, Integer> entry : missingMap.entrySet()) {
                Block block = entry.getKey();
                int count = entry.getValue();
                ItemStack stack = new ItemStack(block, 1);
                String name = stack.getDisplayName();

                fontRenderer.drawString(name, 36, y, TEXT_BODY);
                String countStr = "x" + count;
                fontRenderer.drawString(countStr, xSize - 36 - fontRenderer.getStringWidth(countStr), y, TEXT_ERROR);
                y += 16;
            }
        }

        if (missingMap.isEmpty()) {
            String status = I18n.format("gui.ae2enhanced.unformed.status.ready");
            int sw = fontRenderer.getStringWidth(status);
            fontRenderer.drawString(status, (xSize - sw) / 2, statusYOffset, TEXT_OK);
        } else {
            String status = I18n.format("gui.ae2enhanced.unformed.status.missing");
            int sw = fontRenderer.getStringWidth(status);
            fontRenderer.drawString(status, (xSize - sw) / 2, statusYOffset, TEXT_ERROR);
        }

        drawRect(16, inventoryDividerYOffset, xSize - 16, inventoryDividerYOffset + 1, DIVIDER);
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
        if (isTileFormed()) {
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
