package com.github.aeddddd.ae2enhanced.guide.layout;

import com.github.aeddddd.ae2enhanced.guide.GuideBook;
import com.github.aeddddd.ae2enhanced.guide.GuidePage;
import com.github.aeddddd.ae2enhanced.guide.md.element.BlockElement;
import com.github.aeddddd.ae2enhanced.guide.md.element.InlineElement;
import net.minecraft.client.gui.FontRenderer;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式排版引擎 —— GuideME LineBuilder 核心算法的 1.12.2 简化移植.
 * <p>
 * 保留：空白折叠、逐字符 advance 累加、BreakIterator 断行机会、
 * 无断点强制词中断行、图标原子不可拆分、行高取最大值。
 * 砍掉：浮动（float）、fontScale、文本对齐。
 */
public final class FlowLayouter {

    /** 图标原子宽度（16px 物品图标） */
    private static final int ICON_SIZE = 16;
    /** 段落/列表项后间距 */
    private static final int PARAGRAPH_SPACING = 4;
    /** 列表项缩进 */
    private static final int LIST_INDENT = 12;

    private FlowLayouter() {}

    /**
     * 排版结果.
     */
    public static final class LayoutResult {
        public final List<LayoutLine> lines = new ArrayList<>();
        public final Map<String, Integer> anchorYs = new HashMap<>();
        public int totalHeight;
    }

    /**
     * 行排版中间状态.
     */
    private static final class FlowState {
        final List<LayoutLine.Atom> atoms = new ArrayList<>();
        final StringBuilder buffer = new StringBuilder();
        InlineElement bufferSource;   // 当前缓冲归属的内联元素（样式与命中来源）
        int currentX;
        int bufferWidth;
        int y;
        boolean lastCharWasWhitespace = true; // 行首视作空白后，折叠行首空白
    }

    /**
     * 对整页排版.
     */
    public static LayoutResult layout(GuidePage page, FontRenderer fr, int width) {
        LayoutResult result = new LayoutResult();
        int y = 0;
        boolean first = true;

        for (BlockElement block : page.getBlocks()) {
            int blockWidth = width;
            int xOffset = 0;
            if (block.getType() == BlockElement.Type.LIST_ITEM) {
                blockWidth = width - LIST_INDENT;
                xOffset = LIST_INDENT;
            }

            // 块前间距
            if (!first) {
                y += block.getType() == BlockElement.Type.HEADING && block.getHeadingDepth() > 1 ? 6 : 2;
            }
            first = false;

            // 标题锚点
            if (block.getType() == BlockElement.Type.HEADING) {
                String anchor = GuideBook.toAnchor(block.getPlainText().trim());
                if (!anchor.isEmpty()) {
                    result.anchorYs.putIfAbsent(anchor, y);
                }
            }

            List<LayoutLine> blockLines;
            if (block.getType() == BlockElement.Type.CODE_BLOCK) {
                // 围栏代码块：预格式逐行渲染，不换行、不解析内联
                blockLines = new ArrayList<>();
                int lineY = y;
                for (String codeLine : block.getCodeLines()) {
                    LayoutLine line = new LayoutLine(lineY, fr.FONT_HEIGHT);
                    if (!codeLine.isEmpty()) {
                        line.atoms.add(new LayoutLine.Atom(0, fr.getStringWidth(codeLine), codeLine, false, null));
                    }
                    blockLines.add(line);
                    lineY += fr.FONT_HEIGHT;
                }
                if (blockLines.isEmpty()) {
                    blockLines.add(new LayoutLine(y, fr.FONT_HEIGHT));
                }
            } else {
                blockLines = layoutFlow(block.getChildren(), fr, blockWidth, y, xOffset);
                if (blockLines.isEmpty()) {
                    blockLines.add(new LayoutLine(y, fr.FONT_HEIGHT));
                }
            }
            // 标记行所属块类型（渲染层据此选择标题/正文/代码样式）
            for (LayoutLine line : blockLines) {
                line.blockType = block.getType();
                line.headingDepth = block.getHeadingDepth();
            }

            // 列表项：首行加序号/项目符号（相对块原点负偏移）
            if (block.getType() == BlockElement.Type.LIST_ITEM) {
                String bullet = (block.getMarker() != null ? block.getMarker() : "•") + " ";
                LayoutLine firstLine = blockLines.get(0);
                int bulletWidth = fr.getStringWidth(bullet);
                firstLine.atoms.add(0, new LayoutLine.Atom(-bulletWidth, bulletWidth, bullet, false, null));
            }

            result.lines.addAll(blockLines);
            int blockHeight = 0;
            for (LayoutLine line : blockLines) {
                blockHeight += line.height;
            }
            y += blockHeight + (block.getType() == BlockElement.Type.HEADING ? 3 : PARAGRAPH_SPACING);
        }

        result.totalHeight = y;
        return result;
    }

