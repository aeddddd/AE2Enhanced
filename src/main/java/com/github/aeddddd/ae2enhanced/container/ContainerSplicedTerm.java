package com.github.aeddddd.ae2enhanced.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import appeng.api.storage.ITerminalHost;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotCraftingTerm;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.client.gui.slot.SlotHighCapacity;
import com.github.aeddddd.ae2enhanced.client.gui.slot.SlotOmniUpgrade;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 可拼接终端的容器：原样继承 {@link ContainerOmniTerm}，只增加"槽位归属 + shift-click 白名单 + 可见性掩码"。
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

    /** 稳定槽位列表（构造期按类型/访问器分类一次，不随可见性变化）。 */
    private final List<Slot> playerInventorySlots = new ArrayList<Slot>();
    private final List<Slot> viewCellSlots = new ArrayList<Slot>();
    private final List<Slot> craftingMatrixSlots = new ArrayList<Slot>();
    private final List<Slot> craftingOutputSlots = new ArrayList<Slot>();
    private final List<Slot> storageSlots = new ArrayList<Slot>();
    private final List<Slot> upgradeSlots = new ArrayList<Slot>();
    private final List<Slot> patternSlots = new ArrayList<Slot>();
    private final List<Slot> patternFeedSlots = new ArrayList<Slot>();

    public ContainerSplicedTerm(InventoryPlayer ip, ITerminalHost host) {
        super(ip, host);
        classifySlots();
        applyPanelMaskToPolicy();
    }

    /**
     * 按槽位类型/容器访问器分类（只读遍历，绝不增删/重排）。
     *
     * <p>编码区先分类并登记进"已知槽位"集合：{@code SlotPatternTerm} 继承自
     * {@code SlotCraftingTerm}，若先跑类型判断，craft 产出槽会被误当成合成输出槽
     * （那会让它落进 {@code MODULE_CRAFTING_OUTPUT} 组，语义与白名单都错）。</p>
     */
    private void classifySlots() {
        java.util.Set<Slot> patternArea = Collections.newSetFromMap(new java.util.IdentityHashMap<Slot, Boolean>());
        for (int group = 0; group < this.getPatternGroupCount(); group++) {
            for (RCSlotFakeCraftingMatrix slot : this.getPatternInputGroup(group)) {
                patternSlots.add(slot);
                patternArea.add(slot);
            }
            for (RCSlotPatternOutputs slot : this.getPatternOutputGroup(group)) {
                patternSlots.add(slot);
                patternArea.add(slot);
            }
        }
        if (this.getPatternCraftSlot() != null) {
            patternSlots.add(this.getPatternCraftSlot());
            patternArea.add(this.getPatternCraftSlot());
        }
        if (this.getPatternSlotIn() != null) {
            patternFeedSlots.add(this.getPatternSlotIn());
            patternArea.add(this.getPatternSlotIn());
        }
        if (this.getPatternSlotOut() != null) {
            patternFeedSlots.add(this.getPatternSlotOut());
            patternArea.add(this.getPatternSlotOut());
        }

        for (Slot slot : this.inventorySlots) {
            if (patternArea.contains(slot)) {
                continue;
            }
            if (slot instanceof SlotPlayerInv || slot instanceof SlotPlayerHotBar) {
                playerInventorySlots.add(slot);
            } else if (slot instanceof SlotCraftingMatrix) {
                craftingMatrixSlots.add(slot);
            } else if (slot instanceof SlotCraftingTerm) {
                craftingOutputSlots.add(slot);
            } else if (slot instanceof SlotHighCapacity) {
                storageSlots.add(slot);
            } else if (slot instanceof SlotOmniUpgrade) {
                upgradeSlots.add(slot);
            }
        }
        for (int i = 0; i < this.cellView.length; i++) {
            if (this.cellView[i] != null) {
                viewCellSlots.add(this.cellView[i]);
            }
        }
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

    /** 服务端入口：写入客户端报来的掩码（已归一化）并重算白名单。 */
    public void setPanelMask(int mask) {
        int next = SplicedTermModules.sanitize(mask);
        if (com.github.aeddddd.ae2enhanced.util.dev.SplicedTermTestHarness.enabled()) {
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

    public int getPanelMask() {
        return this.panelMask;
    }

    /**
     * 按 {@link #panelMask} 重新登记各模块组。
     *
     * <p>被关闭的模块登记为空集合，于是 {@code canShiftClickFrom/Into} 与
     * {@code shiftClickTargetsFor} 都会把它排除——服务端不再往看不见的槽位里放东西。</p>
     */
    public void applyPanelMaskToPolicy() {
        registerIfVisible(SplicedTermModules.VIEWCELL, SplicedTermSlotPolicy.MODULE_VIEWCELL, viewCellSlots);
        registerIfVisible(SplicedTermModules.CRAFTING, SplicedTermSlotPolicy.MODULE_CRAFTING_MATRIX,
                craftingMatrixSlots);
        registerIfVisible(SplicedTermModules.CRAFTING, SplicedTermSlotPolicy.MODULE_CRAFTING_OUTPUT,
                craftingOutputSlots);
        registerIfVisible(SplicedTermModules.STORAGE, SplicedTermSlotPolicy.MODULE_STORAGE, storageSlots);
        registerIfVisible(SplicedTermModules.UPGRADE, SplicedTermSlotPolicy.MODULE_UPGRADE, upgradeSlots);
        registerIfVisible(SplicedTermModules.PATTERN, SplicedTermSlotPolicy.MODULE_PATTERN, patternSlots);
        registerIfVisible(SplicedTermModules.PATTERN, SplicedTermSlotPolicy.MODULE_PATTERN_FEED, patternFeedSlots);
        // repo 与 playerinv 由 SplicedTermModules.sanitize 保证永远可见
        slotPolicy.setModuleSlots(SplicedTermSlotPolicy.MODULE_PLAYER, playerInventorySlots);
        // repo 的槽位是客户端运行时增删的 ME 展示槽，服务端没有对应对象；客户端由 RepoPanel 自行登记
    }

    private void registerIfVisible(int maskIndex, String slotGroupId, List<Slot> slots) {
        slotPolicy.setModuleSlots(slotGroupId,
                SplicedTermModules.isVisible(this.panelMask, maskIndex) ? slots : Collections.<Slot>emptyList());
    }

    // ==================== 稳定槽位列表（面板的槽位来源） ====================

    public List<Slot> playerInventorySlots() {
        return Collections.unmodifiableList(playerInventorySlots);
    }

    public List<Slot> viewCellSlots() {
        return Collections.unmodifiableList(viewCellSlots);
    }

    public List<Slot> craftingMatrixSlots() {
        return Collections.unmodifiableList(craftingMatrixSlots);
    }

    /** 合成输出槽（{@code SlotCraftingTerm}），最多一个。 */
    public List<Slot> craftingOutputSlots() {
        return Collections.unmodifiableList(craftingOutputSlots);
    }

    /** 合成输出槽，不存在时返回 {@code null}。 */
    public Slot craftingOutputSlot() {
        return craftingOutputSlots.isEmpty() ? null : craftingOutputSlots.get(0);
    }

    public List<Slot> storageSlots() {
        return Collections.unmodifiableList(storageSlots);
    }

    public List<Slot> upgradeSlots() {
        return Collections.unmodifiableList(upgradeSlots);
    }

    /** 编码区全部槽位（81 输入 + 27 输出 + 1 产出，角色 NONE：AE2 自己处理它们的点击）。 */
    public List<Slot> patternSlots() {
        return Collections.unmodifiableList(patternSlots);
    }

    /** 样板 in/out 槽（角色 BOTH：走槽位转移，与旧终端一致）。 */
    public List<Slot> patternFeedSlots() {
        return Collections.unmodifiableList(patternFeedSlots);
    }

    // ==================== shift-click ====================

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
        detectShiftClickFlood(player, index);
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }
        if (index < 0 || index >= this.inventorySlots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        if (!slotPolicy.canShiftClickFrom(slot)) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getStack();
        shiftClickCount++;
        // 单次 shift-click 的耗时看门狗：正常是微秒级；超过阈值说明点击链上出现了无界/病态工作
        // （历史事故：专用服务端被 ServerHangWatchdog 强杀，栈停在 shiftClickTargetsFor 的 addAll 上）。
        // 只记一次 + 附带来源/目标规模，便于线上定位。
        long clickStart = System.nanoTime();
        List<Slot> targets = slotPolicy.shiftClickTargetsFor(slot);
        long targetBuildNanos = System.nanoTime() - clickStart;
        ItemStack remainder;
        if (slotPolicy.isPlayerSlot(slot) && isViewCell(source)) {
            // A17 修复：view cell 是终端的"升级件"而不是货物，必须只进 view cell 槽。
            // 原来走"玩家侧先进 ME 网络"的通用路径，view cell 会被当成普通物品存进存储元件
            // （实机证据：shift-click 后源槽清空、view cell 槽仍为空、光标为空）。
            // AE2 原生 ContainerMEMonitorable.func_82846_b 正是用一条独立分支处理 view cell，
            // 这里复刻同一语义，并且只在 viewcell 面板可见（白名单非空）时接收。
            remainder = mergeInto(source.copy(), slotPolicy.slotsOf(SplicedTermSlotPolicy.MODULE_VIEWCELL));
        } else if (slotPolicy.isPlayerSlot(slot)) {
            // 与旧终端一致：玩家侧先进 ME 网络，装不下的再进其它已承接槽位
            remainder = transferStackToContainer(source.copy());
            if (!remainder.isEmpty()) {
                remainder = mergeInto(remainder, targets);
            }
        } else {
            remainder = mergeInto(source.copy(), targets);
        }
        long totalNanos = System.nanoTime() - clickStart;
        if (totalNanos > 200000000L && !warnedSlowShiftClick) {
            warnedSlowShiftClick = true;
            AE2Enhanced.LOGGER.warn("[AE2E] shift-click 处理过慢：{}ms（其中算目标 {}ms），来源槽 {}（{}），"
                            + "目标 {} 个；容器槽={}，模块规模={}",
                    totalNanos / 1000000L, targetBuildNanos / 1000000L, index,
                    slotPolicy.moduleOf(slot), targets.size(), this.inventorySlots.size(),
                    slotPolicy.describeGroupSizes());
        }
        int moved = source.getCount() - remainder.getCount();
        if (moved <= 0) {
            // **一个都没搬动时必须返回空栈**（真实 bug 修复，见验收文档 Q 节）：
            // vanilla 1.12.2 Container.slotClick 的 QUICK_MOVE 分支是
            //   ItemStack r = transferStackInSlot(player, slotId);
            //   while (!r.isEmpty() && ItemStack.areItemsEqual(slot.getStack(), r)) {
            //       r = transferStackInSlot(player, slotId);
            //   }
            // 只要"源槽内容不变 + 返回原栈"这个组合成立，它就会**无限重试**——实测把专用服务端
            // 一个 server tick 拖到 60s，被 ServerHangWatchdog 强杀（触发场景：viewcell 面板隐藏时
            // shift-click view cell、所有目标都装不下等"什么都搬不了"的点击）。
            // 返回空栈表示"这次点击没有产生任何变化"，这也正是 AE2/vanilla 其它实现（含 AE2 的
            // func_82846_b）在同等情形下的约定，因此不会改变任何实际搬运语义。
            return ItemStack.EMPTY;
        }
        ItemStack left = source.copy();
        left.shrink(moved);
        slot.putStack(left.isEmpty() ? ItemStack.EMPTY : left);
        slot.onSlotChanged();
        this.detectAndSendChanges();
        return remainder;
    }

    /** 过慢的 shift-click 只告警一次，避免刷屏。 */
    private static boolean warnedSlowShiftClick;
    /** 诊断计数器：服务端累计处理过多少次 shift-click（harness 用它看有没有点击风暴）。 */
    private static int shiftClickCount;
    /** 单 tick 内的点击计数（用于识别"点击洪流"）。 */
    private static int clicksThisTick;
    private static int lastClickTick = -1;
    private static boolean warnedClickFlood;

    /**
     * 单 tick 点击洪流检测：正常玩家一个 tick 最多点一下；同一 tick 内出现成百上千次说明
     * 有循环发包（客户端或服务端链路），而这正是"一个 tick 卡 60s"的直接成因（验收文档 K 节）。
     * 触发时打印一次调用栈，便于定位是谁在反复投递。
     */
    private static void detectShiftClickFlood(EntityPlayer player, int index) {
        int tick;
        try {
            tick = player.getServer().getTickCounter();
        } catch (Throwable t) {
            return;
        }
        if (tick != lastClickTick) {
            lastClickTick = tick;
            clicksThisTick = 0;
        }
        clicksThisTick++;
        if (clicksThisTick == 200 && !warnedClickFlood) {
            warnedClickFlood = true;
            AE2Enhanced.LOGGER.warn("[AE2E] 单个 tick 内 shift-click 已被调用 {} 次（来源槽 {}）——疑似循环发包，"
                    + "下面打印一次调用栈", clicksThisTick, index, new Throwable("shift-click flood"));
        }
    }

    /** 诊断用：服务端累计 shift-click 处理次数。 */
    public static int shiftClickCount() {
        return shiftClickCount;
    }

    /** 是否为本模组/原版 AE2 的 view cell 物品（AE2 原生 shift-click 分支用的同一判据）。 */
    private static boolean isViewCell(ItemStack stack) {
        return isSameAsCached(appeng.api.AEApi.instance().definitions().items().viewCell(), stack);
    }

    /**
     * 把物品合并进给定目标槽位；返回装不下的部分。
     *
     * <p>不使用父类的区间式 {@code mergeItemStack}：它的区间语义无法表达"跳过没有面板承接/已关闭的
     * 槽位"，而这个白名单正是唯一要保证的约束。单槽上限取
     * {@code min(slot.getSlotStackLimit(), stack.getMaxStackSize())}，与 AE2 原生 shift-click 一致。</p>
     */
    private ItemStack mergeInto(ItemStack stack, List<Slot> targets) {
        if (stack.isEmpty() || targets == null || targets.isEmpty()) {
            return stack;
        }
        // 第一遍：并入已有的同类堆（物品 + metadata + NBT 全等，比 AE2 的 isSameItem 更严格，避免 NBT 串味）
        for (Slot target : targets) {
            if (stack.isEmpty()) {
                break;
            }
            if (!slotPolicy.canShiftClickInto(target)) {
                continue;
            }
            // 只取一次目标栈：getHasStack() + getStack() 是两次访问，AE2 的包装槽位每次都会走
            // itemHandler.getStackInSlot，假槽位还可能为显示新建 ItemStack——在点击热路径上必须省。
            ItemStack existing = target.getStack();
            if (existing.isEmpty() || !target.isItemValid(stack)) {
                continue;
            }
            if (existing.getItem() != stack.getItem()
                    || !ItemStack.areItemsEqual(existing, stack)
                    || !ItemStack.areItemStackTagsEqual(existing, stack)) {
                continue;
            }
            int limit = Math.min(target.getSlotStackLimit(), stack.getMaxStackSize());
            int space = limit - existing.getCount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, stack.getCount());
            existing.grow(move);
            stack.shrink(move);
            target.onSlotChanged();
        }
        // 第二遍：放进空槽
        for (Slot target : targets) {
            if (stack.isEmpty()) {
                break;
            }
            if (!slotPolicy.canShiftClickInto(target)) {
                continue;
            }
            ItemStack existing = target.getStack();
            if (!existing.isEmpty() || !target.isItemValid(stack)) {
                continue;
            }
            int move = Math.min(Math.min(target.getSlotStackLimit(), stack.getMaxStackSize()), stack.getCount());
            if (move <= 0) {
                continue;
            }
            ItemStack placed = stack.copy();
            placed.setCount(move);
            target.putStack(placed);
            target.onSlotChanged();
            stack.shrink(move);
        }
        return stack;
    }
}
