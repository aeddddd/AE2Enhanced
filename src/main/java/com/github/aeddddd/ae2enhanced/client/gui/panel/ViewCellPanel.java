package com.github.aeddddd.ae2enhanced.client.gui.panel;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;

import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.container.SplicedTermSlotPolicy;

/**
 * View cell 侧栏模块：承接 5 个 view cell 槽位（AE2 标准终端右侧的 view cell 栏）。
 *
 * <p>旧 GUI 里这 5 个槽位被 {@code ContainerOmniTerm} 的构造器硬编码到 {@code xPos = 366}
 * （357 + 9 的贴图外挂 hack）；新终端里改由本面板在 {@code layoutSlots()} 里按内容区摆放，
 * 因此那个 hack 对新 GUI 不再生效（坐标是纯客户端数据，容器与服务端逻辑不受影响）。</p>
 */
public final class ViewCellPanel extends SlotHostPanel {
    public static final String MODULE_ID = SplicedTermSlotPolicy.MODULE_VIEWCELL;

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_COUNT = 5;

    public ViewCellPanel(ContainerSplicedTerm container) {
        super(container);
        addSlotGroup(MODULE_ID, container.viewCellSlots());
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.spliced_term.viewcell");
    }

    @Override
    public int minWidth() {
        return SLOT_SIZE + 2 * CONTENT_PADDING;
    }

    @Override
    public int defaultWidth() {
        return SLOT_SIZE + 2 * CONTENT_PADDING;
    }

    @Override
    public int minHeight() {
        return TITLE_BAR_HEIGHT + SLOT_COUNT * SLOT_SIZE + CONTENT_PADDING;
    }

    @Override
    public int defaultHeight() {
        return TITLE_BAR_HEIGHT + SLOT_COUNT * SLOT_SIZE + CONTENT_PADDING;
    }

    @Override
    public void layoutSlots() {
        if (!prepareLayout()) {
            return;
        }
        List<Slot> slots = container.viewCellSlots();
        for (int index = 0; index < slots.size(); index++) {
            placeSlot(slots.get(index),
                    contentLeft() + 1,
                    contentTop() + index * SLOT_SIZE + 1);
        }
    }

    @Override
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        if (!visible) {
            return;
        }
        PlayerInvPanel.drawSlotBand(contentLeft(), contentTop(), 1, SLOT_COUNT, zLevel);
        // 空槽提示图案：AE2 19.x 的 view cell 槽位图（有物品时被物品盖住）
        for (int index = 0; index < SLOT_COUNT; index++) {
            net.minecraft.inventory.Slot slot = index < container.viewCellSlots().size()
                    ? container.viewCellSlots().get(index) : null;
            if (slot != null && !slot.getStack().isEmpty()) {
                continue;
            }
            NinePatch.drawSlotPattern(Ae2ModernArt.Icon.BACKGROUND_VIEW_CELL,
                    contentLeft(), contentTop() + index * SLOT_SIZE, zLevel);
        }
    }
}
