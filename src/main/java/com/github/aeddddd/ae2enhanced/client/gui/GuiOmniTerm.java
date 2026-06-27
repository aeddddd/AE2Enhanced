package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.TerminalStyle;
import appeng.api.config.ViewItems;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.IConfigManager;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.InternalSlotME;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.client.me.SlotME;
import appeng.client.ActionKey;
import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.client.gui.util.GuiReflectionCache;
import com.github.aeddddd.ae2enhanced.client.gui.util.GuiResourceCache;
import com.github.aeddddd.ae2enhanced.client.gui.util.SlotPositionManager;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import com.github.aeddddd.ae2enhanced.client.me.CraftingStatus;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniInventoryUpdate;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniSearchRequest;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniSearchResult;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniTermAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniUpdateNotify;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.awt.Rectangle;

/**
 * 全能无线终端 GUI —— 物品库 + 合成栏 + 81槽位编码样板 + 右侧存储
 */
public class GuiOmniTerm extends GuiMEMonitorable implements IJEIGhostIngredients {

    // 反射字段缓存(一次性查找,终身复用)
    private static final java.lang.reflect.Field REPO_FIELD =
            GuiReflectionCache.getField(GuiMEMonitorable.class, "repo");
    private static final java.lang.reflect.Field VIEW_CELL_FIELD =
            GuiReflectionCache.getField(GuiMEMonitorable.class, "viewCell");
    private static final java.lang.reflect.Field SEARCH_FIELD =
            GuiReflectionCache.getField(GuiMEMonitorable.class, "searchField");
    private static final java.lang.reflect.Field AUTO_FOCUS_FIELD;
    private static final java.lang.reflect.Field CRAFTING_STATUS_BTN_FIELD =
            GuiReflectionCache.getField(GuiMEMonitorable.class, "craftingStatusBtn");
    private static final java.lang.reflect.Field ROWS_FIELD =
            GuiReflectionCache.getField(GuiMEMonitorable.class, "rows");
    private static final java.lang.reflect.Field PER_ROW_FIELD =
            GuiReflectionCache.getField(GuiMEMonitorable.class, "perRow");

    static {
        java.lang.reflect.Field temp;
        try {
            temp = GuiReflectionCache.getField(GuiMEMonitorable.class, "isAutoFocus");
        } catch (Exception e) {
            temp = null;
        }
        AUTO_FOCUS_FIELD = temp;
    }

    private final ContainerOmniTerm container;
    private GuiScrollbar patternScrollBar;

    // 按钮
    private GuiTabButton tabCraftButton;
    private GuiTabButton tabProcessButton;
    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;
    private GuiImgButton clearPatternBtn;

    // 动态高度相关
    private int omniRows = 3;
    private int extraHeight = 0;
    private final SlotPositionManager slotPositionMgr = new SlotPositionManager();

    // 鼠标跟踪
    private int currentMouseX;
    private int currentMouseY;

    // 搜索框缓存(避免 keyTyped 中重复反射)
    private MEGuiTextField omniSearchField;

    // V4：renderView 版本追踪（避免重复更新滚动条）
    private long lastRenderedViewVersion = 0;

    // R3：滚动位置追踪（用于发送 PAGE_REQUEST）
    private int lastScrollPosition = -1;

    // View Cell 缓存
    private final net.minecraft.item.ItemStack[] omniViewCells = new net.minecraft.item.ItemStack[5];
    private boolean cachedHasViewCell = false;
    private boolean isOmniRepo = false;



    public GuiOmniTerm(InventoryPlayer inventoryPlayer, ITerminalHost host) {
        super(inventoryPlayer, host, new ContainerOmniTerm(inventoryPlayer, host));
        this.container = (ContainerOmniTerm) this.inventorySlots;
        this.xSize = 357;

        // 通过反射将 final repo 替换为 OmniItemRepo(支持合成置顶)
        try {
            REPO_FIELD.set(this, new com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo(this.getScrollBar(), this));
            this.isOmniRepo = true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to replace ItemRepo with OmniItemRepo", e);
        }
    }

