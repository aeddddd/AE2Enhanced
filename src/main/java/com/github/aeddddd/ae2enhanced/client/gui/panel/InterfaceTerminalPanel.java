package com.github.aeddddd.ae2enhanced.client.gui.panel;

import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;
import appeng.client.render.BlockPosHighlighter;
import appeng.helpers.ItemStackHelper;
import appeng.helpers.PatternHelper;
import appeng.util.BlockPosUtils;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch;
import com.github.aeddddd.ae2enhanced.client.gui.widget.Ae2TextField;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.container.SplicedTermModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 原生接口终端的可拼接投影：供应器与样板内容仍由 AE2 原生容器同步。 */
public final class InterfaceTerminalPanel extends ModulePanel {
    public static final String MODULE_ID = SplicedTermModules.INTERFACE_TERMINAL_ID;
    private static final int SLOT_SIZE = 18;
    private static final int PROVIDER_HEADER = 10;
    private static final int FILTER_HEIGHT = 36;
    private static final int HIGHLIGHT_RAIL = 18;
    private static final String MOLECULAR_ASSEMBLER = "tile.appliedenergistics2.molecular_assembler";

    private final ContainerSplicedTerm container;
    private final Map<Long, Provider> providers = new LinkedHashMap<Long, Provider>();
    private final List<Slot> dynamicSlots = new ArrayList<Slot>();
    private final Ae2TextField inputs;
    private final Ae2TextField outputs;
    private final Ae2TextField names;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;
    private boolean onlyWithSpace;
    private boolean onlyAssemblers;
    private boolean onlyBroken;

