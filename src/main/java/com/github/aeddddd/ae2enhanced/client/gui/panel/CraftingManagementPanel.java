package com.github.aeddddd.ae2enhanced.client.gui.panel;

import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.container.CraftingStatus;
import com.github.aeddddd.ae2enhanced.container.SplicedTermModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;

import java.util.List;

/** 合成管理：复用容器已有的活动合成快照，原生管理页负责完整 CPU 操作。 */
public final class CraftingManagementPanel extends ModulePanel {
    public static final String MODULE_ID = SplicedTermModules.CRAFTING_MANAGEMENT_ID;
    private static final int ROW_HEIGHT = 18;
    private static final int ACTION_HEIGHT = 18;

    private final ContainerSplicedTerm container;
    private final Runnable openManagement;
    private int scrollOffset;

    public CraftingManagementPanel(ContainerSplicedTerm container, Runnable openManagement) {
        this.container = container;
        this.openManagement = openManagement;
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.spliced_term.crafting_management");
    }

    @Override
    public int minWidth() {
        return 180;
    }

    @Override
    public int defaultWidth() {
        return 240;
    }

    @Override
    public int minHeight() {
        return TITLE_BAR_HEIGHT + CONTENT_PADDING + 2 * ROW_HEIGHT + ACTION_HEIGHT;
    }

    @Override
    public int defaultHeight() {
        return TITLE_BAR_HEIGHT + CONTENT_PADDING + 5 * ROW_HEIGHT + ACTION_HEIGHT;
    }

    @Override
    public boolean expandsVertically() {
        return true;
    }

    @Override
    public void layoutSlots() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    private int maxScroll() {
        int rows = Math.max(1, (contentHeight() - ACTION_HEIGHT) / ROW_HEIGHT);
        return Math.max(0, container.getClientActiveCrafting().size() - rows);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (scrollY == 0) {
            return false;
        }
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inAction(mx, my)) {
            openManagement.run();
            return true;
        }
        return false;
    }

    private boolean inAction(double mx, double my) {
        int left = contentLeft();
        int top = contentTop() + contentHeight() - ACTION_HEIGHT;
        return mx >= left && mx < left + contentWidth() && my >= top && my < top + ACTION_HEIGHT;
    }

    @Override
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        if (!visible) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        boolean pushed = NinePatch.pushZLevel(zLevel);
        try {
            List<CraftingStatus> jobs = container.getClientActiveCrafting();
            int rows = Math.max(1, (contentHeight() - ACTION_HEIGHT) / ROW_HEIGHT);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
            if (jobs.isEmpty()) {
                font.drawString(I18n.format("gui.ae2enhanced.spliced_term.crafting_management.empty"),
                        contentLeft() + 4, contentTop() + 5, Ae2ModernArt.MUTED, false);
            }
            for (int row = 0; row < rows; row++) {
                int index = scrollOffset + row;
                if (index >= jobs.size()) {
                    break;
                }
                CraftingStatus job = jobs.get(index);
                int top = contentTop() + row * ROW_HEIGHT;
                Gui.drawRect(contentLeft(), top, contentLeft() + contentWidth(), top + ROW_HEIGHT - 1,
                        (row & 1) == 0 ? Ae2ModernArt.SLOT : Ae2ModernArt.LINE);
                String name = job.output == null ? "?" : job.output.getDefinition().getDisplayName();
                font.drawString(font.trimStringToWidth(name, contentWidth() - 8),
                        contentLeft() + 4, top + 2, Ae2ModernArt.OUTLINE, false);
                String progress = job.remaining + " / " + job.start;
                font.drawString(progress, contentLeft() + 4, top + 10, Ae2ModernArt.MUTED, false);
                int barWidth = Math.max(0, contentWidth() - 70);
                int completed = job.start <= 0 ? barWidth
                        : (int) Math.round((1.0F - job.getRatio()) * barWidth);
                Gui.drawRect(contentLeft() + contentWidth() - barWidth - 4, top + 11,
                        contentLeft() + contentWidth() - 4, top + 14, Ae2ModernArt.DEEP);
                if (completed > 0) {
                    Gui.drawRect(contentLeft() + contentWidth() - barWidth - 4, top + 11,
                            contentLeft() + contentWidth() - barWidth - 4 + completed, top + 14,
                            Ae2ModernArt.HIGHLIGHT);
                }
            }
            int actionTop = contentTop() + contentHeight() - ACTION_HEIGHT;
            NinePatch.drawButtonBase(contentLeft(), actionTop, contentWidth(), ACTION_HEIGHT,
                    inAction(mouseX, mouseY), 0.0F);
            String action = I18n.format("gui.ae2enhanced.spliced_term.crafting_management.open");
            font.drawString(action, contentLeft() + (contentWidth() - font.getStringWidth(action)) / 2,
                    actionTop + 5, Ae2ModernArt.OUTLINE, false);
        } finally {
            NinePatch.popZLevel(pushed);
        }
    }
}
