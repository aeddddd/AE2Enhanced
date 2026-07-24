package com.github.aeddddd.ae2enhanced.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;

import com.github.aeddddd.ae2enhanced.common.menu.PersonalDimensionCreateMenu;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimCreateSubmitPacket;

/**
 * 个人维度创建向导界面:确定地板颜色方案并确认后才会创建维度.
 */
public class PersonalDimensionCreateScreen extends AbstractContainerScreen<PersonalDimensionCreateMenu> {

    private DyeColor roadColor = DyeColor.GRAY;
    private DyeColor lineColor = DyeColor.WHITE;
    private DyeColor platformColor = DyeColor.BLACK;

    public PersonalDimensionCreateScreen(PersonalDimensionCreateMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int x = leftPos + 10;
        int y = topPos + 30;

        addRenderableWidget(colorButton(x, y, "gui.ae2enhanced.pdim.color.road_base", roadColor,
                () -> roadColor = nextColor(roadColor)));
        y += 22;
        addRenderableWidget(colorButton(x, y, "gui.ae2enhanced.pdim.color.road_line", lineColor,
                () -> lineColor = nextColor(lineColor)));
        y += 22;
        addRenderableWidget(colorButton(x, y, "gui.ae2enhanced.pdim.color.platform_base", platformColor,
                () -> platformColor = nextColor(platformColor)));
        y += 30;

        addRenderableWidget(Button.builder(Component.translatable("gui.ae2enhanced.pdim.create"), b -> {
            ModNetwork.CHANNEL.sendToServer(new PersonalDimCreateSubmitPacket(
                    roadColor.getId(), lineColor.getId(), platformColor.getId()));
        }).bounds(x, y, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.ae2enhanced.pdim.cancel"),
                b -> onClose()).bounds(x + 80, y, 72, 20).build());
    }

    private Button colorButton(int x, int y, String labelKey, DyeColor color, Runnable onClick) {
        Component label = Component.translatable(labelKey)
                .append(": ")
                .append(Component.translatable("block.minecraft." + color.getName() + "_concrete"));
        return Button.builder(label, b -> {
            onClick.run();
            rebuildWidgets();
        }).bounds(x, y, 204, 18).build();
    }

    private static DyeColor nextColor(DyeColor color) {
        return DyeColor.byId((color.getId() + 1) % 16);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF202020);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF8B8B8B);
        graphics.drawCenteredString(font, title, leftPos + imageWidth / 2, topPos + 8, 0xFFFFFF);
        renderPresetPreview(graphics);
    }

    /**
     * 预览网格:按当前编辑的颜色方案着色.
     */
    private void renderPresetPreview(GuiGraphics graphics) {
        int width = menu.presetWidth;
        int depth = menu.presetDepth;
        if (width <= 0 || depth <= 0 || menu.presetStates.length == 0) {
            return;
        }
        List<Integer> colors = new ArrayList<>(menu.presetPalette.size());
        for (String name : menu.presetPalette) {
            colors.add(resolvePreviewColor(name));
        }
        int gridSize = Math.min(150, Math.min(imageWidth - 20, topPos + imageHeight - 10 - (topPos + 100)));
        if (gridSize < 16) {
            return;
        }
        int gridX = leftPos + (imageWidth - gridSize) / 2;
        int gridY = topPos + imageHeight - 10 - gridSize;
        int step = Math.max(1, (int) Math.ceil(Math.max(width, depth) / 64.0));
        int cellsX = (width + step - 1) / step;
        int cellsZ = (depth + step - 1) / step;
        float cell = Math.max(1.0f, (float) gridSize / Math.max(cellsX, cellsZ));
        for (int cz = 0; cz < cellsZ; cz++) {
            for (int cx = 0; cx < cellsX; cx++) {
                int idx = Math.min(depth - 1, cz * step) * width + Math.min(width - 1, cx * step);
                if (idx < 0 || idx >= menu.presetStates.length) {
                    continue;
                }
                int paletteIdx = menu.presetStates[idx];
                if (paletteIdx < 0 || paletteIdx >= colors.size()) {
                    continue;
                }
                int x1 = gridX + Math.round(cx * cell);
                int y1 = gridY + Math.round(cz * cell);
                int x2 = gridX + Math.round((cx + 1) * cell);
                int y2 = gridY + Math.round((cz + 1) * cell);
                graphics.fill(x1, y1, Math.max(x1 + 1, x2), Math.max(y1 + 1, y2),
                        0xFF000000 | colors.get(paletteIdx));
            }
        }
        graphics.renderOutline(gridX, gridY, Math.round(cellsX * cell), Math.round(cellsZ * cell), 0xFF8B8B8B);
    }

    /**
     * 按编辑中的颜色方案替换三个角色的占位混凝土色,其余方块用 MapColor.
     */
    private int resolvePreviewColor(String blockName) {
        DyeColor override = switch (blockName) {
            case "minecraft:gray_concrete" -> roadColor;
            case "minecraft:white_concrete" -> lineColor;
            case "minecraft:black_concrete" -> platformColor;
            default -> null;
        };
        if (override != null) {
            return concreteMapColor(override);
        }
        return resolveMapColor(blockName);
    }

    static int concreteMapColor(DyeColor color) {
        ResourceLocation id = new ResourceLocation("minecraft", color.getName() + "_concrete");
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).defaultMapColor().col & 0xFFFFFF;
        }
        return 0x7F7F7F;
    }

    static int resolveMapColor(String blockName) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(blockName);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                return BuiltInRegistries.BLOCK.get(id).defaultMapColor().col & 0xFFFFFF;
            }
        } catch (Exception ignored) {
        }
        return 0x7F7F7F;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题在 renderBg 中绘制
    }
}
