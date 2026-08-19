package com.github.aeddddd.ae2enhanced.guide.client;

import com.github.aeddddd.ae2enhanced.guide.GuideBook;
import com.github.aeddddd.ae2enhanced.guide.GuideManager;
import com.github.aeddddd.ae2enhanced.guide.GuidePage;
import com.github.aeddddd.ae2enhanced.guide.layout.FlowLayouter;
import com.github.aeddddd.ae2enhanced.guide.layout.LayoutLine;
import com.github.aeddddd.ae2enhanced.guide.md.element.BlockElement;
import com.github.aeddddd.ae2enhanced.guide.md.element.InlineElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.List;

/**
 * 指南页面视图 —— 右侧滚动页面区域：渲染 LayoutLine、滚动条、点击/悬停命中检测.
 * 颜色全部取自当前 GuideTheme。
 */
public final class GuidePageView {

    private static final int PADDING = 8;
    private static final int SCROLLBAR_WIDTH = 6;

    private final GuiGuide gui;
    private int x, y, width, height;

    private GuidePage page;
    private FlowLayouter.LayoutResult layout;
    private int scrollY;
    private boolean draggingScrollbar;

    public GuidePageView(GuiGuide gui) {
        this.gui = gui;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * 切换页面；anchor 非空时滚动到锚点.
     */
    public void setPage(GuidePage page, String anchor) {
        this.page = page;
        relayout();
        if (anchor != null && layout != null) {
            Integer anchorY = layout.anchorYs.get(anchor);
            this.scrollY = anchorY != null ? anchorY : 0;
        } else {
            this.scrollY = 0;
        }
        clampScroll();
    }

    public void relayout() {
        if (this.page != null) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            this.layout = FlowLayouter.layout(this.page, fr, contentWidth());
            clampScroll();
        } else {
            this.layout = null;
        }
    }

    private int contentX() {
        return x + PADDING;
    }

    private int contentY() {
        return y + PADDING;
    }

    private int contentWidth() {
        return width - PADDING * 2 - SCROLLBAR_WIDTH - 2;
    }

    private int contentHeight() {
        return height - PADDING * 2;
    }