    @Override
    public void initGui() {
        // 计算行数：SMALL 固定 5 行,TALL 根据屏幕高度动态
        TerminalStyle style = (TerminalStyle) AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
        if (style == TerminalStyle.SMALL) {
            this.omniRows = 5;
        } else {
            int maxExtraRows = Math.max(0, (this.height - 251 - 32) / 18);
            this.omniRows = Math.max(3, 3 + maxExtraRows);
        }
        this.extraHeight = (this.omniRows - 3) * 18;
        final int desiredYSize = 251 + this.extraHeight;
        this.ySize = desiredYSize;
        this.xSize = 357;

        super.initGui();

        final int oldGuiTop = this.guiTop;
        final int oldGuiLeft = this.guiLeft;

        // super.initGui() 会覆盖 xSize/ySize,导致 JEI 错误识别 GUI 区域,必须恢复
        this.xSize = 357;
        this.ySize = desiredYSize;
        this.guiLeft = (this.width - 357) / 2;
        this.guiTop = (this.height - desiredYSize) / 2;

        // 1. 保存并恢复槽位原始 y 位置
        this.slotPositionMgr.captureAll(this.inventorySlots.inventorySlots);
        this.slotPositionMgr.restoreAndOffset(
                this.inventorySlots.inventorySlots,
                this.extraHeight,
                s -> !this.container.isViewCellSlot(s) && s.yPos >= 86);

        // 2. 移除 super 创建的 SlotME,重新创建 18 列 × omniRows 行
        this.inventorySlots.inventorySlots.removeIf(s -> s instanceof SlotME);
        this.getMeSlots().clear();
        for (int row = 0; row < this.omniRows; row++) {
            for (int col = 0; col < 18; col++) {
                this.getMeSlots().add(new InternalSlotME(this.repo, col + row * 18, 8 + col * 18, 18 + row * 18));
            }
        }
        for (InternalSlotME me : this.getMeSlots()) {
            this.inventorySlots.inventorySlots.add(new SlotME(me));
        }

        // 4. 重新定位 AE2 标准按钮(使用相对于旧 guiLeft/guiTop 的偏移,保持原始布局比例)
        for (GuiButton btn : this.buttonList) {
            if (btn instanceof GuiImgButton) {
                GuiImgButton imgBtn = (GuiImgButton) btn;
                Settings setting = imgBtn.getSetting();
                if (setting == Settings.SORT_BY || setting == Settings.VIEW_MODE
                        || setting == Settings.SORT_DIRECTION || setting == Settings.SEARCH_MODE
                        || setting == Settings.TERMINAL_STYLE) {
                    btn.y = btn.y - oldGuiTop + this.guiTop;
                    btn.x = btn.x - oldGuiLeft + this.guiLeft;
                }
            } else if (btn instanceof GuiTabButton) {
                btn.y = btn.y - oldGuiTop + this.guiTop;
                btn.x = btn.x - oldGuiLeft + this.guiLeft;
            }
        }

        // 5. 替换搜索框
        try {
            MEGuiTextField oldField = (MEGuiTextField) SEARCH_FIELD.get(this);
            String oldText = oldField != null ? oldField.getText() : "";
            boolean wasFocused = oldField != null && oldField.isFocused();
            boolean autoFocus = false;
            if (AUTO_FOCUS_FIELD != null) {
                try {
                    autoFocus = AUTO_FOCUS_FIELD.getBoolean(this);
                } catch (Exception ignored) {}
            }
            
            MEGuiTextField newField = new MEGuiTextField(this.fontRenderer, this.guiLeft + 204, this.guiTop + 4, 125, 11);
            newField.setMaxStringLength(35);
            newField.setTextColor(0xFFFFFF);
            newField.setSelectionColor(-16744448);
            newField.setEnableBackgroundDrawing(false);
            newField.setFocused(autoFocus || wasFocused);
            newField.setText(oldText);
            SEARCH_FIELD.set(this, newField);
            this.omniSearchField = newField;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 6. 设置物品库滚动条
        GuiScrollbar itemScrollBar = this.getScrollBar();
        if (itemScrollBar != null) {
            itemScrollBar.setLeft(337).setTop(18).setHeight(this.omniRows * 18);
            this.updateItemScrollRange();
        }

        // 7. 添加合成计划按钮(先移除 super.initGui() 在 viewCell=true 时创建的中间位置旧按钮)
        try {
            GuiTabButton oldBtn = (GuiTabButton) CRAFTING_STATUS_BTN_FIELD.get(this);
            if (oldBtn != null) {
                this.buttonList.remove(oldBtn);
            }
        } catch (Exception ignored) {}

        GuiTabButton craftingStatusBtn = new GuiTabButton(this.guiLeft + 335, this.guiTop - 4, 178, GuiText.CraftingStatus.getLocal(), this.itemRender);
        craftingStatusBtn.setHideEdge(13);
        this.buttonList.add(craftingStatusBtn);
        try {
            CRAFTING_STATUS_BTN_FIELD.set(this, craftingStatusBtn);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 8. 添加编码区按钮
        this.setupPatternButtons();

        // 9. 编码区滚动条
        this.patternScrollBar = new GuiScrollbar();
        this.patternScrollBar.setLeft(180).setTop(88 + this.extraHeight).setHeight(66);
        this.patternScrollBar.setRange(0, this.container.getMaxScrollOffset(), 1);

        // 10. 反射修正 rows/perRow
        try {
            ROWS_FIELD.setInt(this, this.omniRows);
            PER_ROW_FIELD.setInt(this, 18);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 缓存 viewCell 状态,避免 drawBG / getJEIExclusionArea 每帧反射
        try {
            this.cachedHasViewCell = VIEW_CELL_FIELD.getBoolean(this);
        } catch (Exception e) {
            this.cachedHasViewCell = false;
        }

        // 11. 设置容器物品库更新回调
        this.container.setInventoryListener(list -> {
            for (IAEItemStack is : list) {
                this.repo.postUpdate(is);
            }
            this.repo.updateView();
            this.updateItemScrollRange();
        });
        this.container.setGui(this);

        // R3: 配置每页槽位数并发送初始分页请求
        if (this.isOmniRepo) {
            com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo omniRepo =
                    (com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo) this.repo;
            omniRepo.setRowSize(18);
            omniRepo.setSlotsPerPage(18 * this.omniRows);
            Enum<?> viewMode = this.getSortDisplay();
            Enum<?> sortBy = this.getSortBy();
            Enum<?> sortDir = this.getSortDir();
            omniRepo.onQueryParamsChanged("",
                    (byte) 0,
                    (byte) (sortBy != null ? sortBy.ordinal() : 0),
                    (byte) (sortDir != null ? sortDir.ordinal() : 0),
                    (byte) (viewMode != null ? viewMode.ordinal() : 0));
        }
    }

    private void setupPatternButtons() {
        int gl = this.guiLeft;
        int gt = this.guiTop;
        int fy = gt + this.extraHeight; // 固定区域基准 y(随物品库行数增加而下移)

        // 切换 Crafting/Processing 模式按钮 — 位于编码区右上角
        this.tabCraftButton = new GuiTabButton(gl + 335, fy + 74, new ItemStack(Blocks.CRAFTING_TABLE), "Crafting", this.itemRender);
        this.buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(gl + 335, fy + 74, new ItemStack(Blocks.FURNACE), "Processing", this.itemRender);
        this.buttonList.add(this.tabProcessButton);

        // Substitute / Clear 按钮 — 位于合成区左上方
        this.substitutionsEnabledBtn = new GuiImgButton(gl + 240, fy + 92, Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(gl + 240, fy + 92, Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        this.clearBtn = new GuiImgButton(gl + 80, fy + 92, Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        // 编码区清空按钮 — 位于处理模式九宫格右上角右侧,间隔1像素
        this.clearPatternBtn = new GuiImgButton(gl + 251, fy + 92, Settings.ACTIONS, ActionItems.CLOSE);
        this.clearPatternBtn.setHalfSize(true);
        this.buttonList.add(this.clearPatternBtn);

        // 编码区快捷操作按钮(位于编码区右侧,避免与合成区重叠)
        this.x3Btn = new GuiImgButton(gl + 180, fy + 157, Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(gl + 190, fy + 157, Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(gl + 200, fy + 157, Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(gl + 210, fy + 157, Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(gl + 220, fy + 157, Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(gl + 230, fy + 157, Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.buttonList.add(this.minusOneBtn);

        this.encodeBtn = new GuiImgButton(gl + 319, fy + 110, Settings.ACTIONS, ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        try {
            String action = null;
            String value = "1";

            if (this.tabCraftButton == btn || this.tabProcessButton == btn) {
                action = "CraftMode";
                value = this.tabProcessButton == btn ? "1" : "0";
            } else if (this.encodeBtn == btn) {
                action = "Encode";
                value = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) ? "2" : "1";
            } else if (this.clearBtn == btn) {
                action = "ClearCrafting";
            } else if (this.clearPatternBtn == btn) {
                action = "ClearPattern";
            } else if (this.x2Btn == btn) {
                action = "MultiplyByTwo";
            } else if (this.x3Btn == btn) {
                action = "MultiplyByThree";
            } else if (this.divTwoBtn == btn) {
                action = "DivideByTwo";
            } else if (this.divThreeBtn == btn) {
                action = "DivideByThree";
            } else if (this.plusOneBtn == btn) {
                action = "IncreaseByOne";
            } else if (this.minusOneBtn == btn) {
                action = "DecreaseByOne";
            } else if (this.substitutionsEnabledBtn == btn || this.substitutionsDisabledBtn == btn) {
                action = "Substitute";
                value = this.substitutionsEnabledBtn == btn ? "0" : "1";
            }

            if (action != null) {
                AE2Enhanced.network.sendToServer(new PacketOmniTermAction(action, value));
            }
            
            // 让 AE2 标准按钮(SortDir/ViewMode/TerminalStyle 等)也能正常工作
            super.actionPerformed(btn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // 动态拼接 omnigui.png 背景
        this.mc.getTextureManager().bindTexture(GuiResourceCache.OMNI_BG);
        // 顶部固定 18 像素
        Gui.drawModalRectWithCustomSizedTexture(offsetX, offsetY, 0, 0, 357, 18, 512, 512);
        // 物品库可重复行(纹理 y=36~54 为可重复的一行)
        for (int i = 0; i < this.omniRows; i++) {
            Gui.drawModalRectWithCustomSizedTexture(offsetX, offsetY + 18 + i * 18, 0, 36, 357, 18, 512, 512);
        }
        // 底部固定区域(纹理 y=72~251)
        int bottomY = offsetY + 18 + this.omniRows * 18;
        Gui.drawModalRectWithCustomSizedTexture(offsetX, bottomY, 0, 72, 357, 251 - 72, 512, 512);

        // 绑定并绘制 pattern_modes.png 编码区背景
        this.mc.getTextureManager().bindTexture(GuiResourceCache.PATTERN_MODES);
        int modeY = this.container.isPatternCraftMode() ? 0 : 66;
        this.drawTexturedModalRect(offsetX + 180, offsetY + 86 + this.extraHeight, 0, modeY, 124, 66);

        // 合成置顶：第一行高亮背景(crafting.png 只有 9 格,需平铺两次覆盖 18 格)
        java.util.List<CraftingStatus> activeCrafting = this.container.getClientActiveCrafting();
        if (!activeCrafting.isEmpty()) {
            this.mc.getTextureManager().bindTexture(GuiResourceCache.CRAFTING_HIGHLIGHT);
            int hlLeft = offsetX + 7; // 向左偏移 1 像素
            this.drawTexturedModalRect(hlLeft, offsetY + 18, 0, 0, 162, 18);
            this.drawTexturedModalRect(hlLeft + 162, offsetY + 18, 0, 0, 162, 18);
            // 为每个正在合成的物品单独绘制动态颜色描边
            int top = offsetY + 18;
            int bottom = top + 18;
            long time = System.currentTimeMillis();
            int count = Math.min(activeCrafting.size(), 18);
            for (int i = 0; i < count; i++) {
                CraftingStatus status = activeCrafting.get(i);
                int borderColor = this.getCraftingBorderColor(status, time);
                if ((borderColor >>> 24) == 0) {
                    continue; // 已完成(alpha=0),跳过描边
                }
                int slotLeft = hlLeft + i * 18;
                int slotRight = slotLeft + 18;
                Gui.drawRect(slotLeft, top, slotRight, top + 1, borderColor);     // 上
                Gui.drawRect(slotLeft, bottom - 1, slotRight, bottom, borderColor); // 下
                Gui.drawRect(slotLeft, top, slotLeft + 1, bottom, borderColor);     // 左
                Gui.drawRect(slotRight - 1, top, slotRight, bottom, borderColor);   // 右
            }
        }

        // 手动绘制搜索框(因为 super.drawBG 被覆盖)
        if (this.omniSearchField != null) {
            this.omniSearchField.drawTextBox();
        }

        // View Cell 侧栏背景(AE2 标准终端纹理)
        if (this.cachedHasViewCell) {
            this.mc.getTextureManager().bindTexture(GuiResourceCache.AE2_TERMINAL);
            this.drawTexturedModalRect(offsetX + 357, offsetY + this.jeiOffset, 197, 0, 46, 128);
        }

        // View Cell 变化检测与过滤同步
        if (this.cachedHasViewCell) {
            try {
                appeng.container.implementations.ContainerMEMonitorable monitorable =
                        (appeng.container.implementations.ContainerMEMonitorable)
                                GuiReflectionCache.getObject(this, GuiMEMonitorable.class, "monitorableContainer");
                boolean update = false;
                for (int i = 0; i < 5; ++i) {
                    net.minecraft.item.ItemStack current = monitorable.getCellViewSlot(i).func_75211_c();
                    if (this.omniViewCells[i] == current) continue;
                    update = true;
                    this.omniViewCells[i] = current;
                }
                if (update) {
                    this.repo.setViewCell(this.omniViewCells);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        ArrayList<Rectangle> exclusionArea = new ArrayList<>();
        // 左侧设置按钮(SortDir/ViewMode/TerminalStyle 等)
        int yOffset = this.guiTop + 8;
        int visibleButtons = 0;
        for (GuiButton btn : this.buttonList) {
            if (btn instanceof GuiImgButton && btn.enabled && btn.x < this.guiLeft) {
                visibleButtons++;
            }
        }
        if (visibleButtons > 0) {
            exclusionArea.add(new Rectangle(this.guiLeft - 18, yOffset, 20, visibleButtons * 20 + visibleButtons - 2));
        }
        // viewCell 侧栏区域(GUI 右侧外部)
        if (this.cachedHasViewCell) {
            exclusionArea.add(new Rectangle(this.guiLeft + 357, this.guiTop + this.jeiOffset, 46, 19 * 5));
        }
        return exclusionArea;
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;

        // 更新按钮可见性
        if (this.container.isPatternCraftMode()) {
            this.tabCraftButton.visible = true;
            this.tabProcessButton.visible = false;
            this.clearBtn.visible = true;
            this.clearPatternBtn.visible = true;
            this.x2Btn.visible = false;
            this.x3Btn.visible = false;
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = false;
            this.minusOneBtn.visible = false;
            if (this.container.isSubstitute()) {
                this.substitutionsEnabledBtn.visible = true;
                this.substitutionsDisabledBtn.visible = false;
            } else {
                this.substitutionsEnabledBtn.visible = false;
                this.substitutionsDisabledBtn.visible = true;
            }
        } else {
            this.tabCraftButton.visible = false;
            this.tabProcessButton.visible = true;
            this.clearBtn.visible = true;
            this.clearPatternBtn.visible = true;
            this.substitutionsEnabledBtn.visible = false;
            this.substitutionsDisabledBtn.visible = false;
            this.x2Btn.visible = true;
            this.x3Btn.visible = true;
            this.divTwoBtn.visible = true;
            this.divThreeBtn.visible = true;
            this.plusOneBtn.visible = true;
            this.minusOneBtn.visible = true;
        }

        // 绘制编码区滚动条
        if (!this.container.isPatternCraftMode() && this.patternScrollBar != null) {
            this.patternScrollBar.draw(this);
        }

        // 绘制标题文本
        this.fontRenderer.drawString(net.minecraft.client.resources.I18n.format("gui.ae2enhanced.omni_terminal.title"), 8, 6, 0x404040);
        this.fontRenderer.drawString(net.minecraft.client.resources.I18n.format("gui.ae2enhanced.omni_terminal.inventory"), 8, 155 + this.extraHeight, 0x404040);

    }

    /**
     * 根据合成进度计算动态描边颜色.
     * 进度从高(刚开始)到低(快完成)：橙色 → 绿色,同时带呼吸脉冲效果.
     * 已完成(remaining <= 0)时返回完全透明.
     */
    private int getCraftingBorderColor(CraftingStatus status, long time) {
        if (status.isDone()) {
            return 0;
        }
        float ratio = status.getRatio(); // 1.0=刚开始, 0.0=即将完成
        // 颜色插值：橙色(255,136,0) → 绿色(0,255,0)
        int r = (int) (255 * ratio);
        int g = (int) (255 - 119 * ratio);
        int b = 0;
        // 呼吸脉冲：频率随进度加快(快完成时闪烁更快)
        double pulse = Math.sin(time / (200.0 + 300.0 * ratio)) * 0.5 + 0.5;
        int alpha = (int) (120 + pulse * 80 + (1.0f - ratio) * 40);
        if (alpha > 255) alpha = 255;
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void updateScreen() {
        // 同步 active crafting 数据到 OmniItemRepo
        if (this.isOmniRepo) {
            ((com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo) this.repo)
                    .setActiveCrafting(this.container.getClientActiveCrafting());
        }

        // R3: 检测是否有待处理的刷新请求（UPDATE_NOTIFY 节流）
        if (this.isOmniRepo) {
            com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo omniRepo =
                    (com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo) this.repo;
            if (omniRepo.hasPendingRefresh()) {
                omniRepo.handleUpdateNotify();
            }
            // 检测 totalCount 变化更新滚动条
            long currentVersion = omniRepo.getRenderViewVersion();
            if (currentVersion != this.lastRenderedViewVersion) {
                this.lastRenderedViewVersion = currentVersion;
                this.updateItemScrollRange();
            }
        }

        // R3: 检测滚动变化，发送 PAGE_REQUEST
        GuiScrollbar scrollBar = this.getScrollBar();
        if (this.isOmniRepo && scrollBar != null) {
            int currentScroll = scrollBar.getCurrentScroll();
            if (currentScroll != this.lastScrollPosition) {
                this.lastScrollPosition = currentScroll;
                com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo omniRepo =
                        (com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo) this.repo;
                int itemOffset = currentScroll * omniRepo.getRowSize();
                omniRepo.onScrollChanged(itemOffset);
            }
        }

        super.updateScreen();
        // 修复物品库滚动条位置和范围(super.updateScreen 中的 setScrollBar 会重置它们)
        GuiScrollbar bar = this.getScrollBar();
        if (bar != null) {
            bar.setLeft(335).setTop(18).setHeight(this.omniRows * 18);
            int maxScroll = Math.max(0, (this.repo.size() + 17) / 18 - this.omniRows);
            bar.setRange(0, maxScroll, 1);
        }
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        // 中键点击 ghost slot → 打开数量输入子 GUI
        if (btn == 2) {
            Slot slot = this.getSlotUnderMouse(xCoord, yCoord);
            if (slot instanceof RCSlotFakeCraftingMatrix) {
                int amount = slot.getStack().isEmpty() ? 1 : slot.getStack().getCount();
                this.mc.displayGuiScreen(new GuiOmniAmount(this, 0, slot.getSlotIndex(), amount));
                return;
            } else if (slot instanceof RCSlotPatternOutputs) {
                int amount = slot.getStack().isEmpty() ? 1 : slot.getStack().getCount();
                this.mc.displayGuiScreen(new GuiOmniAmount(this, 1, slot.getSlotIndex(), amount));
                return;
            }
        }

        // 编码区滚动条点击
        if (!this.container.isPatternCraftMode() && this.patternScrollBar != null) {
            this.patternScrollBar.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
            int newOffset = this.patternScrollBar.getCurrentScroll();
            if (newOffset != this.container.getScrollOffset()) {
                this.container.setRCSlot(newOffset);
                AE2Enhanced.network.sendToServer(new PacketOmniTermAction("Scroll", String.valueOf(newOffset)));
            }
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            MEGuiTextField searchField = this.omniSearchField;
            if (searchField != null) {
                // F 键 JEI 搜索(在 TOGGLE_FOCUS 和搜索栏输入之前处理)
                if (key == Keyboard.KEY_F && !searchField.isFocused()) {
                    JEISearchKeyHandler.performSearch(this);
                    return;
                }

                if (AppEng.proxy.isActionKey(ActionKey.TOGGLE_FOCUS, key)) {
                    searchField.setFocused(!searchField.isFocused());
                    return;
                }

                if (searchField.isFocused() && key == 28) {
                    searchField.setFocused(false);
                    return;
                }
                if (character == ' ' && searchField.getText().isEmpty()) {
                    return;
                }
                boolean mouseInGui = this.isPointInRegion(0, 0, this.xSize, this.ySize, this.currentMouseX, this.currentMouseY);
                boolean wasSearchFieldFocused = searchField.isFocused();

                String searchMode = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE).name();
                boolean isAutoFocus = searchMode.contains("AUTOSEARCH");
                if (isAutoFocus && !searchField.isFocused() && mouseInGui) {
                    searchField.setFocused(true);
                }
                if (searchField.textboxKeyTyped(character, key)) {
                    this.repo.setSearchString(searchField.getText());
                } else {
                    if (!wasSearchFieldFocused) {
                        searchField.setFocused(false);
                    }
                    super.keyTyped(character, key);
                }
                return;
            }
        }
        super.keyTyped(character, key);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        this.repo.updateView();

        // R3: 排序/视图模式/搜索模式变化时，重新发送分页请求
        if (this.isOmniRepo && (settingName == Settings.SORT_BY
                || settingName == Settings.SORT_DIRECTION
                || settingName == Settings.VIEW_MODE)) {
            com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo omniRepo =
                    (com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo) this.repo;
            String searchText = this.omniSearchField != null ? this.omniSearchField.getText() : "";
            boolean isModSearch = searchText.startsWith("@");
            String query = isModSearch ? searchText.substring(1).toLowerCase() : searchText.toLowerCase();

            omniRepo.onQueryParamsChanged(query,
                    (byte) (isModSearch ? 1 : 0),
                    (byte) (this.getSortBy() != null ? this.getSortBy().ordinal() : 0),
                    (byte) (this.getSortDir() != null ? this.getSortDir().ordinal() : 0),
                    (byte) (this.getSortDisplay() != null ? this.getSortDisplay().ordinal() : 0));
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        int delta = Mouse.getEventDWheel();
        if (delta != 0) {
            int mx = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int my = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            boolean inPatternArea = !this.container.isPatternCraftMode()
                    && mx >= this.guiLeft + 180 && mx <= this.guiLeft + 316
                    && my >= this.guiTop + 88 + this.extraHeight && my <= this.guiTop + 154 + this.extraHeight;
            if (inPatternArea) {
                // 编码区滚轮独立处理,阻止 super 同时滚动物品库
                this.patternScrollBar.wheel(delta);
                int newOffset = this.patternScrollBar.getCurrentScroll();
                if (newOffset != this.container.getScrollOffset()) {
                    this.container.setRCSlot(newOffset);
                    AE2Enhanced.network.sendToServer(new PacketOmniTermAction("Scroll", String.valueOf(newOffset)));
                }
                return;
            }
        }
        super.handleMouseInput();
    }

    /**
     * R3: 接收分页结果，更新缓存和滚动条。
     */
    public void handlePageResult(PacketOmniPageResult packet) {
        if (!(this.repo instanceof com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo)) return;

        com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo omniRepo =
                (com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo) this.repo;
        omniRepo.handlePageResult(packet.getTotalCount(), packet.getOffset(), packet.getEntries());
        this.updateItemScrollRange();
    }

    /**
     * R3: 接收变化通知，触发刷新。
     */
    public void handleUpdateNotify() {
        if (!(this.repo instanceof com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo)) return;
        ((com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo) this.repo).handleUpdateNotify();
    }

    /**
     * 旧协议兼容（已废弃）。
     */
    public void handleOmniInventoryUpdate(PacketOmniInventoryUpdate.Mode mode,
                                          List<PacketOmniInventoryUpdate.Entry> entries) {
        // R3: 旧全量同步协议已废弃
    }

    /**
     * 旧协议兼容（已废弃）。
     */
    public void handleOmniSearchResult(List<PacketOmniSearchResult.Entry> entries) {
        // R3: 旧搜索协议已废弃
    }

    private void updateItemScrollRange() {
        int effectiveRows = this.omniRows;
        if (!this.container.getClientActiveCrafting().isEmpty()) {
            effectiveRows--; // 第一行被合成置顶占用
        }
        int maxScroll = Math.max(0, (this.repo.size() + 17) / 18 - effectiveRows);
        GuiScrollbar bar = this.getScrollBar();
        if (bar != null) {
            bar.setRange(0, maxScroll, 1);
        }
    }

    private Slot getSlotUnderMouse(int mouseX, int mouseY) {
        for (int i = 0; i < this.inventorySlots.inventorySlots.size(); i++) {
            Slot slot = this.inventorySlots.inventorySlots.get(i);
            if (this.isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    // ---- JEI Ghost Ingredient Handler ----

    private final java.util.Map<mezz.jei.api.gui.IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new java.util.HashMap<>();

    @Override
    public java.util.List<mezz.jei.api.gui.IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        this.mapTargetSlot.clear();

        boolean isItem = ingredient instanceof ItemStack;
        boolean isFluid = ingredient instanceof net.minecraftforge.fluids.FluidStack;
        boolean isGas = false;
        if (!isItem && !isFluid) {
            try {
                isGas = ingredient.getClass().getName().equals("mekanism.api.gas.GasStack");
            } catch (Exception ignored) {
            }
        }

        if (!isItem && !isFluid && !isGas) {
            return java.util.Collections.emptyList();
        }

        java.util.ArrayList<mezz.jei.api.gui.IGhostIngredientHandler.Target<?>> targets = new java.util.ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof RCSlotFakeCraftingMatrix) {
                if (((RCSlotFakeCraftingMatrix) slot).visible) {
                    GhostIngredientTarget target = new GhostIngredientTarget(this.getGuiLeft(), this.getGuiTop(), slot);
                    targets.add(target);
                    this.mapTargetSlot.putIfAbsent(target, slot);
                }
            } else if (slot instanceof RCSlotPatternOutputs) {
                if (((RCSlotPatternOutputs) slot).visible) {
                    GhostIngredientTarget target = new GhostIngredientTarget(this.getGuiLeft(), this.getGuiTop(), slot);
                    targets.add(target);
                    this.mapTargetSlot.putIfAbsent(target, slot);
                }
            }
        }
        return targets;
    }

    @Override
    public java.util.Map<mezz.jei.api.gui.IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return this.mapTargetSlot;
    }
}
