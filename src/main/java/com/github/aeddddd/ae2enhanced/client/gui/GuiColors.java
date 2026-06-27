package com.github.aeddddd.ae2enhanced.client.gui;

/**
 * 统一 GUI 颜色常量.
 * 所有 tech-panel 风格的 GUI 共享此配色方案,避免在 7+ 个 GUI 类中重复定义相同的常量.
 */
public final class GuiColors {

    private GuiColors() {}

    public static final int PANEL_BG     = 0xFF1a1a2e;
    public static final int PANEL_LIGHT  = 0xFF16213e;
    public static final int BORDER_DIM   = 0xFF0f3460;
    public static final int ACCENT       = 0xFF00d4ff;
    public static final int ACCENT_SOFT  = 0xFF0f4c75;

    public static final int TEXT_MAIN    = 0xFFe0e0e0;
    public static final int TEXT_DIM     = 0xFF88aaaa;
    public static final int TEXT_SUCCESS = 0xFF55ff88;
    public static final int TEXT_WARN    = 0xFFffaa55;
    public static final int TEXT_ERROR   = 0xFFff5555;

    public static final int SLOT_BORDER  = 0xFF333355;
    public static final int SLOT_HOVER   = 0xFF555577;
    public static final int BAR_BG       = 0xFF0a1a2a;
    public static final int BAR_FILL     = 0xFF00d4ff;
}
