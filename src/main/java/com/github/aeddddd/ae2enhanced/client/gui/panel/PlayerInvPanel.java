package com.github.aeddddd.ae2enhanced.client.gui.panel;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;

import com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.container.SplicedTermSlotPolicy;

/**
 * 玩家背包模块：承接 {@code ContainerOmniTerm.addCustomPlayerInventory} 创建的 36 个槽位
 * （27 主背包 + 9 快捷栏），在面板内容区重排为 9×4 网格。
 *
 * <p>槽位注册顺序与索引完全不动，只是由面板在每次投影重建时写 xPos/yPos（客户端行为）。
 * 槽位底图自绘（不依赖旧 omnigui.png 的固定槽带），风格沿用 omnigui 的槽带规格：
 * 1px 分隔线 {@code 154,159,180} + 槽底 {@code 173,176,196}。</p>
 */
public final class PlayerInvPanel extends SlotHostPanel {
    public static final String MODULE_ID = SplicedTermSlotPolicy.MODULE_PLAYER;

    /** 槽带配色（参照 omnigui 的槽带规格）。 */
    private static final int SLOT_SEPARATOR = 0xFF9A9FB4;
    private static final int SLOT_BG = 0xFFADB0C4;

    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 9;
    private static final int ROWS = 4;

    public PlayerInvPanel(ContainerSplicedTerm container) {
        super(container);
        addSlotGroup(MODULE_ID, container.playerInventorySlots());
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.spliced_term.playerinv");
    }

    /** 玩家背包是物品出入的唯一通道，不可关闭。 */
    @Override
    public boolean closable() {
        return false;
    }

    @Override
    public int minWidth() {
        return contentWidthFor(COLUMNS);
    }

    @Override
    public int defaultWidth() {
        return contentWidthFor(COLUMNS);
    }

    @Override
    public int minHeight() {
        return contentHeightFor(ROWS);
    }

    @Override
    public int defaultHeight() {
        return contentHeightFor(ROWS);
    }

    private static int contentWidthFor(int columns) {
        return columns * SLOT_SIZE + 2 * CONTENT_PADDING;
    }

    private static int contentHeightFor(int rows) {
        return TITLE_BAR_HEIGHT + rows * SLOT_SIZE + CONTENT_PADDING;
    }

    @Override
    public void layoutSlots() {
        if (!prepareLayout()) {
            return;
        }
        List<Slot> slots = container.playerInventorySlots();
        for (int index = 0; index < slots.size(); index++) {
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            // 槽位本身占 16x16（物品区），外面留 1px 作为槽带分隔线
            placeSlot(slots.get(index),
                    contentLeft() + column * SLOT_SIZE + 1,
                    contentTop() + row * SLOT_SIZE + 1);
        }
    }

    @Override
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        if (!visible) {
            return;
        }
        drawSlotBand(contentLeft(), contentTop(), COLUMNS, ROWS, zLevel);
    }

    /**
     * 画一块 {@code columns × rows} 的槽带：每格 18×18，用 AE2 现代版的槽格样式
     * （左上 1px 深色凹陷 + 右下 1px 高光 + 8B8B8B 底；色值取自 {@code guis-modern/terminal.png} 采样）。
     */
    static void drawSlotBand(int left, int top, int columns, int rows, float zLevel) {
        NinePatch.drawSlotBand(left, top, columns, rows, zLevel);
    }
}
