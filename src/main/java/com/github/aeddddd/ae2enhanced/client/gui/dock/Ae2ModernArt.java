package com.github.aeddddd.ae2enhanced.client.gui.dock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * AE2 19.2.17（Minecraft 1.21.1）GUI 素材的绘制入口。
 *
 * <p>素材来源与应用方式见 {@code licenses/AE2-ASSETS.txt}：</p>
 * <ul>
 *   <li>{@code textures/gui/ae2modern/*.png} 是 AE2 19.2.17 的原样文件（逐字节相同），
 *       以 CC BY-NC-SA 3.0 授权（非商用）。因此本类<strong>只做贴图 blit</strong>，
 *       不做"取色重绘"；需要改观感时换文件，而不是改色值；</li>
 *   <li>精灵坐标（{@link Icon}）逐条抄自 AE2 的同名枚举 {@code appeng.client.gui.Icon}，
 *       保证与 19.x 客户端一致；</li>
 *   <li>1.12.2 没有 {@code GuiGraphics}/pose stack，纹理绘制统一走
 *       {@code Gui.drawModalRectWithCustomSizedTexture}（源矩形与目标矩形同尺寸）；</li>
 *   <li>绘制后复位 {@code GlStateManager.color} 并关闭混合，与 vanilla GUI 默认状态一致。</li>
 * </ul>
 */
public final class Ae2ModernArt {
    // ==================== 纹理 ====================

    private static final String ROOT = "textures/gui/ae2modern/";

    /** 窗口 9-slice 边框（256x256，4px 边框，中部 248x248 平铺）。 */
    public static final ResourceLocation BACKGROUND = loc("background.png");
    /** GUI 精灵表：图标、槽格、按钮底、页签底（256x256）。 */
    public static final ResourceLocation STATES = loc("states.png");
    /** 按钮底（200x20，九宫格 border=3）。 */
    public static final ResourceLocation BUTTON = loc("button.png");
    /** 按钮底：hover/按下。 */
    public static final ResourceLocation BUTTON_HIGHLIGHTED = loc("button_highlighted.png");
    /** 按钮底：禁用。 */
    public static final ResourceLocation BUTTON_DISABLED = loc("button_disabled.png");
    /** 滚动条滑块（小样式，7x15）。 */
    public static final ResourceLocation SCROLLER_SMALL = loc("small_scroller.png");
    /** 滚动条滑块（大样式，12x15，可拼接终端回收终端用它）。 */
    public static final ResourceLocation SCROLLER_BIG = loc("big_scroller.png");
    /** 滚动条滑块：列表为空时的禁用态（小）。 */
    public static final ResourceLocation SCROLLER_SMALL_DISABLED = loc("small_scroller_disabled.png");
    /** 滚动条滑块：列表为空时的禁用态（大）。 */
    public static final ResourceLocation SCROLLER_BIG_DISABLED = loc("big_scroller_disabled.png");
    /** 合成台背景图（本终端只 blit 其中的合成箭头）。 */
    public static final ResourceLocation CRAFTING = loc("crafting.png");
    /** 编码区模式底（craft 0,0 / process 0,70 / stonecutting 0,140，各 124x66）。 */
    public static final ResourceLocation PATTERN_MODES = loc("pattern_modes.png");
    /** 数量弹窗底（176x107）。 */
    public static final ResourceLocation CRAFT_AMT = loc("craft_amt.png");
    /** 输入框底（128x128；0/12/24 三档 12px 高条带 = 普通/禁用/聚焦）。 */
    public static final ResourceLocation TEXT_FIELD = loc("text_field.png");

    private static ResourceLocation loc(String name) {
        return new ResourceLocation(AE2Enhanced.MOD_ID, ROOT + name);
    }

    // ==================== 尺寸、九宫格与调色板规格 ====================

    /** 槽格精灵边长（{@link Icon#SLOT_BACKGROUND}），槽间距 18px。 */
    public static final int SLOT_SIZE = 18;
    /** 按钮底源尺寸与边框（对应 button*.png 的 .mcmeta：200x20 nine_slice border=3）。 */
    public static final int BUTTON_SRC_WIDTH = 200;
    public static final int BUTTON_SRC_HEIGHT = 20;
    public static final int BUTTON_BORDER = 3;
    /** 窗口 9-slice：源边长 / 边框 / 平铺步长。 */
    public static final int BACKGROUND_SIZE = 256;
    public static final int BACKGROUND_BORDER = 4;
    public static final int BACKGROUND_TILE = 248;
    /** 输入框：单侧边框 1px、条带高 12px、三档纵向偏移。 */
    public static final int TEXT_FIELD_HEIGHT = 12;
    public static final int TEXT_FIELD_NORMAL = 0;
    public static final int TEXT_FIELD_DISABLED = 12;
    public static final int TEXT_FIELD_FOCUSED = 24;
    /** 合成箭头在 crafting.png 里的源矩形（逐像素定位，见交付报告）。 */
    public static final int CRAFTING_ARROW_U = 82;
    public static final int CRAFTING_ARROW_V = 110;
    public static final int CRAFTING_ARROW_W = 46;
    public static final int CRAFTING_ARROW_H = 18;

    // 19.x 调色板（逐像素采样；仅用于贴图之外的纯色补边/文字/投影）
    /** 面板底（background.png 主体，槽格之间的间隙也由它透出）。 */
    public static final int PLATE = 0xFFCBCCD4;
    /** 槽体 / 内容底。 */
    public static final int SLOT = 0xFFADB0C4;
    /** 分隔线 / 按钮面。 */
    public static final int LINE = 0xFF9A9FB4;
    /** 中调（阴影、次要文字）。 */
    public static final int MUTED = 0xFF878FA5;
    /** 深压（禁用面、深描边）。 */
    public static final int DEEP = 0xFF696D88;
    /** 最深描边 / 正文文字。 */
    public static final int OUTLINE = 0xFF413F54;
    /** 高光。 */
    public static final int HIGHLIGHT = 0xFFF2F2F2;
    /** 悬停面色（button_highlighted.png 主体）。 */
    public static final int HOVER = 0xFF9CD3FF;

    private Ae2ModernArt() {
    }

    // ==================== 精灵坐标（appeng.client.gui.Icon 的子集） ====================

    /** states.png 里的精灵。坐标/尺寸逐条对应 AE2 19.2.17 的 {@code Icon} 枚举。 */
    public enum Icon {
        // 排序 / 视图 / 方向 / 搜索（终端标题栏的 4 个设置按钮）
        SORT_BY_NAME(0, 64),
        SORT_BY_AMOUNT(16, 64),
        SORT_BY_MOD(96, 64),
        SORT_BY_INVENTORY_TWEAKS(80, 64),
        VIEW_MODE_STORED(0, 16),
        VIEW_MODE_ALL(32, 16),
        VIEW_MODE_CRAFTING(48, 16),
        ARROW_UP(0, 48),
        ARROW_DOWN(16, 48),
        SEARCH_AUTO_FOCUS(48, 32),
        SEARCH_DEFAULT(64, 32),
        SEARCH_JEI(80, 32),
        SEARCH_REI(96, 32),
        SEARCH_AUTO_FOCUS_REMEMBER(112, 32),
        SEARCH_REMEMBER(128, 32),
        SEARCH_JEI_AUTO_CLEAR(144, 32),
        SEARCH_REI_AUTO_CLEAR(160, 32),
        // 动作
        CLEAR(96, 0),
        ENTER(112, 0),
        WHITE_ARROW_DOWN(128, 0),
        SUBSTITUTION_ENABLED(64, 48),
        SUBSTITUTION_DISABLED(112, 48),
        COG(32, 64),
        CRAFT_HAMMER(48, 144),
        // 槽位底 / 槽位提示图案
        SLOT_BACKGROUND(192, 192, 18, 18),
        BACKGROUND_VIEW_CELL(224, 64),
        BACKGROUND_BLANK_PATTERN(240, 128),
        BACKGROUND_ENCODED_PATTERN(240, 112),
        // 属性检查器的开关状态（19.x Icon 里的 INVALID/VALID）
        INVALID(0, 128),
        VALID(16, 128),
        // 按钮 / 页签底
        TOOLBAR_BUTTON_BACKGROUND(176, 128, 18, 20),
        TOOLBAR_BUTTON_BACKGROUND_FOCUS(194, 128, 18, 20),
        TOOLBAR_BUTTON_BACKGROUND_HOVER(212, 128, 18, 20),
        HORIZONTAL_TAB(128, 128, 22, 22),
        HORIZONTAL_TAB_SELECTED(128, 150, 22, 22),
        HORIZONTAL_TAB_FOCUS(150, 128, 22, 22),
        TAB_BUTTON_BACKGROUND(160, 192, 20, 20),
        TAB_BUTTON_BACKGROUND_FOCUS(160, 224, 22, 22),
        TAB_BUTTON_BACKGROUND_BORDERLESS(128, 192, 25, 22);

        private final int x;
        private final int y;
        private final int width;
        private final int height;

        Icon(int x, int y) {
            this(x, y, 16, 16);
        }

        Icon(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }
    }

    // ==================== 基础绘制 ====================

    /** 把 {@code tex} 的 {@code (u,v,w,h)} 原尺寸贴到 {@code (x,y)}（1 纹理像素 = 1 GUI 像素）。 */
    public static void blit(ResourceLocation tex, int texWidth, int texHeight,
            int u, int v, int width, int height, int x, int y, float zLevel) {
        if (width <= 0 || height <= 0) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, texWidth, texHeight);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        NinePatch.popZLevel(pushed);
    }

    /** 把 {@code tex} 的 {@code (u,v,srcW,srcH)} 拉伸到 {@code (x,y,w,h)} 目标矩形。 */
    public static void blitScaled(ResourceLocation tex, int texWidth, int texHeight,
            int u, int v, int srcWidth, int srcHeight, int x, int y, int width, int height, float zLevel) {
        if (width <= 0 || height <= 0 || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, srcWidth, srcHeight, width, height, texWidth, texHeight);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        NinePatch.popZLevel(pushed);
    }

    /** 贴一个 16x16（或精灵自身尺寸）图标。 */
    public static void drawIcon(Icon icon, int x, int y, float zLevel) {
        blit(STATES, 256, 256, icon.x(), icon.y(), icon.width(), icon.height(), x, y, zLevel);
    }

    /** 贴一个图标并整体着色（用于禁用态变暗）。 */
    public static void drawIconTinted(Icon icon, int x, int y, float red, float green, float blue,
            float alpha, float zLevel) {
        boolean pushed = NinePatch.pushZLevel(zLevel);
        Minecraft.getMinecraft().getTextureManager().bindTexture(STATES);
        GlStateManager.enableBlend();
        GlStateManager.color(red, green, blue, alpha);
        Gui.drawModalRectWithCustomSizedTexture(x, y, icon.x(), icon.y(), icon.width(), icon.height(),
                256, 256);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        NinePatch.popZLevel(pushed);
    }

    // ==================== 槽格（S1） ====================

    /**
     * 一格 AE2 槽位底：18x18 精灵（16x16 槽体 + 1px 深色顶线，四周 1px 透明）。
     *
     * <p>透明边让面板底色（{@link #PLATE}）透出，正好复现 AE2 19.x 终端里"2px 间隙"的观感，
     * 无需再逐格画凹陷/高光。</p>
     */
    public static void drawSlot(int x, int y, float zLevel) {
        drawIcon(Icon.SLOT_BACKGROUND, x, y, zLevel);
    }

    /** {@code columns × rows} 的槽带（每格 18x18）。 */
    public static void drawSlotBand(int left, int top, int columns, int rows, float zLevel) {
        if (columns <= 0 || rows <= 0) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                drawSlot(left + column * SLOT_SIZE, top + row * SLOT_SIZE, 0.0F);
            }
        }
        NinePatch.popZLevel(pushed);
    }

    /** 在槽里贴一个槽位图案（view cell / 空白样板 / 已编码样板）。 */
    public static void drawSlotPattern(Icon pattern, int slotX, int slotY, float zLevel) {
        drawIcon(pattern, slotX + 1, slotY + 1, zLevel);
    }

    // ==================== 按钮 / 页签（S4 / S9 / S10 / S11 / S18 / E2 / E3） ====================

    /** 九宫格贴图：四角原样、四边单向拉伸、中部双向拉伸（button*.png：border=3，源 200x20）。 */
    public static void nineSlice(ResourceLocation tex, int srcWidth, int srcHeight, int border,
            int x, int y, int width, int height, float zLevel) {
        if (width < 2 * border || height < 2 * border) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int innerW = width - 2 * border;
        int innerH = height - 2 * border;
        int srcInnerW = srcWidth - 2 * border;
        int srcInnerH = srcHeight - 2 * border;
        // 四角（原尺寸）
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, border, border, srcWidth, srcHeight);
        Gui.drawModalRectWithCustomSizedTexture(x + width - border, y, srcWidth - border, 0, border, border,
                srcWidth, srcHeight);
        Gui.drawModalRectWithCustomSizedTexture(x, y + height - border, 0, srcHeight - border, border, border,
                srcWidth, srcHeight);
        Gui.drawModalRectWithCustomSizedTexture(x + width - border, y + height - border,
                srcWidth - border, srcHeight - border, border, border, srcWidth, srcHeight);
        // 上/下边（横向拉伸）
        Gui.drawScaledCustomSizeModalRect(x + border, y, border, 0, srcInnerW, border, innerW, border,
                srcWidth, srcHeight);
        Gui.drawScaledCustomSizeModalRect(x + border, y + height - border, border, srcHeight - border,
                srcInnerW, border, innerW, border, srcWidth, srcHeight);
        // 左/右边（纵向拉伸）
        Gui.drawScaledCustomSizeModalRect(x, y + border, 0, border, border, srcInnerH, border, innerH,
                srcWidth, srcHeight);
        Gui.drawScaledCustomSizeModalRect(x + width - border, y + border, srcWidth - border, border, border,
                srcInnerH, border, innerH, srcWidth, srcHeight);
        // 中部（双向拉伸）
        Gui.drawScaledCustomSizeModalRect(x + border, y + border, border, border, srcInnerW, srcInnerH,
                innerW, innerH, srcWidth, srcHeight);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        NinePatch.popZLevel(pushed);
    }

    /** AE2 19.x 按钮底（九宫格）。{@code hovered} 用高亮贴图，禁用用灰贴图。 */
    public static void drawButton(int x, int y, int width, int height, boolean hovered, boolean enabled,
            float zLevel) {
        ResourceLocation tex = !enabled ? BUTTON_DISABLED : (hovered ? BUTTON_HIGHLIGHTED : BUTTON);
        nineSlice(tex, BUTTON_SRC_WIDTH, BUTTON_SRC_HEIGHT, BUTTON_BORDER, x, y, width, height, zLevel);
    }

    /** AE2 19.x 标题栏小按钮底（18x20 官方精灵，三态）。绘制位置与 19.x {@code IconButton} 一致。 */
    public static void drawToolbarButton(int x, int y, boolean hovered, boolean enabled) {
        Icon icon;
        if (!enabled) {
            icon = Icon.TOOLBAR_BUTTON_BACKGROUND;
        } else {
            icon = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
        }
        blit(STATES, 256, 256, icon.x(), icon.y(), icon.width(), icon.height(), x, y, 0.0F);
    }

    /** 同上，但拉伸到给定矩形（面板自定义尺寸的按钮用；1.12.2 侧沿用旧的按钮底矩形画法）。 */
    public static void drawToolbarButton(int x, int y, int width, int height, boolean hovered,
            boolean enabled, float zLevel) {
        Icon icon = !enabled ? Icon.TOOLBAR_BUTTON_BACKGROUND
                : (hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND);
        blitScaled(STATES, 256, 256, icon.x(), icon.y(), icon.width(), icon.height(),
                x, y, width, height, zLevel);
        if (!enabled) {
            boolean pushed = NinePatch.pushZLevel(zLevel);
            Gui.drawRect(x, y, x + width, y + height, 0x66CBCCD4);
            NinePatch.popZLevel(pushed);
        }
    }

    /** AE2 19.x 页签（22x22：普通 / 选中 / 聚焦）。 */
    public static void drawTab(int x, int y, boolean selected, boolean focused, float zLevel) {
        Icon icon = focused ? Icon.HORIZONTAL_TAB_FOCUS
                : (selected ? Icon.HORIZONTAL_TAB_SELECTED : Icon.HORIZONTAL_TAB);
        drawIcon(icon, x, y, zLevel);
    }

    // ==================== 滚动条（S7） ====================

    /**
     * AE2 19.x 滚动条：只有滑块贴图，没有轨道（19.x 的 {@code Scrollbar} 只画 handle）。
     *
     * @param width  轨道宽度（>=12 时用大滑块，否则用小滑块）
     * @param height 轨道总高
     */
    public static void drawScrollbar(int x, int y, int width, int height, int scroll, int maxScroll,
            boolean hovered, float zLevel) {
        if (width <= 0 || height <= 0) {
            return;
        }
        boolean big = width >= 12;
        ResourceLocation tex;
        int texWidth;
        if (maxScroll <= 0) {
            tex = big ? SCROLLER_BIG_DISABLED : SCROLLER_SMALL_DISABLED;
            texWidth = big ? 12 : 7;
        } else {
            tex = big ? SCROLLER_BIG : SCROLLER_SMALL;
            texWidth = big ? 12 : 7;
        }
        int handleHeight = Math.min(15, height);
        int travel = height - handleHeight;
        int handleY = y;
        if (maxScroll > 0 && travel > 0) {
            handleY = y + travel * Math.max(0, Math.min(scroll, maxScroll)) / maxScroll;
        }
        int drawWidth = Math.min(width, texWidth);
        blit(tex, texWidth, 15, 0, 0, drawWidth, handleHeight, x, handleY, zLevel);
    }

    // ==================== 输入框（S8 / E4） ====================

    /** AE2 19.x 输入框底：左右 1px 边 + 中间拉伸，12px 高（聚焦/禁用换条带）。 */
    public static void drawTextField(int x, int y, int width, int height, boolean focused,
            boolean enabled, float zLevel) {
        if (width < 2 || height < 1) {
            return;
        }
        int v = !enabled ? TEXT_FIELD_DISABLED : (focused ? TEXT_FIELD_FOCUSED : TEXT_FIELD_NORMAL);
        blit(TEXT_FIELD, 128, 128, 0, v, 1, TEXT_FIELD_HEIGHT, x, y, zLevel);
        int middle = Math.min(126, width - 2);
        if (middle > 0) {
            blitScaled(TEXT_FIELD, 128, 128, 1, v, middle, TEXT_FIELD_HEIGHT,
                    x + 1, y, width - 2, height, zLevel);
        }
        blit(TEXT_FIELD, 128, 128, 127, v, 1, TEXT_FIELD_HEIGHT, x + width - 1, y, zLevel);
    }

    // ==================== 合成箭头（S12） / 编码区底（S14） / 数量弹窗（S19） ====================

    /** AE2 19.x 合成箭头（crafting.png 里矩阵→输出之间那一段）。 */
    public static void drawCraftingArrow(int x, int y, int width, int height, float zLevel) {
        blitScaled(CRAFTING, 256, 256, CRAFTING_ARROW_U, CRAFTING_ARROW_V, CRAFTING_ARROW_W,
                CRAFTING_ARROW_H, x, y, width, height, zLevel);
    }

    /** 编码区模式底（124x66）：{@code craftMode} 选 craft(0,0) 或 process(0,70)。 */
    public static void drawPatternModeBlock(int x, int y, boolean craftMode, float zLevel) {
        blit(PATTERN_MODES, 256, 256, 0, craftMode ? 0 : 70, 124, 66, x, y, zLevel);
    }

    /** 数量弹窗底（176x107，源矩形与 AE2 的 craft_amount.json 一致）。 */
    public static void drawAmountDialog(int x, int y, float zLevel) {
        blit(CRAFT_AMT, 256, 256, 0, 0, 176, 107, x, y, zLevel);
    }
}
