package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用内存卡管理 GUI(使用 UMCGUI.png 纹理,支持条带拼接与滚动条).
 */
public class GuiUniversalMemoryCard extends GuiContainer {

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/umc_gui.png");

    // GUI 内容区域尺寸(与纹理内容区 195×251 一致)
    private static final int GUI_WIDTH = 195;
    private static final int GUI_HEIGHT = 251;

    // 列表项参数(条带高 18,与纹理匹配)
    private static final int VISIBLE_COUNT = 8;
    private static final int ENTRY_HEIGHT = 18;
    private static final int LIST_Y = 57;
    private static final int LIST_HEIGHT = VISIBLE_COUNT * ENTRY_HEIGHT;

    // 文字配色
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xDDDDDD;
    private static final int COLOR_TEXT_MUTED = 0x888888;

    // 滚动条滑块配色
    private static final int COLOR_SCROLL_THUMB = 0xFF3A8EBF;

    private final EntityPlayer player;
    private boolean hasConfig = false;
    private String configName = "";
    private int upgradeCount = 0;
    private List<ItemUniversalMemoryCard.SelectionEntry> selections = new ArrayList<>();

    // 滚动条状态
    private int scrollIndex = 0;
    private boolean isDraggingThumb = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

    public GuiUniversalMemoryCard(EntityPlayer player) {
        super(new com.github.aeddddd.ae2enhanced.container.ContainerUniversalMemoryCard(player));
        this.player = player;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        refreshData();
        clampScroll();

        this.buttonList.clear();
        // 底部按钮：左(18,222) 右(111,222) 均 52×17
        this.buttonList.add(new GuiModernButton(0, this.guiLeft + 18, this.guiTop + 222, 52, 17,
                I18n.format("gui.ae2enhanced.umc.btn.clear_config")));
        this.buttonList.add(new GuiModernButton(1, this.guiLeft + 111, this.guiTop + 222, 52, 17,
                I18n.format("gui.ae2enhanced.umc.btn.clear_selections")));

        // 删除按钮(X),与列表项同步：纹理中 X 在(160,62),尺寸 8×9,图标已由纹理提供
        for (int i = 0; i < VISIBLE_COUNT; i++) {
            GuiModernButton btn = new GuiModernButton(2 + i, this.guiLeft + 160, this.guiTop + 62 + i * ENTRY_HEIGHT, 8, 9, "");
            btn.visible = false;
            this.buttonList.add(btn);
        }
    }

