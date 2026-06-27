package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniToolConfig;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolEnchantments;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementRestriction;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 先进ME工具配置GUI —— 严格遵循 me_omni_tool_gui.png UV文档.
 * 支持根据已安装升级动态显示参数，超过8项时启用翻页.
 */
public class GuiOmniToolConfig extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AE2Enhanced.MOD_ID, "textures/gui/me_omni_tool_gui.png");
    private static final ResourceLocation TEXTURE_DREAM = new ResourceLocation(
            AE2Enhanced.MOD_ID, "textures/gui/me_omni_tool_gui_dream.png");

    // GUI尺寸
    private static final int GUI_W = 195;
    private static final int GUI_H = 221;

    // ---- 参数ID ----
    private static final int PID_MODE = 0;
    private static final int PID_DROP = 1;
    private static final int PID_SILK = 2;
    private static final int PID_BLINK = 3;
    private static final int PID_COOLDOWN = 4;
    private static final int PID_CHAOS_KILL = 5;
    private static final int PID_CONFORMAL = 6;
    private static final int PID_ADVANCED_SILK = 7;
    private static final int PID_WALL_PHASE = 8;
    private static final int PID_CABLE_COLOR = 9;
    private static final int PID_REACH_DISTANCE = 10;
    private static final int PID_PLACEMENT_RESTRICTION = 11;
    private static final int PID_COUNT = 12;
    private static final int PID_ENCHANT_BASE = 1000;

    // ---- UV坐标：顶部按钮区 ----
    private static final int LEFT_BTN_X = 4;
    private static final int RIGHT_BTN_X = 116;
    private static final int BTN_W = 75;
    private static final int BTN_H = 17;
    private static final int BTN_Y0 = 25;
    private static final int BTN_GAP = 2;
    private static final int BTN_STEP = BTN_H + BTN_GAP;

    // ---- UV坐标：y=221 纹理复制区 ----
    private static final int TEX_NORMAL_BTN_U = 0;
    private static final int TEX_NORMAL_BTN_V = 221;
    private static final int TEX_HIGHLIGHT_BTN_U = 75;
    private static final int TEX_HIGHLIGHT_BTN_V = 221;
    private static final int TEX_KNOB_U = 150;
    private static final int TEX_KNOB_V = 221;
    private static final int KNOB_W = 12;
    private static final int KNOB_H = 17;

    // ---- UV坐标：y=238 高亮大条 ----
    private static final int TEX_HIGHLIGHT_BAR_U = 0;
    private static final int TEX_HIGHLIGHT_BAR_V = 238;
    private static final int BAR_W = 188;
    private static final int BAR_H = 17;

    // ---- 中间长条坐标 ----
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
        final Function<ItemStack, Integer> getter;
        final BiConsumer<ItemStack, Integer> setter;
        final short enchantmentId; // 仅附魔参数使用，-1 表示普通参数

        ParamDef(int id, String nameKey, String descKey, int min, int max,
                 Predicate<ItemStack> visibleWhen,
                 Function<ItemStack, Integer> getter,
                 BiConsumer<ItemStack, Integer> setter) {
            this(id, nameKey, descKey, min, max, visibleWhen, getter, setter, (short) -1);
        }

        ParamDef(int id, String nameKey, String descKey, int min, int max,
                 Predicate<ItemStack> visibleWhen,
                 Function<ItemStack, Integer> getter,
                 BiConsumer<ItemStack, Integer> setter,
                 short enchantmentId) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
            this.min = min;
            this.max = max;
            this.visibleWhen = visibleWhen;
            this.getter = getter;
            this.setter = setter;
            this.enchantmentId = enchantmentId;
        }

        boolean isEnchantment() {
            return enchantmentId >= 0;
        }
    }

    private static final ParamDef[] BASE_PARAMS = {
        new ParamDef(PID_MODE, "gui.ae2enhanced.omni_tool_config.mode",
                "gui.ae2enhanced.omni_tool_config.mode.desc",
                0, 3, s -> true,
                OmniToolUpgrades::getMode,
                OmniToolUpgrades::setMode),
        new ParamDef(PID_DROP, "gui.ae2enhanced.omni_tool_config.drop_mode",
                "gui.ae2enhanced.omni_tool_config.drop_mode.desc",
                0, 2, s -> true,
                OmniToolUpgrades::getDropMode,
                OmniToolUpgrades::setDropMode),
        new ParamDef(PID_SILK, "gui.ae2enhanced.omni_tool_config.silk_touch",
                "gui.ae2enhanced.omni_tool_config.silk_touch.desc",
                0, 1, s -> true,
                s -> OmniToolUpgrades.isSilkTouchEnabled(s) ? 1 : 0,
                (s, v) -> OmniToolUpgrades.setSilkTouchEnabled(s, v > 0)),
        new ParamDef(PID_BLINK, "gui.ae2enhanced.omni_tool_config.blink_dist",
                "gui.ae2enhanced.omni_tool_config.blink_dist.desc",
                1, 256, s -> true,
                s -> (int) OmniToolUpgrades.getBlinkDistance(s),
                (s, v) -> OmniToolUpgrades.setBlinkDistance(s, v)),
        new ParamDef(PID_COOLDOWN, "gui.ae2enhanced.omni_tool_config.break_cooldown",
                "gui.ae2enhanced.omni_tool_config.break_cooldown.desc",
                0, 100, s -> true,
                OmniToolUpgrades::getBreakCooldown,
                OmniToolUpgrades::setBreakCooldown),
        new ParamDef(PID_CHAOS_KILL, "gui.ae2enhanced.omni_tool_config.chaos_force_kill",
                "gui.ae2enhanced.omni_tool_config.chaos_force_kill.desc",
                0, 1, OmniToolUpgrades::hasChaosCore,
                s -> OmniToolUpgrades.isChaosForceKillEnabled(s) ? 1 : 0,
                (s, v) -> OmniToolUpgrades.setChaosForceKillEnabled(s, v > 0)),
        new ParamDef(PID_CONFORMAL, "gui.ae2enhanced.omni_tool_config.conformal",
                "gui.ae2enhanced.omni_tool_config.conformal.desc",
                0, 1, OmniToolUpgrades::hasConformalCharge,
                s -> OmniToolUpgrades.hasConformalCharge(s) ? 1 : 0,
                (s, v) -> OmniToolUpgrades.setConformalCharge(s, v > 0)),
        new ParamDef(PID_ADVANCED_SILK, "gui.ae2enhanced.omni_tool_config.advanced_silk_touch",
                "gui.ae2enhanced.omni_tool_config.advanced_silk_touch.desc",
                0, 1, s -> true,
                s -> OmniToolUpgrades.isAdvancedSilkTouchEnabled(s) ? 1 : 0,
                (s, v) -> OmniToolUpgrades.setAdvancedSilkTouchEnabled(s, v > 0)),
        new ParamDef(PID_WALL_PHASE, "gui.ae2enhanced.omni_tool_config.wall_phase",
                "gui.ae2enhanced.omni_tool_config.wall_phase.desc",
                0, 1, s -> true,
                s -> OmniToolUpgrades.isWallPhaseEnabled(s) ? 1 : 0,
                (s, v) -> OmniToolUpgrades.setWallPhaseEnabled(s, v > 0)),
        new ParamDef(PID_CABLE_COLOR, "gui.ae2enhanced.omni_tool_config.cable_color",
                "gui.ae2enhanced.omni_tool_config.cable_color.desc",
                0, 16, s -> true,
                s -> new PlacementConfig(s).getCableColor().ordinal(),
                (s, v) -> {}), // 实际应用在 apply() 中通过 PlacementConfig 写入
        new ParamDef(PID_REACH_DISTANCE, "gui.ae2enhanced.omni_tool_config.reach_distance",
                "gui.ae2enhanced.omni_tool_config.reach_distance.desc",
                5, 32, s -> true,
                s -> (int) new PlacementConfig(s).getReachDistance(),
                (s, v) -> {}), // 实际应用在 apply() 中通过 PlacementConfig 写入
        new ParamDef(PID_PLACEMENT_RESTRICTION, "gui.ae2enhanced.omni_tool_config.placement_restriction",
                "gui.ae2enhanced.omni_tool_config.placement_restriction.desc",
                0, PlacementRestriction.values().length - 1, s -> true,
                s -> new PlacementConfig(s).getPlacementRestriction().ordinal(),
                (s, v) -> {}), // 实际应用在 apply() 中通过 PlacementConfig 写入
    };

    private final EntityPlayer player;
    private ItemStack toolStack = ItemStack.EMPTY;

    private final int[] values = new int[PID_COUNT];
    private final Map<Short, Integer> enchantValues = new LinkedHashMap<>();
    private int paramEnabledMask = 0xFF;
    private int dragParam = -1;

    // 动态参数列表与翻页
    private final List<ParamDef> activeParams = new ArrayList<>();
    private int selParam = 0; // activeParams 中的索引
    private int currentPage = 0;

    // 彩蛋
    private int verticalBarClicks = 0;
    private boolean dreamMode = false;

    public GuiOmniToolConfig(EntityPlayer player, ContainerOmniToolConfig container) {
        super(container);
        this.player = player;
        this.xSize = GUI_W;
        this.ySize = GUI_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        reload();
    }

    private void reload() {
        toolStack = ItemStack.EMPTY;
        for (EnumHand hand : EnumHand.values()) {
            ItemStack s = player.getHeldItem(hand);
            if (!s.isEmpty() && s.getItem() instanceof ItemAdvancedMEOmniTool) {
                toolStack = s;
                break;
            }
        }
        if (toolStack.isEmpty()) {
            mc.displayGuiScreen(null);
            return;
        }

        values[PID_MODE] = OmniToolUpgrades.getMode(toolStack);
        values[PID_DROP] = OmniToolUpgrades.getDropMode(toolStack);
        values[PID_SILK] = OmniToolUpgrades.isSilkTouchEnabled(toolStack) ? 1 : 0;
        values[PID_BLINK] = (int) OmniToolUpgrades.getBlinkDistance(toolStack);
        values[PID_COOLDOWN] = OmniToolUpgrades.getBreakCooldown(toolStack);
        values[PID_CHAOS_KILL] = OmniToolUpgrades.isChaosForceKillEnabled(toolStack) ? 1 : 0;
        values[PID_CONFORMAL] = OmniToolUpgrades.hasConformalCharge(toolStack) ? 1 : 0;
        values[PID_ADVANCED_SILK] = OmniToolUpgrades.isAdvancedSilkTouchEnabled(toolStack) ? 1 : 0;
        values[PID_WALL_PHASE] = OmniToolUpgrades.isWallPhaseEnabled(toolStack) ? 1 : 0;
        values[PID_CABLE_COLOR] = new PlacementConfig(toolStack).getCableColor().ordinal();
        values[PID_REACH_DISTANCE] = (int) new PlacementConfig(toolStack).getReachDistance();
        values[PID_PLACEMENT_RESTRICTION] = new PlacementConfig(toolStack).getPlacementRestriction().ordinal();

        paramEnabledMask = 0;
        for (int i = 0; i < PID_COUNT; i++) {
            if (OmniToolUpgrades.isParamEnabled(toolStack, i)) {
                paramEnabledMask |= (1 << i);
            }
        }

        enchantValues.clear();
        NBTTagList stored = OmniToolEnchantments.getStoredEnchantments(toolStack);
        for (int i = 0; i < stored.tagCount(); i++) {
            NBTTagCompound tag = stored.getCompoundTagAt(i);
            enchantValues.put(tag.getShort("id"), (int) tag.getShort("lvl"));
        }

        activeParams.clear();
        for (ParamDef p : BASE_PARAMS) {
            if (p.visibleWhen.test(toolStack)) {
                activeParams.add(p);
            }
        }

        // 附魔调整参数统一放在基础参数后面
        int enchantIdx = 0;
        for (Map.Entry<Short, Integer> entry : enchantValues.entrySet()) {
            short enchId = entry.getKey();
            Enchantment ench = Enchantment.getEnchantmentByID(enchId);
            String name = ench != null ? ench.getName() : "enchantment.unknown";
            // 等级上限取决于合成时附魔书的原始等级
            int sourceLevel = OmniToolEnchantments.getEnchantmentSourceLevel(toolStack, enchId);
            int maxLevel = Math.max(1, sourceLevel);
            ParamDef p = new ParamDef(
                    PID_ENCHANT_BASE + enchantIdx,
                    name,
                    "gui.ae2enhanced.omni_tool_config.enchant.desc",
                    0, maxLevel,
                    s -> true,
                    s -> enchantValues.getOrDefault(enchId, 0),
                    (s, v) -> enchantValues.put(enchId, v),
                    enchId
            );
            activeParams.add(p);
            enchantIdx++;
        }

        selParam = MathHelper.clamp(selParam, 0, Math.max(0, activeParams.size() - 1));
        currentPage = 0;
        ensureSelectionVisible();
    }

    // ==================== 翻页辅助 ====================

    private int getTotalPages() {
        int n = activeParams.size();
        if (n <= 8) return 1;
        int pages = 1;
        int remaining = n - 7; // 第一页最多放7个（留1槽给next）
        while (remaining > 0) {
            pages++;
            if (remaining <= 7) break; // 最后一页最多放7个（留1槽给prev）
            remaining -= 6; // 中间页放6个（留2槽给prev/next）
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
        int base = 7 + (currentPage - 1) * 6;
        int idx = base + (slot - 1);
        if (idx < n) return idx;
        // 如果当前不是最后一页，最后一槽为 next
        if (slot == 7 && hasNextPage()) return -1;
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
            // 翻页直到 selParam 可见
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

        // 1. 完整背景
        if (dreamMode) {
            this.mc.getTextureManager().bindTexture(TEXTURE_DREAM);
        }
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_W, GUI_H);
        this.mc.getTextureManager().bindTexture(TEXTURE);

        // 2. 顶部参数按钮
        for (int slot = 0; slot < 8; slot++) {
            int bx = (slot < 4) ? LEFT_BTN_X : RIGHT_BTN_X;
            int by = BTN_Y0 + (slot % 4) * BTN_STEP;
            int absX = this.guiLeft + bx;
            int absY = this.guiTop + by;

            int idx = getParamIndexForSlot(slot);
            if (idx == -1) {
                // 下一页按钮
                this.drawTexturedModalRect(absX, absY, TEX_NORMAL_BTN_U, TEX_NORMAL_BTN_V, BTN_W, BTN_H);
            } else if (idx == -2) {
                // 上一页按钮
                this.drawTexturedModalRect(absX, absY, TEX_NORMAL_BTN_U, TEX_NORMAL_BTN_V, BTN_W, BTN_H);
            } else if (idx >= 0) {
                boolean selected = (selParam == idx);
                this.drawTexturedModalRect(absX, absY,
                        selected ? TEX_HIGHLIGHT_BTN_U : TEX_NORMAL_BTN_U,
                        selected ? TEX_HIGHLIGHT_BTN_V : TEX_NORMAL_BTN_V,
                        BTN_W, BTN_H);
            }
        }

        // 3. Bar1 — 启用时叠加高亮大条
        if (!activeParams.isEmpty() && isParamEnabled(activeParams.get(selParam).id)) {
            this.drawTexturedModalRect(this.guiLeft + BAR1_X, this.guiTop + BAR1_Y,
                    TEX_HIGHLIGHT_BAR_U, TEX_HIGHLIGHT_BAR_V, BAR_W, BAR_H);
        }

        // 4. Bar2 — 滑块旋钮
        if (!activeParams.isEmpty()) {
            int knobX = computeKnobX(activeParams.get(selParam));
            this.drawTexturedModalRect(knobX, this.guiTop + BAR2_Y,
                    TEX_KNOB_U, TEX_KNOB_V, KNOB_W, KNOB_H);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        String title = I18n.format("gui.ae2enhanced.omni_tool_config.title");
        fontRenderer.drawString(title,
                GUI_W / 2 - fontRenderer.getStringWidth(title) / 2, 6, 0x333333);

        // 顶部按钮文字
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
                ParamDef p = activeParams.get(idx);
                if (p.isEnchantment()) {
                    text = getEnchantmentDisplayName(p.enchantmentId);
                } else {
                    text = I18n.format(p.nameKey);
                }
            } else {
                continue;
            }
            int tx = bx + BTN_W / 2 - fontRenderer.getStringWidth(text) / 2;
            int ty = by + (BTN_H - fontRenderer.FONT_HEIGHT) / 2 + 1;
            fontRenderer.drawString(text, tx, ty, 0x333333);
        }

        if (activeParams.isEmpty()) return;
        ParamDef p = activeParams.get(selParam);

        // Bar1 文字 — 参数名 + ON/OFF（附魔参数显示为等级）
        String bar1Name;
        if (p.isEnchantment()) {
            bar1Name = getEnchantmentDisplayName(p.enchantmentId);
        } else {
            bar1Name = I18n.format(p.nameKey);
        }
        String bar1State = isParamEnabled(p.id) ? "ON" : "OFF";
        fontRenderer.drawString(bar1Name, BAR1_X + 6, BAR1_Y + 4, 0x333333);
        fontRenderer.drawString(bar1State,
                BAR1_X + BAR_W - 6 - fontRenderer.getStringWidth(bar1State), BAR1_Y + 4, 0x333333);

        // Bar2 文字 — 当前值
        String valStr = formatValue(p);
        fontRenderer.drawString(valStr,
                BAR2_X + BAR_W - 6 - fontRenderer.getStringWidth(valStr), BAR2_Y + 4, 0x333333);

        // Bar2 下方描述文字
        String desc = I18n.format(p.descKey);
        fontRenderer.drawSplitString(desc, BAR2_X + 4, BAR2_Y + BAR_H + 6,
                BAR_W - 8, 0x555555);
    }

    private String getEnchantmentDisplayName(short enchantmentId) {
        Enchantment ench = Enchantment.getEnchantmentByID(enchantmentId);
        if (ench != null) {
            return ench.getTranslatedName(enchantValues.getOrDefault(enchantmentId, 0));
        }
        return I18n.format("item.ae2enhanced.me_omni_tool.unknown_enchant", enchantmentId,
                enchantValues.getOrDefault(enchantmentId, 0));
    }

    private String formatValue(ParamDef p) {
        if (p.isEnchantment()) {
            return String.valueOf(getValue(p));
        }
        switch (p.id) {
            case PID_MODE:
                return I18n.format(ItemAdvancedMEOmniTool.getModeNameKey(getValue(p)));
            case PID_DROP:
                return I18n.format(ItemAdvancedMEOmniTool.getDropModeNameKey(getValue(p)));
            case PID_SILK:
            case PID_CHAOS_KILL:
            case PID_CONFORMAL:
            case PID_ADVANCED_SILK:
            case PID_WALL_PHASE:
                return getValue(p) > 0 ? "ON" : "OFF";
            case PID_PLACEMENT_RESTRICTION:
                return I18n.format(PlacementRestriction.fromOrdinal(getValue(p)).getNameKey());
            default:
                return String.valueOf(getValue(p));
        }
    }

    private int getValue(ParamDef p) {
        if (p.isEnchantment()) {
            return enchantValues.getOrDefault(p.enchantmentId, 0);
        }
        return values[p.id];
    }

    private void setValue(ParamDef p, int value) {
        if (p.isEnchantment()) {
            enchantValues.put(p.enchantmentId, value);
        } else {
            values[p.id] = value;
        }
    }

    private int computeKnobX(ParamDef p) {
        int value = getValue(p);
        float ratio = (value - p.min) / (float) (p.max - p.min);
        int trackX = this.guiLeft + BAR2_X;
        return trackX + Math.round(ratio * (BAR_W - KNOB_W));
    }

    private boolean isParamEnabled(int paramId) {
        if (paramId >= PID_ENCHANT_BASE) return true; // 附魔参数始终启用
        return (paramEnabledMask & (1 << paramId)) != 0;
    }

    private void setParamEnabled(int paramId, boolean enabled) {
        if (paramId >= PID_ENCHANT_BASE) return;
        if (enabled) paramEnabledMask |= (1 << paramId);
        else paramEnabledMask &= ~(1 << paramId);
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

        // 中间竖框 — 彩蛋计数
        if (!dreamMode && in(mouseX, mouseY, this.guiLeft + 81, this.guiTop + 25, 33, 75)) {
            verticalBarClicks++;
            if (verticalBarClicks >= 30) {
                dreamMode = true;
            }
            return;
        }

        if (activeParams.isEmpty()) return;
        ParamDef p = activeParams.get(selParam);

        // Bar1 — 切换启用/禁用（附魔参数无效）
        if (!p.isEnchantment() && in(mouseX, mouseY, this.guiLeft + BAR1_X, this.guiTop + BAR1_Y, BAR_W, BAR_H)) {
            setParamEnabled(p.id, !isParamEnabled(p.id));
            return;
        }

        // Bar2 — 开始拖拽
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
        if (toolStack.isEmpty()) return;
        OmniToolUpgrades.setMode(toolStack, values[PID_MODE]);
        OmniToolUpgrades.setDropMode(toolStack, values[PID_DROP]);
        OmniToolUpgrades.setSilkTouchEnabled(toolStack, values[PID_SILK] > 0);
        OmniToolUpgrades.setBlinkDistance(toolStack, values[PID_BLINK]);
        OmniToolUpgrades.setBreakCooldown(toolStack, values[PID_COOLDOWN]);
        OmniToolUpgrades.setChaosForceKillEnabled(toolStack, values[PID_CHAOS_KILL] > 0);
        OmniToolUpgrades.setConformalCharge(toolStack, values[PID_CONFORMAL] > 0);
        OmniToolUpgrades.setAdvancedSilkTouchEnabled(toolStack, values[PID_ADVANCED_SILK] > 0);
        OmniToolUpgrades.setWallPhaseEnabled(toolStack, values[PID_WALL_PHASE] > 0);

        PlacementConfig placementConfig = new PlacementConfig(toolStack);
        int colorIdx = values[PID_CABLE_COLOR];
        if (colorIdx >= 0 && colorIdx < appeng.api.util.AEColor.values().length) {
            placementConfig.setCableColor(appeng.api.util.AEColor.values()[colorIdx]);
        }
        placementConfig.setReachDistance(values[PID_REACH_DISTANCE]);
        placementConfig.setPlacementRestriction(PlacementRestriction.fromOrdinal(values[PID_PLACEMENT_RESTRICTION]));

        for (int i = 0; i < PID_COUNT; i++) {
            OmniToolUpgrades.setParamEnabled(toolStack, i, (paramEnabledMask & (1 << i)) != 0);
        }

        // 应用附魔调整
        NBTTagList enchList = new NBTTagList();
        for (Map.Entry<Short, Integer> entry : enchantValues.entrySet()) {
            if (entry.getValue() <= 0) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", entry.getKey());
            tag.setShort("lvl", entry.getValue().shortValue());
            enchList.appendTag(tag);
        }
        OmniToolEnchantments.setStoredEnchantments(toolStack, enchList);

        AE2Enhanced.network.sendToServer(new PacketOmniToolConfig(
                values[PID_MODE], values[PID_DROP], values[PID_SILK] > 0,
                OmniToolUpgrades.getFortuneLevel(toolStack), values[PID_BLINK], values[PID_COOLDOWN],
                paramEnabledMask, values[PID_CHAOS_KILL] > 0, values[PID_CONFORMAL] > 0,
                values[PID_ADVANCED_SILK] > 0, values[PID_WALL_PHASE] > 0,
                values[PID_CABLE_COLOR], values[PID_REACH_DISTANCE],
                values[PID_PLACEMENT_RESTRICTION], enchList));
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
