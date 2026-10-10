package com.github.aeddddd.ae2enhanced.client.gui.panel;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;

import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;

/**
 * 升级卡模块：承接 {@code ContainerOmniTerm.setupRightStorage} 的 9 个 {@code SlotOmniUpgrade}
 * （9×1），独立成模块以便单独关闭/拼接。
 *
 * <p>白名单语义完全由 {@code SlotOmniUpgrade.isItemValid} 决定（AE2 标准升级卡 / 频道接收卡 /
 * Omni 专用升级卡，每槽 1 张），本面板只摆坐标。槽位角色 {@code BOTH}（与旧终端一致）。</p>
 */
public final class UpgradePanel extends SlotHostPanel {
    public static final String MODULE_ID = "upgrade";

    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 9;
    private static final int ROWS = 1;

    public UpgradePanel(ContainerSplicedTerm container) {
        super(container);
        addSlotGroup(MODULE_ID, container.upgradeSlots());
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.spliced_term.upgrade");
    }

    public static int contentWidthForGrid() {
        return COLUMNS * SLOT_SIZE;
    }

    public static int contentHeightForGrid() {
        return ROWS * SLOT_SIZE;
    }

    /** 面板最小宽度（网格 + 两侧内边距）。 */
    public static int panelWidthForGrid() {
        return contentWidthForGrid() + 2 * CONTENT_PADDING;
    }

    /** 面板最小高度（标题栏 + 网格 + 底部内边距）。 */
    public static int panelHeightForGrid() {
        return TITLE_BAR_HEIGHT + contentHeightForGrid() + CONTENT_PADDING;
    }

    @Override
    public int minWidth() {
        return panelWidthForGrid();
    }

    @Override
    public int defaultWidth() {
        return panelWidthForGrid();
    }

    @Override
    public int minHeight() {
        return panelHeightForGrid();
    }

    @Override
    public int defaultHeight() {
        return panelHeightForGrid();
    }

    @Override
    public void layoutSlots() {
        if (!prepareLayout()) {
            return;
        }
        List<Slot> slots = container.upgradeSlots();
        for (int index = 0; index < slots.size(); index++) {
            placeSlot(slots.get(index),
                    contentLeft() + (index % COLUMNS) * SLOT_SIZE + 1,
                    contentTop() + (index / COLUMNS) * SLOT_SIZE + 1);
        }
    }

    @Override
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        if (!visible) {
            return;
        }
        PlayerInvPanel.drawSlotBand(contentLeft(), contentTop(), COLUMNS, ROWS, zLevel);
    }
}
