package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.ClientPersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.container.ContainerPersonalDimensionConfig;
import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.dimension.DimensionPreference;
import com.github.aeddddd.ae2enhanced.dimension.FloorDefinition;
import com.github.aeddddd.ae2enhanced.dimension.FloorPreset;
import com.github.aeddddd.ae2enhanced.dimension.FloorStyleRegistry;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.dimension.PrivateDimensionInfo;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionFloorAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRules;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.util.Locale;

/**
 * 个人维度管理界面。
 *
 * <p>布局逐项对齐 1.20.1 分支的 {@code PersonalDimensionManagerScreen}：248×222 窗口、
 * 顶部两个 76×18 标签页、规则页六行 204×18 开关 + 时间/速度两条滑块、预设页左侧信息与三个
 * 112×16 配色按钮 + "应用并重铺"入口、右侧 100×100 预设网格预览。1.12.2 没有
 * {@code AbstractSliderButton}，因此滑块用 1.12 的轨/钮画法；其余控件全部使用原生
 * {@link GuiButton}，不再自绘"近似 19.x"的按钮底。</p>
 *
 * <p>权限页按口径移除：团队/权限完全交给 FTB Teams（{@code /ae2e pd mode} 只保留
 * "自动 / 团队 / 个人"这一条进入偏好）。1.20.1 的权限标签页位置因此留空，不塞入其它设置。</p>
 */
public class GuiPersonalDimensionConfig extends GuiContainer {
    private static final int GUI_W = 248;
    private static final int GUI_H = 222;
    private static final int TAB_RULES = 0;
    private static final int TAB_PRESET = 1;

    private static final int ROW_X = 10;
    private static final int ROW_W = 204;
    private static final int ROW_H = 18;
    private static final int RULES_Y = 30;
    private static final int ROW_PITCH = 22;
    private static final int TIME_SLIDER_Y = 162;
    private static final int SPEED_SLIDER_Y = 184;
    private static final int MODE_Y = 204;
    private static final int MODE_W = 74;
    private static final int MODE_H = 14;

    private static final int COLOR_X = 10;
    private static final int COLOR_W = 118;
    private static final int COLOR_H = 16;
    private static final int COLOR_Y = 92;
    private static final int COLOR_PITCH = 22;
    private static final int APPLY_Y = 160;
    private static final int APPLY_W = 118;
    private static final int APPLY_H = 18;
    private static final int PREVIEW_SIZE = 100;

    private static final int[] COLOR_ROLES = {7, 0, 15};
    private static final String[] ROLE_KEYS = {"road_base", "road_line", "platform_base"};
    private static final String[] RULE_KEYS = {
            "spawn_mobs", "lock_weather", "lock_time", "daylight_cycle", "flight", "no_inertia"};

    private final EntityPlayer player;
    private final PersonalDimensionRules rules;
    private final int[] originalColors;
    private String styleId;
    private int[] colors;
    private int tab = TAB_RULES;
    private int dragParam = -1;

    public GuiPersonalDimensionConfig(EntityPlayer player, ContainerPersonalDimensionConfig container) {
        super(container);
        this.player = player;
        this.xSize = GUI_W;
        this.ySize = GUI_H;
        this.rules = ClientPersonalDimensionRules.get().copy();
        FloorDefinition floor = ClientPersonalDimensionRules.floor();
        this.styleId = floor.getFloorStyleId();
        this.colors = floor.getColorScheme();
        this.originalColors = floor.getColorScheme();
    }

    private static void drawBorder(int x, int y, int width, int height, int color) {
        drawRect(x, y, x + width, y + 1, color);
        drawRect(x, y + height - 1, x + width, y + height, color);
        drawRect(x, y, x + 1, y + height, color);
        drawRect(x + width - 1, y, x + width, y + height, color);
    }