    public InterfaceTerminalPanel(ContainerSplicedTerm container) {
        this.container = container;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        this.inputs = new Ae2TextField(0, font, 0, 0, 58);
        this.outputs = new Ae2TextField(1, font, 0, 0, 58);
        this.names = new Ae2TextField(2, font, 0, 0, 104);
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String title() {
        return I18n.format("gui.ae2enhanced.spliced_term.interface_terminal");
    }

    @Override
    public int minWidth() {
        // AE2 1.21 pattern access terminal：9 列物品 + 12px 滚动条 + 2px 间隔
        return HIGHLIGHT_RAIL + 9 * SLOT_SIZE + 12 + 2 + 2 * CONTENT_PADDING;
    }

    @Override
    public int defaultWidth() {
        return minWidth();
    }

    @Override
    public int minHeight() {
        return TITLE_BAR_HEIGHT + FILTER_HEIGHT + 2 * SLOT_SIZE + 2 * CONTENT_PADDING;
    }

    @Override
    public int defaultHeight() {
        return TITLE_BAR_HEIGHT + FILTER_HEIGHT + 5 * SLOT_SIZE + 2 * CONTENT_PADDING;
    }

    @Override
    public boolean expandsVertically() {
        return true;
    }

    public void postUpdate(NBTTagCompound data) {
        if (data.getBoolean("clear")) {
            providers.clear();
        }
        for (String key : data.getKeySet()) {
            if (!key.startsWith("=")) {
                continue;
            }
            try {
                long id = Long.parseLong(key.substring(1), 36);
                NBTTagCompound providerData = data.getCompoundTag(key);
                Provider provider = provider(id, providerData.getLong("sortBy"), providerData.getString("un"));
                provider.upgrades = providerData.getInteger("numUpgrades");
                provider.dimension = providerData.getInteger("dim");
                provider.pos = providerData.hasKey("pos") ? NBTUtil.getPosFromTag(providerData.getCompoundTag("pos")) : null;
                for (int slot = 0; slot < provider.inventory.getInventory().getSlots(); slot++) {
                    String itemKey = Integer.toString(slot);
                    if (providerData.hasKey(itemKey)) {
                        provider.inventory.getInventory().setStackInSlot(slot, ItemStackHelper.stackFromNBT(
                                providerData.getCompoundTag(itemKey)));
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        rebuildSlots();
    }

    private Provider provider(long id, long sortBy, String name) {
        Provider provider = providers.get(id);
        if (provider == null) {
            provider = new Provider(new ClientDCInternalInv(36, id, sortBy, name));
            providers.put(id, provider);
        }
        return provider;
    }

    private List<Provider> visibleProviders() {
        String input = inputs.getText().toLowerCase(Locale.ROOT).trim();
        String output = outputs.getText().toLowerCase(Locale.ROOT).trim();
        String name = names.getText().toLowerCase(Locale.ROOT).trim();
        List<Provider> result = new ArrayList<Provider>();
        for (Provider provider : providers.values()) {
            provider.matchedSlots.clear();
            boolean found = input.isEmpty() && output.isEmpty();
            boolean freeSlots = false;
            boolean broken = false;
            int activeSlots = Math.min(provider.inventory.getInventory().getSlots(), (provider.upgrades + 1) * 9);
            for (int slot = 0; slot < activeSlots; slot++) {
                ItemStack stack = provider.inventory.getInventory().getStackInSlot(slot);
                if (stack.isEmpty()) {
                    freeSlots = true;
                }
                if (onlyBroken && recipeIsBroken(stack)) {
                    broken = true;
                }
                if (!input.isEmpty() && itemStackMatchesSearchTerm(stack, input, 0)
                        || !output.isEmpty() && itemStackMatchesSearchTerm(stack, output, 1)) {
                    found = true;
                    provider.matchedSlots.add(slot);
                }
            }
            if (!found || !provider.inventory.getName().toLowerCase(Locale.ROOT).contains(name)
                    || onlyAssemblers && !MOLECULAR_ASSEMBLER.equals(provider.inventory.getUnlocalizedName())
                    || onlyWithSpace && !freeSlots || onlyBroken && !broken) {
                continue;
            }
            result.add(provider);
        }
        result.sort((left, right) -> {
            int compared = left.inventory.compareTo(right.inventory);
            return compared != 0 ? compared : left.inventory.getName().compareTo(right.inventory.getName());
        });
        return result;
    }

    private boolean recipeIsBroken(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getTagCompound() == null) {
            return true;
        }
        if (Minecraft.getMinecraft().world == null) {
            return false;
        }
        try {
            new PatternHelper(stack, Minecraft.getMinecraft().world);
            return false;
        } catch (Throwable exception) {
            return true;
        }
    }

    private boolean itemStackMatchesSearchTerm(ItemStack stack, String searchTerm, int pass) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        NBTTagCompound encoded = stack.getTagCompound();
        if (encoded == null) {
            return searchTerm.matches(appeng.core.localization.GuiText.InvalidPattern.getLocal());
        }
        NBTTagList tag = encoded.getTagList(pass == 0 ? "in" : "out", 10);
        String[] terms = searchTerm.split(" ");
        boolean found = false;
        for (int index = 0; index < tag.tagCount(); index++) {
            ItemStack parsed = new ItemStack(tag.getCompoundTagAt(index));
            if (parsed.isEmpty()) {
                continue;
            }
            String display = Platform.getItemDisplayName(appeng.api.AEApi.instance().storage()
                    .getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class)
                    .createStack(parsed)).toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                    if (display.contains(term.substring(1))) {
                        return false;
                    }
                } else if (display.contains(term)) {
                    found = true;
                }
            }
        }
        return found;
    }

    private void rebuildSlots() {
        List<Slot> previous = new ArrayList<Slot>(dynamicSlots);
        for (Slot slot : previous) {
            unregisterSlot(slot);
        }
        dynamicSlots.clear();
        for (Provider provider : visibleProviders()) {
            int count = Math.min(provider.inventory.getInventory().getSlots(), (provider.upgrades + 1) * 9);
            for (int slotIndex = 0; slotIndex < count; slotIndex++) {
                SlotDisconnected slot = new SlotDisconnected(provider.inventory, slotIndex, 0, 0);
                registerSlot(slot);
                dynamicSlots.add(slot);
            }
        }
        com.github.aeddddd.ae2enhanced.container.DynamicSlotTable.replace(
                container.inventorySlots, previous, dynamicSlots);
        layoutSlots();
    }

    private int slotLeft() {
        return contentLeft() + HIGHLIGHT_RAIL;
    }

