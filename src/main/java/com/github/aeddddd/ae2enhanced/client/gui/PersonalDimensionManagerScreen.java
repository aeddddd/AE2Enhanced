package com.github.aeddddd.ae2enhanced.client.gui;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;

import com.github.aeddddd.ae2enhanced.menu.PersonalDimensionManagerMenu;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimColorSchemePacket;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimManagerStatePacket;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimPermissionPacket;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimRulesSubmitPacket;

/**
 * 个人维度管理器界面：规则配置、权限管理与地板预设预览三个标签页.
 */
public class PersonalDimensionManagerScreen extends AbstractContainerScreen<PersonalDimensionManagerMenu> {

    private static final int TAB_RULES = 0;
    private static final int TAB_PERMISSIONS = 1;
    private static final int TAB_PRESET = 2;

    private static final int PERM_ROWS_VISIBLE = 5;

    @Nullable
    private PersonalDimManagerStatePacket state;
    private int tab = TAB_RULES;
    private int permScroll = 0;
    @Nullable
    private EditBox inviteBox;
    // 预设页颜色编辑态(提交后由状态包重置)
    private DyeColor editRoad = DyeColor.GRAY;
    private DyeColor editLine = DyeColor.WHITE;
    private DyeColor editPlatform = DyeColor.BLACK;

    public PersonalDimensionManagerScreen(PersonalDimensionManagerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 222;
    }

