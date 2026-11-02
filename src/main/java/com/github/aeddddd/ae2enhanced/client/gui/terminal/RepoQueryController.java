package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import java.util.function.Supplier;

import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.client.gui.widgets.GuiScrollbar;

import com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel;
import com.github.aeddddd.ae2enhanced.client.gui.terminal.access.IGuiMEMonitorableAccessor;
import com.github.aeddddd.ae2enhanced.client.gui.widget.Ae2TextField;
import com.github.aeddddd.ae2enhanced.client.me.JeiSearchHelper;
import com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult;

import net.minecraft.item.ItemStack;

final class RepoQueryController {
    private final OmniItemRepo repo;
    private final RepoPanel panel;
    private final Ae2TextField searchField;
    private final GuiScrollbar scrollBar;
    private final ContainerSplicedTerm container;
    private final IConfigManager config;
    private final Supplier<Enum> searchMode;
    private final ItemStack[] viewCells = new ItemStack[5];

    private boolean initialized;
    private boolean autoFocusSearch;
    private long appliedLayout = -1L;
    private long renderedViewVersion = -1L;
    private int lastScrollPosition = -1;
    private int updateNotifyCount;

    RepoQueryController(OmniItemRepo repo, RepoPanel panel, Ae2TextField searchField,
            GuiScrollbar scrollBar, ContainerSplicedTerm container, IConfigManager config,
            Supplier<Enum> searchMode) {
        this.repo = repo;
        this.panel = panel;
        this.searchField = searchField;
        this.scrollBar = scrollBar;
        this.container = container;
        this.config = config;
        this.searchMode = searchMode;
    }

    private static boolean isAutoFocus(Enum mode) {
        return mode == SearchBoxMode.AUTOSEARCH || mode == SearchBoxMode.JEI_AUTOSEARCH
                || mode == SearchBoxMode.AUTOSEARCH_KEEP || mode == SearchBoxMode.JEI_AUTOSEARCH_KEEP;
    }

    private static boolean isSearchModeKeep(Enum mode) {
        return mode == SearchBoxMode.AUTOSEARCH_KEEP || mode == SearchBoxMode.JEI_AUTOSEARCH_KEEP
                || mode == SearchBoxMode.MANUAL_SEARCH_KEEP || mode == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP;
    }

    private static boolean isSearchModeJei(Enum mode) {
        return mode == SearchBoxMode.JEI_AUTOSEARCH || mode == SearchBoxMode.JEI_MANUAL_SEARCH
                || mode == SearchBoxMode.JEI_AUTOSEARCH_KEEP || mode == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP;
    }

    void init() {
        autoFocusSearch = isAutoFocus(searchMode.get());
        if (!initialized) {
            initialized = true;
            if (isSearchModeJei(searchMode.get())) {
                String jeiText = JeiSearchHelper.getJeiFilterText();
                if (jeiText != null && !jeiText.isEmpty()) {
                    IGuiMEMonitorableAccessor.ae2e$setMemoryText(jeiText);
                }
            }
            if (isSearchModeKeep(searchMode.get())) {
                String remembered = IGuiMEMonitorableAccessor.ae2e$getMemoryText();
                if (remembered != null && !remembered.isEmpty() && searchField.getText().isEmpty()) {
                    searchField.setText(remembered);
                    searchField.selectAll();
                }
            }
            searchField.setFocused(autoFocusSearch);
            pushQuery();
        } else {
            searchField.setFocused(autoFocusSearch);
        }
        applySlots();
    }

    void tick() {
        if (panel.layoutVersion() != appliedLayout) {
            applySlots();
        }
        long viewVersion = repo.getRenderViewVersion();
        if (viewVersion != renderedViewVersion) {
            renderedViewVersion = viewVersion;
            refreshScrollRange();
        }
        if (scrollBar.getCurrentScroll() != lastScrollPosition) {
            applyScroll();
        }
        syncViewCells();
        repo.updateView();
    }

    boolean autoFocusSearch() {
        return autoFocusSearch;
    }

    void applySlots() {
        appliedLayout = panel.layoutVersion();
        panel.applySlots(container.inventorySlots);
        container.slotPolicy().setRepoSlots(panel.hostedSlots());
        refreshScrollRange();
    }

    void pushQuery() {
        String text = searchField.getText() == null ? "" : searchField.getText();
        boolean modSearch = text.startsWith("@");
        String query = text;
        if (modSearch || query.startsWith("#")) {
            query = query.substring(1).trim();
        }
        repo.onQueryParamsChanged(query, (byte) (modSearch ? 1 : 0),
                settingOrdinal(Settings.SORT_BY), settingOrdinal(Settings.SORT_DIRECTION),
                settingOrdinal(Settings.VIEW_MODE));
        repo.setSearchString(text);
        resetScroll();
    }

    void pushSearchText() {
        repo.setSearchString(searchField.getText());
        resetScroll();
        refreshScrollRange();
    }

    void applyScroll() {
        int currentScroll = scrollBar.getCurrentScroll();
        lastScrollPosition = currentScroll;
        repo.onScrollChanged(currentScroll * Math.max(1, repo.getRowSize()));
    }

    void refreshScrollRange() {
        boolean pinned = !container.getClientActiveCrafting().isEmpty();
        int maxScroll = RepoPanel.maxScrollRows(repo.size(), panel.columns(), panel.rows(), pinned);
        scrollBar.setRange(0, maxScroll, 1);
        if (scrollBar.getCurrentScroll() != lastScrollPosition) {
            applyScroll();
        }
    }

    void handlePageResult(PacketOmniPageResult packet) {
        repo.handlePageResult(packet.getTotalCount(), packet.getOffset(), packet.getEntries());
        refreshScrollRange();
    }

    void handleUpdateNotify() {
        updateNotifyCount++;
        repo.handleUpdateNotify();
    }

    int updateNotifyCount() {
        return updateNotifyCount;
    }

    private void resetScroll() {
        scrollBar.setRange(0, 0, 1);
        lastScrollPosition = scrollBar.getCurrentScroll();
    }

    private void syncViewCells() {
        boolean update = false;
        for (int index = 0; index < viewCells.length; index++) {
            ItemStack current = container.getCellViewSlot(index).getStack();
            if (viewCells[index] == current) {
                continue;
            }
            update = true;
            viewCells[index] = current;
        }
        if (update) {
            repo.setViewCell(viewCells);
        }
    }

    private byte settingOrdinal(Settings setting) {
        Enum value = config.getSetting(setting);
        return (byte) (value != null ? value.ordinal() : 0);
    }
}
