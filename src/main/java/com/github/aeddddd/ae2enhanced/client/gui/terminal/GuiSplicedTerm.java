package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.ActionKey;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.GuiNull;
import appeng.client.gui.widgets.GuiImgButton;
import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.widget.ModernImgButton;
import com.github.aeddddd.ae2enhanced.client.gui.widget.ModernTabButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.InternalSlotME;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import com.github.aeddddd.ae2enhanced.client.gui.dock.DockHost;
import com.github.aeddddd.ae2enhanced.client.gui.dock.DockManager;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutTrees;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.AtomicFileDockLayoutStore;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutCodec;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutPersistence;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LegacyMigrationContext;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.TerminalNbtDockLayoutStore;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import com.github.aeddddd.ae2enhanced.client.gui.panel.CraftingPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.PatternEncodingPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.PlayerInvPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.StoragePanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.UpgradePanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.ViewCellPanel;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.container.SplicedTermModules;
import com.github.aeddddd.ae2enhanced.mixin.bridge.TerminalTooltipBridge;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.IGuiMEMonitorableAccessor;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniLayout;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniTermAction;

/**
 * 可拼接全能无线终端 —— 全屏对话框语义的 {@link AEBaseGui}，由 {@code DockManager} 托管模块窗口。
 *
 * <p>与旧 {@code GuiOmniTerm}（继承 GuiMEMonitorable、357 宽固定贴图）的根本区别：</p>
 * <ul>
 *   <li><b>全屏对话框</b>：{@code guiLeft = guiTop = 0}、{@code xSize = ySize = 0}，
 *       槽位坐标就是绝对屏幕坐标，面板可以把槽位摆到任何位置；</li>
 *   <li><b>模块即窗口</b>：物品仓库（{@link RepoPanel}）、玩家背包、view cell、合成、右侧存储、
 *       升级卡各自是独立的可拖拽/可拼接模块；只有样板编码区的槽位暂时清扫到屏幕外（阶段 3b 接管）；</li>
 *   <li><b>槽位点击仍走 AE2</b>：未被 dock 消费的点击交给 {@code super}（AEBaseGui 的
 *       SlotME/SlotFake/背包交互），只是被上层窗口遮住的槽位不可点。</li>
 * </ul>
 *
 * <h3>阶段 2b：物品仓库模块</h3>
 * <p>{@code OmniItemRepo}（R3 服务端分页）与 {@code MEGuiTextField} 搜索框都在<strong>构造器</strong>
 * 里创建：R3 的首个分页包可能早于 {@code initGui} 到达，对象必须已经存在，否则首包会被丢弃。
 * 搜索框与 4 个标准设置按钮由本类持有并摆放；{@link RepoPanel} 负责物品格几何、自持滚动条与
 * 合成状态入口（阶段 3a 起）。</p>
 *
 * <h3>阶段 3a：全模块落地</h3>
 * <p>六个模块面板全部就位；dock 的输入分发新增"内容路由"（见 {@code DockManager.mouseClicked}）：
 * 命中面板内容区时按 Z 序交给最上层叶子面板，面板消费则吞掉，<strong>不再穿透</strong>到下层窗口或
 * AE2 的槽位逻辑。滚动条因此可以完全面板自持，不再借用 {@code AEBaseGui} 的单滚动条。
 * 具体交付见 {@link SplicedTermDefaultLayout}（默认布局）、{@code SplicedTermSlotPolicy}（槽位归属与
 * shift-click 语义）。</p>
 *
 * <p>布局持久化：阶段 2a 先落到客户端配置 {@code config/ae2enhanced/spliced_terminal/layout.json}
 * （占位）。按评估文档 §2.3 的决策，布局最终要存进终端物品 NBT 并由服务端做上限校验，
 * 那是阶段 4 的工作（届时换成 NBT 存储实现即可，DockManager 侧零改动）。</p>
 *
 * <h3>为什么父类是 {@link GuiNull} 而不是 {@link AEBaseGui}（关键，勿改回去）</h3>
 * <p>本项目的编译类路径是"混合映射"：MC/Forge 是 MCP 名，而 AE2-UEL 的 jar 保留 SRG 名
 * （{@code AEBaseGui.func_146976_a}）。于是 javac 眼里 {@code GuiContainer} 的抽象方法
 * {@code drawGuiContainerBackgroundLayer} 在 AE2 层次里<strong>没有实现</strong>，javac 会要求
 * 具体子类自己实现它；可 AE2 在 dev（重映射为 MCP 名）与生产（reobf 后同为
 * {@code func_146976_a}）两个运行时里，那个方法都是 <b>final</b>，真去实现就是
 * {@code VerifyError: overrides final method}（本终端阶段 5 首次实机冒烟就是这么炸的）。</p>
 *
 * <p>绕开这个死结的唯一干净办法是让<strong>直接父类是 AE2 里的具体类</strong>：javac 对"父类已是
 * 具体类"的情形不再追问继承来的抽象方法（它假定二进制类自身完整），而运行时用的是 AE2 自己的
 * final 实现（它会回调本类的 {@code drawBG}/{@code drawFG}）。{@code GuiNull} 是 AE2 里唯一一个
 * 构造函数只收 {@link net.minecraft.inventory.Container}、且除两个空 {@code drawFG/drawBG} 外
 * 不带任何额外字段/行为的 {@code AEBaseGui} 具体子类，因此选它当基类。
 * 项目里其它 GUI 都靠 GuiMEMonitorable/GuiUpgradeable 这类具体父类绕开同一问题；本终端要避开
 * 它们带来的 ItemRepo/搜索框/按钮等全套 UI，所以用 GuiNull 这种"空壳"具体类最合适。
 * 换成 GuiMEMonitorable/GuiUpgradeable 会重新引入 AE2 自身的 repo 与控件（与 R3 分页终端冲突）。</p>
 */
