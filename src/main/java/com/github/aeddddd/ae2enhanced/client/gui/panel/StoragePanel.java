package com.github.aeddddd.ae2enhanced.client.gui.panel;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;

import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;

/**
 * 右侧存储模块：承接 {@code ContainerOmniTerm.setupRightStorage} 的 27 个 {@code SlotHighCapacity}
 * （9×3），摆成与旧终端右侧一致的格子带。
 *
 * <p>槽位角色 {@code BOTH}：既能被 shift-click 填充（旧终端行为），也可以作为来源把物品
 * shift-click 回背包。高容量上限由 {@code SlotHighCapacity.getSlotStackLimit()} 提供，
 * 但单次 shift-click 的合并量仍按 AE2 原生的 {@code min(槽上限, 物品堆叠上限)} 计算。</p>
 */
public final class StoragePanel extends SlotHostPanel {
    public static final String MODULE_ID = "storage";

    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 9;
    private static final int ROWS = 3;

    public StoragePanel(ContainerSplicedTerm container) {
        super(container);
        addSlotGroup(MODULE_ID, container.storageSlots());
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.spliced_term.storage");
    }

    /** 9×3 格子带的内容尺寸。 */
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
        List<Slot> slots = container.storageSlots();
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
