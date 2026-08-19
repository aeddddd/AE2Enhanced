package com.github.aeddddd.ae2enhanced.guide.md;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.guide.md.element.BlockElement;
import com.github.aeddddd.ae2enhanced.guide.md.element.InlineElement;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 子集解析器 —— 块级结构 + 流式内联两层模型（对齐 GuideME PageCompiler 的简化版）.
 * 支持：# / ## / ### 标题、段落、- 列表、**加粗**、[链接](目标)、<ItemLink id="..." />。
 * 不支持的语法降级为纯文本，不抛异常。
 */
public final class MarkdownParser {

    private MarkdownParser() {}

    /**
     * 将页面正文解析为块级元素列表.
     */
    public static List<BlockElement> parse(String body) {
        List<BlockElement> blocks = new ArrayList<>();
        String[] lines = body.split("\n", -1);
        StringBuilder paragraph = new StringBuilder();

        for (int li = 0; li < lines.length; li++) {
            String trimmed = lines[li].trim();

            // 围栏代码块：``` 开始，到下一个 ``` 或文件结束
            if (trimmed.startsWith("```")) {
                flushParagraph(blocks, paragraph);
                BlockElement code = new BlockElement(BlockElement.Type.CODE_BLOCK, 0);
                while (++li < lines.length && !lines[li].trim().startsWith("```")) {
                    code.getCodeLines().add(lines[li]);
                }
                blocks.add(code);
                continue;
            }

            if (trimmed.isEmpty()) {
                flushParagraph(blocks, paragraph);
                continue;
            }

            // 标题
            int headingDepth = headingDepth(trimmed);
            if (headingDepth > 0) {
                flushParagraph(blocks, paragraph);
                String text = trimmed.substring(headingDepth).trim();
                BlockElement heading = new BlockElement(BlockElement.Type.HEADING, headingDepth);
                heading.getChildren().addAll(parseInline(text));
                blocks.add(heading);
                continue;
            }

            // 无序列表项（仅一层）
            if (trimmed.startsWith("- ") || trimmed.equals("-")) {
                flushParagraph(blocks, paragraph);
                String text = trimmed.length() > 2 ? trimmed.substring(2).trim() : "";
                BlockElement item = new BlockElement(BlockElement.Type.LIST_ITEM, 0);
                item.getChildren().addAll(parseInline(text));
                blocks.add(item);
                continue;
            }

            // 有序列表项：数字 + ". "（如 "1. "）
            int orderedEnd = orderedMarkerEnd(trimmed);
            if (orderedEnd > 0) {
                flushParagraph(blocks, paragraph);
                BlockElement item = new BlockElement(BlockElement.Type.LIST_ITEM, 0);
                item.setMarker(trimmed.substring(0, orderedEnd - 1));
                String text = trimmed.substring(orderedEnd).trim();
                item.getChildren().addAll(parseInline(text));
                blocks.add(item);
                continue;
            }

            // 其他内容并入当前段落（段内换行按空格处理，Markdown 语义）
            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(trimmed);
        }
        flushParagraph(blocks, paragraph);
        return blocks;
    }

    /**
     * 若 trimmed 以 "数字. " 开头，返回序号文本结束后的下标（含 ". "），否则返回 0.
     */
    private static int orderedMarkerEnd(String trimmed) {
        int i = 0;
        while (i < trimmed.length() && Character.isDigit(trimmed.charAt(i))) {
            i++;
        }
        if (i > 0 && i + 1 < trimmed.length()
                && trimmed.charAt(i) == '.' && trimmed.charAt(i + 1) == ' ') {
            return i + 2;
        }
        return 0;
    }

    private static void flushParagraph(List<BlockElement> blocks, StringBuilder paragraph) {
        if (paragraph.length() == 0) return;
        BlockElement p = new BlockElement(BlockElement.Type.PARAGRAPH, 0);
        p.getChildren().addAll(parseInline(paragraph.toString()));
        blocks.add(p);
        paragraph.setLength(0);
    }