    public void updateState(PersonalDimManagerStatePacket packet) {
        if (!packet.pos.equals(this.menu.pos)) {
            return;
        }
        this.state = packet;
        this.editRoad = DyeColor.byId(packet.roadColor);
        this.editLine = DyeColor.byId(packet.lineColor);
        this.editPlatform = DyeColor.byId(packet.platformColor);
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        inviteBox = null;
        // 标签页按钮
        int tabY = topPos + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.ae2enhanced.pdim.tab.rules"),
                b -> switchTab(TAB_RULES)).bounds(leftPos + 6, tabY, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.ae2enhanced.pdim.tab.permissions"),
                b -> switchTab(TAB_PERMISSIONS)).bounds(leftPos + 86, tabY, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.ae2enhanced.pdim.tab.preset"),
                b -> switchTab(TAB_PRESET)).bounds(leftPos + 166, tabY, 76, 18).build());

        if (state == null) {
            return;
        }
        if (tab == TAB_RULES) {
            initRulesTab();
        } else if (tab == TAB_PERMISSIONS) {
            initPermissionsTab();
        } else if (tab == TAB_PRESET) {
            initPresetTab();
        }
    }

    private void switchTab(int newTab) {
        tab = newTab;
        permScroll = 0;
        rebuildWidgets();
    }

    // ==================== 规则页 ====================

    private void initRulesTab() {
        int x = leftPos + 10;
        int y = topPos + 30;
        int labelWidth = 150;
        int btnWidth = 50;

        addRuleToggle(x, y, labelWidth, btnWidth, "gui.ae2enhanced.pdim.disable_mob_spawning",
                state.rules.disableMobSpawning, v -> state.rules.disableMobSpawning = v);
        y += 22;
        addRuleToggle(x, y, labelWidth, btnWidth, "gui.ae2enhanced.pdim.lock_weather",
                state.rules.lockWeather, v -> state.rules.lockWeather = v);
        y += 22;
        addRuleToggle(x, y, labelWidth, btnWidth, "gui.ae2enhanced.pdim.lock_time",
                state.rules.lockTime, v -> state.rules.lockTime = v);
        y += 22;
        addRuleToggle(x, y, labelWidth, btnWidth, "gui.ae2enhanced.pdim.daylight_cycle",
                state.rules.daylightCycle, v -> state.rules.daylightCycle = v);
        y += 22;
        addRuleToggle(x, y, labelWidth, btnWidth, "gui.ae2enhanced.pdim.flight",
                state.rules.flightEnabled, v -> state.rules.flightEnabled = v);
        y += 22;
        addRuleToggle(x, y, labelWidth, btnWidth, "gui.ae2enhanced.pdim.no_inertia",
                state.rules.noFlightInertia, v -> state.rules.noFlightInertia = v);
        y += 24;

        // 时间值滑块（0~23999）
        addRenderableWidget(new AbstractSliderButton(x, y, labelWidth + btnWidth + 4, 18,
                timeText(), state.rules.timeValue / 23999.0) {
            @Override
            protected void updateMessage() {
                setMessage(timeText());
            }

            @Override
            protected void applyValue() {
                state.rules.timeValue = Math.round(this.value * 23999.0);
            }

            @Override
            public boolean mouseReleased(double mouseX, double mouseY, int button) {
                boolean result = super.mouseReleased(mouseX, mouseY, button);
                submitRules();
                return result;
            }
        });
        y += 24;

        // 移动速度滑块（5%~200%）
        addRenderableWidget(new AbstractSliderButton(x, y, labelWidth + btnWidth + 4, 18,
                speedText(), (state.rules.movementSpeed - 0.05) / 1.95) {
            @Override
            protected void updateMessage() {
                setMessage(speedText());
            }

            @Override
            protected void applyValue() {
                state.rules.movementSpeed = (float) (0.05 + this.value * 1.95);
            }

            @Override
            public boolean mouseReleased(double mouseX, double mouseY, int button) {
                boolean result = super.mouseReleased(mouseX, mouseY, button);
                submitRules();
                return result;
            }
        });
    }

    private Component timeText() {
        return Component.translatable("gui.ae2enhanced.pdim.time_value", state.rules.timeValue);
    }

    private Component speedText() {
        return Component.translatable("gui.ae2enhanced.pdim.movement_speed",
                Math.round(state.rules.movementSpeed * 1000) / 10.0);
    }

    private void addRuleToggle(int x, int y, int labelWidth, int btnWidth, String labelKey, boolean value,
            java.util.function.Consumer<Boolean> setter) {
        Component label = Component.translatable(labelKey)
                .append(": ")
                .append(Component.translatable(value ? "gui.ae2enhanced.pdim.on" : "gui.ae2enhanced.pdim.off"));
        addRenderableWidget(Button.builder(label, b -> {
            setter.accept(!value);
            submitRules();
        }).bounds(x, y, labelWidth + btnWidth + 4, 18).build());
    }

    private void submitRules() {
        ModNetwork.CHANNEL.sendToServer(
                new PersonalDimRulesSubmitPacket(menu.pos, menu.owner, state.rules.copy()));
    }

    // ==================== 预设页 ====================

    private void initPresetTab() {
        int x = leftPos + 10;
        int y = topPos + 108;

        addRenderableWidget(colorButton(x, y, "gui.ae2enhanced.pdim.color.road_base", editRoad,
                () -> editRoad = nextColor(editRoad)));
        y += 22;
        addRenderableWidget(colorButton(x, y, "gui.ae2enhanced.pdim.color.road_line", editLine,
                () -> editLine = nextColor(editLine)));
        y += 22;
        addRenderableWidget(colorButton(x, y, "gui.ae2enhanced.pdim.color.platform_base", editPlatform,
                () -> editPlatform = nextColor(editPlatform)));
        y += 26;

        // 应用颜色方案并立即重铺全部已生成地板,避免新旧区块颜色不一致
        addRenderableWidget(Button.builder(Component.translatable("gui.ae2enhanced.pdim.apply_recolor"), b -> {
            ModNetwork.CHANNEL.sendToServer(new PersonalDimColorSchemePacket(menu.pos, menu.owner,
                    editRoad.getId(), editLine.getId(), editPlatform.getId(), true));
        }).bounds(x, y, 118, 18).build());
    }

    private Button colorButton(int x, int y, String labelKey, DyeColor color, Runnable onClick) {
        Component label = Component.translatable(labelKey)
                .append(": ")
                .append(Component.translatable("block.minecraft." + color.getName() + "_concrete"));
        return Button.builder(label, b -> {
            onClick.run();
            rebuildWidgets();
        }).bounds(x, y, 118, 16).build();
    }

    private static DyeColor nextColor(DyeColor color) {
        return DyeColor.byId((color.getId() + 1) % 16);
    }

    // ==================== 权限页 ====================

    private void initPermissionsTab() {
        int x = leftPos + 10;
        int y = topPos + 30;
        List<PersonalDimManagerStatePacket.PlayerPerm> players = state.players;

        int end = Math.min(players.size(), permScroll + PERM_ROWS_VISIBLE);
        for (int i = permScroll; i < end; i++) {
            PersonalDimManagerStatePacket.PlayerPerm player = players.get(i);
            int btnX = x + 96;
            for (PersonalDimPermission permission : PersonalDimPermission.values()) {
                boolean granted = (player.mask() & (1 << permission.ordinal())) != 0;
                Component text = Component.literal(permission.name().substring(0, 1))
                        .withStyle(granted ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);
                int ordinal = permission.ordinal();
                addRenderableWidget(Button.builder(text, b -> ModNetwork.CHANNEL.sendToServer(
                        PersonalDimPermissionPacket.setPerm(menu.pos, menu.owner, player.uuid(),
                                PersonalDimPermission.values()[ordinal], !granted)))
                        .bounds(btnX, y, 14, 16).build());
                btnX += 16;
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.ae2enhanced.pdim.kick"),
                    b -> ModNetwork.CHANNEL.sendToServer(
                            PersonalDimPermissionPacket.kick(menu.pos, menu.owner, player.uuid())))
                    .bounds(btnX + 2, y, 30, 16).build());
            y += 18;
        }

        // 邀请输入框
        inviteBox = new EditBox(font, x, topPos + imageHeight - 28, 150, 16,
                Component.translatable("gui.ae2enhanced.pdim.invite_hint"));
        inviteBox.setMaxLength(16);
        addRenderableWidget(inviteBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.ae2enhanced.pdim.invite"), b -> {
            String name = inviteBox.getValue().trim();
            if (!name.isEmpty()) {
                ModNetwork.CHANNEL.sendToServer(
                        PersonalDimPermissionPacket.invite(menu.pos, menu.owner, name));
                inviteBox.setValue("");
            }
        }).bounds(x + 156, topPos + imageHeight - 28, 50, 16).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == TAB_PERMISSIONS && state != null) {
            int max = Math.max(0, state.players.size() - PERM_ROWS_VISIBLE);
            permScroll = Math.max(0, Math.min(max, permScroll - (int) Math.signum(delta)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ==================== 渲染 ====================

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

        if (state == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.ae2enhanced.pdim.loading"),
                    leftPos + imageWidth / 2, topPos + imageHeight / 2, 0xAAAAAA);
            return;
        }

        if (tab == TAB_PERMISSIONS) {
            renderPermissionNames(graphics);
        } else if (tab == TAB_PRESET) {
            renderPresetTab(graphics);
        }
    }

    private void renderPermissionNames(GuiGraphics graphics) {
        int x = leftPos + 10;
        int y = topPos + 34;
        List<PersonalDimManagerStatePacket.PlayerPerm> players = state.players;
        int end = Math.min(players.size(), permScroll + PERM_ROWS_VISIBLE);
        for (int i = permScroll; i < end; i++) {
            graphics.drawString(font, players.get(i).name(), x, y, 0xFFFFFF, false);
            y += 18;
        }
        if (players.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.ae2enhanced.pdim.no_players"),
                    x, y, 0x888888, false);
        }
    }

    private void renderPresetTab(GuiGraphics graphics) {
        int x = leftPos + 10;
        int y = topPos + 30;
        graphics.drawString(font, Component.translatable("gui.ae2enhanced.pdim.owner", state.ownerName),
                x, y, 0xFFFFFF, false);
        y += 12;
        graphics.drawString(font, Component.translatable(state.created
                ? "gui.ae2enhanced.pdim.created" : "gui.ae2enhanced.pdim.not_created"), x, y, 0xFFFFFF, false);
        y += 12;
        graphics.drawString(font, Component.translatable("gui.ae2enhanced.pdim.floor_y", state.floorY),
                x, y, 0xAAAAAA, false);
        y += 12;
        graphics.drawString(font, Component.translatable("gui.ae2enhanced.pdim.entry_y", state.entryY),
                x, y, 0xAAAAAA, false);
        y += 12;
        graphics.drawString(font, Component.translatable("gui.ae2enhanced.pdim.preset_size",
                state.presetWidth, state.presetDepth), x, y, 0xAAAAAA, false);
        y += 12;
        graphics.drawString(font, Component.translatable("gui.ae2enhanced.pdim.preset_note"),
                x, y, 0x888888, false);

        // 预设网格预览：按编辑中的颜色方案着色,网格过密时抽样
        List<Integer> colors = new ArrayList<>(state.presetPalette.size());
        for (String name : state.presetPalette) {
            colors.add(resolvePreviewColor(name));
        }
        int gridRight = leftPos + imageWidth - 10;
        int gridBottom = topPos + imageHeight - 10;
        int gridSize = Math.min(100, Math.min(gridRight - (leftPos + 134), gridBottom - (topPos + 30)));
        if (gridSize < 16) {
            return;
        }
        int gridX = leftPos + imageWidth - 10 - gridSize;
        int gridY = topPos + 30;
        int width = state.presetWidth;
        int depth = state.presetDepth;
        int step = Math.max(1, (int) Math.ceil(Math.max(width, depth) / 64.0));
        int cellsX = (width + step - 1) / step;
        int cellsZ = (depth + step - 1) / step;
        float cell = Math.max(1.0f, (float) gridSize / Math.max(cellsX, cellsZ));
        for (int cz = 0; cz < cellsZ; cz++) {
            for (int cx = 0; cx < cellsX; cx++) {
                int srcX = Math.min(width - 1, cx * step);
                int srcZ = Math.min(depth - 1, cz * step);
                int idx = srcZ * width + srcX;
                if (idx < 0 || idx >= state.presetStates.length) {
                    continue;
                }
                int paletteIdx = state.presetStates[idx];
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
            case "minecraft:gray_concrete" -> editRoad;
            case "minecraft:white_concrete" -> editLine;
            case "minecraft:black_concrete" -> editPlatform;
            default -> null;
        };
        if (override != null) {
            return PersonalDimensionCreateScreen.concreteMapColor(override);
        }
        return resolveMapColor(blockName);
    }

    private static int resolveMapColor(String blockName) {
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
        // 标题绘制到背景上方,避免与标签页按钮重叠
    }
}