public class GuiSplicedTerm extends GuiNull
        implements IOmniTerminalGui, IConfigManagerHost, ISortSource, IJEIGhostIngredients {

    /**
     * 面板/槽位绘制的 z 基线。
     *
     * <p>注意 1.12.2 的 GUI <b>是</b>开着深度测试的（{@code setupOverlayRendering} 只清深度缓冲，
     * 不禁用深度测试），所以"谁压谁"由 z 决定，见 {@link DockManager#ROOT_LAYER_Z}。基线上再叠
     * 每个窗口的"重叠带"z。</p>
     */
    private static final float DOCK_Z = 0.0F;

    /**
     * 是否正处在"按 root 顺序画槽位内容"的通道里。
     *
     * <p>见 {@link #drawSlot}：dock 自己按窗口顺序画完某扇窗的槽位后，原版的压平通道必须跳过
     * 这些槽位，否则所有窗口的物品都会被重画在所有窗口 chrome 之上（层级全错）。</p>
     */
    private boolean drawingRootSlots;
    /** 统计用：压平通道里被跳过的终端槽位次数（harness 用它证明接管生效）。 */
    private int flatPassSkipped;

    private static final int SEARCH_FIELD_HEIGHT = 11;
    /** 搜索框记忆最大长度（与旧终端一致）。 */
    private static final int SEARCH_FIELD_MAX_LENGTH = 35;

    private final ContainerSplicedTerm omniContainer;
    private final List<ModulePanel> dockPanels = new ArrayList<ModulePanel>();
    private final DockManager dock;
    private final IConfigManager configSrc;

    private boolean dockInitialized;

    // --- 物品仓库：repo 在构造器创建（防首包丢失），搜索框随面板几何重建，滚动条面板自持 ---
    private final OmniItemRepo repo;
    private final GuiScrollbar repoScrollBar = new GuiScrollbar();
    private final RepoPanel repoPanel;
    /** 编码区自持滚动条（与 repo 一样不注册到 AEBaseGui.setScrollBar）。 */
    private final GuiScrollbar patternScrollBar = new GuiScrollbar();
    private final PatternEncodingPanel patternPanel;
    private MEGuiTextField searchField;
    private int searchFieldX = Integer.MIN_VALUE;
    private int searchFieldY = Integer.MIN_VALUE;
    private int searchFieldWidth = Integer.MIN_VALUE;
    private int searchFieldHeight = Integer.MIN_VALUE;

    private GuiTabButton craftingStatusBtn;
    private GuiImgButton clearCraftBtn;
    private GuiTabButton patternCraftTabBtn;
    private GuiTabButton patternProcessTabBtn;
    private GuiImgButton substituteEnabledBtn;
    private GuiImgButton substituteDisabledBtn;
    private GuiImgButton clearPatternBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton minusOneBtn;
    private GuiImgButton sortByBtn;
    private GuiImgButton viewModeBtn;
    private GuiImgButton sortDirBtn;
    private GuiImgButton searchModeBtn;

    private final RepoModuleSwitchHost moduleSwitchHost = new RepoModuleSwitchHost();

    private boolean repoQueryInitialized;
    /** panelMask 同步：首次打开时由本地布局推给服务端，之后按差异对齐。 */
    private boolean maskSyncInitialized;
    private int lastPushedPanelMask = -1;
    /** 已推送但还没等到服务端回同步的 mask（-1 = 无在途推送）。 */
    private int pendingMaskPush = -1;
    /** 在途推送已等待的 tick 数（超时会重推，避免一次丢包就永久不一致）。 */
    private int pendingMaskTicks;
    /** 同一个在途值的重推次数（上限保护，避免服务端不认时无限刷包）。 */
    private int pendingMaskRetries;
    private boolean autoFocusSearch;
    private long lastAppliedRepoLayout = -1L;
    private long lastRenderedViewVersion = -1L;
    private int lastScrollPosition = -1;
    /** dev harness 专用：收到的服务端"存储变化"通知次数（E4 增量更新链路的观测点）。 */
    private int updateNotifyCount;
    private int currentMouseX;
    private int currentMouseY;

    // dev harness 专用：强制修饰键状态（null = 用真实键盘）。正常玩家路径永远为 null。
    private Boolean harnessAltOverride;
    private Boolean harnessControlOverride;
    private Boolean harnessShiftOverride;

    private final ItemStack[] viewCells = new ItemStack[5];

    public GuiSplicedTerm(InventoryPlayer inventoryPlayer, ITerminalHost host) {
        // 与 GuiOmniTerm:96-109 相同的构造模式：客户端自建容器并持有其引用
        super(new ContainerSplicedTerm(inventoryPlayer, host));
        this.omniContainer = (ContainerSplicedTerm) this.inventorySlots;
        this.configSrc = ((IConfigurableObject) this.omniContainer).getConfigManager();
        this.omniContainer.setGui(this);

        // R3：滚动条/repo 在构造器就绪（首包可能早于 initGui）。
        // 滚动条由 RepoPanel 自持（绘制/点击/拖拽都在面板内），因此不注册到 AEBaseGui.setScrollBar，
        // 避免 AE2 的单滚动条路径把滚动条画在所有窗口之上。
        this.repo = new OmniItemRepo(this.repoScrollBar, this);

        // 搜索框也在构造器建立（同一生命周期约定）。MEGuiTextField 在构造时固化几何，
        // 因此真正的尺寸/位置由 layoutSearchField() 在 initGui 与面板移动后重建。
        this.searchField = createSearchField(0, 0, 90, SEARCH_FIELD_HEIGHT);

        // 面板在构造器里建立：R3 首个分页包可能早于 initGui 到达，面板必须已经存在
        this.repoPanel = new RepoPanel(this.omniContainer, this.repo, this.repoScrollBar, this);
        this.repoPanel.setScrollListener(this::applyRepoScroll);
        this.dockPanels.add(this.repoPanel);
        this.dockPanels.add(new PlayerInvPanel(omniContainer));
        this.dockPanels.add(new ViewCellPanel(omniContainer));
        this.dockPanels.add(new CraftingPanel(omniContainer));
        this.dockPanels.add(new StoragePanel(omniContainer));
        this.dockPanels.add(new UpgradePanel(omniContainer));
        this.patternPanel = new PatternEncodingPanel(this.omniContainer, this.patternScrollBar, this);
        this.patternPanel.setScrollListener(this::onPatternPageChanged);
        this.dockPanels.add(this.patternPanel);
        this.dock = new DockManager(new TerminalDockHost());
        this.repoPanel.setModuleSwitchHost(this.moduleSwitchHost);
    }

    /** 布局持久化读取源：本终端绑定的物品栈的客户端副本（零同步）。 */
    private ItemStack terminalItemStack() {
        return this.omniContainer.getTerminalItemStack();
    }

    /** 布局持久化写入通道：服务端校验后写进物品 NBT。 */
    private void uploadLayout(String layoutJson) {
        if (!com.github.aeddddd.ae2enhanced.container.OmniTerminalLayoutNbt.isAcceptable(layoutJson)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Omni layout too large to upload ({} chars)",
                    layoutJson == null ? -1 : layoutJson.length());
            return;
        }
        AE2Enhanced.network.sendToServer(new PacketOmniLayout(layoutJson));
    }

    private static MEGuiTextField createSearchField(int x, int y, int width, int height) {
        MEGuiTextField field = new MEGuiTextField(Minecraft.getMinecraft().fontRenderer, x, y, width, height);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(SEARCH_FIELD_MAX_LENGTH);
        field.setTextColor(0xFFFFFF);
        field.setSelectionColor(-16744448);
        field.setVisible(true);
        return field;
    }

    /** 宿主的 dock 适配层：MC 单例只出现在这里。 */
    private final class TerminalDockHost implements DockHost {
        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        /**
         * Alt：把拼接窗口里的一片"拆"成独立窗口（{@code DockManager.shouldDetachJoinedLeaf}）。
         *
         * <p>这个覆写此前漏了 —— {@link DockHost} 的默认实现一律返回 {@code false}，
         * 于是 A9（Alt+拖标题栏拆分）在实机里永远进不了 PENDING_LEAF_DRAG 分支，
         * 拼接窗口只能整块移动。</p>
         */
        @Override
        public boolean altDown() {
            return harnessAltOverride != null ? harnessAltOverride
                    : Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        }

        /** Ctrl：拖标题条时移动整个窗口（而不是把分片拖出去）。 */
        @Override
        public boolean controlDown() {
            return harnessControlOverride != null ? harnessControlOverride
                    : Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        }

        /**
         * Shift：面板内容的 shift 语义。
         *
         * <p>{@code DockHost} 目前没有 {@code shiftDown()} 钩子（面板自己读键盘），
         * 这里先提供终端侧的统一入口，harness 用它做确定性输入；键位回落与其它修饰键一致。</p>
         */
        @SuppressWarnings("unused")
        public boolean shiftDown() {
            return harnessShiftOverride != null ? harnessShiftOverride
                    : Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        }

        /**
         * 本地可见性变化（标题栏关闭按钮等）后立刻把 panelMask 推给服务端。
         *
         * <p>漏了这一步，服务端旧 mask 会在下一次 {@code syncPanelMask()} 里把刚关掉的模块还原。</p>
         */
        @Override
        public void onModuleVisibilityChanged() {
            pushPanelMask(localPanelMask());
        }

        @Override
        public DockLayoutPersistence createPersistence(DockLayoutCodec codec) {
            // 阶段 4a：物品 NBT 权威（读手持副本、经 PacketOmniLayout 上传），
            // 客户端配置文件降级为崩溃兜底 + 调试可见的一份。
            File file = new File(Loader.instance().getConfigDir(),
                    "ae2enhanced/spliced_terminal/layout.json");
            AtomicFileDockLayoutStore backup = new AtomicFileDockLayoutStore(file.toPath(), codec);
            return new TerminalNbtDockLayoutStore(codec, GuiSplicedTerm.this::terminalItemStack,
                    GuiSplicedTerm.this::uploadLayout, backup);
        }

        @Override
        public java.nio.file.Path configDir() {
            // 预设银行与分享目录都落在这里（与布局兜底文件同目录）
            return new File(Loader.instance().getConfigDir(), "ae2enhanced/spliced_terminal").toPath();
        }

        @Override
        public DockWorkspace createDefaultWorkspace(ModuleCatalog catalog, LegacyMigrationContext geometry,
                UnaryOperator<DockWorkspace> shellFit) {
            return buildDefaultLayout(catalog, geometry, shellFit);
        }
    }

    /** 默认布局：见 {@link SplicedTermDefaultLayout}（预拼成接近旧大终端的六模块窗口）。 */
    private static DockWorkspace buildDefaultLayout(ModuleCatalog catalog, LegacyMigrationContext geometry,
            UnaryOperator<DockWorkspace> shellFit) {
        return SplicedTermDefaultLayout.create(catalog, geometry, shellFit);
    }

    // ==================== 生命周期 ====================

    @Override
    public void initGui() {
        // AEBaseGui.initGui 会按 getMeSlots() 重建尾部的 SlotME（用的就是 repo 面板的网格）
        super.initGui();
        // 全屏对话框语义：GUI 原点在屏幕左上角，槽位坐标＝绝对屏幕坐标
        this.guiLeft = 0;
        this.guiTop = 0;
        this.xSize = 0;
        this.ySize = 0;
        Keyboard.enableRepeatEvents(true);

        if (!dockInitialized) {
            dock.init(dockPanels);
            dockInitialized = true;
        } else {
            dock.updateViewport(width, height);
        }
        // V2：打开/重开终端时按 MEST 语义把可见窗口居中（偏好默认关闭，只有真实终端打开它；
        // 无头单测因此不受影响）。居中作用在 canonical 布局上，与"拖动窗口"一样是一次真实布局编辑。
        dock.setCenterOnReturn(true);
        dock.centerVisibleWorkspace();

        createButtons();
        layoutPanelButtons();
        layoutSearchField();
        this.autoFocusSearch = isAutoFocus(searchBoxMode());
        if (!repoQueryInitialized) {
            // 首次打开：搜索记忆回填 + 建立 R3 查询参数
            repoQueryInitialized = true;
            initializeRepoQuery();
        } else {
            this.searchField.setFocused(this.autoFocusSearch);
        }

        applyRepoSlots();
    }

    @Override
    public void onGuiClosed() {
        dock.finishPendingGesture();
        dock.save();
        // 与旧终端共用 GuiMEMonitorable 的 static memoryText，让搜索词在两种终端间保持
        IGuiMEMonitorableAccessor.ae2e$setMemoryText(this.searchField.getText());
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ==================== 按钮 ====================

    private void createButtons() {
        // GuiScreen.initGui 已清空 buttonList，这里重建全部原生按钮
        this.craftingStatusBtn = new ModernTabButton(4, 4, Ae2ModernArt.Icon.CRAFT_HAMMER,
                GuiText.CraftingStatus.getLocal(), this.itemRender);
        this.buttonList.add(this.craftingStatusBtn);

        // 4 个标准设置按钮（不含 TERMINAL_STYLE：可拼接终端没有"贴图高度"概念）
        this.sortByBtn = new ModernImgButton(0, 0, Settings.SORT_BY, this.configSrc.getSetting(Settings.SORT_BY));
        this.viewModeBtn = new ModernImgButton(0, 0, Settings.VIEW_MODE,
                this.configSrc.getSetting(Settings.VIEW_MODE));
        this.sortDirBtn = new ModernImgButton(0, 0, Settings.SORT_DIRECTION,
                this.configSrc.getSetting(Settings.SORT_DIRECTION));
        this.searchModeBtn = new ModernImgButton(0, 0, Settings.SEARCH_MODE, searchBoxMode());
        this.buttonList.add(this.sortByBtn);
        this.buttonList.add(this.viewModeBtn);
        this.buttonList.add(this.sortDirBtn);
        this.buttonList.add(this.searchModeBtn);

        // 合成台：清空（旧终端的 Clear 按钮，动作名 ClearCrafting）
        this.clearCraftBtn = new ModernImgButton(0, 0, Settings.ACTIONS, ActionItems.CLOSE);
        this.buttonList.add(this.clearCraftBtn);

        // 编码区：模式 tab（只有其一可见，与旧终端一致）
        this.patternCraftTabBtn = new ModernTabButton(0, 0, new ItemStack(net.minecraft.init.Blocks.CRAFTING_TABLE),
                I18n.format("gui.ae2enhanced.spliced_term.pattern.mode.crafting"), this.itemRender);
        this.patternProcessTabBtn = new ModernTabButton(0, 0, new ItemStack(net.minecraft.init.Blocks.FURNACE),
                I18n.format("gui.ae2enhanced.spliced_term.pattern.mode.processing"), this.itemRender);
        this.buttonList.add(this.patternCraftTabBtn);
        this.buttonList.add(this.patternProcessTabBtn);

        // 编码区：Substitute（两颗，按状态切换可见性）
        this.substituteEnabledBtn = new ModernImgButton(0, 0, Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substituteDisabledBtn = new ModernImgButton(0, 0, Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.clearPatternBtn = new ModernImgButton(0, 0, Settings.ACTIONS, ActionItems.CLOSE);
        this.encodeBtn = new ModernImgButton(0, 0, Settings.ACTIONS, ActionItems.ENCODE);
        this.buttonList.add(this.substituteEnabledBtn);
        this.buttonList.add(this.substituteDisabledBtn);
        this.buttonList.add(this.clearPatternBtn);
        this.buttonList.add(this.encodeBtn);

        // 编码区：倍率（半尺寸，顺序与旧终端一致：×2 ×3 ÷2 ÷3 +1 -1）
        // 19.x 没有"倍率"图标（它把数量交给数量弹窗），这 6 颗用 19.x 按钮底 + 2 字符文字标签；
        // 尺寸与动作行一致（16x16），不再走 1.12 的 halfSize，避免半尺寸里塞不进可读文字。
        this.x2Btn = new ModernImgButton(0, 0, Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO).setLabel("x2");
        this.x3Btn = new ModernImgButton(0, 0, Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE).setLabel("x3");
        this.divTwoBtn = new ModernImgButton(0, 0, Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO).setLabel("/2");
        this.divThreeBtn = new ModernImgButton(0, 0, Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE).setLabel("/3");
        this.plusOneBtn = new ModernImgButton(0, 0, Settings.ACTIONS, ActionItems.INCREASE_BY_ONE).setLabel("+1");
        this.minusOneBtn = new ModernImgButton(0, 0, Settings.ACTIONS, ActionItems.DECREASE_BY_ONE).setLabel("-1");
        GuiImgButton[] multipliers = { this.x2Btn, this.x3Btn, this.divTwoBtn, this.divThreeBtn,
                this.plusOneBtn, this.minusOneBtn };
        for (GuiImgButton multiplier : multipliers) {
            this.buttonList.add(multiplier);
        }
    }

    private void layoutPanelButtons() {
        int top = this.repoPanel.titleControlsTop();
        this.sortByBtn.x = this.repoPanel.settingControlX(0);
        this.sortByBtn.y = top;
        this.viewModeBtn.x = this.repoPanel.settingControlX(1);
        this.viewModeBtn.y = top;
        this.sortDirBtn.x = this.repoPanel.settingControlX(2);
        this.sortDirBtn.y = top;
        this.searchModeBtn.x = this.repoPanel.settingControlX(3);
        this.searchModeBtn.y = top;
        // 合成状态入口：阶段 3a 起归位到 repo 面板标题栏控件条的最左侧（tab 观感，比标题栏高 3px）
        this.craftingStatusBtn.x = this.repoPanel.craftStatusControlX();
        this.craftingStatusBtn.y = this.repoPanel.craftStatusControlTop();

        layoutCraftingButtons();
        layoutPatternButtons();
    }

    /** 合成面板的清空按钮。 */
    private void layoutCraftingButtons() {
        ModulePanel crafting = panelById(CraftingPanel.MODULE_ID);
        boolean show = crafting instanceof CraftingPanel && crafting.visible
                && isPanelAreaUnobscured(crafting);
        this.clearCraftBtn.x = show ? ((CraftingPanel) crafting).clearButtonX() : -1000;
        this.clearCraftBtn.y = show ? ((CraftingPanel) crafting).clearButtonY() : -1000;
        this.clearCraftBtn.visible = show;
    }

    /**
     * 编码区按钮：位置由面板给出；<strong>被上层窗口遮挡时直接隐藏</strong>。
     *
     * <p>GuiScreen 的按钮绘制在 dock 全部背景之上，天然会"穿透"到覆盖它的窗口之上；这里用
     * dock 的遮挡判定把被盖住的按钮藏掉，因此遮挡时既看不到也点不到（点会落到上层窗口）。</p>
     */
    private void layoutPatternButtons() {
        boolean visible = this.patternPanel.visible && isPanelAreaUnobscured(this.patternPanel);
        boolean craft = this.omniContainer.isPatternCraftMode();

        setButtonVisible(this.patternCraftTabBtn, visible && craft);
        setButtonVisible(this.patternProcessTabBtn, visible && !craft);
        this.patternCraftTabBtn.x = this.patternPanel.modeTabX();
        this.patternCraftTabBtn.y = this.patternPanel.modeTabY();
        this.patternProcessTabBtn.x = this.patternPanel.modeTabX();
        this.patternProcessTabBtn.y = this.patternPanel.modeTabY();

        boolean substituteShown = visible && craft;
        setButtonVisible(this.substituteEnabledBtn, substituteShown && this.omniContainer.isSubstitute());
        setButtonVisible(this.substituteDisabledBtn, substituteShown && !this.omniContainer.isSubstitute());
        setButtonVisible(this.clearPatternBtn, visible);
        setButtonVisible(this.encodeBtn, visible);
        this.substituteEnabledBtn.x = this.patternPanel.actionX(0);
        this.substituteEnabledBtn.y = this.patternPanel.actionY();
        this.substituteDisabledBtn.x = this.patternPanel.actionX(0);
        this.substituteDisabledBtn.y = this.patternPanel.actionY();
        this.clearPatternBtn.x = this.patternPanel.actionX(1);
        this.clearPatternBtn.y = this.patternPanel.actionY();
        this.encodeBtn.x = this.patternPanel.actionX(2);
        this.encodeBtn.y = this.patternPanel.actionY();

        GuiImgButton[] multipliers = { this.x2Btn, this.x3Btn, this.divTwoBtn, this.divThreeBtn,
                this.plusOneBtn, this.minusOneBtn };
        for (int i = 0; i < multipliers.length; i++) {
            setButtonVisible(multipliers[i], visible);
            multipliers[i].x = this.patternPanel.multiplierX(i);
            multipliers[i].y = this.patternPanel.multiplierY();
        }
    }

    private void setButtonVisible(GuiButton button, boolean shown) {
        if (button == null) {
            return;
        }
        button.visible = shown;
        if (!shown) {
            // 移出屏幕外：把 visible=false 之外再兜一层，避免任何遗留的命中测试
            button.x = -1000;
            button.y = -1000;
        }
    }

    /**
     * 面板内容区是否没有被更高窗口盖住。
     *
     * <p>面板不可见（或还没投影）时返回 false。</p>
     */
    private boolean isPanelAreaUnobscured(ModulePanel panel) {
        if (panel == null || !panel.visible) {
            return false;
        }
        return dock.isAreaUnobscured(panel, new DockRect(
                panel.contentLeft(), panel.contentTop(), panel.contentWidth(), panel.contentHeight()));
    }

    private ModulePanel panelById(String moduleId) {
        for (ModulePanel panel : dockPanels) {
            if (panel.id().equals(moduleId)) {
                return panel;
            }
        }
        return null;
    }

    private void layoutSearchField() {
        int[] bounds = this.repoPanel.searchFieldBounds();
        if (this.searchFieldX == bounds[0] && this.searchFieldY == bounds[1]
                && this.searchFieldWidth == bounds[2] && this.searchFieldHeight == bounds[3]) {
            return;
        }
        this.searchFieldX = bounds[0];
        this.searchFieldY = bounds[1];
        this.searchFieldWidth = bounds[2];
        this.searchFieldHeight = bounds[3];
        // MEGuiTextField 把几何固化在构造器里（_xPos/_yPos/_width/_height 是 final），
        // 因此面板一移动就必须重建实例；文本与聚焦状态在这里接力。
        String text = this.searchField != null ? this.searchField.getText() : "";
        boolean focused = this.searchField != null && this.searchField.isFocused();
        MEGuiTextField field = createSearchField(bounds[0], bounds[1], bounds[2], bounds[3]);
        field.setText(text);
        field.setFocused(focused);
        this.searchField = field;
    }

    @Override
    public void actionPerformed(GuiButton button) throws IOException {
        if (button == this.craftingStatusBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_STATUS));
            return;
        }
        if (handleTerminalActionButton(button)) {
            return;
        }
        if (button instanceof GuiImgButton) {
            GuiImgButton imgBtn = (GuiImgButton) button;
            if (imgBtn.getSetting() != Settings.ACTIONS) {
                boolean backwards = Mouse.isButtonDown(1);
                Enum currentValue = imgBtn.getCurrentValue();
                Enum next = Platform.rotateEnum(currentValue, backwards,
                        imgBtn.getSetting().getPossibleValues());
                if (button == this.searchModeBtn) {
                    AEConfig.instance().getConfigManager().putSetting(imgBtn.getSetting(), next);
                } else {
                    try {
                        NetworkHandler.instance()
                                .sendToServer(new PacketValueConfig(imgBtn.getSetting().name(), next.name()));
                    } catch (IOException e) {
                        AELog.debug(e);
                    }
                }
                imgBtn.set(next);
                if (next.getClass() == SearchBoxMode.class) {
                    // 搜索模式决定自动聚焦/记忆行为，需要重建按钮与搜索框状态
                    reinitalize();
                }
                return;
            }
        }
        super.actionPerformed(button);
    }

    /**
     * 终端功能按钮 → {@code PacketOmniTermAction}。
     *
     * <p>全部走服务端已有的动作名（CraftMode / Encode / ClearCrafting / ClearPattern /
     * MultiplyByTwo... / Substitute），本阶段只新增 PanelVisibility。</p>
     *
     * @return true 表示已处理（调用方应直接返回）
     */
    private boolean handleTerminalActionButton(GuiButton button) {
        String action = null;
        String value = "1";
        if (button == this.clearCraftBtn) {
            action = "ClearCrafting";
        } else if (button == this.patternCraftTabBtn || button == this.patternProcessTabBtn) {
            action = "CraftMode";
            value = button == this.patternProcessTabBtn ? "1" : "0";
        } else if (button == this.substituteEnabledBtn || button == this.substituteDisabledBtn) {
            action = "Substitute";
            value = button == this.substituteEnabledBtn ? "0" : "1";
        } else if (button == this.clearPatternBtn) {
            action = "ClearPattern";
        } else if (button == this.encodeBtn) {
            action = "Encode";
            value = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) ? "2" : "1";
        } else if (button == this.x2Btn) {
            action = "MultiplyByTwo";
        } else if (button == this.x3Btn) {
            action = "MultiplyByThree";
        } else if (button == this.divTwoBtn) {
            action = "DivideByTwo";
        } else if (button == this.divThreeBtn) {
            action = "DivideByThree";
        } else if (button == this.plusOneBtn) {
            action = "IncreaseByOne";
        } else if (button == this.minusOneBtn) {
            action = "DecreaseByOne";
        }
        if (action == null) {
            return false;
        }
        AE2Enhanced.network.sendToServer(new PacketOmniTermAction(action, value));
        return true;
    }

    /** 编码区页号变化（面板自持滚动条）→ 通知服务端，服务端的操作（multiply/encode）按页号定位。 */
    private void onPatternPageChanged(int offset) {
        AE2Enhanced.network.sendToServer(new PacketOmniTermAction("Scroll", Integer.toString(offset)));
    }

    /** 与 GuiMEMonitorable 一致：清空按钮表后整体重新初始化。 */
    private void reinitalize() {
        this.buttonList.clear();
        this.initGui();
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        if (this.sortByBtn != null) {
            this.sortByBtn.set(this.configSrc.getSetting(Settings.SORT_BY));
        }
        if (this.sortDirBtn != null) {
            this.sortDirBtn.set(this.configSrc.getSetting(Settings.SORT_DIRECTION));
        }
        if (this.viewModeBtn != null) {
            this.viewModeBtn.set(this.configSrc.getSetting(Settings.VIEW_MODE));
        }
        if (this.searchModeBtn != null) {
            this.searchModeBtn.set(searchBoxMode());
        }
        // R3：排序/视图模式变化 → 重新下发查询参数（搜索模式只影响客户端行为）
        if (settingName == Settings.SORT_BY || settingName == Settings.SORT_DIRECTION
                || settingName == Settings.VIEW_MODE) {
            pushRepoQuery();
        }
    }

    // ==================== 渲染分层 ====================

    /**
     * 背景层（AE2 规定可覆写的 {@code drawBG} 钩子）。
     *
     * <p><b>绝对不要覆写 {@code drawGuiContainerBackgroundLayer}：</b>AE2-UEL 的 {@code AEBaseGui}
     * 把它实现成 <b>final</b>（jar 里是 SRG 名 {@code func_146976_a}）。javac 只在编译类路径上看到
     * {@code GuiContainer} 的抽象版本，看不到这个 final，于是"能编译、必崩"：
     * dev 运行时 AE2 被重映射为 MCP 名 → {@code VerifyError}；生产 reobf 之后我们的方法同样变成
     * {@code func_146976_a} → 一样 {@code VerifyError}。</p>
     *
     * <p>AE2 的 final 实现会先把 {@code drawBG} 调回来（所以这里照常生效），再替"需要 AE2 代画底图"
     * 的 {@code IOptionalSlot}（{@code isRenderDisabled() == true}）与 {@code GuiCustomSlot} 补底图。
     * 本终端不受影响：{@code ContainerOmniTerm.setupPatternArea} 对 {@code RCSlotPatternOutputs}
     * 调了 {@code setRenderDisabled(false)}（与旧终端 {@code GuiOmniTerm} 完全一致），
     * 其余槽位都由各模块自绘（{@code PlayerInvPanel.drawSlotBand} 等），{@code guiSlots} 为空。</p>
     */
    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // offsetX/offsetY == guiLeft/guiTop == 0
        // 槽位内容交给 dock 按 root 顺序画（每扇窗画完自己的 chrome 就画自己的槽位），
        // 这样任意抬升/遮挡组合下物品层级都正确；压平通道由 drawSlot 静音。
        //
        // 同时必须复刻原版 item 通道的前置 GL 状态：原版在 drawScreen 里、槽位循环之前会
        // 复位颜色 + 调 RenderHelper.enableGUIStandardItemLighting()（GUI 物品的固定光照贴图）
        // + enableRescaleNormal；少了这些，物品会被画成一片黑（槽位在 drawBG 里画，早于原版那一步）。
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();
        drawingRootSlots = true;
        try {
            dock.render(mouseX, mouseY, 0.0F, DOCK_Z, this::drawPanelSlot);
        } finally {
            drawingRootSlots = false;
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
        // 搜索框属于 repo 面板内容，面板本身不画它（需要 MEGuiTextField 的光标/选区状态）。
        // 底色用 AE2 19.x 的 text_field.png（聚焦态换条带），否则搜索框是"裸字"。
        if (this.repoPanel.visible) {
            int[] bounds = this.repoPanel.searchFieldBounds();
            if (bounds != null && bounds[2] > 0 && bounds[3] > 0) {
                // 搜索框属于 repo 面板：必须画在 repo 所在根的 z 上，否则窗口背景（同根 z）会挡住它
                boolean pushed = com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch
                        .pushZLevel(dock.paintZOf(this.repoPanel));
                try {
                    com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.drawTextField(
                            bounds[0] - 1, bounds[1] - 1, bounds[2] + 2, bounds[3] + 2,
                            this.searchField.isFocused(), 0.0F);
                    this.searchField.drawTextBox();
                } finally {
                    com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.popZLevel(pushed);
                }
            }
        }
    }

    /**
     * 槽位内容绘制（dock 按 root 顺序回调；{@code zLevel} 是本窗口的槽位 z）。
     *
     * <p>直接复用 AE2-UEL 的 {@code AEBaseGui.func_146977_a}：ME 槽位的大数字（{@code StackSizeRenderer}）、
     * 断电变暗、图标槽、流体槽等分支都在里面，不能自己重写一遍。它的物品绘制自带
     * {@code +250} 的内部 z（见 {@link DockManager#ROOT_LAYER_Z}），所以这里的 {@code zLevel} 是
     * "根 z + {@code SLOT_CONTENT_Z}"，与压住本窗的那扇窗的根 z 仍差 {@code ROOT_LAYER_Z - 250 > 0}。</p>
     */
    private void drawPanelSlot(net.minecraft.inventory.Slot slot, int mouseX, int mouseY,
            float partialTicks, float zLevel) {
        if (!slot.isEnabled()) {
            return;
        }
        boolean pushed = com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.pushZLevel(zLevel);
        try {
            // 原版槽位循环在每个槽位前都保证颜色是白的；AE2 的断电暗色（0x66111111）等
            // GlStateManager 状态会留在上一个槽位上，不复位会让后面的物品/图标整体变暗。
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawSlotContents(slot);
        } finally {
            com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.popZLevel(pushed);
        }
    }

    /**
     * 单个槽位的"内容"（物品 + 数量 + ME 槽位特化）。
     *
     * <p>调用 AE2-UEL 的实现（SRG 名 {@code func_146977_a}，因为 AE2 的 AT 把
     * {@code GuiContainer.drawSlot} 放宽成了公开方法，AE2 自己也是覆写它）。</p>
     */
    private void drawSlotContents(net.minecraft.inventory.Slot slot) {
        super.func_146977_a(slot);
    }

    /**
     * 原版压平槽位通道的钩子：终端自己的槽位一律<b>跳过</b>（它们的绘制已经由 dock 按 root 顺序接管）。
     *
     * <p><b>为什么这里能覆写</b>：1.12.2 的 {@code GuiContainer.drawSlot} 本身是 private，
     * 但 AE2-UEL 通过自带的 access transformer（{@code appliedenergistics2_at.cfg}：
     * {@code public ... GuiContainer func_146977_a(Slot)V}）在运行时把它放宽为 public，AE2 的
     * {@code AEBaseGui} 正是靠这一点覆写它；本类沿用同一机制（reobf 后同样叫 {@code func_146977_a}）。
     * 编译期它仍是 private，因此不能加 {@code @Override}，也不能用 MCP 名调 {@code super}。</p>
     *
     * <p>不归终端管的槽位（例如 AE2 的不可达槽 {@code SlotInaccessible}）保持原版行为。</p>
     */
    public void drawSlot(net.minecraft.inventory.Slot slot) {   // overrides GuiContainer.drawSlot via AE2's AT
        if (!drawingRootSlots && dockOwnsSlot(slot)) {
            flatPassSkipped++;
            return;
        }
        drawSlotContents(slot);
    }

    /** 该槽位是否归终端的模块面板所有（dock 会按 root 顺序画它）。 */
    private boolean dockOwnsSlot(net.minecraft.inventory.Slot slot) {
        for (ModulePanel panel : this.dockPanels) {
            if (panel.ownsSlot(slot)) {
                return true;
            }
        }
        return false;
    }

    /** harness：压平通道里被跳过的终端槽位次数（> 0 证明 per-root 槽位接管真的生效）。 */
    public int harnessFlatPassSkippedSlots() {
        return flatPassSkipped;
    }

    /** harness：设置搜索框文本（走与手输完全相同的 pushRepoQuery 路径）。 */
    public void harnessSetSearchText(String text) {
        if (this.searchField == null) {
            return;
        }
        this.searchField.setText(text == null ? "" : text);
        pushRepoQuery();
    }

    /** harness：当前搜索框文本。 */
    public String harnessSearchText() {
        return this.searchField == null ? "" : this.searchField.getText();
    }

    /** harness：搜索模式设置名（用于核对 JEI 系模式的取词/记忆行为）。 */
    public String harnessSearchModeName() {
        Enum mode = searchBoxMode();
        return mode == null ? "null" : mode.name();
    }

    /** harness：本次打开时是否处于 JEI 系搜索模式。 */
    public boolean harnessSearchModeIsJei() {
        return isSearchModeJei(searchBoxMode());
    }

    /** harness：共用搜索记忆（GuiMEMonitorable 的 static memoryText）。 */
    public String harnessMemoryText() {
        return IGuiMEMonitorableAccessor.ae2e$getMemoryText();
    }

    /**
     * harness：各面板"承接的槽位数 / 交给 dock 绘制的槽位数" + 供电状态。
     *
     * <p>这两个数必须一致：压平通道按 {@code ownsSlot} 判断归属，per-root 通道按
     * {@code slotsToRender()} 取槽，两者不一致的槽位会彻底不渲染（修 RepoPanel 前实测：ME 槽
     * owned=140 而渲染=0 → 仓库整片空白）。</p>
     */
    public String harnessSlotRenderReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("repo.hasPower=").append(this.repo.hasPower())
                .append(" container.isPowered=").append(this.omniContainer.isPowered());
        for (ModulePanel panel : this.dockPanels) {
            int owned = 0;
            for (net.minecraft.inventory.Slot slot : this.omniContainer.inventorySlots) {
                if (panel.ownsSlot(slot)) {
                    owned++;
                }
            }
            sb.append(" | ").append(panel.id()).append(panel.visible ? "" : "(hidden)")
                    .append(" owned=").append(owned)
                    .append(" render=").append(panel.renderedSlotCount());
        }
        return sb.toString();
    }

    /** harness：某个模块所在根的绘制 z（重叠带 × ROOT_LAYER_Z）。 */
    public float harnessRootZ(String moduleId) {
        ModulePanel panel = harnessPanel(moduleId);
        return panel == null ? -1.0F : this.dock.paintZOf(panel);
    }

    /**
     * harness：按 JEI 看到的"GUI 本体矩形"口径重算一遍并输出（面板并集 + 各面板矩形）。
     *
     * <p>这里不引用 JEI 的 {@code IGuiProperties}（那会让验收 harness 依赖 JEI 才能加载），
     * 只是把同一份并集算出来，方便和截图里的 HEI 物品列表/书签位置对照。</p>
     */
    public String harnessJeiGuiRectReport() {
        java.util.List<java.awt.Rectangle> areas = getJEIExclusionArea();
        if (areas.isEmpty()) {
            return "GUI 矩形=无可见面板 排除区=0";
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (java.awt.Rectangle area : areas) {
            left = Math.min(left, (int) area.getMinX());
            top = Math.min(top, (int) area.getMinY());
            right = Math.max(right, (int) area.getMaxX());
            bottom = Math.max(bottom, (int) area.getMaxY());
        }
        int x = Math.max(0, Math.min(left, Math.max(0, this.width - 1)));
        int y = Math.max(0, Math.min(top, Math.max(0, this.height - 1)));
        int w = Math.max(1, Math.min(right, this.width) - x);
        int h = Math.max(1, Math.min(bottom, this.height) - y);
        return "GUI 矩形=[" + x + "," + y + " " + w + "x" + h + "]"
                + "（物品列表应落在 x=" + (x + w + 2) + " 右侧、书签区宽 " + Math.max(0, x - 4) + "）"
                + " 排除区=" + areas.size() + " 块 屏=" + this.width + "x" + this.height;
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        dock.renderForeground(mouseX, mouseY, 0.0F, DOCK_Z);
    }

    @Override
    protected boolean isPowered() {
        // AE2 的 drawSlot 用它给断电的 SlotME 压一层暗色
        return this.repo.hasPower();
    }

    // ==================== 心跳 ====================

    @Override
    public void updateScreen() {
        super.updateScreen();

        // 断电 / 合成置顶 心跳
        this.repo.setPower(this.omniContainer.isPowered());
        this.repo.setActiveCrafting(this.omniContainer.getClientActiveCrafting());

        // R3：UPDATE_NOTIFY 节流
        if (this.repo.hasPendingRefresh()) {
            this.repo.handleUpdateNotify();
        }

        // 编码区：模式/页号可能被容器（@GuiSync 回放 / 服务端 Scroll）改过 → 面板重新摆位
        this.patternPanel.tick();

        // 面板几何变化（拖动/缩放/隐藏）→ 重建或重定位 ME 槽位
        if (this.repoPanel.layoutVersion() != lastAppliedRepoLayout) {
            applyRepoSlots();
        }
        // 按钮与搜索框的位置只取决于 repo 面板的矩形：拖动窗口不是结构性变更，
        // 不能靠 dock.structureVersion() 触发，逐 tick 对齐最省心（代价是几个字段写入）。
        layoutPanelButtons();
        layoutSearchField();

        long viewVersion = this.repo.getRenderViewVersion();
        if (viewVersion != lastRenderedViewVersion) {
            lastRenderedViewVersion = viewVersion;
            refreshRepoScrollRange();
        }
        if (this.repoScrollBar.getCurrentScroll() != lastScrollPosition) {
            applyRepoScroll();
        }

        syncViewCells();
        this.repo.updateView();
        syncPanelMask();
    }

    // ==================== panelMask（可见性同步到服务端） ====================

    /**
     * 可见性同步。
     *
     * <p>客户端是布局的权威来源：本地开关 → 立即推给服务端；服务端回同步值若与本地不同
     * （例如被 {@code SplicedTermModules.sanitize} 归一化），则采纳服务端值把本地拉齐。
     * 收敛后双方每个 tick 都相等，不会来回抖。</p>
     */
    private void syncPanelMask() {
        int local = localPanelMask();
        int remote = SplicedTermModules.sanitize(this.omniContainer.getPanelMask());
        if (!maskSyncInitialized) {
            maskSyncInitialized = true;
            this.lastPushedPanelMask = remote;
            if (local != remote) {
                pushPanelMask(local);
            }
            return;
        }
        if (pendingMaskPush != -1 && remote == pendingMaskPush) {
            // 服务端已回同步我们的推送 → 确认完成
            pendingMaskPush = -1;
        }
        if (local == remote) {
            return;
        }
        if (pendingMaskPush != -1) {
            // 推送在途（服务端那一份还是旧值）：不能用旧值覆盖本地刚改的可见性，
            // 否则"关掉模块"会在下一 tick 被服务端旧 mask 还原（A13 的历史 bug）。
            // 但也不能无限等：40 tick 还没等到回同步就重推一次（丢包/服务端没处理都会走到这里），
            // 重推若干次仍不成功则采纳服务端值收敛，避免两端永久不一致。
            if (++pendingMaskTicks < 40) {
                return;
            }
            pendingMaskTicks = 0;
            if (++pendingMaskRetries <= 5) {
                AE2Enhanced.LOGGER.debug("[AE2E] panelMask 待在途回同步超时，重推 0x{}（第 {} 次）",
                        Integer.toHexString(pendingMaskPush), pendingMaskRetries);
                pushPanelMask(localPanelMask());
                return;
            }
            AE2Enhanced.LOGGER.warn("[AE2E] panelMask 推送 0x{} 多次无回同步，采纳服务端值 0x{} 收敛",
                    Integer.toHexString(pendingMaskPush), Integer.toHexString(remote));
            this.dock.applyVisibleModules(SplicedTermModules.visibleModuleIds(remote));
            pendingMaskPush = -1;
            pendingMaskRetries = 0;
            return;
        }
        if (remote != lastPushedPanelMask) {
            // 服务端那份不是我们刚推上去的值 → 以服务端为准（服务端会强制 repo/playerinv 可见）
            this.dock.applyVisibleModules(SplicedTermModules.visibleModuleIds(remote));
        }
        // 走到这里 = 本地与服务端不一致、且没有在途推送：无条件把本地真相推上去。
        // 这一步是自愈用的兜底：任何改可见性的入口（开关/关闭按钮/revealModule/布局载入/编辑器）
        // 只要漏了 onModuleVisibilityChanged 或通知时本地投影还没更新，就会落到这里；
        // 少了它，两端会永久停在不一致上（服务端继续按旧掩码拒绝"玩家能看见的面板"的槽位）。
        pushPanelMask(localPanelMask());
    }

    /** 本地（布局树）算出的可见模块掩码。 */
    private int localPanelMask() {
        int mask = SplicedTermModules.ALWAYS_VISIBLE;
        for (String moduleId : this.dock.visibleModuleIds()) {
            mask = SplicedTermModules.withVisibility(mask, moduleId, true);
        }
        return SplicedTermModules.sanitize(mask);
    }

    private void pushPanelMask(int mask) {
        int sanitized = SplicedTermModules.sanitize(mask);
        this.lastPushedPanelMask = sanitized;
        this.pendingMaskPush = sanitized;
        this.pendingMaskTicks = 0;
        this.pendingMaskRetries = 0;
        AE2Enhanced.network.sendToServer(
                new PacketOmniTermAction("PanelVisibility", Integer.toString(sanitized)));
    }

    /** repo 面板上的模块开关（最小显隐入口；布局编辑器留给阶段 4）。 */
    private final class RepoModuleSwitchHost implements RepoPanel.ModuleSwitchHost {
        @Override
        public int switchCount() {
            return SplicedTermModules.moduleIds().size();
        }

        @Override
        public String switchLabel(int index) {
            String title = switchTitle(index);
            if (title.isEmpty()) {
                return "?";
            }
            // W3：按模块 id 给固定缩写（首两字母会出现两个 ST：Storage Repo / Storage）。
            // 面板按钮已加宽到 22px，tooltip 仍显示全名（switchTooltip）。
            String moduleId = com.github.aeddddd.ae2enhanced.container.SplicedTermModules.moduleIdAt(index);
            if ("repo".equals(moduleId)) {
                return "RE";
            }
            if ("playerinv".equals(moduleId)) {
                return "IN";
            }
            if ("viewcell".equals(moduleId)) {
                return "VC";
            }
            if ("crafting".equals(moduleId)) {
                return "CR";
            }
            if ("storage".equals(moduleId)) {
                return "SG";
            }
            if ("upgrade".equals(moduleId)) {
                return "UP";
            }
            if ("pattern".equals(moduleId)) {
                return "PA";
            }
            int first = title.codePointAt(0);
            int cut = title.offsetByCodePoints(0, 1);
            String label = new String(Character.toChars(first));
            if (cut < title.length()) {
                label = label + new String(Character.toChars(title.codePointAt(cut)));
            }
            return label.toUpperCase(java.util.Locale.ROOT);
        }

        @Override
        public String switchTooltip(int index) {
            return switchTitle(index);
        }

        @Override
        public boolean isSwitchOn(int index) {
            String moduleId = SplicedTermModules.moduleIdAt(index);
            return moduleId != null && dock.isModuleVisible(moduleId);
        }

        @Override
        public void toggleSwitch(int index) {
            String moduleId = SplicedTermModules.moduleIdAt(index);
            if (moduleId == null) {
                return;
            }
            boolean next = !dock.isModuleVisible(moduleId);
            if (dock.setModuleVisible(moduleId, next)) {
                pushPanelMask(localPanelMask());
            }
        }

        @Override
        public boolean hasEditorButton() {
            return true;
        }

        @Override
        public String editorButtonLabel() {
            return "E";
        }

        @Override
        public String editorButtonTooltip() {
            return I18n.format("gui.ae2enhanced.spliced_editor.open");
        }

        @Override
        public void onEditorButton() {
            openLayoutEditor();
        }

        private String switchTitle(int index) {
            String moduleId = SplicedTermModules.moduleIdAt(index);
            if (moduleId == null) {
                return "";
            }
            ModulePanel panel = panelById(moduleId);
            return panel != null ? panel.title() : moduleId;
        }
    }

    // ==================== JEI ghost 拖放（编码区假槽位） ====================

    private final Map<IGhostIngredientHandler.Target<?>, Object> ghostTargetSlots =
            new HashMap<IGhostIngredientHandler.Target<?>, Object>();

    /**
     * JEI/HEI 的 ghost 拖放目标 = 当前可见的编码假槽位。
     *
     * <p>坐标用面板写好的绝对屏幕坐标（新终端 guiLeft/guiTop 恒为 0），因此目标矩形随面板
     * 拖拽自动跟随，无需重算。只上报 {@code visible} 的槽位：屏幕外的 72 个输入槽不该接东西。</p>
     */
    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        this.ghostTargetSlots.clear();
        List<IGhostIngredientHandler.Target<?>> targets =
                new ArrayList<IGhostIngredientHandler.Target<?>>();
        for (ModulePanel panel : this.dockPanels) {
            if (!(panel instanceof PatternEncodingPanel) || !panel.visible) {
                continue;
            }
            for (Slot slot : this.omniContainer.patternSlots()) {
                boolean visible = slot instanceof RCSlotFakeCraftingMatrix
                        ? ((RCSlotFakeCraftingMatrix) slot).visible
                        : slot instanceof RCSlotPatternOutputs && ((RCSlotPatternOutputs) slot).visible;
                if (!visible || slot.xPos <= -1000 || slot.yPos <= -1000) {
                    continue;
                }
                GhostIngredientTarget target = new GhostIngredientTarget(0, 0, slot);
                targets.add(target);
                this.ghostTargetSlots.putIfAbsent(target, slot);
            }
        }
        return targets;
    }

    @Override
    public Map<IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return this.ghostTargetSlots;
    }

    // ==================== R3 分页契约 ====================

    @Override
    public void handlePageResult(PacketOmniPageResult packet) {
        this.repo.handlePageResult(packet.getTotalCount(), packet.getOffset(), packet.getEntries());
        refreshRepoScrollRange();
    }

    @Override
    public void handleUpdateNotify() {
        this.updateNotifyCount++;
        this.repo.handleUpdateNotify();
    }

    /** dev harness 专用：累计收到多少次服务端"存储变化"通知（{@code PacketOmniUpdateNotify}，R3 增量更新）。 */
    public int harnessUpdateNotifyCount() {
        return this.updateNotifyCount;
    }

    private void applyRepoSlots() {
        this.lastAppliedRepoLayout = this.repoPanel.layoutVersion();
        this.repoPanel.applySlots(this.inventorySlots.inventorySlots);
        this.omniContainer.slotPolicy().setRepoSlots(this.repoPanel.hostedSlots());
        // 列/行可能刚变过：每屏物品数变了，滚动范围要跟着重算
        refreshRepoScrollRange();
    }

    /** 首次打开时建立 R3 查询参数并拉取第一页（同时回填共用的搜索记忆）。 */
    private void initializeRepoQuery() {
        // 与旧终端同款：JEI 系搜索模式打开时先取 JEI 的搜索词写进共用记忆
        // （AE2 在 GuiMEMonitorable.initGui 里做同一件事：isJEIEnabled → Integrations.jei().getSearchText()）
        if (isSearchModeJei(searchBoxMode())) {
            String jeiText = com.github.aeddddd.ae2enhanced.client.me.JeiSearchHelper.getJeiFilterText();
            if (jeiText != null && !jeiText.isEmpty()) {
                IGuiMEMonitorableAccessor.ae2e$setMemoryText(jeiText);
            }
        }
        if (isSearchModeKeep(searchBoxMode())) {
            String remembered = IGuiMEMonitorableAccessor.ae2e$getMemoryText();
            if (remembered != null && !remembered.isEmpty() && this.searchField.getText().isEmpty()) {
                this.searchField.setText(remembered);
                this.searchField.selectAll();
            }
        }
        this.searchField.setFocused(this.autoFocusSearch);
        pushRepoQuery();
    }

    /** 把搜索框文本 + 排序/视图设置下发给 R3 repo。 */
    private void pushRepoQuery() {
        String text = this.searchField.getText() == null ? "" : this.searchField.getText();
        boolean modSearch = text.startsWith("@");
        String query = text;
        if (modSearch || query.startsWith("#")) {
            query = query.substring(1).trim();
        }
        this.repo.onQueryParamsChanged(query, (byte) (modSearch ? 1 : 0),
                settingOrdinal(Settings.SORT_BY),
                settingOrdinal(Settings.SORT_DIRECTION),
                settingOrdinal(Settings.VIEW_MODE));
        // 同步父类 ItemRepo 的 searchString 字段与 @/# 语义（参数一致时不会重复发请求）
        this.repo.setSearchString(text);
        resetRepoScroll();
    }

    private void pushSearchText() {
        this.repo.setSearchString(this.searchField.getText());
        resetRepoScroll();
        refreshRepoScrollRange();
    }

    /** 查询变化后回到顶部（与 repo 内部被重置的 scrollOffset 保持一致）。 */
    private void resetRepoScroll() {
        this.repoScrollBar.setRange(0, 0, 1);
        this.lastScrollPosition = this.repoScrollBar.getCurrentScroll();
    }

    private byte settingOrdinal(Settings setting) {
        Enum value = this.configSrc.getSetting(setting);
        return (byte) (value != null ? value.ordinal() : 0);
    }

    private Enum searchBoxMode() {
        return AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
    }

    private static boolean isAutoFocus(Enum searchMode) {
        return searchMode == SearchBoxMode.AUTOSEARCH
                || searchMode == SearchBoxMode.JEI_AUTOSEARCH
                || searchMode == SearchBoxMode.AUTOSEARCH_KEEP
                || searchMode == SearchBoxMode.JEI_AUTOSEARCH_KEEP;
    }

    private static boolean isSearchModeKeep(Enum searchMode) {
        return searchMode == SearchBoxMode.AUTOSEARCH_KEEP
                || searchMode == SearchBoxMode.JEI_AUTOSEARCH_KEEP
                || searchMode == SearchBoxMode.MANUAL_SEARCH_KEEP
                || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP;
    }

    /** JEI 系搜索模式（与 JEI/HEI 搜索框同步的那几种）。 */
    private static boolean isSearchModeJei(Enum searchMode) {
        return searchMode == SearchBoxMode.JEI_AUTOSEARCH
                || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH
                || searchMode == SearchBoxMode.JEI_AUTOSEARCH_KEEP
                || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP;
    }

    // ==================== 滚动 / 视图 ====================

    /** 滚动条位置 → R3 分页偏移（同时更新去重用的 {@code lastScrollPosition}）。 */
    private void applyRepoScroll() {
        int currentScroll = this.repoScrollBar.getCurrentScroll();
        this.lastScrollPosition = currentScroll;
        this.repo.onScrollChanged(currentScroll * Math.max(1, this.repo.getRowSize()));
    }

    /** 依据服务端总数与当前列/行数刷新滚动条范围（合成置顶行会吃掉一行）。 */
    private void refreshRepoScrollRange() {
        boolean pinned = !this.omniContainer.getClientActiveCrafting().isEmpty();
        int maxScroll = RepoPanel.maxScrollRows(this.repo.size(), this.repoPanel.columns(),
                this.repoPanel.rows(), pinned);
        this.repoScrollBar.setRange(0, maxScroll, 1);
        if (this.repoScrollBar.getCurrentScroll() != lastScrollPosition) {
            // setRange 可能把当前滚动位置夹紧，需要同步一次
            applyRepoScroll();
        }
    }

    /** view cell 栏变化 → 同步给 repo 的过滤条件（每 tick 检测，与旧终端一致）。 */
    private void syncViewCells() {
        boolean update = false;
        for (int i = 0; i < viewCells.length; i++) {
            ItemStack current = this.omniContainer.getCellViewSlot(i).getStack();
            if (viewCells[i] == current) {
                continue;
            }
            update = true;
            viewCells[i] = current;
        }
        if (update) {
            this.repo.setViewCell(viewCells);
        }
    }

    // ==================== 事件转发：dock 优先，未消费则交回 AE2 ====================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;

        // 标题栏上的原生按钮优先于窗口拖动（否则按下就会被 dock 当成拖动起点）
        if (isOverNativeButton(mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (this.repoPanel.visible) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
            if (mouseButton == 1 && this.searchField.isMouseIn(mouseX, mouseY)) {
                // 右键清空搜索（与旧终端一致）
                this.searchField.setText("");
                this.repo.setSearchString("");
                refreshRepoScrollRange();
            }
        }

        // 两段式：先抬升光标下的窗口（待提交），再交给 dock 手势或原生槽位/按钮
        dock.beginPanelInteraction(mouseX, mouseY);
        boolean consumed = dock.mouseClicked(mouseX, mouseY, mouseButton);
        if (!consumed) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
        dock.commitPanelInteraction();
    }

    private boolean isOverNativeButton(int mouseX, int mouseY) {
        for (GuiButton button : this.buttonList) {
            if (!button.enabled || !button.visible) {
                continue;
            }
            if (mouseX >= button.x && mouseX < button.x + button.width
                    && mouseY >= button.y && mouseY < button.y + button.height) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!dock.mouseDragged(mouseX, mouseY)) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dock.mouseReleased(mouseX, mouseY, state);
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        this.currentMouseX = Mouse.getEventX() * width / this.mc.displayWidth;
        this.currentMouseY = height - Mouse.getEventY() * height / this.mc.displayHeight - 1;
        int delta = Mouse.getEventDWheel();
        if (delta != 0 && dock.mouseScrolled(currentMouseX, currentMouseY, delta / 120.0D)) {
            // 面板消费了滚轮（repo 列表 / 阶段 3 的编码区滚动条）：不再触发 AE2 的槽位滚轮与滚动条
            return;
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (dock.keyPressed(keyCode)) {
            return;
        }
        if (!checkHotbarKeys(keyCode)) {
            // F 键：把 JEI/HEI 悬停物品的名称填入搜索框（旧终端同款行为）
            if (keyCode == Keyboard.KEY_F && !this.searchField.isFocused()) {
                JEISearchKeyHandler.performSearch(this.searchField, this.repo, this::afterExternalSearch,
                        this.currentMouseX, this.currentMouseY);
                return;
            }
            if (AppEng.proxy.isActionKey(ActionKey.TOGGLE_FOCUS, keyCode)) {
                this.searchField.setFocused(!this.searchField.isFocused());
                return;
            }
            if (this.searchField.isFocused() && keyCode == 28) {
                this.searchField.setFocused(false);
                return;
            }
            if (typedChar == ' ' && this.searchField.getText().isEmpty()) {
                return;
            }
            boolean mouseInRepo = this.repoPanel.visible
                    && this.repoPanel.contains(this.currentMouseX, this.currentMouseY);
            boolean wasFocused = this.searchField.isFocused();
            if (this.autoFocusSearch && !wasFocused && mouseInRepo) {
                this.searchField.setFocused(true);
            }
            if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
                pushSearchText();
                return;
            }
            if (!wasFocused) {
                this.searchField.setFocused(false);
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** JEI 搜索写入搜索框后的收尾（repo 已由 JEI handler 更新，这里刷新滚动条范围）。 */
    private void afterExternalSearch() {
        refreshRepoScrollRange();
    }

    // ==================== 槽位命中：被更高窗口遮住的槽位不可点 ====================

    /**
     * AE2 的 {@code getSlot(int,int)} 用在拖拽/滚轮等自有路径上，父类默认按坐标命中所有槽位。
     * 全屏终端里必须叠加"该槽位所在窗口没有被更高窗口盖住"的判定。
     */
    @Override
    protected Slot getSlot(int mouseX, int mouseY) {
        Slot slot = super.getSlot(mouseX, mouseY);
        return isSlotReachable(slot) ? slot : null;
    }

    /**
     * vanilla 的点击路径（{@code GuiContainer.mouseClicked} 内部的私有命中测试）无法覆写，
     * 因此在最终的点击漏斗处再过滤一次。
     */
    @Override
    protected void handleMouseClick(Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (slot != null && !isSlotReachable(slot)) {
            return;
        }
        super.handleMouseClick(slot, slotId, mouseButton, clickType);
    }

    private boolean isSlotReachable(Slot slot) {
        if (slot == null) {
            return true;
        }
        for (ModulePanel panel : dockPanels) {
            if (!panel.ownsSlot(slot)) {
                continue;
            }
            // SlotME（repo 物品格）与背包/view cell 一视同仁：面板可见且没有被更高窗口盖住才能点
            return this.dock.isAreaUnobscured(panel, new DockRect(slot.xPos, slot.yPos, 16, 16));
        }
        // 不属于任何面板（已被清扫到屏幕外）：不可交互
        return false;
    }

    @Override
    protected boolean hasClickedOutside(int mouseX, int mouseY, int guiLeft, int guiTop) {
        // 全屏语义下 xSize/ySize 为 0，父类会把任何点击都判成"界外"，从而把携带中的物品丢到世界上。
        // 这里改为：只有落在所有可见窗口之外的空桌面才算界外。
        return this.dock.topPanelAt(mouseX, mouseY) == null;
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        if (renderModuleSwitchTooltip(mouseX, mouseY)) {
            return;
        }
        // 旧 GUI 由 MixinGuiMEMonitorableTooltip 注入 GuiMEMonitorable.renderHoveredToolTip；
        // 新 GUI 不继承该类，因此直接在这里调用同一个桥接（流体/气体/源质假物品 tooltip）
        Slot slot = this.getSlotUnderMouse();
        if (TerminalTooltipBridge.onRenderHoveredToolTip(this, slot, mouseX, mouseY)) {
            return;
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        // 每个可见窗口单独上报（取代旧 GUI 手算的固定矩形），repo/背包/view cell 都覆盖到
        List<Rectangle> areas = new ArrayList<Rectangle>();
        for (ModulePanel panel : this.dockPanels) {
            if (!panel.visible) {
                continue;
            }
            areas.add(new Rectangle(panel.x - 2, panel.y - 2, panel.width + 4, panel.height + 4));
        }
        return areas;
    }

    /** 模块开关的悬浮提示（名称 + 当前状态 + 点击后果）。 */
    private boolean renderModuleSwitchTooltip(int mouseX, int mouseY) {
        if (!this.repoPanel.visible) {
            return false;
        }
        if (this.repoPanel.editorButtonAt(mouseX, mouseY)) {
            this.drawHoveringText(java.util.Arrays.asList(
                    this.moduleSwitchHost.editorButtonTooltip()), mouseX, mouseY);
            return true;
        }
        int index = this.repoPanel.moduleSwitchAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        String stateKey = this.moduleSwitchHost.isSwitchOn(index)
                ? "gui.ae2enhanced.spliced_term.module_switch.on"
                : "gui.ae2enhanced.spliced_term.module_switch.off";
        this.drawHoveringText(java.util.Arrays.asList(
                this.moduleSwitchHost.switchTooltip(index), I18n.format(stateKey)), mouseX, mouseY);
        return true;
    }

    /** 打开全屏布局编辑器（阶段 4b）；编辑期间同一个 DockManager 继续被两边共用。 */
    private void openLayoutEditor() {
        this.mc.displayGuiScreen(new GuiSplicedLayoutEditor(this, this.dock));
    }

    // ==================== 内部 ====================

    @Override
    protected List<InternalSlotME> getMeSlots() {
        // ME 展示槽位由 repo 面板按当前列/行维护，AEBaseGui.initGui 直接用它重建尾部 SlotME
        return this.repoPanel.meSlots();
    }

    /** 供阶段 2b/调试使用：当前 dock 运行时。 */
    public DockManager dock() {
        return this.dock;
    }

    // ==================== ISortSource ====================

    @Override
    public Enum getSortBy() {
        return this.configSrc.getSetting(Settings.SORT_BY);
    }

    @Override
    public Enum getSortDir() {
        return this.configSrc.getSetting(Settings.SORT_DIRECTION);
    }

    @Override
    public Enum getSortDisplay() {
        return this.configSrc.getSetting(Settings.VIEW_MODE);
    }

    // ==================== dev 自检 harness 入口 ====================
    // 仅供 SplicedTermTestHarness 在配置开关（splicedTerminalTestHarness）打开时调用，
    // 用于在无鼠标键盘的环境下驱动 GUI。玩家正常操作路径完全不经过这些方法。

    /**
     * dev harness 专用：模拟在搜索框里输入文本。
     *
     * <p>走与 {@code keyTyped} 完全相同的收尾路径（{@link #pushSearchText()}），
     * 因此触发的服务端分页请求与真实输入一致。</p>
     */
    public void harnessSetSearch(String text) {
        this.searchField.setText(text == null ? "" : text);
        pushSearchText();
    }

    /**
     * dev harness 专用：模拟滚轮滚动物品仓库（正数 = 向下）。
     *
     * <p>{@link GuiScrollbar#wheel(int)} 会把增量夹成 ±1 个 pageSize，所以逐格调用，
     * 每次都走面板监听器 {@link #applyRepoScroll()}，与真实滚轮一致。</p>
     *
     * @return 滚动后的滚动条位置
     */
    public int harnessScroll(int notches) {
        for (int i = 0; i < Math.abs(notches); i++) {
            this.repoScrollBar.wheel(notches > 0 ? -1 : 1);
            applyRepoScroll();
        }
        return this.repoScrollBar.getCurrentScroll();
    }

    /** dev harness 专用：一页能显示多少格（仓库面板列数 × 行数）。 */
    public int harnessPageCapacity() {
        return this.repoPanel.columns() * this.repoPanel.rows();
    }

    /** dev harness 专用：当前列表需要多少行滚动（0 = 一页放得下，滚动不可用）。 */
    public int harnessMaxScrollRows() {
        boolean pinned = !this.omniContainer.getClientActiveCrafting().isEmpty();
        return RepoPanel.maxScrollRows(this.repo.size(), this.repoPanel.columns(), this.repoPanel.rows(), pinned);
    }

    /**
     * dev harness 专用：模拟点击排序按钮一次（走真实 {@code PacketValueConfig} 链路）。
     *
     * @return {@code "旧值 -> 新值"} 形式的描述
     */
    public String harnessCycleSort() {
        if (this.sortByBtn == null) {
            return "n/a";
        }
        String before = String.valueOf(this.sortByBtn.getCurrentValue());
        try {
            actionPerformed(this.sortByBtn);
        } catch (IOException e) {
            AELog.debug(e);
            return before + " -> (failed)";
        }
        return before + " -> " + this.sortByBtn.getCurrentValue();
    }

    /** dev harness 专用：服务端已确认的仓库总数（搜索过滤后的条数）。 */
    public int harnessRepoSize() {
        return this.repo.size();
    }

    /** dev harness 专用：终端物品 NBT 上的 {@code omni_layout}（服务端写入后经容器同步回客户端的副本）。 */
    public String harnessTerminalItemLayoutJson() {
        return com.github.aeddddd.ae2enhanced.container.OmniTerminalLayoutNbt.read(terminalItemStack());
    }

    /**
     * dev harness 专用：仓库面板里第一个匹配关键字的 ME 展示槽。
     *
     * @return {@code {槽位号, 点击中心 x, 点击中心 y}}；找不到返回 {@code null}
     */
    public int[] harnessRepoSlotPos(String keyword) {
        for (appeng.client.me.SlotME slot : this.repoPanel.slotObjects()) {
            if (matchesItemName(slot.getStack(), keyword)) {
                return new int[] {slot.slotNumber, slot.xPos + 8, slot.yPos + 8};
            }
        }
        return null;
    }

    /** dev harness 专用：仓库面板第一个非空 ME 展示槽的注册名（分页/排序换页的观测点）。 */
    public String harnessRepoFirstItem() {
        for (appeng.client.me.SlotME slot : this.repoPanel.slotObjects()) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.getItem().getRegistryName() != null) {
                return stack.getItem().getRegistryName().toString() + " x" + stack.getCount();
            }
        }
        return "空";
    }

    /** dev harness 专用：玩家快捷栏第 {@code hotbarIndex} 格（0~8）的槽位号，找不到返回 -1。 */
    public int harnessHotbarSlotNumber(int hotbarIndex) {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof appeng.container.slot.SlotPlayerHotBar && slot.getSlotIndex() == hotbarIndex) {
                return slot.slotNumber;
            }
        }
        return -1;
    }

    /**
     * dev harness 专用：任意槽位的点击中心（屏幕坐标）。
     *
     * <p>本终端是全屏对话框（{@code guiLeft = guiTop = 0}），所以槽位坐标就是屏幕坐标。</p>
     *
     * @return {@code {x, y}}；槽位号不存在返回 {@code null}
     */
    public int[] harnessSlotPos(int slotNumber) {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot.slotNumber == slotNumber) {
                return new int[] {slot.xPos + 8, slot.yPos + 8};
            }
        }
        return null;
    }

    private static boolean matchesItemName(ItemStack stack, String keyword) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return false;
        }
        return stack.getItem().getRegistryName().getPath().contains(keyword);
    }

    // ==================== dev harness 专用：合成输入注入 ====================
    // 这些方法把"合成事件"送进与真实鼠标/键盘完全相同的处理链
    // （mouseClicked / mouseClickMove / mouseReleased / keyTyped），因此能连同
    // GuiSplicedTerm 的事件转发一起验证，而不是绕过 GUI 直接调 dock。

    /** dev harness 专用：合成一次鼠标按下（走 {@code GuiSplicedTerm.mouseClicked}）。 */
    public void harnessMousePressed(int mouseX, int mouseY, int button) {
        try {
            mouseClicked(mouseX, mouseY, button);
        } catch (IOException e) {
            AELog.debug(e);
        }
    }

    /** dev harness 专用：合成一次拖拽中间帧（走 {@code GuiSplicedTerm.mouseClickMove}）。 */
    public void harnessMouseDragged(int mouseX, int mouseY, long sinceLastEventMs) {
        mouseClickMove(mouseX, mouseY, 0, sinceLastEventMs);
    }

    /** dev harness 专用：合成一次鼠标松开（走 {@code GuiSplicedTerm.mouseReleased}）。 */
    public void harnessMouseReleased(int mouseX, int mouseY, int button) {
        mouseReleased(mouseX, mouseY, button);
    }

    /** dev harness 专用：合成一次按键（走 {@code GuiSplicedTerm.keyTyped}）。 */
    public void harnessKeyTyped(char typedChar, int keyCode) {
        try {
            keyTyped(typedChar, keyCode);
        } catch (IOException e) {
            AELog.debug(e);
        }
    }

    /** dev harness 专用：合成一次 GUI 内左键点击（按下+松开，同一坐标）。 */
    public void harnessClick(int mouseX, int mouseY) {
        harnessMousePressed(mouseX, mouseY, 0);
        harnessMouseReleased(mouseX, mouseY, 0);
    }

    /**
     * dev harness 专用：对指定槽位做一次 shift-click（走 {@code handleMouseClick} 的 QUICK_MOVE 分支，
     * 与真实 shift+左键完全同路）。
     *
     * @return 是否找到了该槽位
     */
    public boolean harnessShiftClickSlot(int slotIndex) {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot.slotNumber == slotIndex) {
                handleMouseClick(slot, slot.slotNumber, 0, ClickType.QUICK_MOVE);
                return true;
            }
        }
        return false;
    }

    /** dev harness 专用：等同点击"合成状态"按钮（走 {@code actionPerformed} → PacketSwitchGuis）。 */
    public void harnessClickCraftingStatus() {
        if (this.craftingStatusBtn == null) {
            return;
        }
        try {
            actionPerformed(this.craftingStatusBtn);
        } catch (IOException e) {
            AELog.debug(e);
        }
    }

    /** dev harness 专用：等同点击"编码"按钮（走 {@code actionPerformed} → PacketOmniTermAction）。 */
    public void harnessClickEncode() {
        if (this.encodeBtn == null) {
            return;
        }
        try {
            actionPerformed(this.encodeBtn);
        } catch (IOException e) {
            AELog.debug(e);
        }
    }

    /** dev harness 专用：强制 Alt 状态（{@code null} = 回落到真实键盘）。 */
    public void harnessSetAltOverride(Boolean down) {
        this.harnessAltOverride = down;
    }

    /** dev harness 专用：强制 Ctrl 状态（{@code null} = 回落到真实键盘）。 */
    public void harnessSetControlOverride(Boolean down) {
        this.harnessControlOverride = down;
    }

    /** dev harness 专用：强制 Shift 状态（{@code null} = 回落到真实键盘）。 */
    public void harnessSetShiftOverride(Boolean down) {
        this.harnessShiftOverride = down;
    }

    /** dev harness 专用：dock 的面板列表（几何断言用；面板 x/y/width/height 与投影一致）。 */
    public List<com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel> harnessPanels() {
        return this.dock.panels();
    }

    /** dev harness 专用：按 id 找面板（找不到返回 null）。 */
    public com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel harnessPanel(String moduleId) {
        for (com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel panel : this.dock.panels()) {
            if (panel.id().equals(moduleId)) {
                return panel;
            }
        }
        return null;
    }

    /**
     * dev harness 专用：某个槽位当前是否"可达"。
     *
     * <p>与点击漏斗（{@link #handleMouseClick} → {@link #isSlotReachable}）同一判据：
     * 面板可见且没有被更高层窗口盖住。被盖住的槽位客户端会直接丢弃点击（不发包），
     * 所以验收里遇到"点了没反应"时先看这个值。</p>
     */
    public boolean harnessSlotReachable(int slotNumber) {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot.slotNumber == slotNumber) {
                return isSlotReachable(slot);
            }
        }
        return false;
    }

    /** dev harness 专用：某个模块当前是否可见（dock 策略层，等于 panelMask 的服务端视图）。 */
    public boolean harnessModuleVisible(String moduleId) {
        return this.dock.isModuleVisible(moduleId);
    }

    /** dev harness 专用：panelMask 同步的内部状态（本地/远端/上次推送/在途/等待 tick）。 */
    public String harnessMaskSyncState() {
        return "local=0x" + Integer.toHexString(localPanelMask())
                + " remote=0x" + Integer.toHexString(SplicedTermModules.sanitize(this.omniContainer.getPanelMask()))
                + " lastPushed=0x" + Integer.toHexString(this.lastPushedPanelMask)
                + " pending=0x" + Integer.toHexString(this.pendingMaskPush)
                + " pendingTicks=" + this.pendingMaskTicks
                + " retries=" + this.pendingMaskRetries
                + " init=" + this.maskSyncInitialized;
    }

    /** dev harness 专用：容器上的 panelMask（{@code @GuiSync(90)} 服务端权威，回同步到客户端）。 */
    public int harnessPanelMask() {
        return this.omniContainer.getPanelMask();
    }

    /** dev harness 专用：本机按面板可见性算出的 mask（与 panelMask 同步前的本地值）。 */
    public int harnessLocalPanelMask() {
        return localPanelMask();
    }

    /** dev harness 专用：当前打开的根窗口数量。 */
    public int harnessRootCount() {
        return this.dock.workspaceSnapshot().roots().size();
    }

    /** dev harness 专用：当前 workspace 的 root/leaf id 快照（排障 A22 布局回滚用）。 */
    public String harnessRootIds() {
        StringBuilder sb = new StringBuilder();
        for (FloatingRoot root : this.dock.workspaceSnapshot().roots()) {
            sb.append(root.rootId()).append('{');
            for (LeafNode leaf : LayoutTrees.leaves(root.content())) {
                sb.append(leaf.moduleId()).append('=').append(leaf.nodeId()).append(' ');
            }
            sb.append("} ");
        }
        return sb.toString();
    }

    /** dev harness 专用：dock 手态势诊断串（调试布局手势用，见 DockManager#diagnoseGesture）。 */
    public String harnessGestureState() {
        return this.dock.diagnoseGesture();
    }

    /** dev harness 专用：当前 root 的 bounds（多个 root 时返回最上层那个）。 */
    public String harnessTopRootBounds() {
        List<com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot> roots =
                this.dock.workspaceSnapshot().roots();
        if (roots.isEmpty()) {
            return "none";
        }
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot root = roots.get(roots.size() - 1);
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect b = root.bounds();
        return b.x() + "," + b.y() + " " + b.width() + "x" + b.height();
    }

    /** dev harness 专用：包含某个模块的 root 的 bounds（{@code "x,y wxh"}；找不到返回 {@code "none"}）。 */
    /** dev harness 专用：包含指定模块的 root 的右下握把点（{@code {x, y}}，找不到返回 null）。 */
    public int[] harnessRootCornerOf(String moduleId) {
        for (com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot root
                : this.dock.workspaceSnapshot().roots()) {
            for (com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode leaf
                    : com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutTrees.leaves(root.content())) {
                if (leaf.moduleId().equals(moduleId)) {
                    com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect b = root.bounds();
                    return new int[] { b.right() - 4, b.bottom() - 4 };
                }
            }
        }
        return null;
    }

    public String harnessRootBoundsOf(String moduleId) {
        for (com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot root
                : this.dock.workspaceSnapshot().roots()) {
            for (com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode leaf
                    : com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutTrees.leaves(root.content())) {
                if (leaf.moduleId().equals(moduleId)) {
                    com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect b = root.bounds();
                    return b.x() + "," + b.y() + " " + b.width() + "x" + b.height();
                }
            }
        }
        return "none";
    }

    /** dev harness 专用：某个模块在投影里的矩形（{@code "x,y wxh"}，不可见返回 {@code "hidden"}）。 */
    public String harnessPanelRect(String moduleId) {
        com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel panel = harnessPanel(moduleId);
        if (panel == null) {
            return "missing";
        }
        return panel.x + "," + panel.y + " " + panel.width + "x" + panel.height;
    }

    /** dev harness 专用：布局 JSON（断言持久化/结构用）。 */
    public String harnessLayoutJson() {
        return this.dock.exportLayoutJson();
    }

    /**
     * dev harness 专用：描述某个屏幕坐标上的 chrome 命中情况（布局手势排障用，只读）。
     *
     * <p>把"这一点到底被谁吃掉了"一次性说清楚：原生按钮、面板矩形、标题栏/标题栏控件/关闭按钮，
     * 以及 dock 当前的手势状态。</p>
     */
    public String harnessDescribeHit(int x, int y) {
        StringBuilder sb = new StringBuilder();
        sb.append("screen=").append(this.width).append('x').append(this.height);
        sb.append(" nativeButton=").append(isOverNativeButton(x, y));
        for (GuiButton button : this.buttonList) {
            if (!button.enabled || !button.visible) {
                continue;
            }
            if (x >= button.x && x < button.x + button.width && y >= button.y && y < button.y + button.height) {
                sb.append(" hitBtn=#").append(button.id).append('/').append(button.displayString);
            }
        }
        com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel panel = null;
        for (com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel candidate : this.dock.panels()) {
            if (!candidate.visible) {
                continue;
            }
            if (x >= candidate.x && x < candidate.x + candidate.width
                    && y >= candidate.y && y < candidate.y + candidate.height) {
                panel = candidate;
            }
        }
        if (panel == null) {
            sb.append(" panel=none");
        } else {
            sb.append(" panel=").append(panel.id()).append('[').append(panel.x).append(',').append(panel.y)
                    .append(' ').append(panel.width).append('x').append(panel.height).append(']');
            sb.append(" titleBar=").append(panel.inTitleBar(x, y));
            sb.append(" titleControls=").append(panel.inTitleBarControls(x, y));
            sb.append(" close=").append(panel.inCloseButton(x, y));
            sb.append(" pin=").append(panel.inPinButton(x, y));
            sb.append(" float=").append(panel.inFloatButton(x, y));
            sb.append(" resizeHandle=").append(panel.inResizeHandle(x, y));
            sb.append(" content=").append(panel.containsContent(x, y));
        }
        sb.append(" gesture=").append(this.dock.diagnoseGesture());
        return sb.toString();
    }

    /** dev harness 专用：标题栏里"可拖动安全区"的一个点（避开标题栏控件与原生按钮）。 */
    public int[] harnessTitleDragPoint(String moduleId) {
        com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel panel = harnessPanel(moduleId);
        if (panel == null) {
            return null;
        }
        // 标题栏左侧（标题文字附近）永远是安全区：控件都在右端，原生按钮也在右侧
        int y = panel.y + com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel.TITLE_BAR_HEIGHT / 2;
        for (int candidateX = panel.x + 4; candidateX < panel.x + panel.width; candidateX += 4) {
            if (panel.inTitleBar(candidateX, y) && !isOverNativeButton(candidateX, y)) {
                return new int[] {candidateX, y};
            }
        }
        return null;
    }
}
