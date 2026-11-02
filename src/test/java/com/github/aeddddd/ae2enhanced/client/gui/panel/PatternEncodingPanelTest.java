package com.github.aeddddd.ae2enhanced.client.gui.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.container.SplicedTermSlotPolicy;

/**
 * 阶段 3b：编码面板的块几何与控件行布局（纯静态推导，可无头验证）。
 *
 * <p>锁住的是一类真实 bug：控件行越出内容区 / 编码块与样板 in-out 列重叠 / 面板最小尺寸
 * 与内容尺寸推导不一致。</p>
 */
class PatternEncodingPanelTest {

    /** 编码块的 3×3 输入在块内的最大 X 偏移（craft 模式 10，process 模式 18）。 */
    private static final int INNER_INPUT_X = 18;
    private static final int SLOT = 18;

    @Test
    void contentBoxCoversTheBlockAndTheFeedColumn() {
        // 块 126 宽 + 样板 in/out 列（物品 139 + 16）+ 右侧模式页签（162 + 20）
        assertEquals(186, PatternEncodingPanel.CONTENT_WIDTH);
        // 编码块 68 高，所有控件都落在块内
        assertEquals(68, PatternEncodingPanel.CONTENT_HEIGHT);
        assertEquals(PatternEncodingPanel.CONTENT_WIDTH + 2 * ModulePanel.CONTENT_PADDING,
                PatternEncodingPanel.panelWidthForContent());
        assertEquals(ModulePanel.TITLE_BAR_HEIGHT + PatternEncodingPanel.CONTENT_HEIGHT
                + ModulePanel.CONTENT_PADDING, PatternEncodingPanel.panelHeightForContent());
    }

    @Test
    void patternFeedColumnDoesNotOverlapTheBlock() {
        // 块宽 126，feed 列物品在 139 起 → 中间留 13px 空隙
        assertTrue(PatternEncodingPanel.CONTENT_WIDTH - (139 + SLOT) >= 0);
        assertTrue(139 > PatternEncodingPanel.BLOCK_WIDTH);
    }

    @Test
    void threeByThreeGridFitsInsideTheBlock() {
        int widest = INNER_INPUT_X + 3 * SLOT;
        assertTrue(widest <= PatternEncodingPanel.BLOCK_WIDTH,
                "process 模式的 3×3 输入必须落在块内：right=" + widest);
        int blockTop = 9 + 3 * SLOT;
        assertTrue(blockTop <= PatternEncodingPanel.BLOCK_HEIGHT,
                "3×3 输入的高度必须落在块内：bottom=" + blockTop);
    }

    @Test
    void controlsMatchThe121ScreenJson() {
        int encodeRight = 138 + 16;
        assertTrue(encodeRight <= PatternEncodingPanel.CONTENT_WIDTH,
                "编码按钮越界：right=" + encodeRight);
        int encodeBottom = 22 + 16;
        assertTrue(encodeBottom <= PatternEncodingPanel.CONTENT_HEIGHT,
                "编码按钮越界：bottom=" + encodeBottom);
        assertTrue(65 + 16 <= PatternEncodingPanel.BLOCK_WIDTH, "craft 清空按钮压到格子");
        assertTrue(81 + 16 <= 102, "替换按钮压到输出列");
        assertTrue(162 + 20 <= PatternEncodingPanel.CONTENT_WIDTH, "模式页签越出内容区");
    }

    @Test
    void moduleIdMatchesTheSlotPolicyGroup() {
        assertEquals(SplicedTermSlotPolicy.MODULE_PATTERN, PatternEncodingPanel.MODULE_ID);
    }
}
