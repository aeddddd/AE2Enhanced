package com.github.aeddddd.ae2enhanced.diag.check;

import net.minecraft.util.text.TextComponentTranslation;

/**
 * 单条诊断检查结果.
 *
 * <p>内容为本地化键 + 参数：聊天栏经 {@link #toComponent()} 由客户端按各自语言渲染；
 * 报告文件经 {@link #toPlainText()} 在服务端渲染（英文）。</p>
 */
public final class CheckResult {

    public enum Level {
        OK, WARN, ERROR
    }

    public final Level level;
    public final String key;
    public final Object[] args;

    private CheckResult(Level level, String key, Object... args) {
        this.level = level;
        this.key = key;
        this.args = args;
    }

    public static CheckResult ok(String key, Object... args) {
        return new CheckResult(Level.OK, key, args);
    }

    public static CheckResult warn(String key, Object... args) {
        return new CheckResult(Level.WARN, key, args);
    }

    public static CheckResult error(String key, Object... args) {
        return new CheckResult(Level.ERROR, key, args);
    }

    static CheckResult create(Level level, String key, Object... args) {
        return new CheckResult(level, key, args);
    }

    /** 聊天栏显示组件（客户端本地化渲染）。 */
    public TextComponentTranslation toComponent() {
        return new TextComponentTranslation(key, args);
    }

    /** 服务端纯文本渲染（报告文件用，英文 en_us）。 */
    public String toPlainText() {
        return toComponent().getUnformattedComponentText();
    }

    @Override
    public String toString() {
        return "[" + level + "] " + toPlainText();
    }
}
