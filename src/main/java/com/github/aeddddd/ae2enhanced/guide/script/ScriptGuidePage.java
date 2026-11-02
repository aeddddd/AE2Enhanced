package com.github.aeddddd.ae2enhanced.guide.script;

/** 一页脚本指南的不可变文本；language 为空字符串时表示默认正文。 */
public final class ScriptGuidePage {
    private final String pageId;
    private final String language;
    private final String content;

    public ScriptGuidePage(String pageId, String language, String content) {
        this.pageId = pageId;
        this.language = language;
        this.content = content;
    }

    public String getPageId() {
        return pageId;
    }

    public String getLanguage() {
        return language;
    }

    public String getContent() {
        return content;
    }

    public int contentBytes() {
        return content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