    public void render(int mouseX, int mouseY) {
        GuideTheme theme = gui.getTheme();
        // 背景
        GuiScreen.drawRect(x, y, x + width, y + height, theme.windowBg);

        if (layout == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;

        // 裁剪滚动区域
        beginScissor(contentX(), contentY(), contentWidth(), contentHeight(), mc);

        for (LayoutLine line : layout.lines) {
            int lineTop = contentY() + line.y - scrollY;
            if (lineTop + line.height < contentY() || lineTop > contentY() + contentHeight()) {
                continue;
            }
            // 代码块行底色（整行宽）
            if (line.blockType == BlockElement.Type.CODE_BLOCK) {
                GuiScreen.drawRect(contentX(), lineTop, contentX() + contentWidth(), lineTop + line.height,
                        theme.panelBg);
            }
            for (LayoutLine.Atom atom : line.atoms) {
                int atomX = contentX() + atom.x;
                if (atom.isIcon()) {
                    ItemStack stack = atom.source.getItemStack();
                    if (stack != null && !stack.isEmpty()) {
                        int iconY = lineTop + (line.height - 16) / 2;
                        drawItemStack(mc, stack, atomX, iconY);
                    }
                } else if (atom.text != null) {
                    boolean linkHover = isLinkHover(atom, mouseX, mouseY, atomX, lineTop, line.height);
                    int color = colorFor(line, atom, theme);
                    String text = atom.bold || line.blockType == BlockElement.Type.HEADING
                            ? "§l" + atom.text : atom.text;
                    int textY = lineTop + (line.height - fr.FONT_HEIGHT);
                    // 行内代码：文字底色
                    if (atom.source != null && atom.source.getKind() == InlineElement.Kind.CODE) {
                        GuiScreen.drawRect(atomX - 1, textY - 1, atomX + atom.width + 1,
                                textY + fr.FONT_HEIGHT + 1, theme.panelBg);
                    }
                    fr.drawString(text, atomX, textY, color);
                    // 链接悬停时下划线
                    if (linkHover) {
                        GuiScreen.drawRect(atomX, textY + fr.FONT_HEIGHT - 1,
                                atomX + atom.width, textY + fr.FONT_HEIGHT, theme.link);
                    }
                }
            }
        }

        endScissor();

        // 滚动条
        if (needsScrollbar()) {
            int trackX = x + width - SCROLLBAR_WIDTH - 1;
            GuiScreen.drawRect(trackX, contentY(), trackX + SCROLLBAR_WIDTH, contentY() + contentHeight(),
                    theme.panelBg);
            int thumbH = thumbHeight();
            int thumbY = contentY() + thumbOffset(thumbH);
            GuiScreen.drawRect(trackX + 1, thumbY, trackX + SCROLLBAR_WIDTH - 1, thumbY + thumbH,
                    theme.scrollThumb);
        }

        // 悬停 tooltip
        LayoutLine.Atom hovered = atomAt(mouseX, mouseY);
        if (hovered != null && hovered.source != null) {
            if (hovered.source.getKind() == InlineElement.Kind.ITEM_LINK
                    && hovered.source.getItemStack() != null && !hovered.source.getItemStack().isEmpty()) {
                gui.renderItemTooltip(hovered.source.getItemStack(), mouseX, mouseY);
            } else if (hovered.source.getKind() == InlineElement.Kind.LINK) {
                String tip = linkTooltip(hovered.source);
                if (tip != null) {
                    gui.drawHoveringText(Collections.singletonList(tip), mouseX, mouseY);
                }
            }
        }
    }

    private boolean isLinkHover(LayoutLine.Atom atom, int mouseX, int mouseY, int atomX, int lineTop, int lineHeight) {
        return atom.source != null && atom.source.getKind() == InlineElement.Kind.LINK
                && mouseX >= atomX && mouseX < atomX + atom.width
                && mouseY >= lineTop && mouseY < lineTop + lineHeight;
    }

    private int colorFor(LayoutLine line, LayoutLine.Atom atom, GuideTheme theme) {
        if (atom.source != null && atom.source.getKind() == InlineElement.Kind.LINK) {
            return theme.link;
        }
        if (line.blockType == BlockElement.Type.HEADING) {
            return line.headingDepth <= 1 ? theme.heading1 : theme.heading;
        }
        return theme.text;
    }

    private String linkTooltip(InlineElement link) {
        if (link.isExternalLink()) {
            return link.getLinkTarget();
        }
        GuideBook book = GuideManager.getInstance().getBook();
        if (book == null) {
            return null;
        }
        String target = link.getLinkTarget();
        int hash = target.indexOf('#');
        String pageId = hash >= 0 ? target.substring(0, hash) : target;
        GuidePage targetPage = book.getPage(pageId);
        return targetPage != null ? targetPage.getTitle() : pageId;
    }

    /**
     * 命中检测：返回鼠标处的原子（仅可视区域内）.
     */
    public LayoutLine.Atom atomAt(int mouseX, int mouseY) {
        if (layout == null) return null;
        if (mouseX < contentX() || mouseX >= contentX() + contentWidth()
                || mouseY < contentY() || mouseY >= contentY() + contentHeight()) {
            return null;
        }
        int relY = mouseY - contentY() + scrollY;
        for (LayoutLine line : layout.lines) {
            if (relY >= line.y && relY < line.y + line.height) {
                return line.atomAt(mouseX - contentX());
            }
        }
        return null;
    }

    /**
     * 点击处理；返回被点击的 LINK/ITEM_LINK 元素，否则 null.
     */
    public InlineElement mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return null;
        }
        // 滚动条拖拽
        if (needsScrollbar()) {
            int trackX = x + width - SCROLLBAR_WIDTH - 1;
            if (mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                    && mouseY >= contentY() && mouseY < contentY() + contentHeight()) {
                draggingScrollbar = true;
                scrollToMouse(mouseY);
                return null;
            }
        }
        LayoutLine.Atom atom = atomAt(mouseX, mouseY);
        if (atom != null && atom.source != null
                && (atom.source.getKind() == InlineElement.Kind.LINK
                || atom.source.getKind() == InlineElement.Kind.ITEM_LINK)) {
            return atom.source;
        }
        return null;
    }

    public void mouseReleased() {
        draggingScrollbar = false;
    }

    public void mouseClickMove(int mouseY) {
        if (draggingScrollbar) {
            scrollToMouse(mouseY);
        }
    }

    private void scrollToMouse(int mouseY) {
        int thumbH = thumbHeight();
        int trackH = contentHeight() - thumbH;
        if (trackH <= 0) {
            return;
        }
        int rel = mouseY - contentY() - thumbH / 2;
        int maxScroll = layout.totalHeight - contentHeight();
        this.scrollY = (int) ((long) rel * maxScroll / trackH);
        clampScroll();
    }

    /**
     * 滚轮滚动（dWheel 为正向上）.
     */
    public void scrollWheel(int dWheel) {
        if (layout == null) return;
        // 兼容 CRL lwjglxx（返回 ±1）与标准 LWJGL2（返回 ±120）,统一按符号滚动
        this.scrollY -= Integer.signum(dWheel) * 27;
        clampScroll();
    }

    public void scrollToAnchor(String anchor) {
        if (layout != null && anchor != null) {
            Integer anchorY = layout.anchorYs.get(anchor);
            if (anchorY != null) {
                this.scrollY = anchorY;
                clampScroll();
            }
        }
    }

    private boolean needsScrollbar() {
        return layout != null && layout.totalHeight > contentHeight();
    }

    private int thumbHeight() {
        if (!needsScrollbar()) {
            return contentHeight();
        }
        return Math.max(20, contentHeight() * contentHeight() / layout.totalHeight);
    }

    private int thumbOffset(int thumbH) {
        int maxScroll = layout.totalHeight - contentHeight();
        if (maxScroll <= 0) {
            return 0;
        }
        return (int) ((long) scrollY * (contentHeight() - thumbH) / maxScroll);
    }

    private void clampScroll() {
        if (layout == null) {
            scrollY = 0;
            return;
        }
        int maxScroll = Math.max(0, layout.totalHeight - contentHeight());
        scrollY = Math.max(0, Math.min(scrollY, maxScroll));
    }

    private static void drawItemStack(Minecraft mc, ItemStack stack, int x, int y) {
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().zLevel = 100.0F;
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        mc.getRenderItem().zLevel = 0.0F;
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private static void beginScissor(int sx, int sy, int sw, int sh, Minecraft mc) {
        int scale = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx * scale, mc.displayHeight - (sy + sh) * scale, sw * scale, sh * scale);
    }

    private static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /**
     * 供 GuiGuide 判断鼠标是否在页面视图内（滚轮归属）.
     */
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public List<LayoutLine> getLines() {
        return layout != null ? layout.lines : Collections.emptyList();
    }
}
