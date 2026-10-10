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

    /** 编码块的 3×3 输入在块内的最大 X 偏移（craft 模式 7，process 模式 16）。 */
    private static final int INNER_INPUT_X = 16;
    private static final int SLOT = 18;

    @Test
    void contentBoxCoversTheBlockAndTheFeedColumn() {
        // 块 124 宽 + 样板 in/out 列（偏移 139 + 18）
        assertEquals(157, PatternEncodingPanel.CONTENT_WIDTH);
        // 块 66 高 + 倍率行（y=68，16px 按钮）+ 动作行（y=86，22px tab）
        assertEquals(108, PatternEncodingPanel.CONTENT_HEIGHT);
        assertEquals(PatternEncodingPanel.CONTENT_WIDTH + 2 * ModulePanel.CONTENT_PADDING,
                PatternEncodingPanel.panelWidthForContent());
        assertEquals(ModulePanel.TITLE_BAR_HEIGHT + PatternEncodingPanel.CONTENT_HEIGHT
                + ModulePanel.CONTENT_PADDING, PatternEncodingPanel.panelHeightForContent());
    }

    @Test
    void patternFeedColumnDoesNotOverlapTheBlock() {
        // 块宽 124，feed 列在 139 起 → 中间留 15px 空隙
        assertTrue(PatternEncodingPanel.CONTENT_WIDTH - (139 + SLOT) >= 0);
        assertTrue(139 > PatternEncodingPanel.BLOCK_WIDTH);
    }

    @Test
    void threeByThreeGridFitsInsideTheBlock() {
        int widest = INNER_INPUT_X + 3 * SLOT;
        assertTrue(widest <= PatternEncodingPanel.BLOCK_WIDTH,
                "process 模式的 3×3 输入必须落在块内：right=" + widest);
        int blockTop = 7 + 3 * SLOT;
        assertTrue(blockTop <= PatternEncodingPanel.BLOCK_HEIGHT,
                "3×3 输入的高度必须落在块内：bottom=" + blockTop);
    }

    @Test
    void controlRowsFitTheContentWidth() {
        // 倍率行：6 个半尺寸按钮，间距 10
        int multiplierRight = (PatternEncodingPanel.MULTIPLIER_COUNT - 1) * 10 + 8;
        assertTrue(multiplierRight <= PatternEncodingPanel.CONTENT_WIDTH,
                "倍率行越界：right=" + multiplierRight);
        // 动作行：tab(22) + 3 个 16px 按钮
        int actionRight = 24 + (PatternEncodingPanel.ACTION_BUTTON_COUNT - 1) * 18 + 16;
        assertTrue(actionRight <= PatternEncodingPanel.CONTENT_WIDTH,
                "动作行越界：right=" + actionRight);
    }

    @Test
    void moduleIdMatchesTheSlotPolicyGroup() {
        assertEquals(SplicedTermSlotPolicy.MODULE_PATTERN, PatternEncodingPanel.MODULE_ID);
    }
}
