package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.container.ContainerRingConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRingConfig;
import com.github.aeddddd.ae2enhanced.ring.RingLocator;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 网络链接指环配置 GUI —— 严格遵循先进 ME 工具 GUI 的交互范式：
 * 动态参数列表(按指环阶段过滤) + next/prev 槽位翻页 + Bar1 状态条 + Bar2 滑块.
 */
public class GuiRingConfig extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AE2Enhanced.MOD_ID, "textures/gui/me_omni_tool_gui.png");

    private static final int GUI_W = 195;
    private static final int GUI_H = 221;

    // ---- 参数ID ----
    private static final int PID_FLIGHT = 0;
    private static final int PID_FORCE_FLIGHT = 1;
    private static final int PID_FLY_SPEED = 2;
    private static final int PID_WALK_TWEAK = 3;
    private static final int PID_WALK_SPEED = 4;
    private static final int PID_JUMP = 5;
    private static final int PID_NO_INERTIA = 6;
    private static final int PID_WALL_PHASE = 7;
    private static final int PID_NIGHT_VISION = 8;
    private static final int PID_REACH = 9;
    private static final int PID_FEED = 10;
    private static final int PID_FEED_MODE = 11;
    private static final int PID_HEAL_AUTO = 12;
    private static final int PID_HEAL_PCT = 13;
    private static final int PID_POTION_MODE = 14;
    private static final int PID_MINING = 15;
    private static final int PID_DMG_BLOCK = 16;

    // ---- UV坐标(与先进 ME 工具 GUI 相同) ----
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

    // ---- 参数定义 ----
    private static class ParamDef {
        final int id;
        final String nameKey;
        final String descKey;
        final int min;
        final int max;
        final Predicate<ItemStack> visibleWhen;
        final Function<NBTTagCompound, Integer> getter;
        final BiConsumer<NBTTagCompound, Integer> setter;

        ParamDef(int id, String nameKey, String descKey, int min, int max,
                 Predicate<ItemStack> visibleWhen,
                 Function<NBTTagCompound, Integer> getter,
                 BiConsumer<NBTTagCompound, Integer> setter) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
            this.min = min;
            this.max = max;
            this.visibleWhen = visibleWhen;
            this.getter = getter;
            this.setter = setter;
        }
    }

    private static Predicate<ItemStack> tier(int minTier) {
        return s -> RingNBT.tierAtLeast(s, minTier);
    }

    private static final ParamDef[] BASE_PARAMS = {
        // ---- 阶段 I ----
        new ParamDef(PID_MINING, "gui.ae2enhanced.ring.mining", "gui.ae2enhanced.ring.mining.desc",
                0, 1, tier(0),
                c -> boolVal(c, RingNBT.MINING_FIX, true), (c, v) -> c.setBoolean(RingNBT.MINING_FIX, v > 0)),
        new ParamDef(PID_NIGHT_VISION, "gui.ae2enhanced.ring.night_vision", "gui.ae2enhanced.ring.night_vision.desc",
                0, 1, tier(0),
                c -> boolVal(c, RingNBT.NIGHT_VISION, false), (c, v) -> c.setBoolean(RingNBT.NIGHT_VISION, v > 0)),
        new ParamDef(PID_REACH, "gui.ae2enhanced.ring.reach", "gui.ae2enhanced.ring.reach.desc",
                50, (int) (AE2EnhancedConfig.ring.maxReachDistance * 10), tier(0),
                c -> Math.round((c.hasKey(RingNBT.REACH) ? c.getFloat(RingNBT.REACH) : 5.0f) * 10),
                (c, v) -> c.setFloat(RingNBT.REACH, v / 10f)),
        new ParamDef(PID_WALK_TWEAK, "gui.ae2enhanced.ring.walk_tweak", "gui.ae2enhanced.ring.walk_tweak.desc",
                0, 1, tier(0),
                c -> boolVal(c, RingNBT.WALK_TWEAK, false), (c, v) -> c.setBoolean(RingNBT.WALK_TWEAK, v > 0)),
        new ParamDef(PID_WALK_SPEED, "gui.ae2enhanced.ring.walk_speed", "gui.ae2enhanced.ring.walk_speed.desc",
                50, maxSpeedPct(), tier(0),
                c -> speedPct(c, RingNBT.WALK_SPEED, 0.1f), (c, v) -> c.setFloat(RingNBT.WALK_SPEED, 0.1f * v / 100f)),
        new ParamDef(PID_FEED, "gui.ae2enhanced.ring.feed", "gui.ae2enhanced.ring.feed.desc",
                0, 1, tier(0),
                c -> boolVal(c, RingNBT.FEED, false), (c, v) -> c.setBoolean(RingNBT.FEED, v > 0)),
        new ParamDef(PID_FEED_MODE, "gui.ae2enhanced.ring.feed_mode", "gui.ae2enhanced.ring.feed_mode.desc",
                0, 1, tier(0),
                c -> c.getInteger(RingNBT.FEED_MODE), (c, v) -> c.setInteger(RingNBT.FEED_MODE, v)),
        new ParamDef(PID_POTION_MODE, "gui.ae2enhanced.ring.potion_mode", "gui.ae2enhanced.ring.potion_mode.desc",
                0, 2, tier(0),
                c -> c.getInteger(RingNBT.POTION_MODE), (c, v) -> c.setInteger(RingNBT.POTION_MODE, v)),
        // ---- 阶段 II ----
        new ParamDef(PID_FLIGHT, "gui.ae2enhanced.ring.flight", "gui.ae2enhanced.ring.flight.desc",
                0, 1, tier(1),
                c -> boolVal(c, RingNBT.FLIGHT, false), (c, v) -> c.setBoolean(RingNBT.FLIGHT, v > 0)),
        new ParamDef(PID_FLY_SPEED, "gui.ae2enhanced.ring.fly_speed", "gui.ae2enhanced.ring.fly_speed.desc",
                100, maxSpeedPct(), tier(1),
                c -> speedPct(c, RingNBT.FLY_SPEED, 0.05f), (c, v) -> c.setFloat(RingNBT.FLY_SPEED, 0.05f * v / 100f)),
        new ParamDef(PID_NO_INERTIA, "gui.ae2enhanced.ring.no_inertia", "gui.ae2enhanced.ring.no_inertia.desc",
                0, 1, tier(1),
                c -> boolVal(c, RingNBT.NO_INERTIA, false), (c, v) -> c.setBoolean(RingNBT.NO_INERTIA, v > 0)),
        new ParamDef(PID_JUMP, "gui.ae2enhanced.ring.jump", "gui.ae2enhanced.ring.jump.desc",
                100, AE2EnhancedConfig.ring.maxJumpPercent, tier(1),
                c -> c.hasKey(RingNBT.JUMP_PCT) ? c.getInteger(RingNBT.JUMP_PCT) : 100,
                (c, v) -> c.setInteger(RingNBT.JUMP_PCT, v)),
        new ParamDef(PID_HEAL_AUTO, "gui.ae2enhanced.ring.heal_auto", "gui.ae2enhanced.ring.heal_auto.desc",
                0, 1, tier(1),
                c -> boolVal(c, RingNBT.HEAL_AUTO, false), (c, v) -> c.setBoolean(RingNBT.HEAL_AUTO, v > 0)),
        new ParamDef(PID_HEAL_PCT, "gui.ae2enhanced.ring.heal_pct", "gui.ae2enhanced.ring.heal_pct.desc",
                1, 100, tier(1),
                c -> c.hasKey(RingNBT.HEAL_PCT) ? c.getInteger(RingNBT.HEAL_PCT) : 50,
                (c, v) -> c.setInteger(RingNBT.HEAL_PCT, v)),
        // ---- 阶段 III ----
        new ParamDef(PID_WALL_PHASE, "gui.ae2enhanced.ring.wall_phase", "gui.ae2enhanced.ring.wall_phase.desc",
                0, 1, tier(2),
                c -> boolVal(c, RingNBT.WALL_PHASE, false), (c, v) -> c.setBoolean(RingNBT.WALL_PHASE, v > 0)),
        new ParamDef(PID_DMG_BLOCK, "gui.ae2enhanced.ring.dmg_block", "gui.ae2enhanced.ring.dmg_block.desc",
                0, 1, tier(2),
                c -> boolVal(c, RingNBT.DMG_BLOCK, true), (c, v) -> c.setBoolean(RingNBT.DMG_BLOCK, v > 0)),
        // ---- 阶段 IV(飞升) ----
        new ParamDef(PID_FORCE_FLIGHT, "gui.ae2enhanced.ring.force_flight", "gui.ae2enhanced.ring.force_flight.desc",
                0, 1, RingNBT::isAscended,
                c -> boolVal(c, RingNBT.FORCE_FLIGHT, false), (c, v) -> c.setBoolean(RingNBT.FORCE_FLIGHT, v > 0)),
    };

    private final EntityPlayer player;
    private ItemStack ringStack = ItemStack.EMPTY;
    private NBTTagCompound config = new NBTTagCompound();

    // 动态参数列表与翻页(与先进 ME 工具 GUI 相同模型)
    private final List<ParamDef> activeParams = new ArrayList<>();
    private int selParam = 0; // activeParams 中的索引
    private int currentPage = 0;
    private int dragParam = -1;

    public GuiRingConfig(EntityPlayer player, ContainerRingConfig container) {
        super(container);
        this.player = player;
        this.xSize = GUI_W;
        this.ySize = GUI_H;
    }

    private static int maxSpeedPct() {
        return AE2EnhancedConfig.ring.maxSpeedPercent;
    }

    private static int boolVal(NBTTagCompound c, String key, boolean def) {
        return (c.hasKey(key) ? c.getBoolean(key) : def) ? 1 : 0;
    }

    private static int speedPct(NBTTagCompound c, String key, float base) {
        float speed = c.hasKey(key) ? c.getFloat(key) : base;
        return Math.round(speed / base * 100f);
    }

    @Override
    public void initGui() {
        super.initGui();
        reload();
    }

    private void reload() {
        ringStack = RingLocator.findRing(player);
        if (ringStack.isEmpty()) {
            mc.displayGuiScreen(null);
            return;
        }
        config = ringStack.hasTagCompound() ? ringStack.getTagCompound().copy() : new NBTTagCompound();

        activeParams.clear();
        for (ParamDef p : BASE_PARAMS) {
            if (p.visibleWhen.test(ringStack)) {
                activeParams.add(p);
            }
        }
        selParam = MathHelper.clamp(selParam, 0, Math.max(0, activeParams.size() - 1));
        currentPage = 0;
        ensureSelectionVisible();
    }

    // ==================== 翻页辅助(与先进 ME 工具 GUI 相同) ====================

    private int getTotalPages() {
        int n = activeParams.size();
        if (n <= 8) return 1;
        int pages = 1;
        int remaining = n - 7;
        while (remaining > 0) {
            pages++;
            if (remaining <= 7) break;
            remaining -= 6;
        }
        return pages;
    }

    private boolean hasNextPage() {
        return currentPage < getTotalPages() - 1;
    }

    private boolean hasPrevPage() {
        return currentPage > 0;
    }

    /**
     * 获取指定槽位对应的 activeParams 索引.
     * @return >=0: 参数索引; -1: 下一页; -2: 上一页; -3: 空槽
     */
    private int getParamIndexForSlot(int slot) {
        int n = activeParams.size();
        if (currentPage == 0) {
            if (slot >= 0 && slot <= 6) {
                return slot < n ? slot : -3;
            }
            if (slot == 7) {
                return n > 8 ? -1 : (slot < n ? slot : -3);
            }
            return -3;
        }
        if (slot == 0) return -2; // prev
        // 翻页槽位优先：中间页第 8 槽必须是 next 按钮,否则后续页参数永远无法到达
        if (slot == 7 && hasNextPage()) return -1;
        int base = 7 + (currentPage - 1) * 6;
        int idx = base + (slot - 1);
        if (idx < n) return idx;
        return -3;
    }

    private int getSlotForParam(int paramIdx) {
        for (int slot = 0; slot < 8; slot++) {
            if (getParamIndexForSlot(slot) == paramIdx) return slot;
        }
        return -1;
    }

    private void ensureSelectionVisible() {
        if (activeParams.isEmpty()) return;
        if (selParam < 0 || selParam >= activeParams.size()) {
            selParam = 0;
        }
        if (getSlotForParam(selParam) < 0) {
            for (int p = 0; p < getTotalPages(); p++) {
                currentPage = p;
                if (getSlotForParam(selParam) >= 0) return;
            }
            selParam = 0;
            currentPage = 0;
        }
    }

    // ==================== 绘制 ====================

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

            int idx = getParamIndexForSlot(slot);
            if (idx == -1 || idx == -2) {
                this.drawTexturedModalRect(absX, absY, TEX_NORMAL_BTN_U, TEX_NORMAL_BTN_V, BTN_W, BTN_H);
            } else if (idx >= 0) {
                boolean selected = (selParam == idx);
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
        String title = I18n.format("gui.ae2enhanced.ring.title");
        fontRenderer.drawString(title,
                GUI_W / 2 - fontRenderer.getStringWidth(title) / 2, 6, 0x333333);

        for (int slot = 0; slot < 8; slot++) {
            int bx = (slot < 4) ? LEFT_BTN_X : RIGHT_BTN_X;
            int by = BTN_Y0 + (slot % 4) * BTN_STEP;
            int idx = getParamIndexForSlot(slot);

            String text;
            if (idx == -1) {
                text = I18n.format("gui.ae2enhanced.omni_tool_config.next_page");
            } else if (idx == -2) {
                text = I18n.format("gui.ae2enhanced.omni_tool_config.prev_page");
            } else if (idx >= 0) {
                text = I18n.format(activeParams.get(idx).nameKey);
            } else {
                continue;
            }
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
            case PID_MINING:
            case PID_NIGHT_VISION:
            case PID_WALK_TWEAK:
            case PID_FEED:
            case PID_FLIGHT:
            case PID_NO_INERTIA:
            case PID_HEAL_AUTO:
            case PID_WALL_PHASE:
            case PID_DMG_BLOCK:
            case PID_FORCE_FLIGHT:
                return v > 0 ? "ON" : "OFF";
            case PID_FLY_SPEED:
            case PID_WALK_SPEED:
            case PID_JUMP:
            case PID_HEAL_PCT:
                return v + "%";
            case PID_REACH:
                return String.format("%.1f", v / 10.0);
            case PID_FEED_MODE:
                return I18n.format(v == 0 ? "gui.ae2enhanced.ring.feed_mode.all" : "gui.ae2enhanced.ring.feed_mode.equipment");
            case PID_POTION_MODE:
                return I18n.format("gui.ae2enhanced.ring.potion_mode." + v);
            default:
                return String.valueOf(v);
        }
    }

    private int getValue(ParamDef p) {
        return p.getter.apply(config);
    }

    private void setValue(ParamDef p, int value) {
        p.setter.accept(config, MathHelper.clamp(value, p.min, p.max));
    }

    private int computeKnobX(ParamDef p) {
        int value = getValue(p);
        float ratio = (value - p.min) / (float) (p.max - p.min);
        int trackX = this.guiLeft + BAR2_X;
        return trackX + Math.round(ratio * (BAR_W - KNOB_W));
    }

    // ==================== 交互 ====================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // 顶部参数按钮
        for (int slot = 0; slot < 8; slot++) {
            int bx = this.guiLeft + ((slot < 4) ? LEFT_BTN_X : RIGHT_BTN_X);
            int by = this.guiTop + BTN_Y0 + (slot % 4) * BTN_STEP;
            if (!in(mouseX, mouseY, bx, by, BTN_W, BTN_H)) continue;

            int idx = getParamIndexForSlot(slot);
            if (idx == -1) {
                if (hasNextPage()) {
                    currentPage++;
                    selParam = getParamIndexForSlot(1);
                    if (selParam < 0) selParam = 0;
                }
                return;
            } else if (idx == -2) {
                if (hasPrevPage()) {
                    currentPage--;
                    selParam = getParamIndexForSlot(currentPage == 0 ? 0 : 1);
                    if (selParam < 0) selParam = 0;
                }
                return;
            } else if (idx >= 0) {
                selParam = idx;
                return;
            }
        }

        if (activeParams.isEmpty()) return;
        ParamDef p = activeParams.get(selParam);

        // Bar1 — 布尔/枚举参数点击切换(布尔翻转,三态循环)
        if (in(mouseX, mouseY, this.guiLeft + BAR1_X, this.guiTop + BAR1_Y, BAR_W, BAR_H)) {
            int value = getValue(p);
            if (p.max - p.min == 1) {
                setValue(p, value > 0 ? 0 : 1);
            } else if (p.max - p.min == 2) {
                setValue(p, (value + 1) % 3);
            }
            return;
        }

        // Bar2 — 开始拖拽
        if (in(mouseX, mouseY, this.guiLeft + BAR2_X, this.guiTop + BAR2_Y, BAR_W, BAR_H)) {
            dragParam = p.id;
            updateSlider(mouseX);
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
        if (dragParam >= 0) {
            ParamDef p = getParamDefById(dragParam);
            if (p != null) {
                updateSliderFor(mouseX, p);
            }
        }
    }

    private void updateSlider(int mouseX) {
        ParamDef p = getParamDefById(dragParam);
        if (p != null) {
            updateSliderFor(mouseX, p);
        }
    }

    private void updateSliderFor(int mouseX, ParamDef p) {
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
        if (!ringStack.isEmpty()) {
            AE2Enhanced.network.sendToServer(new PacketRingConfig(config));
        }
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
