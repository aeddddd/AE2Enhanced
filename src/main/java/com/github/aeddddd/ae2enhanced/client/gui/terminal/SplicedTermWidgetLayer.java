package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import com.github.aeddddd.ae2enhanced.client.gui.dock.DockManager;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.CraftingPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.PatternEncodingPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel;
import com.github.aeddddd.ae2enhanced.client.gui.widget.ModernImgButton;
import com.github.aeddddd.ae2enhanced.client.gui.widget.ModernTabButton;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.dock.model.DockRect;

import net.minecraft.client.gui.GuiButton;

final class SplicedTermWidgetLayer {
    private final DockManager dock;

    SplicedTermWidgetLayer(DockManager dock) {
        this.dock = dock;
    }

    private static void setVisible(GuiButton button, boolean shown) {
        if (button == null) {
            return;
        }
        button.visible = shown;
        if (!shown) {
            button.x = -1000;
            button.y = -1000;
        }
    }

    void layoutRepo(RepoPanel panel, ModernImgButton sortBy, ModernImgButton viewMode,
            ModernImgButton sortDir, ModernImgButton searchMode, ModernTabButton craftingStatus) {
        int top = panel.titleControlsTop();
        sortBy.x = panel.settingControlX(0);
        sortBy.y = top;
        viewMode.x = panel.settingControlX(1);
        viewMode.y = top;
        sortDir.x = panel.settingControlX(2);
        sortDir.y = top;
        searchMode.x = panel.settingControlX(3);
        searchMode.y = top;
        craftingStatus.x = panel.craftStatusControlX();
        craftingStatus.y = panel.craftStatusControlTop();
        float z = 750.0F;
        sortBy.setPaintZ(z);
        viewMode.setPaintZ(z);
        sortDir.setPaintZ(z);
        searchMode.setPaintZ(z);
        craftingStatus.setPaintZ(z);
        boolean shown = panel.visible && titleBarUnobscured(panel);
        setVisible(sortBy, shown);
        setVisible(viewMode, shown);
        setVisible(sortDir, shown);
        setVisible(searchMode, shown);
        setVisible(craftingStatus, shown);
    }

    void layoutCrafting(ModulePanel panel, ModernImgButton clear) {
        boolean shown = panel instanceof CraftingPanel && panel.visible && contentUnobscured(panel);
        if (shown) {
            CraftingPanel crafting = (CraftingPanel) panel;
            clear.x = crafting.clearButtonX();
            clear.y = crafting.clearButtonY();
        }
        clear.visible = shown;
        if (!shown) {
            clear.x = -1000;
            clear.y = -1000;
        }
        clear.setPaintZ(750.0F);
    }

    void layoutPattern(PatternEncodingPanel panel, ContainerSplicedTerm container, PatternWidgets widgets) {
        boolean visible = panel.visible && contentUnobscured(panel);
        boolean craft = container.isPatternCraftMode();
        float z = 750.0F;

        setVisible(widgets.craftTab, visible && craft);
        setVisible(widgets.processTab, visible && !craft);
        widgets.craftTab.setPaintZ(z);
        widgets.processTab.setPaintZ(z);
        widgets.craftTab.x = panel.modeTabX();
        widgets.craftTab.y = panel.modeTabY();
        widgets.processTab.x = panel.modeTabX();
        widgets.processTab.y = panel.modeTabY();

        boolean substituteShown = visible && craft;
        setVisible(widgets.substituteEnabled, substituteShown && container.isSubstitute());
        setVisible(widgets.substituteDisabled, substituteShown && !container.isSubstitute());
        setVisible(widgets.clear, visible);
        setVisible(widgets.encode, visible);
        widgets.substituteEnabled.setPaintZ(z);
        widgets.substituteDisabled.setPaintZ(z);
        widgets.clear.setPaintZ(z);
        widgets.encode.setPaintZ(z);
        widgets.substituteEnabled.x = panel.actionX(0);
        widgets.substituteEnabled.y = panel.actionY(0);
        widgets.substituteDisabled.x = panel.actionX(0);
        widgets.substituteDisabled.y = panel.actionY(0);
        widgets.clear.x = panel.actionX(1);
        widgets.clear.y = panel.actionY(1);
        widgets.encode.x = panel.actionX(2);
        widgets.encode.y = panel.actionY(2);
    }

    private boolean contentUnobscured(ModulePanel panel) {
        return panel != null && dock.isAreaUnobscured(panel, new DockRect(
                panel.contentLeft(), panel.contentTop(), panel.contentWidth(), panel.contentHeight()));
    }

    private boolean titleBarUnobscured(ModulePanel panel) {
        return panel != null && dock.isAreaUnobscured(panel,
                new DockRect(panel.x, panel.y, panel.width, ModulePanel.TITLE_BAR_HEIGHT));
    }

    static final class PatternWidgets {
        private final ModernTabButton craftTab;
        private final ModernTabButton processTab;
        private final ModernImgButton substituteEnabled;
        private final ModernImgButton substituteDisabled;
        private final ModernImgButton clear;
        private final ModernImgButton encode;

        PatternWidgets(ModernTabButton craftTab, ModernTabButton processTab,
                ModernImgButton substituteEnabled, ModernImgButton substituteDisabled,
                ModernImgButton clear, ModernImgButton encode) {
            this.craftTab = craftTab;
            this.processTab = processTab;
            this.substituteEnabled = substituteEnabled;
            this.substituteDisabled = substituteDisabled;
            this.clear = clear;
            this.encode = encode;
        }
    }
}