    private void refreshData() {
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() instanceof ItemUniversalMemoryCard) {
            hasConfig = ItemUniversalMemoryCard.hasConfig(stack);
            if (hasConfig) {
                NBTTagCompound config = ItemUniversalMemoryCard.getConfig(stack);
                configName = config.getString("name");
                NBTTagCompound data = config.getCompoundTag("data");
                upgradeCount = data.hasKey("ae2e:upgrades") ? data.getTagList("ae2e:upgrades", 10).tagCount() : 0;
            } else {
                configName = "";
                upgradeCount = 0;
            }
            selections = ItemUniversalMemoryCard.getSelections(stack);
        }
    }

    private void clampScroll() {
        if (scrollIndex < 0) scrollIndex = 0;
        int max = Math.max(0, selections.size() - VISIBLE_COUNT);
        if (scrollIndex > max) scrollIndex = max;
    }

    private int getScrollBarX() {
        return this.guiLeft + 178;
    }

    private int getScrollBarY() {
        return this.guiTop + 53;
    }

    private int getThumbHeight() {
        if (selections.size() <= VISIBLE_COUNT) return LIST_HEIGHT;
        return Math.max(16, LIST_HEIGHT * VISIBLE_COUNT / selections.size());
    }

    private int getThumbY() {
        int scrollBarY = getScrollBarY();
        if (selections.size() <= VISIBLE_COUNT) return scrollBarY;
        int maxScroll = selections.size() - VISIBLE_COUNT;
        return scrollBarY + scrollIndex * (LIST_HEIGHT - getThumbHeight()) / maxScroll;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_CONFIG, -1));
                break;
            case 1:
                AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_SELECTIONS, -1));
                break;
            default:
                int visibleIdx = button.id - 2;
                int actualIndex = scrollIndex + visibleIdx;
                if (actualIndex >= 0 && actualIndex < selections.size()) {
                    AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.REMOVE_SELECTION, actualIndex));
                }
                break;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() instanceof ItemUniversalMemoryCard) {
            int currentCount = ItemUniversalMemoryCard.getSelectionCount(stack);
            boolean currentHasConfig = ItemUniversalMemoryCard.hasConfig(stack);
            if (currentCount != selections.size() || currentHasConfig != hasConfig) {
                this.initGui();
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = org.lwjgl.input.Mouse.getEventDWheel();
        if (delta != 0) {
            scrollIndex -= Integer.signum(delta);
            clampScroll();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int sbX = getScrollBarX();
        int sbY = getScrollBarY();
        if (mouseX >= sbX && mouseX < sbX + 6 &&
            mouseY >= sbY && mouseY < sbY + LIST_HEIGHT &&
            selections.size() > VISIBLE_COUNT) {
            int thumbY = getThumbY();
            int thumbH = getThumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                isDraggingThumb = true;
                dragStartY = mouseY;
                dragStartScroll = scrollIndex;
            } else if (mouseY < thumbY) {
                scrollIndex -= VISIBLE_COUNT;
                clampScroll();
            } else {
                scrollIndex += VISIBLE_COUNT;
                clampScroll();
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (isDraggingThumb && selections.size() > VISIBLE_COUNT) {
            int thumbH = getThumbHeight();
            int maxScroll = selections.size() - VISIBLE_COUNT;
            int deltaPixels = mouseY - dragStartY;
            int deltaSlots = deltaPixels * maxScroll / (LIST_HEIGHT - thumbH);
            scrollIndex = dragStartScroll + deltaSlots;
            clampScroll();
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        isDraggingThumb = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDefaultBackground();
        refreshData();
        clampScroll();

        int x = this.guiLeft;
        int y = this.guiTop;

        Minecraft.getMinecraft().getTextureManager().bindTexture(GUI_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // 1. 绘制整个 GUI 纹理作为背景(包含标题栏、配置栏、默认列表项、滚动条、按钮)
        drawTexturedModalRect(x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // 2. 覆盖列表项区域：用条带正下方的纯色纹理片段向上平移覆盖,
        //    保证横向纹理坐标完全一致,避免错位
        for (int i = 0; i < VISIBLE_COUNT; i++) {
            int rowY = y + 57 + i * ENTRY_HEIGHT;
            // 列表项条带覆盖：源坐标(7, 75) = 原条带(7,57) 向下平移 18
            drawTexturedModalRect(x + 7, rowY, 7, 57 + ENTRY_HEIGHT, 146, ENTRY_HEIGHT);
            // X 按钮覆盖：源坐标(160, 80) = 原X(160,62) 向下平移 18
            drawTexturedModalRect(x + 160, rowY + 5, 160, 62 + ENTRY_HEIGHT, 8, 9);
        }

        // 3. 绘制实际的列表项条带 + X 按钮(一并重复)
        int maxDisplay = Math.min(selections.size() - scrollIndex, VISIBLE_COUNT);
        for (int i = 0; i < maxDisplay; i++) {
            // 列表项条带：纹理(7,57) 146×18
            drawTexturedModalRect(x + 7, y + 57 + i * ENTRY_HEIGHT, 7, 57, 146, ENTRY_HEIGHT);
            // X 删除按钮：纹理(160,62) 8×9
            drawTexturedModalRect(x + 160, y + 62 + i * ENTRY_HEIGHT, 160, 62, 8, 9);
        }

        // 4. 滚动条滑块(代码绘制,纹理中没有专门滑块)
        if (selections.size() > VISIBLE_COUNT) {
            int thumbY = getThumbY();
            int thumbH = getThumbHeight();
            drawRect(x + 178, thumbY, x + 178 + 6, thumbY + thumbH, COLOR_SCROLL_THUMB);
        }

        // ===== 文字绘制 =====

        // 标题文字(居中于标题栏)
        String title = I18n.format("gui.ae2enhanced.umc.title");
        int titleWidth = this.fontRenderer.getStringWidth(title);
        this.fontRenderer.drawString(title, x + 2 + (191 - titleWidth) / 2, y + 2 + 6, COLOR_TEXT);

        // 配置区文字
        if (hasConfig) {
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.source", configName), x + 10, y + 30, COLOR_TEXT);
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.upgrades", upgradeCount), x + 10, y + 42, COLOR_TEXT_DIM);
        } else {
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.no_config"), x + 10, y + 30, COLOR_TEXT_MUTED);
        }

        // 选取区标题
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.selections", selections.size()), x + 10, y + 52, COLOR_TEXT);

        // 选取列表文字
        for (int i = 0; i < maxDisplay; i++) {
            ItemUniversalMemoryCard.SelectionEntry entry = selections.get(scrollIndex + i);
            String text = entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ();
            if (entry.side >= 0) {
                text += " [P]";
            }
            this.fontRenderer.drawString(text, x + 12, y + 62 + i * ENTRY_HEIGHT, COLOR_TEXT_DIM);
        }

        // 更新按钮位置和可见性
        for (int i = 0; i < VISIBLE_COUNT; i++) {
            int btnIdx = 2 + i;
            if (btnIdx < this.buttonList.size()) {
                GuiButton btn = this.buttonList.get(btnIdx);
                btn.x = x + 160;
                btn.y = y + 62 + i * ENTRY_HEIGHT;
                btn.visible = i < maxDisplay;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    // ============================================================
    // 现代半透明按钮(保留 hover 效果,文字叠加在纹理按钮上)
    // ============================================================

    public static class GuiModernButton extends GuiButton {

        private static final int COLOR_BTN_TEXT = 0xFFFFFF;
        private static final int COLOR_BTN_TEXT_DISABLED = 0x888888;

        public GuiModernButton(int buttonId, int x, int y, int width, int height, String text) {
            super(buttonId, x, y, width, height, text);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

            // 文字
            int textColor = this.enabled ? COLOR_BTN_TEXT : COLOR_BTN_TEXT_DISABLED;
            this.drawCenteredString(mc.fontRenderer, this.displayString, this.x + this.width / 2, this.y + (this.height - 8) / 2, textColor);
        }
    }
}
