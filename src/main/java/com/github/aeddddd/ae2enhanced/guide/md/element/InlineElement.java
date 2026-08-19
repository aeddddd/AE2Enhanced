package com.github.aeddddd.ae2enhanced.guide.md.element;

import net.minecraft.item.ItemStack;

/**
 * 流式（内联）元素 —— 段落内的最小内容单元（对齐 GuideME document/flow）.
 * 不可变；链接目标为页面 id（可带 #锚点）或 http(s) 外部链接。
 */
public final class InlineElement {

    public enum Kind {
        TEXT,       // 普通文本 run
        CODE,       // 行内代码 `code`（带底色渲染，无点击行为）
        LINK,       // 页面链接 / 外部链接（文本显示，点击跳转）
        ITEM_LINK,  // <ItemLink> 内联物品图标
        BREAK       // 显式换行（列表项分隔等内部使用）
    }

    private final Kind kind;
    private final String text;
    private final boolean bold;
    private final String linkTarget;   // LINK：pageId / pageId#anchor / http(s)://...
    private final ItemStack itemStack; // ITEM_LINK：渲染用物品

    private InlineElement(Kind kind, String text, boolean bold, String linkTarget, ItemStack itemStack) {
        this.kind = kind;
        this.text = text;
        this.bold = bold;
        this.linkTarget = linkTarget;
        this.itemStack = itemStack;
    }

    public static InlineElement text(String text, boolean bold) {
        return new InlineElement(Kind.TEXT, text, bold, null, null);
    }

    public static InlineElement code(String text) {
        return new InlineElement(Kind.CODE, text, false, null, null);
    }

    public static InlineElement link(String displayText, String target) {
        return new InlineElement(Kind.LINK, displayText, false, target, null);
    }

    public static InlineElement itemLink(ItemStack stack) {
        return new InlineElement(Kind.ITEM_LINK, null, false, null, stack);
    }

    public static InlineElement lineBreak() {
        return new InlineElement(Kind.BREAK, null, false, null, null);
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public boolean isBold() {
        return bold;
    }

    public String getLinkTarget() {
        return linkTarget;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public boolean isExternalLink() {
        return kind == Kind.LINK && linkTarget != null
                && (linkTarget.startsWith("http://") || linkTarget.startsWith("https://"));
    }
}
