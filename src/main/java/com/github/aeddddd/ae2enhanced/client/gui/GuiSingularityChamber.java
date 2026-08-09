package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerSingularityChamber;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChamberAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChamberCatalog;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChamberSync;
import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 奇点处理仓 GUI.
 *
 * <p><b>主页</b>：能量条、9×3 输入缓存（点击取回一组 / shift 点击全部取回）、
 * 输出缓冲行、卡片槽、红石模式按钮.</p>
 * <p><b>配方页</b>：全部可处理配方目录,点击逐条启用/禁用（黑名单即时生效）,
 * 滚轮翻页.</p>
 *
 * <p>shift-click 玩家背包中的非卡片物品会直接倒入原料缓存.</p>
 */
public class GuiSingularityChamber extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/singularity_chamber.png");

    private static final int TEXT = 0xFFCCCCCC;
    private static final int TEXT_DIM = 0xFF888888;
    private static final int TEXT_DISABLED = 0xFF555555;
    private static final int ACCENT = 0xFFB048E0;

    private static final int PAGE_MAIN = 0;
    private static final int PAGE_RECIPES = 1;
    private static final int RECIPE_ROW_HEIGHT = 22;
    private static final int RECIPE_ROWS_VISIBLE = 8;

    private final BlockPos pos;

    private int page = PAGE_MAIN;
    private int recipeScroll = 0;

    // ---- 同步数据 ----
    private int energy = 0;
    private long parallelChannels = 1;
    private long usedChannels = 0;
    private int activeJobs = 0;
    private int redstoneMode = 0;
    private Set<String> disabledRecipes = new HashSet<>();
    private List<ItemStack> inputItems = new ArrayList<>();
    private List<Long> inputCounts = new ArrayList<>();
    private List<ItemStack> outputItems = new ArrayList<>();
    private List<Long> outputCounts = new ArrayList<>();
    private List<PacketChamberCatalog.RecipeView> catalog = new ArrayList<>();

    public GuiSingularityChamber(InventoryPlayer playerInv, TileSingularityChamber tile) {
        super(new ContainerSingularityChamber(playerInv, tile));
        this.pos = tile.getPos();
        this.xSize = 232;
        this.ySize = 216;
    }

    public void acceptSync(PacketChamberSync packet) {
        if (!packet.getPos().equals(pos)) {
            return;
        }
        this.energy = packet.getEnergy();
        this.parallelChannels = packet.getParallelChannels();
        this.usedChannels = packet.getUsedChannels();
        this.activeJobs = packet.getActiveJobs();
        this.redstoneMode = packet.getRedstoneMode();
        this.disabledRecipes = new HashSet<>(packet.getDisabledRecipes());
        this.inputItems = packet.getInputItems();
        this.inputCounts = packet.getInputCounts();
        this.outputItems = packet.getOutputItems();
        this.outputCounts = packet.getOutputCounts();
    }

    public void acceptCatalog(List<PacketChamberCatalog.RecipeView> recipes) {
        this.catalog = recipes;
    }

    // ---- 背景 ----

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // 页签高亮
        int tabX = page == PAGE_MAIN ? guiLeft + 196 : guiLeft + 214;
        drawRect(tabX, guiTop + 4, tabX + 14, guiTop + 16, 0xFF3D2A52);
        drawRect(tabX + 2, guiTop + 15, tabX + 12, guiTop + 16, ACCENT);
        fontRenderer.drawString(page == PAGE_MAIN ? "M" : "R",
                (page == PAGE_MAIN ? guiLeft + 200 : guiLeft + 218), guiTop + 6, TEXT);

        if (page == PAGE_MAIN) {
            drawMainPage();
        } else {
            drawRecipePage();
        }
    }

    private void drawMainPage() {
        // 能量条
        double ratio = Math.min(1.0, energy / (double) Integer.MAX_VALUE);
        int fill = (int) (100 * ratio);
        if (energy > 0 && fill == 0) {
            fill = 1;
        }
        drawRect(guiLeft + 8, guiTop + 118 - fill, guiLeft + 20, guiTop + 118, 0xFF8A2BE2);

        // 输入缓存
        drawStoreGrid(26, 18, 9, 3, inputItems, inputCounts);
        // 输出缓存
        drawStoreGrid(26, 84, 9, 1, outputItems, outputCounts);

        // 红石模式图标
        drawRedstoneIcon(guiLeft + 140, guiTop + 108);
    }

    private void drawRedstoneIcon(int x, int y) {
        int color;
        switch (redstoneMode) {
            case 1: color = 0xFFFF4444; break;   // HIGH：红色
            case 2: color = 0xFF4444FF; break;   // LOW：蓝色
            default: color = 0xFF666666; break;  // IGNORE：灰色
        }
        drawRect(x + 3, y + 3, x + 13, y + 13, 0xFF000000 | (color & 0x00FFFFFF));
        drawRect(x + 5, y + 5, x + 11, y + 11, 0xFF000000 | ((color & 0x00FEFEFE) >> 1));
    }

    private void drawStoreGrid(int baseX, int baseY, int cols, int rows,
                               List<ItemStack> items, List<Long> counts) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= items.size()) {
                    break;
                }
                ItemStack stack = items.get(idx);
                if (stack.isEmpty()) {
                    continue;
                }
                int x = guiLeft + baseX + col * 18;
                int y = guiTop + baseY + row * 18;
                itemRender.renderItemAndEffectIntoGUI(stack, x, y);
                drawCount(formatCount(counts.get(idx)), x, y);
            }
        }
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawCount(String text, int slotX, int slotY) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 260);
        float scale = text.length() > 4 ? 0.5f : 0.75f;
        GlStateManager.scale(scale, scale, 1.0f);
        int w = fontRenderer.getStringWidth(text);
        fontRenderer.drawString(text,
                (int) ((slotX + 17 - w * scale) / scale),
                (int) ((slotY + 18 - 9 * scale) / scale),
                0xFFFFFFFF, true);
        GlStateManager.popMatrix();
    }

    private void drawRecipePage() {
        // 遮盖主页烘焙的槽位纹理,避免视觉干扰（滚动轨道保留）
        drawRect(guiLeft + 4, guiTop + 17, guiLeft + 221, guiTop + 199, 0xFF1B1B24);

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int row = 0; row < RECIPE_ROWS_VISIBLE; row++) {
            int idx = recipeScroll + row;
            if (idx >= catalog.size()) {
                break;
            }
            PacketChamberCatalog.RecipeView recipe = catalog.get(idx);
            int y = guiTop + 18 + row * RECIPE_ROW_HEIGHT;
            boolean enabled = !disabledRecipes.contains(recipe.id);

            // 启用指示块
            drawRect(guiLeft + 8, y + 3, guiLeft + 18, y + 13,
                    enabled ? 0xFF2E7D32 : 0xFF5A1A1A);
            fontRenderer.drawString(enabled ? "ON" : "--", guiLeft + 10, y + 4,
                    enabled ? 0xFF9EFF9E : 0xFFFF9E9E);

            // 输入图标（最多 4 个）
            int ix = guiLeft + 24;
            int shown = Math.min(4, recipe.inputTemplates.size());
            for (int j = 0; j < shown; j++) {
                ItemStack stack = recipe.inputTemplates.get(j);
                if (!stack.isEmpty()) {
                    itemRender.renderItemAndEffectIntoGUI(stack, ix, y + 2);
                    long count = recipe.inputCounts.get(j);
                    if (count > 1) {
                        drawCount(String.valueOf(count), ix, y + 2);
                    }
                }
                ix += 17;
            }
            if (recipe.inputTemplates.size() > 4) {
                fontRenderer.drawString("+" + (recipe.inputTemplates.size() - 4), ix, y + 6, TEXT_DIM);
                ix += 10;
            }

            // 箭头 + 输出
            fontRenderer.drawString("->", guiLeft + 104, y + 6, enabled ? TEXT : TEXT_DISABLED);
            if (!recipe.output.isEmpty()) {
                itemRender.renderItemAndEffectIntoGUI(recipe.output, guiLeft + 118, y + 2);
            }

            // 时间与状态
            String timeText = recipe.timeTicks + "t";
            fontRenderer.drawString(timeText, guiLeft + 140, y + 6, TEXT_DIM);
            if (!enabled) {
                drawRect(guiLeft + 6, y, guiLeft + 220, y + 16, 0x80000000);
            }
        }
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // 滚动条
        if (catalog.size() > RECIPE_ROWS_VISIBLE) {
            int trackH = 180;
            int barH = Math.max(16, trackH * RECIPE_ROWS_VISIBLE / catalog.size());
            int maxScroll = catalog.size() - RECIPE_ROWS_VISIBLE;
            int barY = guiTop + 18 + (trackH - barH) * recipeScroll / maxScroll;
            drawRect(guiLeft + 223, barY, guiLeft + 229, barY + barH, 0xFF5A4A7A);
        }
    }

    // ---- 前景文字 ----

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.chamber.title"), 8, 6, TEXT);
        if (page == PAGE_MAIN) {
            String parallelText = parallelChannels == Long.MAX_VALUE ? "∞" : String.valueOf(parallelChannels);
            String usedText = usedChannels == Long.MAX_VALUE ? "∞" : String.valueOf(usedChannels);
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.chamber.status",
                    usedText, parallelText, activeJobs), 90, 6, TEXT_DIM);
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.chamber.output"), 26, 74, TEXT_DIM);
        } else {
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.chamber.recipes",
                    catalog.size(), disabledRecipes.size()), 90, 6, TEXT_DIM);
        }
    }

    // ---- 交互 ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // 页签
        if (inRect(mouseX, mouseY, guiLeft + 196, guiTop + 4, 14, 12)) {
            switchPage(PAGE_MAIN);
            return;
        }
        if (inRect(mouseX, mouseY, guiLeft + 214, guiTop + 4, 14, 12)) {
            switchPage(PAGE_RECIPES);
            return;
        }

        if (page == PAGE_MAIN) {
            // 红石按钮
            if (inRect(mouseX, mouseY, guiLeft + 139, guiTop + 107, 18, 18)) {
                sendAction(PacketChamberAction.ACTION_CYCLE_REDSTONE, "");
                return;
            }
            // 输入缓存取回
            int idx = gridIndexAt(mouseX, mouseY, 26, 18, 9, 3, inputItems.size());
            if (idx >= 0) {
                ItemStack stack = inputItems.get(idx);
                String key = com.github.aeddddd.ae2enhanced.chamber.LongItemStore.keyOf(stack);
                sendAction(isShiftKeyDown()
                        ? PacketChamberAction.ACTION_WITHDRAW_ALL
                        : PacketChamberAction.ACTION_WITHDRAW_STACK, key);
                return;
            }
        } else {
            // 配方启用切换
            for (int row = 0; row < RECIPE_ROWS_VISIBLE; row++) {
                int idx = recipeScroll + row;
                if (idx >= catalog.size()) {
                    break;
                }
                int y = guiTop + 18 + row * RECIPE_ROW_HEIGHT;
                if (inRect(mouseX, mouseY, guiLeft + 6, y, 214, 16)) {
                    sendAction(PacketChamberAction.ACTION_TOGGLE_RECIPE, catalog.get(idx).id);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (page == PAGE_RECIPES && catalog.size() > RECIPE_ROWS_VISIBLE) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                int maxScroll = catalog.size() - RECIPE_ROWS_VISIBLE;
                recipeScroll = Math.max(0, Math.min(maxScroll, recipeScroll + (wheel < 0 ? 1 : -1)));
            }
        }
    }

    private void switchPage(int target) {
        page = target;
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(
                SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private void sendAction(int action, String param) {
        AE2Enhanced.network.sendToServer(new PacketChamberAction(pos, action, param));
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(
                SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int gridIndexAt(int mx, int my, int baseX, int baseY, int cols, int rows, int size) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= size) {
                    return -1;
                }
                if (inRect(mx, my, guiLeft + baseX + col * 18, guiTop + baseY + row * 18, 18, 18)) {
                    return idx;
                }
            }
        }
        return -1;
    }

    // ---- Tooltip ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);

        // 能量条 tooltip
        if (inRect(mouseX, mouseY, guiLeft + 7, guiTop + 17, 14, 102)) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add(I18n.format("gui.ae2enhanced.chamber.energy",
                    formatCount(energy), formatCount(Integer.MAX_VALUE)));
            drawHoveringText(tooltip, mouseX, mouseY);
            return;
        }

        if (page == PAGE_MAIN) {
            // 红石按钮 tooltip
            if (inRect(mouseX, mouseY, guiLeft + 139, guiTop + 107, 18, 18)) {
                List<String> tooltip = new ArrayList<>();
                tooltip.add(I18n.format("gui.ae2enhanced.chamber.redstone." + redstoneMode));
                drawHoveringText(tooltip, mouseX, mouseY);
                return;
            }
            // 输入缓存 tooltip
            int idx = gridIndexAt(mouseX, mouseY, 26, 18, 9, 3, inputItems.size());
            if (idx >= 0) {
                ItemStack stack = inputItems.get(idx);
                if (!stack.isEmpty()) {
                    List<String> tooltip = getItemToolTip(stack);
                    tooltip.add(I18n.format("gui.ae2enhanced.chamber.count", inputCounts.get(idx)));
                    tooltip.add(I18n.format("gui.ae2enhanced.chamber.withdraw_hint"));
                    drawHoveringText(tooltip, mouseX, mouseY);
                    return;
                }
            }
            int outIdx = gridIndexAt(mouseX, mouseY, 26, 84, 9, 1, outputItems.size());
            if (outIdx >= 0) {
                ItemStack stack = outputItems.get(outIdx);
                if (!stack.isEmpty()) {
                    List<String> tooltip = getItemToolTip(stack);
                    tooltip.add(I18n.format("gui.ae2enhanced.chamber.count", outputCounts.get(outIdx)));
                    drawHoveringText(tooltip, mouseX, mouseY);
                }
            }
        } else {
            // 配方行 tooltip
            for (int row = 0; row < RECIPE_ROWS_VISIBLE; row++) {
                int idx = recipeScroll + row;
                if (idx >= catalog.size()) {
                    break;
                }
                int y = guiTop + 18 + row * RECIPE_ROW_HEIGHT;
                if (inRect(mouseX, mouseY, guiLeft + 6, y, 214, 16)) {
                    PacketChamberCatalog.RecipeView recipe = catalog.get(idx);
                    List<String> tooltip = new ArrayList<>();
                    tooltip.add(recipe.output.isEmpty() ? recipe.id
                            : recipe.output.getDisplayName());
                    tooltip.add(I18n.format("gui.ae2enhanced.chamber.recipe_time", recipe.timeTicks));
                    tooltip.add(I18n.format(disabledRecipes.contains(recipe.id)
                            ? "gui.ae2enhanced.chamber.recipe_disabled"
                            : "gui.ae2enhanced.chamber.recipe_enabled"));
                    drawHoveringText(tooltip, mouseX, mouseY);
                    return;
                }
            }
        }
    }

    static String formatCount(long count) {
        if (count == Long.MAX_VALUE) {
            return "∞";
        }
        if (count < 1000) {
            return String.valueOf(count);
        }
        String[] units = {"k", "M", "G", "T", "P", "E"};
        double value = count;
        int unit = -1;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000.0;
            unit++;
        }
        return String.format("%.1f%s", value, units[unit]);
    }
}
