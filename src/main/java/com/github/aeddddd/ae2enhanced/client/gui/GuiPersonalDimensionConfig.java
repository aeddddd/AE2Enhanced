package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.ClientPersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.container.ContainerPersonalDimensionConfig;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRules;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 个人维度规则配置 GUI —— 复用先进 ME 工具 GUI 的布局与交互风格。
 */
public class GuiPersonalDimensionConfig extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AE2Enhanced.MOD_ID, "textures/gui/me_omni_tool_gui.png");

    private static final int GUI_W = 195;
    private static final int GUI_H = 221;

    private static final int PID_FLIGHT = 0;
    private static final int PID_SPEED = 1;
    private static final int PID_NO_INERTIA = 2;
    private static final int PID_MOB_SPAWN = 3;
    private static final int PID_LOCK_WEATHER = 4;
    private static final int PID_LOCK_TIME = 5;
    private static final int PID_DAYLIGHT = 6;
    private static final int PID_TIME_VALUE = 7;

    private static final int LEFT_BTN_X = 4;
    private static final int RIGHT_BTN_X = 116;
    private static final int BTN_W = 75;
    private static final int BTN_H = 17;
    private static final int BTN_Y0 = 25;
    private static final int BTN_GAP = 2;
    private static final int BTN_STEP = BTN_H + BTN_GAP;

    private static final int TEX_NORMAL_BTN_U = 0;
    private static final int TEX_NORMAL_BTN_V = 221;
    private static final int TEX_HIGHLIGHT_BTN_U = 75;
    private static final int TEX_HIGHLIGHT_BTN_V = 221;
    private static final int TEX_KNOB_U = 150;
    private static final int TEX_KNOB_V = 221;
    private static final int KNOB_W = 12;
    private static final int KNOB_H = 17;

    private static final int TEX_HIGHLIGHT_BAR_U = 0;
    private static final int TEX_HIGHLIGHT_BAR_V = 238;
    private static final int BAR_W = 188;
    private static final int BAR_H = 17;

    private static final int BAR1_X = 4;
    private static final int BAR1_Y = 102;
    private static final int BAR2_X = 4;
    private static final int BAR2_Y = 122;

    private static class ParamDef {
        final int id;
        final String nameKey;
        final String descKey;
        final int min;
        final int max;
        final Function<PersonalDimensionRules, Integer> getter;
        final BiConsumer<PersonalDimensionRules, Integer> setter;

        ParamDef(int id, String nameKey, String descKey, int min, int max,
                 Function<PersonalDimensionRules, Integer> getter,
                 BiConsumer<PersonalDimensionRules, Integer> setter) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
        }
    }

    private static final ParamDef[] BASE_PARAMS = {
        new ParamDef(PID_FLIGHT, "gui.ae2enhanced.personal_dimension.flight",
                "gui.ae2enhanced.personal_dimension.flight.desc", 0, 1,
                r -> r.flightEnabled ? 1 : 0,
                (r, v) -> r.flightEnabled = v > 0),
        new ParamDef(PID_SPEED, "gui.ae2enhanced.personal_dimension.speed",
                "gui.ae2enhanced.personal_dimension.speed.desc", 5, 200,
                r -> Math.round(r.movementSpeed * 100f),
                (r, v) -> r.movementSpeed = v / 100f),
        new ParamDef(PID_NO_INERTIA, "gui.ae2enhanced.personal_dimension.no_inertia",
                "gui.ae2enhanced.personal_dimension.no_inertia.desc", 0, 1,
                r -> r.noFlightInertia ? 1 : 0,
                (r, v) -> r.noFlightInertia = v > 0),
        new ParamDef(PID_MOB_SPAWN, "gui.ae2enhanced.personal_dimension.spawn_mobs",
                "gui.ae2enhanced.personal_dimension.spawn_mobs.desc", 0, 1,
                r -> r.disableMobSpawning ? 0 : 1,
                (r, v) -> r.disableMobSpawning = v <= 0),
        new ParamDef(PID_LOCK_WEATHER, "gui.ae2enhanced.personal_dimension.lock_weather",
                "gui.ae2enhanced.personal_dimension.lock_weather.desc", 0, 1,
                r -> r.lockWeather ? 1 : 0,
                (r, v) -> r.lockWeather = v > 0),
        new ParamDef(PID_LOCK_TIME, "gui.ae2enhanced.personal_dimension.lock_time",
                "gui.ae2enhanced.personal_dimension.lock_time.desc", 0, 1,
                r -> r.lockTime ? 1 : 0,
                (r, v) -> r.lockTime = v > 0),
        new ParamDef(PID_DAYLIGHT, "gui.ae2enhanced.personal_dimension.daylight_cycle",
                "gui.ae2enhanced.personal_dimension.daylight_cycle.desc", 0, 1,
                r -> r.daylightCycle ? 1 : 0,
                (r, v) -> r.daylightCycle = v > 0),
        new ParamDef(PID_TIME_VALUE, "gui.ae2enhanced.personal_dimension.time_value",
                "gui.ae2enhanced.personal_dimension.time_value.desc", 0, 24000,
                r -> (int) r.timeValue,
                (r, v) -> r.timeValue = v)
    };

    private final EntityPlayer player;
    private PersonalDimensionRules rules;

    private final List<ParamDef> activeParams = new ArrayList<>();
    private int selParam = 0;
    private int dragParam = -1;

    public GuiPersonalDimensionConfig(EntityPlayer player, ContainerPersonalDimensionConfig container) {
        super(container);
        this.player = player;
        this.xSize = GUI_W;
        this.ySize = GUI_H;
        this.rules = ClientPersonalDimensionRules.get().copy();
    }

    @Override
    public void initGui() {
        super.initGui();
        activeParams.clear();
        for (ParamDef p : BASE_PARAMS) {
            activeParams.add(p);
        }
        selParam = MathHelper.clamp(selParam, 0, Math.max(0, activeParams.size() - 1));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_W, GUI_H);

        for (int slot = 0; slot < 8; slot++) {
            int bx = (slot < 4) ? LEFT_BTN_X : RIGHT_BTN_X;
            int by = BTN_Y0 + (slot % 4) * BTN_STEP;
            int absX = this.guiLeft + bx;
            int absY = this.guiTop + by;
            if (slot < activeParams.size()) {
                boolean selected = (selParam == slot);
                this.drawTexturedModalRect(absX, absY,
                        selected ? TEX_HIGHLIGHT_BTN_U : TEX_NORMAL_BTN_U,
                        selected ? TEX_HIGHLIGHT_BTN_V : TEX_NORMAL_BTN_V,
                        BTN_W, BTN_H);
            }
        }

        if (!activeParams.isEmpty() && getValue(activeParams.get(selParam)) > 0) {
            this.drawTexturedModalRect(this.guiLeft + BAR1_X, this.guiTop + BAR1_Y,
                    TEX_HIGHLIGHT_BAR_U, TEX_HIGHLIGHT_BAR_V, BAR_W, BAR_H);
        }

        if (!activeParams.isEmpty()) {
            int knobX = computeKnobX(activeParams.get(selParam));
            this.drawTexturedModalRect(knobX, this.guiTop + BAR2_Y,
                    TEX_KNOB_U, TEX_KNOB_V, KNOB_W, KNOB_H);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.personal_dimension.title");
        fontRenderer.drawString(title,
                GUI_W / 2 - fontRenderer.getStringWidth(title) / 2, 6, 0x333333);

        for (int slot = 0; slot < 8; slot++) {
            int bx = (slot < 4) ? LEFT_BTN_X : RIGHT_BTN_X;
            int by = BTN_Y0 + (slot % 4) * BTN_STEP;
            if (slot >= activeParams.size()) continue;
            ParamDef p = activeParams.get(slot);
            String text = I18n.format(p.nameKey);
            int tx = bx + BTN_W / 2 - fontRenderer.getStringWidth(text) / 2;
            int ty = by + (BTN_H - fontRenderer.FONT_HEIGHT) / 2 + 1;
            fontRenderer.drawString(text, tx, ty, 0x333333);
        }

        if (activeParams.isEmpty()) return;
        ParamDef p = activeParams.get(selParam);

        String bar1Name = I18n.format(p.nameKey);
        String bar1State = formatValue(p);
        fontRenderer.drawString(bar1Name, BAR1_X + 6, BAR1_Y + 4, 0x333333);
        fontRenderer.drawString(bar1State,
                BAR1_X + BAR_W - 6 - fontRenderer.getStringWidth(bar1State), BAR1_Y + 4, 0x333333);

        String valStr = String.valueOf(getValue(p));
        fontRenderer.drawString(valStr,
                BAR2_X + BAR_W - 6 - fontRenderer.getStringWidth(valStr), BAR2_Y + 4, 0x333333);

        String desc = I18n.format(p.descKey);
        fontRenderer.drawSplitString(desc, BAR2_X + 4, BAR2_Y + BAR_H + 6,
                BAR_W - 8, 0x555555);
    }

    private String formatValue(ParamDef p) {
        int v = getValue(p);
        switch (p.id) {
            case PID_FLIGHT:
            case PID_NO_INERTIA:
            case PID_LOCK_WEATHER:
            case PID_LOCK_TIME:
            case PID_DAYLIGHT:
                return v > 0 ? I18n.format("gui.ae2enhanced.personal_dimension.on")
                        : I18n.format("gui.ae2enhanced.personal_dimension.off");
            case PID_MOB_SPAWN:
                return v > 0 ? I18n.format("gui.ae2enhanced.personal_dimension.on")
                        : I18n.format("gui.ae2enhanced.personal_dimension.off");
            case PID_SPEED:
                return v + "%";
            case PID_TIME_VALUE:
                return String.valueOf(v);
            default:
                return String.valueOf(v);
        }
    }

    private int getValue(ParamDef p) {
        return p.getter.apply(rules);
    }

    private void setValue(ParamDef p, int value) {
        p.setter.accept(rules, MathHelper.clamp(value, p.min, p.max));
    }

    private int computeKnobX(ParamDef p) {
        int value = getValue(p);
        float ratio = (value - p.min) / (float) (p.max - p.min);
        int trackX = this.guiLeft + BAR2_X;
        return trackX + Math.round(ratio * (BAR_W - KNOB_W));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        for (int slot = 0; slot < 8; slot++) {
            int bx = this.guiLeft + ((slot < 4) ? LEFT_BTN_X : RIGHT_BTN_X);
            int by = this.guiTop + BTN_Y0 + (slot % 4) * BTN_STEP;
            if (!in(mouseX, mouseY, bx, by, BTN_W, BTN_H)) continue;
            if (slot < activeParams.size()) {
                selParam = slot;
                return;
            }
        }

        if (activeParams.isEmpty()) return;
        ParamDef p = activeParams.get(selParam);

        if (in(mouseX, mouseY, this.guiLeft + BAR1_X, this.guiTop + BAR1_Y, BAR_W, BAR_H)) {
            int value = getValue(p);
            if (p.max - p.min == 1) {
                setValue(p, value > 0 ? 0 : 1);
            }
            return;
        }

        if (in(mouseX, mouseY, this.guiLeft + BAR2_X, this.guiTop + BAR2_Y, BAR_W, BAR_H)) {
            dragParam = p.id;
            updateSlider(mouseX);
            return;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragParam = -1;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (dragParam >= 0) updateSlider(mouseX);
    }

    private void updateSlider(int mouseX) {
        ParamDef p = getParamDefById(dragParam);
        if (p == null) return;
        int trackX = this.guiLeft + BAR2_X;
        float ratio = MathHelper.clamp((mouseX - trackX) / (float) (BAR_W - KNOB_W), 0f, 1f);
        int value = p.min + Math.round(ratio * (p.max - p.min));
        setValue(p, value);
    }

    private ParamDef getParamDefById(int id) {
        for (ParamDef p : activeParams) {
            if (p.id == id) return p;
        }
        return null;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        apply();
    }

    private void apply() {
        AE2Enhanced.network.sendToServer(new PacketPersonalDimensionRules(rules));
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
