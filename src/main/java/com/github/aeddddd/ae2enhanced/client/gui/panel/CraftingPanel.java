package com.github.aeddddd.ae2enhanced.client.gui.panel;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;

import com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.container.SplicedTermSlotPolicy;

/**
 * 合成模块：承接 {@code ContainerOmniTerm.setupCraftingArea} 的 9 个 {@code SlotCraftingMatrix}
 * 与 1 个 {@code SlotCraftingTerm} 输出槽，摆成 3×3 + 输出的经典合成台形态。
 *
 * <p>合成预览（{@code updateRealCraftingRecipe}）完全在容器侧：{@code craftingInv} 的
 * 内容变更回调会重算配方并把结果写进输出槽，再经 {@code detectAndSendChanges} 同步到客户端。
 * 面板只负责坐标与底图，因此这条链在新终端里照常工作。</p>
 *
 * <p>槽位角色：矩阵是 {@code SOURCE_ONLY}（可以 shift-click 收回，但不会被 shift-click 填充，
 * 与旧终端 AE2 原生语义一致）；输出槽是 {@code NONE}——它的 shift-click 由 AE2 的
 * {@code CRAFT_SHIFT} 动作处理，若落进槽位转移会变成"产出进背包但材料不消耗"。</p>
 */
public final class CraftingPanel extends SlotHostPanel {
    public static final String MODULE_ID = SplicedTermSlotPolicy.MODULE_CRAFTING_MATRIX;

    private static final int SLOT_SIZE = 18;
    private static final int MATRIX_COLUMNS = 3;
    private static final int MATRIX_ROWS = 3;
    /** 矩阵与输出槽之间的间隔（比格子间隔宽，视觉上区分"输入/产出"）。 */
    private static final int OUTPUT_GAP = 12;

    public CraftingPanel(ContainerSplicedTerm container) {
        super(container);
        addSlotGroup(MODULE_ID, container.craftingMatrixSlots());
        Slot output = container.craftingOutputSlot();
        addSlotGroup(SplicedTermSlotPolicy.MODULE_CRAFTING_OUTPUT,
                output == null ? null : java.util.Collections.singletonList(output));
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.spliced_term.crafting");
    }

    /** 3×3 矩阵 + 间隔 + 输出列所需的内容宽度。 */
    public static int contentWidthForGrid() {
        return MATRIX_COLUMNS * SLOT_SIZE + OUTPUT_GAP + SLOT_SIZE;
    }

    /** 3×3 矩阵所需的内容高度。 */
    public static int contentHeightForGrid() {
        return MATRIX_ROWS * SLOT_SIZE;
    }

    /** 输出槽相对内容区左边缘的偏移（单元格原点）。 */
    public static int outputCellOffsetX() {
        return MATRIX_COLUMNS * SLOT_SIZE + OUTPUT_GAP;
    }

    /**
     * 清空合成台按钮（AE2 {@code ActionItems.CLOSE}）的位置：输出槽正下方。
     *
     * <p>清空动作走 {@code PacketOmniTermAction("ClearCrafting")}（服务端已实现），
     * 与旧终端编码区左侧那颗 Clear 按钮等价。</p>
     */
    public int clearButtonX() {
        return contentLeft() + outputCellOffsetX();
    }

    public int clearButtonY() {
        return contentTop() + 2 * SLOT_SIZE + 1;
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
        List<Slot> matrix = container.craftingMatrixSlots();
        for (int index = 0; index < matrix.size(); index++) {
            int column = index % MATRIX_COLUMNS;
            int row = index / MATRIX_COLUMNS;
            placeSlot(matrix.get(index),
                    contentLeft() + column * SLOT_SIZE + 1,
                    contentTop() + row * SLOT_SIZE + 1);
        }
        Slot output = container.craftingOutputSlot();
        if (output != null) {
            // 输出槽与矩阵中间一行对齐
            placeSlot(output,
                    contentLeft() + outputCellOffsetX() + 1,
                    contentTop() + SLOT_SIZE + 1);
        }
    }

    @Override
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        if (!visible) {
            return;
        }
        PlayerInvPanel.drawSlotBand(contentLeft(), contentTop(), MATRIX_COLUMNS, MATRIX_ROWS, zLevel);
        PlayerInvPanel.drawSlotBand(contentLeft() + outputCellOffsetX(), contentTop() + SLOT_SIZE,
                1, 1, zLevel);
        // 矩阵 → 输出：AE2 的合成箭头（crafting.png src 83,109,43,10），垂直居中于两格之间
        NinePatch.drawCraftingArrow(contentLeft() + MATRIX_COLUMNS * 18 + 1,
                contentTop() + SLOT_SIZE + 4, OUTPUT_GAP - 2, 10, zLevel);
    }
}