    /** 每帧守卫：窗口被拖动/缩放/合并后按新的内容尺寸重排供应器列表。 */
    public void tick() {
        if (contentWidth() != laidOutWidth || contentHeight() != laidOutHeight) {
            layoutSlots();
        }
    }

    @Override
    public void layoutSlots() {
        this.laidOutWidth = contentWidth();
        this.laidOutHeight = contentHeight();
        if (!visible) {
            for (Slot slot : dynamicSlots) {
                hideSlot(slot);
            }
            container.slotPolicy().setModuleSlots(MODULE_ID, Collections.<Slot>emptyList());
            return;
        }
        container.slotPolicy().setModuleSlots(MODULE_ID, dynamicSlots);
        layoutSearchFields();
        List<Provider> visible = visibleProviders();
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, visible.size() - 1)));
        int top = contentTop() + FILTER_HEIGHT;
        int providerSlotBase = 0;
        for (int index = 0; index < visible.size(); index++) {
            Provider provider = visible.get(index);
            int rows = provider.upgrades + 1;
            int providerSlotCount = Math.min(dynamicSlots.size() - providerSlotBase, rows * 9);
            if (index >= scrollOffset
                    && top + PROVIDER_HEADER + rows * SLOT_SIZE <= contentTop() + contentHeight()) {
                for (int slot = 0; slot < providerSlotCount; slot++) {
                    placeSlot(dynamicSlots.get(providerSlotBase + slot),
                            slotLeft() + (slot % 9) * SLOT_SIZE + 1,
                            top + PROVIDER_HEADER + (slot / 9) * SLOT_SIZE + 1);
                }
                top += PROVIDER_HEADER + rows * SLOT_SIZE;
            } else {
                for (int slot = 0; slot < providerSlotCount; slot++) {
                    hideSlot(dynamicSlots.get(providerSlotBase + slot));
                }
            }
            providerSlotBase += providerSlotCount;
        }
        while (providerSlotBase < dynamicSlots.size()) {
            hideSlot(dynamicSlots.get(providerSlotBase++));
        }
    }

    private void layoutSearchFields() {
        inputs.setBounds(slotLeft(), contentTop() + 1, 58);
        outputs.setBounds(slotLeft() + 62, contentTop() + 1, 58);
        names.setBounds(slotLeft(), contentTop() + 17, 104);
    }

    private int scrollBarLeft() {
        return contentLeft() + HIGHLIGHT_RAIL + 9 * SLOT_SIZE + 2;
    }

    private int scrollBarTop() {
        return contentTop() + FILTER_HEIGHT;
    }

    private int scrollBarHeight() {
        return Math.max(18, contentHeight() - FILTER_HEIGHT - 2);
    }

    private boolean inScrollBar(double mx, double my) {
        int left = scrollBarLeft();
        int top = scrollBarTop();
        return mx >= left && mx < left + 12 && my >= top && my < top + scrollBarHeight();
    }

    private void scrollTo(double my) {
        int max = Math.max(0, visibleProviders().size() - 1);
        float ratio = (float) ((my - scrollBarTop()) / (double) Math.max(1, scrollBarHeight() - 15));
        scrollOffset = Math.max(0, Math.min(max, Math.round(ratio * max)));
        layoutSlots();
    }

    @Override
    public boolean scrollbarPressed(double mx, double my) {
        if (!visible || !inScrollBar(mx, my)) {
            return false;
        }
        this.draggingScrollbar = true;
        scrollTo(my);
        return true;
    }

    @Override
    public boolean scrollbarDragged(double mx, double my) {
        if (!draggingScrollbar) {
            return false;
        }
        scrollTo(my);
        return true;
    }

    @Override
    public void scrollbarReleased() {
        this.draggingScrollbar = false;
    }

    @Override
    public boolean scrollbarDragging() {
        return this.draggingScrollbar;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (scrollY == 0 || providers.isEmpty()) {
            return false;
        }
        int max = Math.max(0, visibleProviders().size() - 1);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(scrollY)));
        layoutSlots();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !visible) {
            return false;
        }
        inputs.mouseClicked((int) mx, (int) my, button);
        outputs.mouseClicked((int) mx, (int) my, button);
        names.mouseClicked((int) mx, (int) my, button);
        if (inputs.isMouseIn((int) mx, (int) my) || outputs.isMouseIn((int) mx, (int) my)
                || names.isMouseIn((int) mx, (int) my)) {
            return true;
        }
        int filter = filterAt(mx, my);
        if (filter >= 0) {
            if (filter == 0) {
                onlyAssemblers = !onlyAssemblers;
            } else if (filter == 1) {
                onlyWithSpace = !onlyWithSpace;
            } else {
                onlyBroken = !onlyBroken;
            }
            scrollOffset = 0;
            rebuildSlots();
            return true;
        }
        List<Provider> visible = visibleProviders();
        int top = contentTop() + FILTER_HEIGHT;
        for (int index = scrollOffset; index < visible.size(); index++) {
            Provider provider = visible.get(index);
            int height = PROVIDER_HEADER + (provider.upgrades + 1) * SLOT_SIZE;
            if (top + height > contentTop() + contentHeight()) {
                break;
            }
            if (my >= top && my < top + PROVIDER_HEADER && mx >= contentLeft() && mx < slotLeft()) {
                highlight(provider);
                return true;
            }
            top += height;
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!visible || !inputs.isFocused() && !outputs.isFocused() && !names.isFocused()) {
            return false;
        }
        boolean handled = inputs.textboxKeyTyped(typedChar, keyCode)
                | outputs.textboxKeyTyped(typedChar, keyCode)
                | names.textboxKeyTyped(typedChar, keyCode);
        if (handled) {
            scrollOffset = 0;
            rebuildSlots();
        }
        return handled;
    }

    private int filterAt(double mx, double my) {
        if (my < contentTop() + 17 || my >= contentTop() + 33) {
            return -1;
        }
        for (int index = 0; index < 3; index++) {
            int left = slotLeft() + 108 + index * 18;
            if (mx >= left && mx < left + 16) {
                return index;
            }
        }
        return -1;
    }

    private void highlight(Provider provider) {
        if (provider.pos == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player.dimension != provider.dimension) {
            try {
                mc.player.sendMessage(appeng.core.localization.PlayerMessages.InterfaceInOtherDimParam.get(
                        provider.dimension,
                        DimensionManager.getWorld(provider.dimension).provider.getDimensionType().getName()));
            } catch (Exception exception) {
                mc.player.sendMessage(appeng.core.localization.PlayerMessages.InterfaceInOtherDim.get());
            }
        } else {
            BlockPosHighlighter.hilightBlock(provider.pos,
                    System.currentTimeMillis() + 500L * BlockPosUtils.getDistance(provider.pos,
                            mc.player.getPosition()), provider.dimension);
            mc.player.sendMessage(appeng.core.localization.PlayerMessages.InterfaceHighlighted.get(
                    provider.pos.getX(), provider.pos.getY(), provider.pos.getZ()));
        }
        mc.displayGuiScreen(null);
    }

    @Override
    public void renderBackgroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        if (!visible) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        boolean pushed = NinePatch.pushZLevel(zLevel);
        try {
            List<Provider> visible = visibleProviders();
            if (visible.isEmpty()) {
                font.drawString(I18n.format("gui.ae2enhanced.spliced_term.interface_terminal.empty"),
                        slotLeft() + 4, contentTop() + FILTER_HEIGHT + 4, Ae2ModernArt.MUTED, false);
            }
            int top = contentTop() + FILTER_HEIGHT;
            Map<String, Integer> groupSizes = new HashMap<String, Integer>();
            for (Provider provider : visible) {
                String name = provider.inventory.getName();
                groupSizes.put(name, groupSizes.getOrDefault(name, 0) + 1);
            }
            for (int index = scrollOffset; index < visible.size(); index++) {
                Provider provider = visible.get(index);
                int rows = provider.upgrades + 1;
                if (top + PROVIDER_HEADER + rows * SLOT_SIZE > contentTop() + contentHeight()) {
                    break;
                }
                String name = provider.inventory.getName();
                int groupSize = groupSizes.get(name);
                if (groupSize > 1) {
                    name += " (" + groupSize + ')';
                }
                font.drawString(font.trimStringToWidth(name, contentWidth() - HIGHLIGHT_RAIL - 4),
                        slotLeft() + 2, top + 1, Ae2ModernArt.OUTLINE, false);
                top += PROVIDER_HEADER;
                for (int row = 0; row < rows; row++) {
                    for (int column = 0; column < 9; column++) {
                        if (provider.matchedSlots.contains(row * 9 + column)) {
                            Gui.drawRect(slotLeft() + column * SLOT_SIZE, top,
                                    slotLeft() + column * SLOT_SIZE + 16, top + 16, 0x2a66ff66);
                        }
                    }
                    PlayerInvPanel.drawSlotBand(slotLeft(), top, 9, 1, zLevel);
                    top += SLOT_SIZE;
                }
            }
            renderSearchField(inputs, "inputs", mouseX, mouseY);
            renderSearchField(outputs, "outputs", mouseX, mouseY);
            renderSearchField(names, "names", mouseX, mouseY);
            renderFilters(mouseX, mouseY);
            int scrollLeft = contentLeft() + HIGHLIGHT_RAIL + 9 * SLOT_SIZE + 2;
            int scrollHeight = Math.max(18, contentHeight() - FILTER_HEIGHT - 2);
            NinePatch.drawScrollbar(scrollLeft, contentTop() + FILTER_HEIGHT, 12, scrollHeight,
                    scrollOffset, Math.max(0, visible.size() - 1), true, zLevel);
        } finally {
            NinePatch.popZLevel(pushed);
        }
    }

    private void renderSearchField(Ae2TextField field, String key, int mouseX, int mouseY) {
        field.drawTextBox();
        if (field.getText().isEmpty() && !field.isFocused()) {
            Minecraft.getMinecraft().fontRenderer.drawString(
                    I18n.format("gui.ae2enhanced.spliced_term.interface_terminal.search." + key),
                    field.x + 2, field.y + 3, Ae2ModernArt.MUTED, false);
        }
    }

    private void renderFilters(int mouseX, int mouseY) {
        for (int index = 0; index < 3; index++) {
            int left = slotLeft() + 108 + index * 18;
            int top = contentTop() + 17;
            boolean hovered = mouseX >= left && mouseX < left + 16 && mouseY >= top && mouseY < top + 16;
            boolean active = index == 0 ? onlyAssemblers : index == 1 ? onlyWithSpace : onlyBroken;
            Ae2ModernArt.drawToolbarButton(left - 1, top, hovered || active, true);
            String label = index == 0 ? "A" : index == 1 ? "F" : "B";
            Minecraft.getMinecraft().fontRenderer.drawString(label, left + 5, top + 5,
                    active ? Ae2ModernArt.HIGHLIGHT : Ae2ModernArt.OUTLINE, false);
        }
    }

    @Override
    public void renderForegroundContent(int mouseX, int mouseY, float partialTicks, float zLevel) {
        int filter = filterAt(mouseX, mouseY);
        if (filter >= 0 && Minecraft.getMinecraft().currentScreen != null) {
            String key = filter == 0 ? "assemblers" : filter == 1 ? "with_space" : "broken";
            Minecraft.getMinecraft().currentScreen.drawHoveringText(Collections.singletonList(
                    I18n.format("gui.ae2enhanced.spliced_term.interface_terminal.filter." + key)), mouseX, mouseY);
        }
    }

    public int harnessProviderCount() {
        return providers.size();
    }

    public int harnessDynamicSlotCount() {
        return dynamicSlots.size();
    }

    public int harnessEncodedPatternCount() {
        int count = 0;
        for (Provider provider : providers.values()) {
            for (int index = 0; index < provider.inventory.getInventory().getSlots(); index++) {
                if (provider.inventory.getInventory().getStackInSlot(index).getItem()
                        instanceof appeng.items.misc.ItemEncodedPattern) {
                    count++;
                }
            }
        }
        return count;
    }

    private static final class Provider {
        private final ClientDCInternalInv inventory;
        private final Set<Integer> matchedSlots = new HashSet<Integer>();
        private int upgrades;
        private int dimension;
        private BlockPos pos;

        private Provider(ClientDCInternalInv inventory) {
            this.inventory = inventory;
        }
    }
}
