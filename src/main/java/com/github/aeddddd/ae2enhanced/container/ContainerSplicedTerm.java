package com.github.aeddddd.ae2enhanced.container;

import appeng.api.storage.ITerminalHost;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotCraftingTerm;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * 可拼接终端的容器：原样继承 {@link ContainerOmniTerm}，增加槽位归属、可见性掩码与全交互白名单。
 *
 * <p><strong>槽位注册顺序冻结</strong>：本类不改动任何槽位的创建顺序、数量与 slotNumber，
 * 也不改 R3 分页协议；坐标（xPos/yPos）是纯客户端概念，服务端逻辑只按索引区间工作。</p>
 *
 * <h3>shift-click 语义（与旧终端对齐）</h3>
 * <p>旧终端的 shift-click 由 AE2 原生 {@code AEBaseContainer.func_82846_b} 处理，其语义是：
 * 玩家侧来源 → 先进 ME 网络，装不下的再进"非 {@code SlotFake}/非 {@code SlotCraftingMatrix}
 * 且能装下该物品的槽位"；非玩家侧来源 → 玩家侧槽位。本类用
 * {@link SplicedTermSlotPolicy#shiftClickTargetsFor(Slot)} 复刻同一语义，但把目标限制在
 * <strong>当前可见</strong>的模块槽位（关掉的面板玩家看不到，绝不能接收物品）。</p>
 *
 * <h3>可见性掩码（panelMask，@GuiSync(90)）</h3>
 * <p>客户端是布局的权威来源：本地改布局 → 发 {@code PacketOmniTermAction("PanelVisibility")} →
 * 服务端写 {@link #panelMask} 并回同步。服务端据此重算 shift-click 白名单，因此
 * <strong>关闭面板后服务端会拒绝把物品放进那些槽位</strong>（这是评估文档 §2.3 相对 MEST 的有意增强）。
 * 掩码只是位图，不含任何几何信息。</p>
 *
 * <p>本类在服务端与客户端都会实例化：服务端的掩码由动作包驱动，客户端的掩码由本地布局驱动
 * （两边最终一致）。客户端面板会在自己的 {@code layoutSlots} 里按实际可见性覆盖自己的模块组，
 * 二者不冲突——面板看到的就是玩家看到的那份真相。</p>
 */
public class ContainerSplicedTerm extends ContainerOmniTerm {

    /** 可见性位掩码的同步通道（S→C 回放；C→S 由 PanelVisibility 动作写）。 */
    public static final int GUI_SYNC_PANEL_MASK = 90;

    private final SplicedTermSlotPolicy slotPolicy = new SplicedTermSlotPolicy();

    /** 所有模块默认可见（{@link SplicedTermModules#ALL}）。 */
    @GuiSync(GUI_SYNC_PANEL_MASK)
    public int panelMask = SplicedTermModules.ALL;

    private final OmniSlotGroups slotGroups;
    private final SplicedShiftClickService shiftClickService;
    private final appeng.container.implementations.ContainerInterfaceTerminal interfaceTerminal;

    public ContainerSplicedTerm(InventoryPlayer ip, ITerminalHost host) {
        super(ip, host);
        this.interfaceTerminal = host instanceof appeng.helpers.WirelessTerminalGuiObject
                ? new appeng.container.implementations.ContainerInterfaceTerminal(ip,
                        (appeng.helpers.WirelessTerminalGuiObject) host, true)
                : null;
        this.slotGroups = OmniSlotGroups.classify(this.inventorySlots, this.patternInputSlots,
                this.outputSlotGroup, this.getPatternCraftSlot(), this.getPatternSlotIn(), this.getPatternSlotOut(),
                this.cellView);
        this.shiftClickService = new SplicedShiftClickService(this);
        applyPanelMaskToPolicy();
    }

    /** 面板/宿主用来查询槽位归属（哪个槽位由哪个模块承接）。 */
    public SplicedTermSlotPolicy slotPolicy() {
        return this.slotPolicy;
    }

    /**
     * 本终端绑定的物品栈（客户端读布局 / 服务端写布局都用它）。
     *
     * <p>与 {@code ContainerOmniTerm} 的 {@code loadStateFromItemNBT/saveStateToItemNBT} 取的是同一个
     * 对象：{@code WirelessTerminalGuiObject.getItemStack()}。非无线宿主返回空栈。</p>
     */
    public ItemStack getTerminalItemStack() {
        appeng.helpers.WirelessTerminalGuiObject wireless = this.getWirelessTerminalObject();
        if (wireless == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = wireless.getItemStack();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    // ==================== 可见性掩码 ====================

    public static boolean allowsRecipeTransfer(int mask, byte mode, boolean isCrafting) {
        if (mode < 0 || mode > 2) return false;
        if (isCrafting) {
            boolean crafting = mode == 0 || mode == 2;
            boolean pattern = mode == 0 || mode == 1;
            return (!crafting || SplicedTermModules.isVisible(mask, SplicedTermModules.CRAFTING))
                    && (!pattern || SplicedTermModules.isVisible(mask, SplicedTermModules.PATTERN));
        }
        int module = mode == 2 ? SplicedTermModules.STORAGE : SplicedTermModules.PATTERN;
        return SplicedTermModules.isVisible(mask, module);
    }

    public int getPanelMask() {
        return this.panelMask;
    }

    public static int shiftClickCount() {
        return SplicedShiftClickService.shiftClickCount();
    }

    @Override
    public void onUpdate(String field, Object oldValue, Object newValue) {
        super.onUpdate(field, oldValue, newValue);
        if ("panelMask".equals(field)) {
            applyPanelMaskToPolicy();
        }
    }

    private void registerIfVisible(int maskIndex, String slotGroupId, List<Slot> slots) {
        slotPolicy.setModuleSlots(slotGroupId,
                SplicedTermModules.isVisible(this.panelMask, maskIndex) ? slots : Collections.<Slot>emptyList());
    }

    // ==================== 稳定槽位列表（面板的槽位来源） ====================

    /**
     * 按 {@link #panelMask} 重新登记各模块组。
     *
     * <p>被关闭的模块登记为空集合，于是 {@code canShiftClickFrom/Into} 与
     * {@code shiftClickTargetsFor} 都会把它排除——服务端不再往看不见的槽位里放东西。</p>
     */
    public void applyPanelMaskToPolicy() {
        registerIfVisible(SplicedTermModules.VIEWCELL, SplicedTermSlotPolicy.MODULE_VIEWCELL, slotGroups.viewCells);
        registerIfVisible(SplicedTermModules.CRAFTING, SplicedTermSlotPolicy.MODULE_CRAFTING_MATRIX,
                slotGroups.craftingMatrix);
        registerIfVisible(SplicedTermModules.CRAFTING, SplicedTermSlotPolicy.MODULE_CRAFTING_OUTPUT,
                slotGroups.craftingOutput);
        registerIfVisible(SplicedTermModules.STORAGE, SplicedTermSlotPolicy.MODULE_STORAGE, slotGroups.storage);
        registerIfVisible(SplicedTermModules.UPGRADE, SplicedTermSlotPolicy.MODULE_UPGRADE, slotGroups.upgrades);
        registerIfVisible(SplicedTermModules.PATTERN, SplicedTermSlotPolicy.MODULE_PATTERN,
                visiblePatternSlots());
        registerIfVisible(SplicedTermModules.PATTERN, SplicedTermSlotPolicy.MODULE_PATTERN_FEED, slotGroups.patternFeed);
        // repo 与 playerinv 由 SplicedTermModules.sanitize 保证永远可见
        slotPolicy.setModuleSlots(SplicedTermSlotPolicy.MODULE_PLAYER, slotGroups.player);
        // repo 的槽位是客户端运行时增删的 ME 展示槽，服务端没有对应对象；客户端由 RepoPanel 自行登记
    }

    public List<Slot> playerInventorySlots() {
        return slotGroups.player;
    }

    public List<Slot> viewCellSlots() {
        return slotGroups.viewCells;
    }

    public List<Slot> craftingMatrixSlots() {
        return slotGroups.craftingMatrix;
    }

    /** 合成输出槽（{@code SlotCraftingTerm}），最多一个。 */
    public List<Slot> craftingOutputSlots() {
        return slotGroups.craftingOutput;
    }

    /** 合成输出槽，不存在时返回 {@code null}。 */
    public Slot craftingOutputSlot() {
        return slotGroups.craftingOutput.isEmpty() ? null : slotGroups.craftingOutput.get(0);
    }

    public List<Slot> storageSlots() {
        return slotGroups.storage;
    }

    public List<Slot> upgradeSlots() {
        return slotGroups.upgrades;
    }

    /** 编码区全部槽位（81 输入 + 27 输出 + 1 产出，角色 NONE：AE2 自己处理它们的点击）。 */
    public List<Slot> patternSlots() {
        return slotGroups.pattern;
    }

    /** 样板 in/out 槽（角色 BOTH：走槽位转移，与旧终端一致）。 */
    public List<Slot> patternFeedSlots() {
        return slotGroups.patternFeed;
    }

    // ==================== 全交互可见性 ====================

    /** 服务端入口：写入客户端报来的掩码（已归一化）并重算白名单。 */
    public void setPanelMask(int mask) {
        int next = SplicedTermModules.sanitize(mask);
        if (com.github.aeddddd.ae2enhanced.container.dev.SplicedTermTestHarness.enabled()) {
            // E2 排障：确认 PanelVisibility 包是否真的到达服务端、以及是否在"值没变"时早退
            AE2Enhanced.LOGGER.info("[AE2E][HARNESS] NOTE E2-server-mask PacketOmniTermAction 到达：mask=0x{} → sanitize=0x{}，"
                            + "当前=0x{}，早退={}",
                    Integer.toHexString(mask), Integer.toHexString(next), Integer.toHexString(this.panelMask),
                    next == this.panelMask);
        }
        if (next == this.panelMask) {
            return;
        }
        this.panelMask = next;
        applyPanelMaskToPolicy();
        this.detectAndSendChanges();
    }

    @Override
    protected boolean usesLegacySlotCoordinates() {
        return false;
    }

    @Override
    public void loadPattern(byte mode, boolean isCrafting, int gridSize,
            java.util.Map<Integer, ItemStack> inputs, java.util.Map<Integer, ItemStack> outputs) {
        if (!allowsRecipeTransfer(panelMask, mode, isCrafting)) return;
        super.loadPattern(mode, isCrafting, gridSize, inputs, outputs);
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, net.minecraft.inventory.ClickType clickType,
            EntityPlayer player) {
        if (slotId >= 0 && slotId < inventorySlots.size()
                && !slotPolicy.isHosted(inventorySlots.get(slotId))) {
            return player.inventory.getItemStack();
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    public boolean isActionAllowed(String action) {
        return SplicedActionRouter.isActionAllowed(panelMask, action);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (interfaceTerminal != null
                && SplicedTermModules.isVisible(panelMask, SplicedTermModules.INTERFACE_TERMINAL)) {
            interfaceTerminal.detectAndSendChanges();
        }
    }

    // ==================== shift-click ====================

    @Override
    public void doAction(net.minecraft.entity.player.EntityPlayerMP player,
            appeng.helpers.InventoryAction action, int slot, long id) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidPatternHelper.isAe2fcFluidDrop(getTargetStack())
                && action != appeng.helpers.InventoryAction.AUTO_CRAFT
                && action != appeng.helpers.InventoryAction.ROLL_DOWN) {
            return;
        }
        boolean hosted = slot < 0 || slot >= inventorySlots.size()
                || slotPolicy.isHosted(inventorySlots.get(slot));
        SplicedActionRouter.Target target = SplicedActionRouter.route(interfaceTerminal != null,
                SplicedTermModules.isVisible(panelMask, SplicedTermModules.INTERFACE_TERMINAL),
                id, slot, inventorySlots.size(), hosted);
        switch (target) {
            case INTERFACE:
                interfaceTerminal.doAction(player, action, slot, id);
                return;
            case OMNI:
                super.doAction(player, action, slot, id);
                return;
            default:
        }
    }

    /**
     * shift-click 转移：与旧终端对齐的自定白名单实现。
     *
     * <ul>
     *   <li>玩家背包/快捷栏里的 <strong>view cell</strong> → view cell 槽（与旧终端一致：AE2 原生
     *       {@code ContainerMEMonitorable.func_82846_b} 对 view cell 有专用分支，直接写 view cell
     *       存储，不当作货物塞进 ME 网络）；viewcell 面板不可见时拒收；</li>
     *   <li>玩家背包/快捷栏里的其它物品 → ME 网络（{@code transferStackToContainer}），装不下的再进
     *       view cell / 右侧存储 / 升级卡槽 / 样板 in 槽；</li>
     *   <li>view cell / 存储 / 升级卡槽 / 合成台矩阵 / 样板 in-out → 玩家背包；</li>
     *   <li>合成输出槽（AE2 的 CRAFT_SHIFT 处理）与编码区的 SlotFake/craft 产出槽
     *       （AE2 的 InventoryAction 处理）不走这里；被关闭的模块与没有面板承接的槽位一律拒绝。</li>
     * </ul>
     *
     * @return 未能转移的剩余物品（已全部转移时为空栈），与 vanilla 的约定一致
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return this.shiftClickService.transferStackInSlot(player, index);
    }

    ItemStack transferStackToNetwork(ItemStack stack) {
        return this.transferStackToContainer(stack);
    }
}
