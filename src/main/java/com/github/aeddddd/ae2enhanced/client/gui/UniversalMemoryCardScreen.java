package com.github.aeddddd.ae2enhanced.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.menu.UniversalMemoryCardMenu;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;

/**
 * 通用内存卡管理 GUI(使用 umc_gui.png 纹理,支持条带拼接与滚动条,移植自 1.12 GuiUniversalMemoryCard).
 */
public class UniversalMemoryCardScreen extends AbstractContainerScreen<UniversalMemoryCardMenu> {

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(AE2Enhanced.MOD_ID,
            "textures/gui/umc_gui.png");

    // GUI 内容区域尺寸(与纹理内容区 195×251 一致)
    private static final int GUI_WIDTH = 195;
    private static final int GUI_HEIGHT = 251;

    // 列表项参数(条带高 18,与纹理匹配)
    private static final int VISIBLE_COUNT = 8;
    private static final int ENTRY_HEIGHT = 18;
    private static final int LIST_HEIGHT = VISIBLE_COUNT * ENTRY_HEIGHT;

    // 文字配色
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xDDDDDD;
    private static final int COLOR_TEXT_MUTED = 0x888888;

    // 滚动条滑块配色
    private static final int COLOR_SCROLL_THUMB = 0xFF3A8EBF;

    private boolean hasConfig = false;
    private String configName = "";
    private int upgradeCount = 0;
    private List<UniversalMemoryCardItem.SelectionEntry> selections = new ArrayList<>();

    // 滚动条状态
    private int scrollIndex = 0;
    private boolean isDraggingThumb = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

    private final List<Button> deleteButtons = new ArrayList<>();

    public UniversalMemoryCardScreen(UniversalMemoryCardMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    private ItemStack getHeldCard() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        InteractionHand[] hands = InteractionHand.values();
        int ordinal = Math.min(this.menu.handOrdinal, hands.length - 1);
        return mc.player.getItemInHand(hands[ordinal]);
    }