    private static boolean in(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void initGui() {
        super.initGui();
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.buttonList.clear();
        addTab(6, TAB_RULES, "rules");
        addTab(86, TAB_PRESET, "preset");
        PrivateDimensionInfo info = ClientPersonalDimensionRules.info();
        boolean canManage = info.canManage;
        if (tab == TAB_RULES) {
            addRuleButtons(canManage);
            addModeButtons();
        } else {
            addPresetButtons(canManage);
        }
    }

    private void addTab(int x, int target, String key) {
        GuiButton button = new GuiButton(100 + target, this.guiLeft + x, this.guiTop + 4, 76, 18,
                I18n.format("gui.ae2enhanced.personal_dimension.tab." + key));
        button.enabled = tab != target;
        this.buttonList.add(button);
    }

    private void addRuleButtons(boolean canManage) {
        for (int index = 0; index < RULE_KEYS.length; index++) {
            boolean value = ruleValue(index);
            String label = I18n.format("gui.ae2enhanced.personal_dimension." + RULE_KEYS[index])
                    + ": " + I18n.format(value ? "gui.ae2enhanced.personal_dimension.on"
                    : "gui.ae2enhanced.personal_dimension.off");
            GuiButton button = new GuiButton(index, this.guiLeft + ROW_X,
                    this.guiTop + RULES_Y + index * ROW_PITCH, ROW_W, ROW_H, label);
            button.enabled = canManage;
            this.buttonList.add(button);
        }
    }

    private void addModeButtons() {
        DimensionPreference[] modes = DimensionPreference.values();
        DimensionPreference current = ClientPersonalDimensionRules.info().preference;
        for (int index = 0; index < modes.length; index++) {
            String label = I18n.format("gui.ae2enhanced.personal_dimension.mode."
                    + modes[index].name().toLowerCase(Locale.ROOT));
            GuiButton button = new GuiButton(20 + index, this.guiLeft + ROW_X + index * (MODE_W + 3),
                    this.guiTop + MODE_Y, MODE_W, MODE_H, label);
            button.enabled = current != modes[index];
            this.buttonList.add(button);
        }
    }

    private void addPresetButtons(boolean canManage) {
        for (int index = 0; index < COLOR_ROLES.length; index++) {
            int role = COLOR_ROLES[index];
            String dye = new ItemStack(Blocks.CONCRETE, 1, colors[role]).getDisplayName();
            GuiButton button = new GuiButton(10 + index, this.guiLeft + COLOR_X,
                    this.guiTop + COLOR_Y + index * COLOR_PITCH, COLOR_W, COLOR_H,
                    I18n.format("gui.ae2enhanced.personal_dimension.floor.color." + ROLE_KEYS[index], dye));
            button.enabled = canManage;
            this.buttonList.add(button);
        }
        GuiButton apply = new GuiButton(13, this.guiLeft + COLOR_X, this.guiTop + APPLY_Y,
                APPLY_W, APPLY_H, I18n.format("gui.ae2enhanced.personal_dimension.floor.apply"));
        apply.enabled = canManage;
        this.buttonList.add(apply);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 100) {
            tab = TAB_RULES;
            dragParam = -1;
            rebuildButtons();
            return;
        }
        if (button.id == 101) {
            tab = TAB_PRESET;
            dragParam = -1;
            rebuildButtons();
            return;
        }
        if (button.id >= 0 && button.id < RULE_KEYS.length) {
            toggleRule(button.id);
            return;
        }
        if (button.id >= 20 && button.id < 20 + DimensionPreference.values().length) {
            DimensionPreference mode = DimensionPreference.values()[button.id - 20];
            this.mc.player.sendChatMessage("/ae2e pd mode " + mode.name().toLowerCase(Locale.ROOT));
            return;
        }
        if (button.id >= 10 && button.id < 10 + COLOR_ROLES.length) {
            int role = COLOR_ROLES[button.id - 10];
            colors[role] = (colors[role] + 1) % FloorDefinition.COLOR_ROLES;
            rebuildButtons();
            return;
        }
        if (button.id == 13) {
            AE2Enhanced.network.sendToServer(new PacketPersonalDimensionFloorAction(definition(), true));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void toggleRule(int index) {
        switch (index) {
            case 0:
                rules.disableMobSpawning = !rules.disableMobSpawning;
                break;
            case 1:
                rules.lockWeather = !rules.lockWeather;
                break;
            case 2:
                rules.lockTime = !rules.lockTime;
                break;
            case 3:
                rules.daylightCycle = !rules.daylightCycle;
                break;
            case 4:
                rules.flightEnabled = !rules.flightEnabled;
                break;
            default:
                rules.noFlightInertia = !rules.noFlightInertia;
                break;
        }
        submitRules();
        rebuildButtons();
    }

    private boolean ruleValue(int index) {
        switch (index) {
            case 0:
                return !rules.disableMobSpawning;
            case 1:
                return rules.lockWeather;
            case 2:
                return rules.lockTime;
            case 3:
                return rules.daylightCycle;
            case 4:
                return rules.flightEnabled;
            default:
                return rules.noFlightInertia;
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(0, 0, GUI_W, GUI_H, 0xff202020);
        drawBorder(0, 0, GUI_W, GUI_H, 0xff8b8b8b);
        if (tab == TAB_RULES) {
            drawSlider(TIME_SLIDER_Y, (int) rules.timeValue, 0, 23999,
                    I18n.format("gui.ae2enhanced.personal_dimension.time_value", rules.timeValue));
            drawSlider(SPEED_SLIDER_Y, Math.round(rules.movementSpeed * 100), 5, 200,
                    I18n.format("gui.ae2enhanced.personal_dimension.speed") + ": "
                            + Math.round(rules.movementSpeed * 100) + "%");
        } else {
            drawPresetPreview();
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        if (tab == TAB_RULES) {
            PrivateDimensionInfo info = ClientPersonalDimensionRules.info();
            String scope = I18n.format("gui.ae2enhanced.personal_dimension.scope."
                    + info.scope.name().toLowerCase(Locale.ROOT), info.displayName);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(scope, ROW_W - 4),
                    ROW_X, ROW_H * 6 + RULES_Y + 4, 0xaaaaaa);
            return;
        }
        drawPresetInfo();
    }

    private void drawPresetInfo() {
        PrivateDimensionInfo info = ClientPersonalDimensionRules.info();
        FloorPreset preset = FloorStyleRegistry.resolve(definition());
        int y = 30;
        fontRenderer.drawString(fontRenderer.trimStringToWidth(
                I18n.format("gui.ae2enhanced.personal_dimension.owner", info.displayName), COLOR_W),
                COLOR_X, y, 0xffffff);
        y += 12;
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.personal_dimension.floor_y",
                ServerConfig.current().personalDimension.floorY), COLOR_X, y, 0xaaaaaa);
        y += 12;
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.personal_dimension.entry_y",
                ServerConfig.current().personalDimension.entryY), COLOR_X, y, 0xaaaaaa);
        y += 12;
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.personal_dimension.preset_size",
                preset.width, preset.depth), COLOR_X, y, 0xaaaaaa);
        y += 12;
        fontRenderer.drawString(fontRenderer.trimStringToWidth(
                I18n.format("gui.ae2enhanced.personal_dimension.preset_note"), COLOR_W),
                COLOR_X, y, 0x888888);
        int pending = ClientPersonalDimensionRules.repaintPending();
        String status = pending < 0 ? I18n.format("gui.ae2enhanced.personal_dimension.floor.idle")
                : I18n.format("gui.ae2enhanced.personal_dimension.floor.pending", pending);
        fontRenderer.drawSplitString(status, COLOR_X, APPLY_Y + APPLY_H + 6, COLOR_W, 0xaaaaaa);
    }

    /** 1.12.2 滑块：8px 凹槽 + 12×16 钮，几何与 1.20.1 的 204×18 滑条一致。 */
    private void drawSlider(int y, int value, int min, int max, String text) {
        int trackTop = y + 7;
        drawRect(ROW_X, trackTop, ROW_X + ROW_W, trackTop + 4, 0xff101010);
        drawBorder(ROW_X, trackTop, ROW_W, 4, 0xff8b8b8b);
        int knob = ROW_X + Math.round((value - min) / (float) (max - min) * (ROW_W - 12));
        drawRect(knob, y + 1, knob + 12, y + 17, 0xffc6c6c6);
        drawBorder(knob, y + 1, 12, 16, 0xff555555);
        fontRenderer.drawString(text, ROW_X + 2, trackTop - 10, 0xffffff);
    }

    private void drawPresetPreview() {
        FloorPreset preset = FloorStyleRegistry.resolve(definition());
        int gridX = GUI_W - 10 - PREVIEW_SIZE;
        int gridY = 30;
        int cells = Math.max(1, Math.min(PREVIEW_SIZE, Math.max(preset.width, preset.depth)));
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                int sourceX = Math.min(preset.width - 1, x * preset.width / cells);
                int sourceZ = Math.min(preset.depth - 1, z * preset.depth / cells);
                IBlockState state = preset.getState(sourceX, sourceZ);
                int x1 = gridX + x * PREVIEW_SIZE / cells;
                int y1 = gridY + z * PREVIEW_SIZE / cells;
                int x2 = gridX + (x + 1) * PREVIEW_SIZE / cells;
                int y2 = gridY + (z + 1) * PREVIEW_SIZE / cells;
                drawRect(x1, y1, Math.max(x1 + 1, x2), Math.max(y1 + 1, y2), previewColor(state));
            }
        }
        drawBorder(gridX, gridY, PREVIEW_SIZE, PREVIEW_SIZE, 0xff8b8b8b);
    }

    /** 预览里三个角色占位混凝土改用编辑中的配色，其余方块用 MapColor。 */
    private int previewColor(IBlockState state) {
        if (state == null) {
            return 0xff000000;
        }
        try {
            if (state.getBlock() == Blocks.CONCRETE) {
                int meta = state.getBlock().getMetaFromState(state);
                for (int index = 0; index < COLOR_ROLES.length; index++) {
                    int role = COLOR_ROLES[index];
                    if (meta == originalColors[role] && meta != colors[role]) {
                        IBlockState recolored = Blocks.CONCRETE.getStateFromMeta(colors[role]);
                        return 0xff000000 | recolored.getBlock().getMapColor(recolored, null, null).colorValue;
                    }
                }
            }
            return 0xff000000 | state.getBlock().getMapColor(state, null, null).colorValue;
        } catch (Exception exception) {
            return 0xff000000 | state.getMaterial().getMaterialMapColor().colorValue;
        }
    }

    private FloorDefinition definition() {
        FloorDefinition definition = new FloorDefinition();
        definition.setStyle(styleId, colors);
        return definition;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (tab != TAB_RULES || !ClientPersonalDimensionRules.info().canManage) {
            return;
        }
        if (in(mouseX, mouseY, this.guiLeft + ROW_X, this.guiTop + TIME_SLIDER_Y, ROW_W, ROW_H)) {
            dragParam = 0;
            updateSlider(mouseX);
        } else if (in(mouseX, mouseY, this.guiLeft + ROW_X, this.guiTop + SPEED_SLIDER_Y, ROW_W, ROW_H)) {
            dragParam = 1;
            updateSlider(mouseX);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        updateSlider(mouseX);
    }

    private void updateSlider(int mouseX) {
        if (dragParam < 0) {
            return;
        }
        float ratio = MathHelper.clamp((mouseX - this.guiLeft - ROW_X) / (float) (ROW_W - 12), 0.0F, 1.0F);
        if (dragParam == 0) {
            rules.timeValue = Math.round(ratio * 23999);
        } else {
            rules.movementSpeed = (5 + Math.round(ratio * 195)) / 100.0F;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (dragParam >= 0) {
            dragParam = -1;
            submitRules();
        }
    }

    private void submitRules() {
        if (ClientPersonalDimensionRules.info().canManage) {
            AE2Enhanced.network.sendToServer(new PacketPersonalDimensionRules(rules));
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        submitRules();
    }
}