    /**
     * 对一组内联元素做流式排版，产出若干行.
     */
    private static List<LayoutLine> layoutFlow(List<InlineElement> elements, FontRenderer fr,
                                               int lineWidth, int startY, int xOffset) {
        List<LayoutLine> lines = new ArrayList<>();
        FlowState s = new FlowState();
        s.y = startY;

        for (InlineElement el : elements) {
            switch (el.getKind()) {
                case TEXT:
                case CODE:
                case LINK:
                    appendText(el, fr, lineWidth, xOffset, lines, s);
                    break;
                case ITEM_LINK:
                    // 图标原子：先冲刷文本缓冲，宽度不足整原子下沉一行
                    flushBuffer(s, xOffset);
                    if (s.currentX + ICON_SIZE > lineWidth && s.currentX > 0) {
                        endLine(lines, s, fr);
                    }
                    s.atoms.add(new LayoutLine.Atom(xOffset + s.currentX, ICON_SIZE, null, false, el));
                    s.currentX += ICON_SIZE;
                    s.lastCharWasWhitespace = false;
                    break;
                case BREAK:
                    flushBuffer(s, xOffset);
                    endLine(lines, s, fr);
                    s.lastCharWasWhitespace = true;
                    break;
                default:
                    break;
            }
        }

        flushBuffer(s, xOffset);
        if (!s.atoms.isEmpty()) {
            endLine(lines, s, fr);
        }
        return lines;
    }

    /**
     * 追加文本元素：空白折叠 + 逐字符宽度累加 + BreakIterator 断行.
     */
    private static void appendText(InlineElement el, FontRenderer fr, int lineWidth, int xOffset,
                                   List<LayoutLine> lines, FlowState s) {
        String text = el.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        // 来源变化（样式/链接归属不同）时先冲刷旧缓冲
        if (s.buffer.length() > 0 && s.bufferSource != el) {
            flushBuffer(s, xOffset);
        }
        boolean bold = el.isBold();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int codePoint = ch;
            int charLen = 1;
            // UTF-16 代理对处理
            if (Character.isHighSurrogate(ch) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                codePoint = Character.toCodePoint(ch, text.charAt(i + 1));
                charLen = 2;
            }

            // 空白折叠
            if (Character.isWhitespace(codePoint)) {
                if (s.lastCharWasWhitespace) {
                    i += charLen - 1;
                    continue;
                }
                s.lastCharWasWhitespace = true;
            } else {
                s.lastCharWasWhitespace = false;
            }

            String unit = new String(Character.toChars(codePoint));
            int advance = fr.getStringWidth(unit) + (bold ? 1 : 0);

            // 行缓冲溢出：需要断行
            if (s.currentX + s.bufferWidth + advance > lineWidth && (s.currentX + s.bufferWidth) > 0) {
                if (Character.isWhitespace(codePoint)) {
                    // 空白处断行：冲刷缓冲，丢弃该空白
                    s.bufferSource = el;
                    flushBuffer(s, xOffset);
                    endLine(lines, s, fr);
                    i += charLen - 1;
                    continue;
                }
                // 在「缓冲 + 当前字符」中找前一个断行机会
                BreakIterator bi = BreakIterator.getLineInstance();
                bi.setText(bufferWithUnit(s.buffer, unit));
                int bp = bi.preceding(s.buffer.length() + 1);
                s.bufferSource = el;
                if (bp > 0 && bp < s.buffer.length()) {
                    // 断点前的内容成行，断点后留到新缓冲
                    String head = s.buffer.substring(0, bp);
                    String tail = s.buffer.substring(bp);
                    int headWidth = stringWidth(fr, head, bold);
                    s.atoms.add(new LayoutLine.Atom(xOffset + s.currentX, headWidth, head, bold, el));
                    endLine(lines, s, fr);
                    // 去除新行首空白
                    int strip = 0;
                    while (strip < tail.length() && Character.isWhitespace(tail.charAt(strip))) {
                        strip++;
                    }
                    tail = tail.substring(strip);
                    s.buffer.setLength(0);
                    s.buffer.append(tail);
                    s.bufferWidth = stringWidth(fr, tail, bold);
                } else {
                    // 无断行机会（超长单词或缓冲即一个词）：强制词中断行
                    flushBuffer(s, xOffset);
                    endLine(lines, s, fr);
                }
                s.lastCharWasWhitespace = false;
            }

            s.bufferSource = el;
            s.buffer.append(unit);
            s.bufferWidth += advance;
            i += charLen - 1;
        }
    }

    private static String bufferWithUnit(StringBuilder buffer, String unit) {
        return buffer.toString() + unit;
    }

    private static void flushBuffer(FlowState s, int xOffset) {
        if (s.buffer.length() == 0) {
            return;
        }
        boolean bold = s.bufferSource != null && s.bufferSource.isBold();
        s.atoms.add(new LayoutLine.Atom(xOffset + s.currentX, s.bufferWidth, s.buffer.toString(), bold, s.bufferSource));
        s.currentX += s.bufferWidth;
        s.buffer.setLength(0);
        s.bufferWidth = 0;
        s.bufferSource = null;
    }

    /**
     * 结束当前行：行高 = max(文本行高, 图标高度)，y 推进.
     */
    private static void endLine(List<LayoutLine> lines, FlowState s, FontRenderer fr) {
        int lineHeight = fr.FONT_HEIGHT;
        for (LayoutLine.Atom atom : s.atoms) {
            if (atom.isIcon()) {
                lineHeight = Math.max(lineHeight, ICON_SIZE);
            }
        }
        LayoutLine line = new LayoutLine(s.y, lineHeight);
        line.atoms.addAll(s.atoms);
        lines.add(line);
        s.atoms.clear();
        s.currentX = 0;
        s.bufferWidth = 0;
        s.y += lineHeight;
    }

    private static int stringWidth(FontRenderer fr, String text, boolean bold) {
        if (text.isEmpty()) {
            return 0;
        }
        return fr.getStringWidth(text) + (bold ? text.codePointCount(0, text.length()) : 0);
    }
}
