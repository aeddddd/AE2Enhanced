package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternEncode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternMiniGuiScroll;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternModify;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternReplace;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternScroll;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternToggle;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 智能样板接口的客户端 GUI v3.
 *
 * <p>核心变更：</p>
 * <ul>
 *   <li>右侧面板扩展至 223 高,新增顶部 16 个配置按钮 + 底部替换操作区</li>
 *   <li>顶部 8×2 小按钮：×2/×3/×4/×5, /2//3//4//5, +1/-1, 轮换/清除输入输出, 堆叠/展开</li>
 *   <li>底部操作区：左侧当前物品槽 + → 替换按钮 + 右侧目标槽 + X 保留主产物</li>
 * </ul>
 */
public class GuiSmartPatternInterface extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation("ae2enhanced", "textures/gui/smartpatterninterface.png");

    // 主面板配方网格坐标
    private static final int[] COL_X = {8, 26, 44, 62, 80, 98, 116, 134, 152};
    private static final int[] ROW_Y = {36, 54, 72, 90, 108};

    // 编码按钮
    private static final int BTN_ENCODE_X = 134;
    private static final int BTN_ENCODE_Y = 20;
    private static final int BTN_SIZE = 16;

    // 翻页按钮(纹理中已有图标,高度12)
    private static final int BTN_PREV_X = 116;
    private static final int BTN_PREV_Y = 126;
    private static final int BTN_NEXT_X = 152;
    private static final int BTN_NEXT_Y = 126;
    private static final int BTN_PAGE_H = 12;
    private static final int BTN_PAGE_UV_Y = 126;

    // 删除已禁用配方按钮
    private static final int BTN_DELETE_DISABLED_X = 96;
    private static final int BTN_DELETE_DISABLED_Y = 126;
    private static final int BTN_DELETE_DISABLED_W = 16;
    private static final int BTN_DELETE_DISABLED_H = 12;

    // 顶部小按钮 8×2
    private static final int[] SMALL_BTN_X = {175, 183, 191, 199, 207, 215, 223, 231};
    private static final int[] SMALL_BTN_Y = {1, 9};
    private static final int SMALL_BTN_SIZE = 8;

    // 底部操作区
    private static final int SLOT_REPLACE_L_X = 176;
    private static final int SLOT_REPLACE_L_Y = 162;
    private static final int BTN_ARROW_X = 194;
    private static final int BTN_ARROW_Y = 161;
    private static final int SLOT_REPLACE_R_X = 212;
    private static final int SLOT_REPLACE_R_Y = 162;
    private static final int BTN_KEEP_X = 228;
    private static final int BTN_KEEP_Y = 161;
    private static final int BTN_KEEP_W = 14;
    private static final int BTN_KEEP_H = 16;

    // X 标记纹理坐标
    private static final int MARK_X_U = 8;
    private static final int MARK_Y_V = 37;
    private static final int MARK_W = 16;
    private static final int MARK_H = 15;

    private final TileSmartPatternInterface tile;

    // MiniGUI 滚动条状态
    private int miniGuiScrollOffset = 0;

    // 点击闪烁反馈
    private int flashSlot = -1;
    private int flashTicks = 0;
    private static final int FLASH_DURATION = 8;

    // 小按钮 tooltip 名称
    private static final String[][] SMALL_BTN_TOOLTIPS = {
        {"×2", "×4", "×3", "×5", "+1",
         "gui.ae2enhanced.smart_pattern_interface.rotate_inputs",
         "gui.ae2enhanced.smart_pattern_interface.clear_inputs",
         "gui.ae2enhanced.smart_pattern_interface.unstack"},
        {"/2", "/4", "/3", "/5", "-1",
         "gui.ae2enhanced.smart_pattern_interface.rotate_outputs",
         "gui.ae2enhanced.smart_pattern_interface.clear_outputs",
         "gui.ae2enhanced.smart_pattern_interface.stack"}
    };

    // 小按钮 action 名称
    private static final String[][] SMALL_BTN_ACTIONS = {
        {"multiply2", "multiply4", "multiply3", "multiply5", "add1",
         "rotateInputs", "clearInputs", "unstack"},
        {"divide2", "divide4", "divide3", "divide5", "add-1",
         "rotateOutputs", "clearOutputs", "stack"}
    };

    // MiniGUI 滚动条常量
    private static final int SCROLLBAR_X = 232;
    private static final int SCROLLBAR_Y = 19;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = 54;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;
    private static final ResourceLocation SCROLLBAR_TEXTURE =
        new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");

    public GuiSmartPatternInterface(InventoryPlayer inventoryPlayer, TileSmartPatternInterface tile) {
        super(new ContainerSmartPatternInterface(inventoryPlayer, tile));
        this.tile = tile;
        this.miniGuiScrollOffset = tile.getMiniGuiScrollOffset();
        this.xSize = 242;
        this.ySize = 224;
    }

    public TileSmartPatternInterface getTile() {
        return tile;
    }



    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);

        // 主面板背景 (0, 0, 176, 224)
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, 176, this.ySize);

        // 右侧面板 (175, 0, 67, 223)
        this.drawTexturedModalRect(this.guiLeft + 175, this.guiTop, 175, 0, 67, 223);

        // 大向下箭头 (196, 76, 16, 23)
        this.drawTexturedModalRect(this.guiLeft + 196, this.guiTop + 76, 196, 76, 16, 23);

        // MiniGUI 滚动条
        drawMiniGuiScrollBar();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 标题
        String title = I18n.format("gui.ae2enhanced.smart_pattern_interface.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);

        // 绑定状态
        if (tile.isBound()) {
            String displayName = tile.getBoundBlockDisplayName();
            String boundText = I18n.format("gui.ae2enhanced.smart_pattern_interface.bound_to", displayName);
            this.fontRenderer.drawString(boundText, 8, 21, 0x404040);
        } else {
            String noTarget = I18n.format("gui.ae2enhanced.smart_pattern_interface.no_target");
            this.fontRenderer.drawString(noTarget, 8, 21, 0xAA0000);
        }

        // 玩家背包标签
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, 129, 0x404040);

        // 页码
        drawPageIndicator();

        // 禁用标记 X
        drawDisabledMarkers();

        // 冲突标记
        drawConflictMarkers();

        // 锁定标记
        drawLockMarkers();

        // 点击闪烁
        drawFlashEffect();

        // 顶部小按钮(从纹理绘制)
        drawSmallButtons(relX, relY);

        // 翻页按钮(从纹理绘制)
        drawPageButtons(relX, relY);

        // 删除禁用按钮
        drawDeleteDisabledButton(relX, relY);

        // 底部操作区(从纹理绘制)
        drawBottomArea(relX, relY);
    }

    // ---- 绘制方法 ----

    private void drawPageIndicator() {
        SmartPatternData data = tile.getPatternData();
        if (data == null || data.getRecipeCount() <= 45) return;
        int currentPage = tile.getScrollOffset();
        int maxPage = Math.max(0, (data.getRecipeCount() - 1) / 45);
        String pageText = (currentPage + 1) + " / " + (maxPage + 1);
        int textWidth = this.fontRenderer.getStringWidth(pageText);
        this.fontRenderer.drawString(pageText, BTN_PREV_X + (BTN_NEXT_X + BTN_SIZE - BTN_PREV_X - textWidth) / 2, BTN_PREV_Y + 3, 0x404040);
    }

    private void drawSmallButtons(int relX, int relY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        boolean locked = tile.getLockedRecipeIndex() >= 0;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 8; col++) {
                int x = SMALL_BTN_X[col];
                int y = SMALL_BTN_Y[row];
                boolean hover = relX >= x && relX < x + SMALL_BTN_SIZE
                             && relY >= y && relY < y + SMALL_BTN_SIZE;
                int color = hover ? 0xFFCCCCCC : 0xFFFFFFFF;
                if (!locked) {
                    // 所有小按钮都需要锁定配方才能使用
                    color = 0xFF777777;
                }
                GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F);
                this.drawTexturedModalRect(x, y, x, y, SMALL_BTN_SIZE, SMALL_BTN_SIZE);
            }
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F);
    }

    private void drawPageButtons(int relX, int relY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        // 上一页
        int prevColor = isInPrevButton(relX, relY) ? 0xFFCCCCCC : 0xFFFFFFFF;
        GlStateManager.color((prevColor >> 16 & 255) / 255.0F, (prevColor >> 8 & 255) / 255.0F, (prevColor & 255) / 255.0F);
        this.drawTexturedModalRect(BTN_PREV_X, BTN_PREV_Y, BTN_PREV_X, BTN_PAGE_UV_Y, BTN_SIZE, BTN_PAGE_H);
        // 下一页
        int nextColor = isInNextButton(relX, relY) ? 0xFFCCCCCC : 0xFFFFFFFF;
        GlStateManager.color((nextColor >> 16 & 255) / 255.0F, (nextColor >> 8 & 255) / 255.0F, (nextColor & 255) / 255.0F);
        this.drawTexturedModalRect(BTN_NEXT_X, BTN_NEXT_Y, BTN_NEXT_X, BTN_PAGE_UV_Y, BTN_SIZE, BTN_PAGE_H);
        GlStateManager.color(1.0F, 1.0F, 1.0F);
    }

    private void drawDeleteDisabledButton(int relX, int relY) {
        SmartPatternData data = tile.getPatternData();
        boolean hasDisabled = data != null && data.getRecipeCount() > data.getEnabledCount();
        if (!hasDisabled) return;
        // 按钮图形已在 GUI 纹理中绘制,无需额外代码绘制
    }

    private void drawBottomArea(int relX, int relY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        // 左侧槽位背景
        this.drawTexturedModalRect(SLOT_REPLACE_L_X, SLOT_REPLACE_L_Y, SLOT_REPLACE_L_X, SLOT_REPLACE_L_Y, 16, 16);
        // → 按钮
        int arrowColor = isInArrowButton(relX, relY) ? 0xFFCCCCCC : 0xFFFFFFFF;
        GlStateManager.color((arrowColor >> 16 & 255) / 255.0F, (arrowColor >> 8 & 255) / 255.0F, (arrowColor & 255) / 255.0F);
        this.drawTexturedModalRect(BTN_ARROW_X, BTN_ARROW_Y, BTN_ARROW_X, BTN_ARROW_Y, 16, 16);
        GlStateManager.color(1.0F, 1.0F, 1.0F);
        // 右侧槽位背景
        this.drawTexturedModalRect(SLOT_REPLACE_R_X, SLOT_REPLACE_R_Y, SLOT_REPLACE_R_X, SLOT_REPLACE_R_Y, 16, 16);
        // X 按钮(只保留主产物)
        int keepColor = isInKeepButton(relX, relY) ? 0xFFCCCCCC : 0xFFFFFFFF;
        GlStateManager.color((keepColor >> 16 & 255) / 255.0F, (keepColor >> 8 & 255) / 255.0F, (keepColor & 255) / 255.0F);
        this.drawTexturedModalRect(BTN_KEEP_X, BTN_KEEP_Y, BTN_KEEP_X, BTN_KEEP_Y, BTN_KEEP_W, BTN_KEEP_H);
        GlStateManager.color(1.0F, 1.0F, 1.0F);
    }

    private void drawFlashEffect() {
        if (flashSlot < 0 || flashTicks <= 0) return;
        int row = flashSlot / 9;
        int col = flashSlot % 9;
        if (row < ROW_Y.length && col < COL_X.length) {
            int x = COL_X[col];
            int y = ROW_Y[row];
            int alpha = (int) (0x55 * flashTicks / FLASH_DURATION);
            drawRect(x, y, x + 16, y + 15, (alpha << 24) | 0xFFFFFF);
        }
    }

    private void drawDisabledMarkers() {
        SmartPatternData data = tile.getPatternData();
        if (data == null) return;
        int pageStart = tile.getScrollOffset() * 45;
        this.mc.getTextureManager().bindTexture(TEXTURE);
        int slotIndex = 0;
        for (int row = 0; row < ROW_Y.length; row++) {
            for (int col = 0; col < COL_X.length; col++) {
                int sortedIndex = pageStart + slotIndex;
                if (data.isDisabled(sortedIndex)) {
                    int x = COL_X[col];
                    int y = ROW_Y[row];
                    this.drawTexturedModalRect(x, y, MARK_X_U, MARK_Y_V, MARK_W, MARK_H);
                }
                slotIndex++;
            }
        }
    }

    private void drawConflictMarkers() {
        SmartPatternData data = tile.getPatternData();
        if (data == null) return;
        int pageStart = tile.getScrollOffset() * 45;
        int slotIndex = 0;
        for (int row = 0; row < ROW_Y.length; row++) {
            for (int col = 0; col < COL_X.length; col++) {
                int sortedIndex = pageStart + slotIndex;
                if (sortedIndex < data.getRecipeCount() && data.isConflict(sortedIndex)) {
                    int x = COL_X[col];
                    int y = ROW_Y[row];
                    drawRect(x, y, x + 16, y + 15, 0x66FF0000);
                    drawRect(x, y, x + 16, y + 1, 0xFFFF0000);
                    drawRect(x, y + 14, x + 16, y + 15, 0xFFFF0000);
                    drawRect(x, y, x + 1, y + 15, 0xFFFF0000);
                    drawRect(x + 15, y, x + 16, y + 15, 0xFFFF0000);
                }
                slotIndex++;
            }
        }
    }

    private void drawLockMarkers() {
        int lockedIndex = tile.getLockedRecipeIndex();
        if (lockedIndex < 0) return;
        int pageStart = tile.getScrollOffset() * 45;
        int slotIndex = lockedIndex - pageStart;
        if (slotIndex < 0 || slotIndex >= 45) return;
        int row = slotIndex / 9;
        int col = slotIndex % 9;
        if (row < ROW_Y.length && col < COL_X.length) {
            int x = COL_X[col];
            int y = ROW_Y[row];
            drawRect(x, y, x + 16, y + 1, 0xFF00AAFF);
            drawRect(x, y + 14, x + 16, y + 15, 0xFF00AAFF);
            drawRect(x, y, x + 1, y + 15, 0xFF00AAFF);
            drawRect(x + 15, y, x + 16, y + 15, 0xFF00AAFF);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (flashTicks > 0) flashTicks--;
        if (flashTicks <= 0) flashSlot = -1;

        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            int relX = mouseX - this.guiLeft;
            int relY = mouseY - this.guiTop;
            if (isInRecipeGrid(relX, relY)) {
                SmartPatternData data = tile.getPatternData();
                if (data != null && data.getRecipeCount() > 45) {
                    int maxPage = Math.max(0, (data.getRecipeCount() - 1) / 45);
                    int newPage = tile.getScrollOffset() + (wheel > 0 ? -1 : 1);
                    newPage = Math.max(0, Math.min(maxPage, newPage));
                    if (newPage != tile.getScrollOffset()) {
                        tile.setScrollOffset(newPage);
                        AE2Enhanced.network.sendToServer(new PacketSmartPatternScroll(tile.getPos(), newPage));
                    }
                }
            } else if (isInMiniGuiArea(relX, relY) && tile.getLockedRecipeIndex() >= 0) {
                // MiniGUI 区域滚轮
                int delta = wheel > 0 ? -1 : 1;
                int newOffset = Math.max(0, Math.min(8, miniGuiScrollOffset + delta));
                if (newOffset != miniGuiScrollOffset) {
                    miniGuiScrollOffset = newOffset;
                    tile.setMiniGuiScrollOffset(newOffset);
                    ((ContainerSmartPatternInterface) this.inventorySlots).setScrollOffset(newOffset);
                    AE2Enhanced.network.sendToServer(new PacketSmartPatternMiniGuiScroll(tile.getPos(), newOffset));
                }
            }
        }

        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 编码按钮 tooltip
        if (isInEncodeButton(relX, relY)) {
            drawHoveringText(getEncodeButtonTooltip(), mouseX, mouseY);
            return;
        }

        // 顶部小按钮 tooltip
        int smallBtn = getSmallButtonAt(relX, relY);
        if (smallBtn >= 0) {
            int row = smallBtn / 8;
            int col = smallBtn % 8;
            String key = SMALL_BTN_TOOLTIPS[row][col];
            String text = key.startsWith("gui.") ? I18n.format(key) : key;
            drawHoveringText(Collections.singletonList(text), mouseX, mouseY);
            return;
        }

        // 删除已禁用配方按钮 tooltip
        if (isInDeleteDisabledButton(relX, relY)) {
            SmartPatternData data = tile.getPatternData();
            int disabledCount = data != null ? (data.getRecipeCount() - data.getEnabledCount()) : 0;
            drawHoveringText(Collections.singletonList(
                I18n.format("gui.ae2enhanced.smart_pattern_interface.delete_disabled", disabledCount)
            ), mouseX, mouseY);
            return;
        }

        // 翻页按钮 tooltip
        if (isInPrevButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.smart_pattern_interface.prev_page")), mouseX, mouseY);
            return;
        }
        if (isInNextButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.smart_pattern_interface.next_page")), mouseX, mouseY);
            return;
        }

        // 底部操作区 tooltip
        if (isInArrowButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.smart_pattern_interface.replace_all")), mouseX, mouseY);
            return;
        }
        if (isInKeepButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.smart_pattern_interface.keep_primary")), mouseX, mouseY);
            return;
        }

        // 配方槽位状态 tooltip(仅当 slot 为空时)
        int slot = getRecipeSlotAt(relX, relY);
        if (slot >= 0) {
            Slot recipeSlot = this.inventorySlots.getSlot(slot);
            if (!recipeSlot.getHasStack()) {
                SmartPatternData data = tile.getPatternData();
                if (data != null) {
                    int sortedIndex = tile.getScrollOffset() * 45 + slot;
                    if (sortedIndex < data.getRecipeCount()) {
                        List<String> lines = new ArrayList<>();
                        if (data.isConflict(sortedIndex)) {
                            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.conflict_marker"));
                        }
                        if (data.isDisabled(sortedIndex)) {
                            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.disabled_marker"));
                        }
                        if (!lines.isEmpty()) {
                            drawHoveringText(lines, mouseX, mouseY);
                            return;
                        }
                    }
                }
            }
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private List<String> getEncodeButtonTooltip() {
        List<String> lines = new ArrayList<>();
        SmartPatternData data = tile.getPatternData();
        if (data == null) {
            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_disabled_no_data"));
            return lines;
        }
        if (data.hasConflicts()) {
            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_disabled_conflict"));
            return lines;
        }
        ItemStack output = tile.getInventory().getStackInSlot(1);
        if (!output.isEmpty()) {
            if (output.getItem() instanceof ItemSmartPattern && isSamePatternData(output, data)) {
                lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_update_tooltip"));
            } else {
                lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_disabled_output_mismatch"));
            }
            return lines;
        }
        ItemStack input = tile.getInventory().getStackInSlot(0);
        if (input.isEmpty() || !(input.getItem() instanceof ItemSmartBlankPattern)) {
            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_disabled_no_blank"));
            return lines;
        }
        lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_tooltip"));
        return lines;
    }

    private boolean isSamePatternData(ItemStack output, SmartPatternData data) {
        if (!output.hasTagCompound()) {
            return false;
        }
        java.util.UUID outputId = output.getTagCompound().getUniqueId("patternDataId");
        return outputId != null && outputId.equals(data.getPatternDataId());
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 编码按钮
        if (isInEncodeButton(relX, relY) && mouseButton == 0) {
            SmartPatternData data = tile.getPatternData();
            if (data != null && !data.hasConflicts()) {
                ItemStack output = tile.getInventory().getStackInSlot(1);
                boolean canUpdate = !output.isEmpty()
                        && output.getItem() instanceof ItemSmartPattern
                        && isSamePatternData(output, data);
                boolean canCreate = output.isEmpty()
                        && !tile.getInventory().getStackInSlot(0).isEmpty()
                        && tile.getInventory().getStackInSlot(0).getItem() instanceof ItemSmartBlankPattern;
                if (canUpdate || canCreate) {
                    AE2Enhanced.network.sendToServer(new PacketSmartPatternEncode(tile.getPos()));
                }
            }
            return;
        }

        // 顶部小按钮
        int smallBtn = getSmallButtonAt(relX, relY);
        if (smallBtn >= 0 && mouseButton == 0) {
            int row = smallBtn / 8;
            int col = smallBtn % 8;
            boolean locked = tile.getLockedRecipeIndex() >= 0;
            if (!locked) return;
            String action = SMALL_BTN_ACTIONS[row][col];
            tile.modifyLockedRecipe(action);
            AE2Enhanced.network.sendToServer(new PacketSmartPatternModify(tile.getPos(), action));
            return;
        }

        // 删除已禁用配方按钮
        if (mouseButton == 0 && isInDeleteDisabledButton(relX, relY)) {
            tile.deleteDisabledRecipes();
            AE2Enhanced.network.sendToServer(new PacketSmartPatternModify(tile.getPos(), "deleteDisabled"));
            return;
        }

        // 翻页按钮
        if (mouseButton == 0) {
            if (isInPrevButton(relX, relY)) {
                int newPage = tile.getScrollOffset() - 1;
                if (newPage >= 0) {
                    tile.setScrollOffset(newPage);
                    AE2Enhanced.network.sendToServer(new PacketSmartPatternScroll(tile.getPos(), newPage));
                }
                return;
            }
            if (isInNextButton(relX, relY)) {
                SmartPatternData data = tile.getPatternData();
                if (data != null) {
                    int maxPage = Math.max(0, (data.getRecipeCount() - 1) / 45);
                    int newPage = tile.getScrollOffset() + 1;
                    if (newPage <= maxPage) {
                        tile.setScrollOffset(newPage);
                        AE2Enhanced.network.sendToServer(new PacketSmartPatternScroll(tile.getPos(), newPage));
                    }
                }
                return;
            }
        }

        // 底部 → 替换按钮
        if (mouseButton == 0 && isInArrowButton(relX, relY)) {
            ItemStack from = tile.getReplaceInventory().getStackInSlot(0);
            ItemStack to = tile.getReplaceInventory().getStackInSlot(1);
            if (!from.isEmpty()) {
                tile.replaceInAllRecipes(from, to);
                AE2Enhanced.network.sendToServer(new PacketSmartPatternReplace(tile.getPos(), from, to));
            }
            return;
        }

        // 底部 X 保留主产物按钮
        if (mouseButton == 0 && isInKeepButton(relX, relY) && tile.getLockedRecipeIndex() >= 0) {
            tile.modifyLockedRecipe("keepPrimary");
            AE2Enhanced.network.sendToServer(new PacketSmartPatternModify(tile.getPos(), "keepPrimary"));
            return;
        }

        // MiniGUI 滚动条点击
        if (tile.getLockedRecipeIndex() >= 0) {
            int newOffset = handleMiniGuiScrollClick(relX, relY);
            if (newOffset != miniGuiScrollOffset) {
                miniGuiScrollOffset = newOffset;
                tile.setMiniGuiScrollOffset(newOffset);
                ((ContainerSmartPatternInterface) this.inventorySlots).setScrollOffset(newOffset);
                AE2Enhanced.network.sendToServer(new PacketSmartPatternMiniGuiScroll(tile.getPos(), newOffset));
            }
        }

        // 配方槽位点击
        if (mouseButton == 0) {
            int clickedSlot = getRecipeSlotAt(relX, relY);
            if (clickedSlot >= 0) {
                int sortedIndex = tile.getScrollOffset() * 45 + clickedSlot;
                boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                if (shift) {
                    if (tile.isRecipeLocked(sortedIndex)) {
                        tile.unlockRecipe();
                        AE2Enhanced.network.sendToServer(new PacketSmartPatternModify(tile.getPos(), "unlock"));
                    } else {
                        tile.lockRecipe(sortedIndex);
                        AE2Enhanced.network.sendToServer(new PacketSmartPatternModify(tile.getPos(), "lock", sortedIndex));
                    }
                } else {
                    tile.toggleRecipe(sortedIndex);
                    AE2Enhanced.network.sendToServer(new PacketSmartPatternToggle(tile.getPos(), sortedIndex));
                }
                flashSlot = clickedSlot;
                flashTicks = FLASH_DURATION;
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    // ---- 碰撞检测 ----

    private boolean isInEncodeButton(int x, int y) {
        return x >= BTN_ENCODE_X && x < BTN_ENCODE_X + BTN_SIZE
            && y >= BTN_ENCODE_Y && y < BTN_ENCODE_Y + BTN_SIZE;
    }

    private boolean isInPrevButton(int x, int y) {
        return x >= BTN_PREV_X && x < BTN_PREV_X + BTN_SIZE
            && y >= BTN_PREV_Y && y < BTN_PREV_Y + BTN_PAGE_H;
    }

    private boolean isInNextButton(int x, int y) {
        return x >= BTN_NEXT_X && x < BTN_NEXT_X + BTN_SIZE
            && y >= BTN_NEXT_Y && y < BTN_NEXT_Y + BTN_PAGE_H;
    }

    private boolean isInDeleteDisabledButton(int x, int y) {
        return x >= BTN_DELETE_DISABLED_X && x < BTN_DELETE_DISABLED_X + BTN_DELETE_DISABLED_W
            && y >= BTN_DELETE_DISABLED_Y && y < BTN_DELETE_DISABLED_Y + BTN_DELETE_DISABLED_H;
    }

    private int getSmallButtonAt(int x, int y) {
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 8; col++) {
                int bx = SMALL_BTN_X[col];
                int by = SMALL_BTN_Y[row];
                if (x >= bx && x < bx + SMALL_BTN_SIZE && y >= by && y < by + SMALL_BTN_SIZE) {
                    return row * 8 + col;
                }
            }
        }
        return -1;
    }

    private boolean isInArrowButton(int x, int y) {
        return x >= BTN_ARROW_X && x < BTN_ARROW_X + 16
            && y >= BTN_ARROW_Y && y < BTN_ARROW_Y + 16;
    }

    private boolean isInKeepButton(int x, int y) {
        return x >= BTN_KEEP_X && x < BTN_KEEP_X + BTN_KEEP_W
            && y >= BTN_KEEP_Y && y < BTN_KEEP_Y + BTN_KEEP_H;
    }

    private boolean isInRecipeGrid(int x, int y) {
        return x >= COL_X[0] && x < COL_X[8] + 16
            && y >= ROW_Y[0] && y < ROW_Y[4] + 15;
    }

    private boolean isInMiniGuiArea(int x, int y) {
        // 输入区 178-230, 19-71 + 滚动条 232-244
        return (x >= 178 && x < 244 && y >= 19 && y < 71)
            || (x >= 178 && x < 230 && y >= 104 && y < 156);
    }

    private int getRecipeSlotAt(int x, int y) {
        int slotIndex = 0;
        for (int row = 0; row < ROW_Y.length; row++) {
            for (int col = 0; col < COL_X.length; col++) {
                int sx = COL_X[col];
                int sy = ROW_Y[row];
                if (x >= sx && x < sx + 16 && y >= sy && y < sy + 15) {
                    return slotIndex;
                }
                slotIndex++;
            }
        }
        return -1;
    }

    // ---- MiniGUI 滚动条 ----

    private void drawMiniGuiScrollBar() {
        if (tile.getLockedRecipeIndex() < 0) return;
        this.mc.getTextureManager().bindTexture(SCROLLBAR_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int range = 8; // max - min = 8 - 0
        if (range == 0) {
            this.drawTexturedModalRect(this.guiLeft + SCROLLBAR_X, this.guiTop + SCROLLBAR_Y,
                232 + SCROLLBAR_WIDTH, 0, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
        } else {
            int offset = miniGuiScrollOffset * (SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT) / range;
            this.drawTexturedModalRect(this.guiLeft + SCROLLBAR_X, this.guiTop + SCROLLBAR_Y + offset,
                232, 0, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
        }
    }

    private int handleMiniGuiScrollClick(int x, int y) {
        if (x > SCROLLBAR_X && x <= SCROLLBAR_X + SCROLLBAR_WIDTH
                && y > SCROLLBAR_Y && y <= SCROLLBAR_Y + SCROLLBAR_HEIGHT) {
            int range = 8;
            int relativeY = y - SCROLLBAR_Y;
            int newScroll = relativeY * 2 * range / SCROLLBAR_HEIGHT;
            newScroll = (newScroll + 1) >> 1;
            return Math.max(0, Math.min(8, newScroll));
        }
        return miniGuiScrollOffset;
    }
}
