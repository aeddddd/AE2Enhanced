package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyUnformed;
import com.github.aeddddd.ae2enhanced.network.PacketRequestAssembly;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
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

public class GuiAssemblyUnformed extends GuiContainer {

    private static final int PANEL_BG = 0xFF1a1a2e;
    private static final int PANEL_LIGHT = 0xFF16213e;
    private static final int BORDER_DIM = 0xFF0f3460;
    private static final int ACCENT = 0xFF00d4ff;
    private static final int ACCENT_SOFT = 0xFF0f4c75;
    private static final int TEXT_MAIN = 0xFFe0e0e0;
    private static final int TEXT_ERROR = 0xFFff5555;
    private static final int TEXT_SUCCESS = 0xFF55ff88;
    private static final int TEXT_WARN = 0xFFffaa55;

    private final TileAssemblyController tile;
    private GuiButtonTech assembleButton;
    private Map<Block, Integer> missingMap = new HashMap<>();
    private int refreshTicks = 0;

    public GuiAssemblyUnformed(InventoryPlayer playerInv, TileAssemblyController tile) {
        super(new ContainerAssemblyUnformed(playerInv, tile));
        this.tile = tile;
        this.xSize = 280;
        this.ySize = 240;
    }

    private void refreshMissingMap() {
        this.missingMap = AssemblyStructure.getMissingMap(mc.world, tile.getPos());
    }

    @Override
    public void initGui() {
        super.initGui();
        int centerX = guiLeft + xSize / 2;
        assembleButton = new GuiButtonTech(0, centerX - 80, guiTop + 132, 160, 24, getAssembleButtonText());
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

    /**
     * 检查玩家背包是否有足够材料组装缺失的方块。
     * 客户端计算，用于生存模式下启用/禁用组装按钮。
     */
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

        // 主背景
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, PANEL_BG);

        // 内面板区域
        drawRect(guiLeft + 10, guiTop + 40, guiLeft + xSize - 10, guiTop + 150, PANEL_LIGHT);

        // 顶部高亮条
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 2, ACCENT);

        // 外边框
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 1, BORDER_DIM);
        drawRect(guiLeft, guiTop + ySize - 1, guiLeft + xSize, guiTop + ySize, BORDER_DIM);
        drawRect(guiLeft, guiTop, guiLeft + 1, guiTop + ySize, BORDER_DIM);
        drawRect(guiLeft + xSize - 1, guiTop, guiLeft + xSize, guiTop + ySize, BORDER_DIM);

        // 角落装饰
        int corner = 10;
        drawRect(guiLeft, guiTop, guiLeft + corner, guiTop + 2, ACCENT);
        drawRect(guiLeft, guiTop, guiLeft + 2, guiTop + corner, ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop, guiLeft + xSize, guiTop + 2, ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop, guiLeft + xSize, guiTop + corner, ACCENT);
        drawRect(guiLeft, guiTop + ySize - 2, guiLeft + corner, guiTop + ySize, ACCENT);
        drawRect(guiLeft, guiTop + ySize - corner, guiLeft + 2, guiTop + ySize, ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop + ySize - 2, guiLeft + xSize, guiTop + ySize, ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop + ySize - corner, guiLeft + xSize, guiTop + ySize, ACCENT);

        // 内面板边框
        drawRect(guiLeft + 10, guiTop + 40, guiLeft + xSize - 10, guiTop + 41, BORDER_DIM);
        drawRect(guiLeft + 10, guiTop + 149, guiLeft + xSize - 10, guiTop + 150, BORDER_DIM);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        String title = I18n.format("gui.ae2enhanced.unformed.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, (xSize - titleWidth) / 2, 12, ACCENT);

        // 副标题
        String subtitle = I18n.format("gui.ae2enhanced.unformed.subtitle");
        int subWidth = fontRenderer.getStringWidth(subtitle);
        fontRenderer.drawString(subtitle, (xSize - subWidth) / 2, 28, 0xFF88ccdd);

        // 分隔线
        drawRect(16, 36, xSize - 16, 37, ACCENT_SOFT);

        if (missingMap.isEmpty()) {
            String ready = I18n.format("gui.ae2enhanced.unformed.ready");
            int rw = fontRenderer.getStringWidth(ready);
            fontRenderer.drawString(ready, (xSize - rw) / 2, 62, TEXT_SUCCESS);

            String hint = I18n.format("gui.ae2enhanced.unformed.hint");
            int hw = fontRenderer.getStringWidth(hint);
            fontRenderer.drawString(hint, (xSize - hw) / 2, 82, 0xFF88aaaa);
        } else {
            String missingTitle = I18n.format("gui.ae2enhanced.unformed.missing");
            fontRenderer.drawString(missingTitle, 26, 46, TEXT_WARN);

            // 表头
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.unformed.header.material"), 36, 62, 0xFF88aabb);
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.unformed.header.quantity"), xSize - 90, 62, 0xFF88aabb);
            drawRect(30, 74, xSize - 30, 75, BORDER_DIM);

            int y = 80;
            int shown = 0;
            int remaining = 0;
            for (Map.Entry<Block, Integer> entry : missingMap.entrySet()) {
                if (shown >= 3) {
                    remaining++;
                    continue;
                }
                Block block = entry.getKey();
                int count = entry.getValue();
                ItemStack stack = new ItemStack(block, 1);
                String name = stack.getDisplayName();

                fontRenderer.drawString(name, 36, y, TEXT_MAIN);
                String countStr = "x" + count;
                fontRenderer.drawString(countStr, xSize - 36 - fontRenderer.getStringWidth(countStr), y, TEXT_ERROR);
                y += 16;
                shown++;
            }
            if (remaining > 0) {
                String more = I18n.format("gui.ae2enhanced.unformed.missing.more", remaining);
                fontRenderer.drawString(more, 36, y, 0xFF88aaaa);
            }
        }

        // 状态提示
        if (missingMap.isEmpty()) {
            String status = I18n.format("gui.ae2enhanced.unformed.status.ready");
            int sw = fontRenderer.getStringWidth(status);
            fontRenderer.drawString(status, (xSize - sw) / 2, 126, TEXT_SUCCESS);
        } else {
            String status = I18n.format("gui.ae2enhanced.unformed.status.missing");
            int sw = fontRenderer.getStringWidth(status);
            fontRenderer.drawString(status, (xSize - sw) / 2, 126, TEXT_ERROR);
        }

        // 背包上方分隔线
        drawRect(16, 152, xSize - 16, 153, ACCENT_SOFT);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
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
