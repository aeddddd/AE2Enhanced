package com.github.aeddddd.ae2enhanced.guide.md.element;

import java.util.ArrayList;
import java.util.List;

/**
 * 块级元素 —— 页面的顶层结构（对齐 GuideME document/block）.
 */
public final class BlockElement {

    public enum Type {
        PARAGRAPH,
        HEADING,
        LIST_ITEM,
        CODE_BLOCK      // 围栏代码块（```），内容预格式逐行渲染
    }

    private final Type type;
    private final int headingDepth;          // 仅 HEADING 有效（1-3）
    private final List<InlineElement> children;
    private String marker;                   // 仅 LIST_ITEM 有效：有序列表序号（如 "1."），null 为无序
    private final List<String> codeLines = new ArrayList<>(); // 仅 CODE_BLOCK 有效

    public BlockElement(Type type, int headingDepth) {
        this.type = type;
        this.headingDepth = headingDepth;
        this.children = new ArrayList<>();
    }

    public Type getType() {
        return type;
    }

    public int getHeadingDepth() {
        return headingDepth;
    }

    public List<InlineElement> getChildren() {
        return children;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public List<String> getCodeLines() {
        return codeLines;
    }

    /**
     * 提取纯文本（供锚点生成等）.
     */
    public String getPlainText() {
        if (type == Type.CODE_BLOCK) {
            return String.join("\n", codeLines);
        }
        StringBuilder sb = new StringBuilder();
        for (InlineElement el : children) {
            if (el.getText() != null) {
                sb.append(el.getText());
            } else if (el.getKind() == InlineElement.Kind.ITEM_LINK && el.getItemStack() != null) {
                sb.append(el.getItemStack().getDisplayName());
            }
        }
        return sb.toString();
    }
}