    @Override
    protected void init() {
        super.init();
        refreshData();
        clampScroll();

        // 底部按钮:左(18,222) 右(111,222) 均 52×17(按钮背景由纹理提供,仅绘制文字)
        addRenderableWidget(new TextOnlyButton(this.leftPos + 18, this.topPos + 222, 52, 17,
                Component.translatable("gui.ae2enhanced.umc.btn.clear_config"),
                btn -> ModNetwork.CHANNEL.sendToServer(
                        new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_CONFIG, -1))));
        addRenderableWidget(new TextOnlyButton(this.leftPos + 111, this.topPos + 222, 52, 17,
                Component.translatable("gui.ae2enhanced.umc.btn.clear_selections"),
                btn -> ModNetwork.CHANNEL.sendToServer(
                        new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_SELECTIONS, -1))));

        // 删除按钮(X),与列表项同步:纹理中 X 在(160,62),尺寸 8×9,图标已由纹理提供
        deleteButtons.clear();
        for (int i = 0; i < VISIBLE_COUNT; i++) {
            final int visibleIdx = i;
            Button btn = new TextOnlyButton(this.leftPos + 160, this.topPos + 62 + i * ENTRY_HEIGHT, 8, 9,
                    Component.empty(), b -> {
                        int actualIndex = scrollIndex + visibleIdx;
                        if (actualIndex >= 0 && actualIndex < selections.size()) {
                            ModNetwork.CHANNEL.sendToServer(
                                    new PacketUMCAction(PacketUMCAction.ActionType.REMOVE_SELECTION, actualIndex));
                        }
                    });
            btn.visible = false;
            deleteButtons.add(btn);
            addRenderableWidget(btn);
        }
    }

    private void refreshData() {
        ItemStack stack = getHeldCard();
        if (stack.getItem() instanceof UniversalMemoryCardItem) {
            hasConfig = UniversalMemoryCardItem.hasConfig(stack);
            if (hasConfig) {
                CompoundTag config = UniversalMemoryCardItem.getConfig(stack);
                configName = config.getString("name");
                CompoundTag data = config.getCompound("data");
                upgradeCount = data.contains("ae2e:upgrades")
                        ? data.getList("ae2e:upgrades", Tag.TAG_COMPOUND).size()
                        : 0;
            } else {
                configName = "";
                upgradeCount = 0;
            }
            selections = UniversalMemoryCardItem.getSelections(stack);
        }
    }

    private void clampScroll() {
        if (scrollIndex < 0) {
            scrollIndex = 0;
        }
        int max = Math.max(0, selections.size() - VISIBLE_COUNT);
        if (scrollIndex > max) {
            scrollIndex = max;
        }
    }

    private int getScrollBarX() {
        return this.leftPos + 178;
    }

    private int getScrollBarY() {
        return this.topPos + 53;
    }

    private int getThumbHeight() {
        if (selections.size() <= VISIBLE_COUNT) {
            return LIST_HEIGHT;
        }
        return Math.max(16, LIST_HEIGHT * VISIBLE_COUNT / selections.size());
    }

    private int getThumbY() {
        int scrollBarY = getScrollBarY();
        if (selections.size() <= VISIBLE_COUNT) {
            return scrollBarY;
        }
        int maxScroll = selections.size() - VISIBLE_COUNT;
        return scrollBarY + scrollIndex * (LIST_HEIGHT - getThumbHeight()) / maxScroll;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack stack = getHeldCard();
        if (!(stack.getItem() instanceof UniversalMemoryCardItem)) {
            // 内存卡不在手中时关闭界面
            Minecraft.getInstance().player.closeContainer();
            return;
        }
        int currentCount = UniversalMemoryCardItem.getSelectionCount(stack);
        boolean currentHasConfig = UniversalMemoryCardItem.hasConfig(stack);
        if (currentCount != selections.size() || currentHasConfig != hasConfig) {
            this.rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            scrollIndex -= (int) Math.signum(delta);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int sbX = getScrollBarX();
        int sbY = getScrollBarY();
        if (mouseX >= sbX && mouseX < sbX + 6
                && mouseY >= sbY && mouseY < sbY + LIST_HEIGHT
                && selections.size() > VISIBLE_COUNT) {
            int thumbY = getThumbY();
            int thumbH = getThumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                isDraggingThumb = true;
                dragStartY = (int) mouseY;
                dragStartScroll = scrollIndex;
            } else if (mouseY < thumbY) {
                scrollIndex -= VISIBLE_COUNT;
                clampScroll();
            } else {
                scrollIndex += VISIBLE_COUNT;
                clampScroll();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingThumb && selections.size() > VISIBLE_COUNT) {
            int thumbH = getThumbHeight();
            int maxScroll = selections.size() - VISIBLE_COUNT;
            int deltaPixels = (int) mouseY - dragStartY;
            int deltaSlots = deltaPixels * maxScroll / (LIST_HEIGHT - thumbH);
            scrollIndex = dragStartScroll + deltaSlots;
            clampScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingThumb = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        refreshData();
        clampScroll();

        int x = this.leftPos;
        int y = this.topPos;

        // 1. 绘制整个 GUI 纹理作为背景(包含标题栏、配置栏、默认列表项、滚动条、按钮)
        graphics.blit(GUI_TEXTURE, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // 2. 覆盖列表项区域:用条带正下方的纯色纹理片段向上平移覆盖,
        //    保证横向纹理坐标完全一致,避免错位
        for (int i = 0; i < VISIBLE_COUNT; i++) {
            int rowY = y + 57 + i * ENTRY_HEIGHT;
            // 列表项条带覆盖:源坐标(7, 75) = 原条带(7,57) 向下平移 18
            graphics.blit(GUI_TEXTURE, x + 7, rowY, 7, 57 + ENTRY_HEIGHT, 146, ENTRY_HEIGHT);
            // X 按钮覆盖:源坐标(160, 80) = 原X(160,62) 向下平移 18
            graphics.blit(GUI_TEXTURE, x + 160, rowY + 5, 160, 62 + ENTRY_HEIGHT, 8, 9);
        }

        // 3. 绘制实际的列表项条带 + X 按钮(一并重复)
        int maxDisplay = Math.min(selections.size() - scrollIndex, VISIBLE_COUNT);
        for (int i = 0; i < maxDisplay; i++) {
            // 列表项条带:纹理(7,57) 146×18
            graphics.blit(GUI_TEXTURE, x + 7, y + 57 + i * ENTRY_HEIGHT, 7, 57, 146, ENTRY_HEIGHT);
            // X 删除按钮:纹理(160,62) 8×9
            graphics.blit(GUI_TEXTURE, x + 160, y + 62 + i * ENTRY_HEIGHT, 160, 62, 8, 9);
        }

        // 4. 滚动条滑块(代码绘制,纹理中没有专门滑块)
        if (selections.size() > VISIBLE_COUNT) {
            int thumbY = getThumbY();
            int thumbH = getThumbHeight();
            graphics.fill(x + 178, thumbY, x + 178 + 6, thumbY + thumbH, COLOR_SCROLL_THUMB);
        }

        // 更新删除按钮位置和可见性
        for (int i = 0; i < VISIBLE_COUNT && i < deleteButtons.size(); i++) {
            Button btn = deleteButtons.get(i);
            btn.setX(x + 160);
            btn.setY(y + 62 + i * ENTRY_HEIGHT);
            btn.visible = i < maxDisplay;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题文字(居中于标题栏)
        Component title = Component.translatable("gui.ae2enhanced.umc.title");
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, 2 + (191 - titleWidth) / 2, 8, COLOR_TEXT, false);

        // 配置区文字
        if (hasConfig) {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.umc.source", configName), 10, 30,
                    COLOR_TEXT, false);
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.umc.upgrades", upgradeCount), 10,
                    42, COLOR_TEXT_DIM, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.umc.no_config"), 10, 30,
                    COLOR_TEXT_MUTED, false);
        }

        // 选取区标题
        graphics.drawString(this.font, Component.translatable("gui.ae2enhanced.umc.selections", selections.size()),
                10, 52, COLOR_TEXT, false);

        // 选取列表文字
        int maxDisplay = Math.min(selections.size() - scrollIndex, VISIBLE_COUNT);
        for (int i = 0; i < maxDisplay; i++) {
            UniversalMemoryCardItem.SelectionEntry entry = selections.get(scrollIndex + i);
            String text = entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ();
            if (entry.side >= 0) {
                text += " [P]";
            }
            graphics.drawString(this.font, text, 12, 62 + i * ENTRY_HEIGHT, COLOR_TEXT_DIM, false);
        }
    }

    /**
     * 现代半透明按钮(保留 hover 效果,文字叠加在纹理按钮上,移植自 1.12 GuiModernButton).
     */
    private static class TextOnlyButton extends Button {

        private static final int COLOR_BTN_TEXT = 0xFFFFFF;
        private static final int COLOR_BTN_TEXT_HOVER = 0xFF9FD8F0;

        TextOnlyButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            int textColor = this.isHoveredOrFocused() ? COLOR_BTN_TEXT_HOVER : COLOR_BTN_TEXT;
            graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                    this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);
        }
    }
}