    private static int headingDepth(String trimmed) {
        int depth = 0;
        while (depth < trimmed.length() && trimmed.charAt(depth) == '#') {
            depth++;
        }
        if (depth >= 1 && depth <= 6 && depth < trimmed.length() && trimmed.charAt(depth) == ' ') {
            return Math.min(depth, 6);
        }
        return 0;
    }

    /**
     * 解析行内语法：**bold**、[text](target)、<ItemLink id="..." />.
     */
    public static List<InlineElement> parseInline(String text) {
        List<InlineElement> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean bold = false;
        int i = 0;
        int n = text.length();

        while (i < n) {
            // 行内代码：`code` 或 ``code``（反引号数量需配对）
            if (text.charAt(i) == '`') {
                int ticks = 1;
                while (i + ticks < n && text.charAt(i + ticks) == '`') {
                    ticks++;
                }
                String fence = text.substring(i, i + ticks);
                int close = text.indexOf(fence, i + ticks);
                if (close > 0) {
                    flushText(out, buf, bold);
                    String codeText = text.substring(i + ticks, close);
                    if (!codeText.isEmpty()) {
                        out.add(InlineElement.code(codeText));
                    }
                    i = close + ticks;
                    continue;
                }
                buf.append(text.charAt(i));
                i++;
                continue;
            }
            // **bold** 切换
            if (text.startsWith("**", i)) {
                flushText(out, buf, bold);
                bold = !bold;
                i += 2;
                continue;
            }
            // [text](target)
            if (text.charAt(i) == '[') {
                int closeBracket = text.indexOf(']', i);
                if (closeBracket > 0 && closeBracket + 1 < n && text.charAt(closeBracket + 1) == '(') {
                    int closeParen = text.indexOf(')', closeBracket + 2);
                    if (closeParen > closeBracket) {
                        flushText(out, buf, bold);
                        String display = text.substring(i + 1, closeBracket);
                        String target = text.substring(closeBracket + 2, closeParen).trim();
                        if (!display.isEmpty() && !target.isEmpty()) {
                            out.add(InlineElement.link(display, target));
                        }
                        i = closeParen + 1;
                        continue;
                    }
                }
                buf.append(text.charAt(i));
                i++;
                continue;
            }
            // <ItemLink id="..." />
            if (text.charAt(i) == '<' && text.startsWith("<ItemLink", i)) {
                int tagEnd = text.indexOf('>', i);
                if (tagEnd > 0) {
                    String tag = text.substring(i, tagEnd + 1);
                    String id = extractIdAttr(tag);
                    if (id != null) {
                        flushText(out, buf, bold);
                        ItemStack stack = resolveItem(id);
                        if (stack != null) {
                            out.add(InlineElement.itemLink(stack));
                        } else {
                            // 物品不存在：降级为显示 id 文本
                            out.add(InlineElement.text(id, bold));
                        }
                        i = tagEnd + 1;
                        continue;
                    }
                }
                buf.append(text.charAt(i));
                i++;
                continue;
            }
            buf.append(text.charAt(i));
            i++;
        }
        flushText(out, buf, bold);
        return out;
    }

    private static void flushText(List<InlineElement> out, StringBuilder buf, boolean bold) {
        if (buf.length() == 0) return;
        out.add(InlineElement.text(buf.toString(), bold));
        buf.setLength(0);
    }

    /**
     * 从 <ItemLink id="xxx" /> 标签中提取 id 属性.
     */
    private static String extractIdAttr(String tag) {
        int idx = tag.indexOf("id=");
        if (idx < 0) return null;
        int start = idx + 3;
        if (start >= tag.length()) return null;
        char quote = tag.charAt(start);
        if (quote != '"' && quote != '\'') return null;
        int end = tag.indexOf(quote, start + 1);
        if (end < 0) return null;
        return tag.substring(start + 1, end);
    }

    /**
     * 解析物品 id → ItemStack。缺省域为本 mod。
     */
    private static ItemStack resolveItem(String id) {
        ResourceLocation rl = id.contains(":")
                ? new ResourceLocation(id)
                : new ResourceLocation(AE2Enhanced.MOD_ID, id);
        Item item = Item.REGISTRY.getObject(rl);
        if (item == null) {
            return null;
        }
        return new ItemStack(item);
    }
}
