package com.github.aeddddd.ae2enhanced.client.gui;

/**
 * GUI 相关常量集中定义.
 * <p>注意：本类仅包含基础类型常量,避免引入客户端专属类,因其可能被 common 包引用.</p>
 */
public final class GuiConstants {

    private GuiConstants() {
    }

    // ==================== 通用尺寸 ====================

    public static final int DEFAULT_IMAGE_WIDTH = 176;
    public static final int DEFAULT_IMAGE_HEIGHT = 166;

    public static final int PANEL_WIDTH = 280;
    public static final int PANEL_HEIGHT = 200;

    // ==================== 各 Screen 专用尺寸 ====================

    public static final int PATTERN_IMAGE_WIDTH = 320;
    public static final int PATTERN_IMAGE_HEIGHT = 228;

    public static final int NEXUS_IMAGE_HEIGHT = 190;

    // ==================== 按钮尺寸 ====================

    public static final int PATTERN_BUTTON_WIDTH = 56;
    public static final int PATTERN_BUTTON_HEIGHT = 20;
    public static final int PATTERN_PREV_BUTTON_X = 7;
    public static final int PATTERN_NEXT_BUTTON_X = 257;
    public static final int PATTERN_BUTTON_Y = 178;

    public static final int ASSEMBLY_BUTTON_WIDTH = 91;
    public static final int ASSEMBLY_BUTTON_HEIGHT = 20;
    public static final int ASSEMBLY_BUTTON_X = 79;
    public static final int ASSEMBLY_BUTTON_Y = 23;

    // ==================== 高亮纹理坐标 ====================

    public static final int ASSEMBLY_HIGHLIGHT_U = 0;
    public static final int ASSEMBLY_HIGHLIGHT_V = 186;
    public static final int PATTERN_HIGHLIGHT_U = 0;
    public static final int PATTERN_HIGHLIGHT_V = 247;

    // ==================== 缩放因子 ====================

    public static final float DEFAULT_INV_SCALE = 0.85F;
    public static final float DEFAULT_INV_SCALE_INVERSE = 1.0F / DEFAULT_INV_SCALE;

    // ==================== 容器交互距离 ====================

    /**
     * 容器 stillValid 最大距离平方,对应 8 格直线距离（与 Minecraft 原容器一致）.
     */
    public static final double CONTAINER_MAX_DISTANCE_SQR = 64.0;

    // ==================== 安全迭代上限与日志前缀 ====================

    /**
     * 装配枢纽批量任务安全迭代上限,防止死循环.
     */
    public static final int MAX_BATCH_ITERATIONS = 100000;

    public static final String LOGGER_PREFIX = "[AE2E]";

    // ==================== 颜色（ARGB） ====================

    public static final int PATTERN_TITLE_COLOR = 0xFF00ccff;
    public static final int ASSEMBLY_TITLE_COLOR = 0xFFffaa00;
    public static final int BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DISABLED_BUTTON_TEXT_COLOR = 0xFF888888;

    public static final int DARK_TEXT_COLOR = 0xFF222222;

    // 装配枢纽已成形界面：浅色背景上的深色信息文字
    public static final int ASSEMBLY_INFO_COLOR = 0xFF2f3b47;
    public static final int ASSEMBLY_STATUS_ACTIVE_COLOR = 0xFF1e7d46;
    public static final int ASSEMBLY_STATUS_BOOTING_COLOR = 0xFF9a6200;
    public static final int ASSEMBLY_STATUS_OFFLINE_COLOR = 0xFFb32626;

    public static final int SAFE_MODE_BANNER_COLOR = 0x55ff0000;
    public static final int SAFE_MODE_TEXT_COLOR = 0xFFffaaaa;

    public static final int COMPUTATION_EMPTY_TEXT_COLOR = 0xFF668899;
    public static final int COMPUTATION_INITIALIZING_TEXT_COLOR = 0xFF556677;
    public static final int COMPUTATION_HINT_TEXT_COLOR = 0xFF445566;

    // ==================== 通用布局位置 ====================

    public static final int TITLE_LABEL_Y = 8;
    public static final int PATTERN_PAGE_TEXT_Y = 200;

    public static final int PARALLEL_TEXT_X = 12;
    public static final int PARALLEL_TEXT_Y = 50;
    public static final int NETWORK_STATUS_RIGHT_MARGIN = 12;
    public static final int NETWORK_STATUS_Y = 50;
    public static final int JOBS_TEXT_X = 12;
    public static final int JOBS_TEXT_Y = 62;

    // ==================== 计算核心布局 ====================

    public static final int PANEL_CONTENT_LEFT_MARGIN = 10;
    public static final int PANEL_CONTENT_TOP_MARGIN = 40;

    public static final int COMPUTATION_INNER_PANEL_TOP = 36;
    public static final int COMPUTATION_INNER_PANEL_BOTTOM_MARGIN = 28;
    public static final int COMPUTATION_TITLE_Y = 8;
    public static final int COMPUTATION_SEPARATOR_Y = 22;
    public static final int COMPUTATION_SEPARATOR_LEFT_MARGIN = 16;
    public static final int COMPUTATION_CONTENT_START_X = 20;
    public static final int COMPUTATION_CONTENT_START_Y = 42;
    public static final int COMPUTATION_TILE_UNAVAILABLE_Y = 40;
    public static final int COMPUTATION_LINE_HEIGHT = 14;
    public static final int COMPUTATION_SMALL_LINE_SPACING = 12;
    public static final int COMPUTATION_PARAGRAPH_SPACING = 4;
    public static final int COMPUTATION_BAR_MAX_WIDTH = 140;
    public static final int COMPUTATION_BAR_HEIGHT = 8;
    public static final int COMPUTATION_DIVIDER_VERTICAL_MARGIN = 6;
    public static final int COMPUTATION_HINT_BOTTOM_MARGIN = 18;
    public static final int COMPUTATION_CORNER_ACCENT_SIZE = 10;
    public static final int COMPUTATION_FRAME_ACCENT_THICKNESS = 2;
    public static final int COMPUTATION_BORDER_THICKNESS = 1;

    // ==================== 超维度仓储 Nexus 布局 ====================

    public static final int NEXUS_TITLE_Y = 10;
    public static final int NEXUS_SEPARATOR_Y = 22;
    public static final int NEXUS_SEPARATOR_LEFT_MARGIN = 16;
    public static final int NEXUS_SAFE_MODE_BANNER_Y = 26;
    public static final int NEXUS_SAFE_MODE_BANNER_LEFT_MARGIN = 10;
    public static final int NEXUS_SAFE_MODE_BANNER_HEIGHT = 10;
    public static final int NEXUS_SAFE_MODE_BANNER_OFFSET = 12;
    public static final int NEXUS_CONTENT_START_X = 20;
    public static final int NEXUS_CONTENT_START_Y = 34;
    public static final int NEXUS_LINE_HEIGHT = 11;
    public static final int NEXUS_TOOLTIP_START_X = 20;
    public static final int NEXUS_TOOLTIP_START_Y = 34;

    // ==================== 未成形界面 ====================

    public static final int UNFORMED_REFRESH_INTERVAL_TICKS = 20;

    // ==================== 默认数值 ====================

    public static final int FALLBACK_PARALLEL_CAPACITY = 64;
}
