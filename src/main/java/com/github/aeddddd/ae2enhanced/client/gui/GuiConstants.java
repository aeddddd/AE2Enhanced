package com.github.aeddddd.ae2enhanced.client.gui;

/**
 * GUI 相关常量集中定义。
 * <p>注意：本类仅包含基础类型常量，避免引入客户端专属类，因其可能被 common 包引用。</p>
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

    public static final int ASSEMBLE_BUTTON_WIDTH = 160;
    public static final int ASSEMBLE_BUTTON_HEIGHT = 24;

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
     * 容器 stillValid 最大距离平方，对应 8 格直线距离（与 Minecraft 原容器一致）。
     */
    public static final double CONTAINER_MAX_DISTANCE_SQR = 64.0;

    // ==================== 安全迭代上限与日志前缀 ====================

    /**
     * 装配枢纽批量任务安全迭代上限，防止死循环。
     */
    public static final int MAX_BATCH_ITERATIONS = 100000;

    public static final String LOGGER_PREFIX = "[AE2E]";

    // ==================== 颜色（ARGB） ====================

    public static final int PATTERN_TITLE_COLOR = 0xFF00ccff;
    public static final int ASSEMBLY_TITLE_COLOR = 0xFFffaa00;
    public static final int BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DISABLED_BUTTON_TEXT_COLOR = 0xFF888888;

    public static final int DARK_TEXT_COLOR = 0xFF222222;
    public static final int SUBTITLE_COLOR = 0xFF88ccdd;
    public static final int HINT_COLOR = 0xFF88aaaa;
    public static final int HEADER_TEXT_COLOR = 0xFF88aabb;

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

    // ==================== 未成形界面布局 ====================

    public static final int UNFORMED_TITLE_Y = 12;
    public static final int UNFORMED_SUBTITLE_Y = 28;
    public static final int UNFORMED_HEADER_DIVIDER_Y = 36;
    public static final int UNFORMED_OUTER_DIVIDER_LEFT_MARGIN = 16;
    public static final int UNFORMED_MISSING_TITLE_X = 26;
    public static final int UNFORMED_MATERIAL_HEADER_X = 36;
    public static final int UNFORMED_QUANTITY_HEADER_RIGHT_OFFSET = 90;
    public static final int UNFORMED_HEADER_DIVIDER_LEFT_MARGIN = 30;
    public static final int UNFORMED_MISSING_ITEM_NAME_X = 36;
    public static final int UNFORMED_MISSING_ITEM_COUNT_RIGHT_MARGIN = 36;
    public static final int UNFORMED_LIST_ITEM_SPACING = 16;
    public static final int UNFORMED_REFRESH_INTERVAL_TICKS = 20;

    // ==================== 大型未成形界面（装配枢纽）构造参数 ====================

    public static final int UNFORMED_LARGE_YSIZE = 350;
    public static final int UNFORMED_LARGE_BUTTON_Y_OFFSET = 236;
    public static final int UNFORMED_LARGE_INNER_PANEL_BOTTOM = 210;
    public static final int UNFORMED_LARGE_STATUS_Y_OFFSET = 224;
    public static final int UNFORMED_LARGE_INVENTORY_DIVIDER_Y_OFFSET = 256;
    public static final int UNFORMED_LARGE_MISSING_LIST_START_Y = 80;
    public static final int UNFORMED_LARGE_READY_TEXT_Y = 62;
    public static final int UNFORMED_LARGE_HINT_TEXT_Y = 82;
    public static final int UNFORMED_LARGE_MISSING_TITLE_Y = 46;
    public static final int UNFORMED_LARGE_HEADER_Y = 62;
    public static final int UNFORMED_LARGE_HEADER_DIVIDER_Y = 74;

    // ==================== 小型未成形界面（计算核心/超维度仓储）构造参数 ====================

    public static final int UNFORMED_SMALL_YSIZE = 260;
    public static final int UNFORMED_SMALL_BUTTON_Y_OFFSET = 150;
    public static final int UNFORMED_SMALL_INNER_PANEL_BOTTOM = 140;
    public static final int UNFORMED_SMALL_STATUS_Y_OFFSET = 134;
    public static final int UNFORMED_SMALL_INVENTORY_DIVIDER_Y_OFFSET = 170;
    public static final int UNFORMED_SMALL_MISSING_LIST_START_Y = 76;
    public static final int UNFORMED_SMALL_READY_TEXT_Y = 54;
    public static final int UNFORMED_SMALL_HINT_TEXT_Y = 70;
    public static final int UNFORMED_SMALL_MISSING_TITLE_Y = 46;
    public static final int UNFORMED_SMALL_HEADER_Y = 58;
    public static final int UNFORMED_SMALL_HEADER_DIVIDER_Y = 70;

    // ==================== 默认数值 ====================

    public static final int FALLBACK_PARALLEL_CAPACITY = 64;
}
