package com.github.aeddddd.ae2enhanced.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;

import net.minecraft.inventory.Slot;

/**
 * "哪些槽位由哪个模块承接、能否参与 shift-click"的唯一判据。
 *
 * <p>可拼接终端把大终端拆成模块，但<strong>槽位注册顺序被冻结</strong>（slotNumber、shift-click
 * 区间、{@code PacketSetSlotAmount} 都按索引走），因此容器仍注册全部槽位。阶段 3b 起
 * <strong>每一个槽位都有归属</strong>：面板在自己的 {@code layoutSlots} 里登记当前可见的槽位，
 * 关闭的面板（{@code panelMask}）与没有面板的槽位一律不登记，于是既不参与 shift-click，
 * 也不会被摆到屏幕外——旧的 {@code hideUnhosted} 兜底清扫因此删除。</p>
 *
 * <h3>模块与转移角色</h3>
 * <p>槽位按模块 id 分组（与 {@code client/gui/panel/*Panel.MODULE_ID} 一致），每组有一个固定的
 * {@link TransferRole}。角色由模块决定、不随可见性变化；可见性只决定"这批槽位是否登记"。
 * 服务端在容器构造期按"全部可见"登记一次，客户端面板随后按实际可见性重新登记同一批槽位
 * （换集合不换角色）。</p>
 *
 * <p>本类只做纯粹的分类与隐藏动作，便于脱离 MC 渲染环境做单元测试。</p>
 */
public final class SplicedTermSlotPolicy {
    /** 屏幕外坐标（与 {@code ModulePanel.hideSlot} 一致，编译期常量会被内联，不引入客户端类依赖）。 */
    public static final int HIDDEN_SLOT_POSITION = ModulePanel.HIDDEN_SLOT_POSITION;

    // ---- 模块 id（必须与 client/gui/panel/*Panel.MODULE_ID 逐一对应，见 SplicedTermSlotPolicyTest） ----
    public static final String MODULE_PLAYER = "playerinv";
    public static final String MODULE_VIEWCELL = "viewcell";
    public static final String MODULE_CRAFTING_MATRIX = "crafting";
    public static final String MODULE_CRAFTING_OUTPUT = "crafting_output";
    public static final String MODULE_STORAGE = "storage";
    public static final String MODULE_UPGRADE = "upgrade";
    public static final String MODULE_REPO = "repo";
    public static final String MODULE_PATTERN = "pattern";
    /** 样板 in/out 槽（{@code SlotRestrictedInput}）：不走 AE2 的 SlotFake 通道，参与槽位转移。 */
    public static final String MODULE_PATTERN_FEED = "pattern_feed";

    /**
     * 槽位在 shift-click 转移中的角色。
     *
     * <p>取值对齐 AE2 原生 {@code AEBaseContainer.func_82846_b}（旧终端的 shift-click 语义）：
     * 目标集合是"非 {@code SlotFake}/非 {@code SlotCraftingMatrix} 且能装下该物品的槽位"，
     * 反方向则是"玩家侧槽位"。</p>
     */
    public enum TransferRole {
        /** 既可作为来源也可作为目标（玩家背包、view cell、右侧存储、升级卡槽）。 */
        BOTH,
        /** 只能作为来源（合成台矩阵：可以 shift-click 收回，但不会被 shift-click 填充）。 */
        SOURCE_ONLY,
        /** 完全不参与槽位转移（合成输出槽由 AE2 的 CRAFT_SHIFT 动作处理；repo 是 ME 展示槽）。 */
        NONE
    }

    /**
     * 单个模块组登记的槽位上限。
     *
     * <p>终端面板的正常规模是几十个槽位（整张槽位表 300 上下），512 这个上限在正常使用中**永远不会**触发；
     * 它的意义是兜住"有人把一张巨大的列表当槽位表塞进来"这种病态输入——历史事故：
     * 专用服务端在 shift-click 链上被 ServerHangWatchdog 强杀（`ArrayList.addAll` 长时间不返回，
     * 见验收文档 K 节），而 {@link #setModuleSlots} 的"按身份去重"是 O(n²)。</p>
     */
    private static final int MAX_GROUP_SLOTS = 512;
    /** 单次 shift-click 的目标槽位总量上限（同上：正常规模远小于它）。 */
    private static final int MAX_TARGET_SLOTS = 512;
    /** 病态输入只告警一次，避免刷屏。 */
    private static boolean warnedTooManySlots;

    private static void warnTooMany(String moduleId, int requested, int kept) {
        if (warnedTooManySlots) {
            return;
        }
        warnedTooManySlots = true;
        org.apache.logging.log4j.LogManager.getLogger("ae2enhanced.SplicedTermSlotPolicy")
                .warn("[AE2E] 模块 {} 登记的槽位数异常（请求 {}，只保留 {}）——已按上限截断，避免点击链上出现无界遍历",
                        moduleId, requested, kept);
    }

    private static final class Group {
        private final String moduleId;
        private final TransferRole role;
        private final List<Slot> slots;
        /** 对外的只读视图（只建一次：点击热路径里每次调用都新建包装器会白白制造垃圾）。 */
        private final List<Slot> readOnlyView;

        private Group(String moduleId, TransferRole role, List<Slot> slots) {
            this.moduleId = moduleId;
            this.role = role;
            this.slots = slots;
            this.readOnlyView = Collections.unmodifiableList(slots);
        }
    }

    /** 模块 id → 槽位组；LinkedHashMap 保序，shift-click 目标顺序 = 模块登记顺序。 */
    private final Map<String, Group> groups = new LinkedHashMap<String, Group>();
    /** 槽位 → 模块 id 反查（身份比较，同坐标的不同槽位对象互不影响）。 */
    private final Map<Slot, String> moduleOfSlot = new IdentityHashMap<Slot, String>();

    public SplicedTermSlotPolicy() {
    }

    /**
     * 兼容构造：等价于登记玩家背包与 view cell 两个模块（阶段 2a 的用法）。
     */
    public SplicedTermSlotPolicy(Collection<Slot> playerSlots, Collection<Slot> viewCellSlots) {
        setModuleSlots(MODULE_PLAYER, playerSlots);
        setModuleSlots(MODULE_VIEWCELL, viewCellSlots);
    }

    /** 模块的固定转移角色。未内置的模块按 {@link TransferRole#NONE} 处理（安全默认）。 */
    public static TransferRole roleFor(String moduleId) {
        if (MODULE_PLAYER.equals(moduleId) || MODULE_VIEWCELL.equals(moduleId)
                || MODULE_STORAGE.equals(moduleId) || MODULE_UPGRADE.equals(moduleId)
                || MODULE_PATTERN_FEED.equals(moduleId)) {
            return TransferRole.BOTH;
        }
        if (MODULE_CRAFTING_MATRIX.equals(moduleId)) {
            return TransferRole.SOURCE_ONLY;
        }
        // pattern（81 输入 + 27 输出 + craft 产出槽）：AE2 客户端对 SlotFake / SlotPatternTerm
        // 有专门分支（AEBaseGui.func_184098_a），从不把它们的点击送进 vanilla 槽位转移，
        // 因此这里声明 NONE 与"不可达"等价，同时挡住 craft 产出槽那条意外的传递路径。
        return TransferRole.NONE;
    }

    /**
     * 重新登记某个模块当前承接的槽位（传空集合 = 该模块当前不可见/未承接）。
     *
     * <p>重复调用同一模块只替换集合，不改变模块在登记顺序中的位置。</p>
     */
    public void setModuleSlots(String moduleId, Collection<? extends Slot> slots) {
        Group previous = groups.get(moduleId);
        if (previous != null) {
            for (Slot slot : previous.slots) {
                moduleOfSlot.remove(slot);
            }
        }
        List<Slot> copy = new ArrayList<Slot>();
        if (slots != null) {
            for (Slot slot : slots) {
                if (slot == null) {
                    continue;
                }
                if (copy.size() >= MAX_GROUP_SLOTS) {
                    warnTooMany(moduleId, slots.size(), copy.size());
                    break;
                }
                if (!copy.contains(slot)) {
                    copy.add(slot);
                }
            }
        }
        groups.put(moduleId, new Group(moduleId, roleFor(moduleId), copy));
        for (Slot slot : copy) {
            moduleOfSlot.put(slot, moduleId);
        }
    }

    /** 兼容入口：repo 的 ME 展示槽（角色 NONE，AE2 自己处理它的 shift-click）。 */
    public void setRepoSlots(Collection<? extends Slot> slots) {
        setModuleSlots(MODULE_REPO, slots);
    }

    // ==================== 查询 ====================

    /** 指定模块当前登记的槽位（按登记顺序）。 */
    public List<Slot> slotsOf(String moduleId) {
        Group group = groups.get(moduleId);
        return group == null ? Collections.<Slot>emptyList() : group.readOnlyView;
    }

    public List<Slot> playerSlots() {
        return slotsOf(MODULE_PLAYER);
    }

    public List<Slot> viewCellSlots() {
        return slotsOf(MODULE_VIEWCELL);
    }

    public List<Slot> craftingMatrixSlots() {
        return slotsOf(MODULE_CRAFTING_MATRIX);
    }

    public List<Slot> storageSlots() {
        return slotsOf(MODULE_STORAGE);
    }

    public List<Slot> upgradeSlots() {
        return slotsOf(MODULE_UPGRADE);
    }

    public List<Slot> patternSlots() {
        return slotsOf(MODULE_PATTERN);
    }

    public List<Slot> patternFeedSlots() {
        return slotsOf(MODULE_PATTERN_FEED);
    }

    /** 槽位所属的模块 id，未登记返回 {@code null}。 */
    public String moduleOf(Slot slot) {
        return moduleOfSlot.get(slot);
    }

    public TransferRole roleOf(Slot slot) {
        String moduleId = moduleOfSlot.get(slot);
        return moduleId == null ? TransferRole.NONE : roleFor(moduleId);
    }

    public boolean isPlayerSlot(Slot slot) {
        return MODULE_PLAYER.equals(moduleOfSlot.get(slot));
    }

    public boolean isViewCellSlot(Slot slot) {
        return MODULE_VIEWCELL.equals(moduleOfSlot.get(slot));
    }

    public boolean isCraftingMatrixSlot(Slot slot) {
        return MODULE_CRAFTING_MATRIX.equals(moduleOfSlot.get(slot));
    }

    public boolean isStorageSlot(Slot slot) {
        return MODULE_STORAGE.equals(moduleOfSlot.get(slot));
    }

    public boolean isUpgradeSlot(Slot slot) {
        return MODULE_UPGRADE.equals(moduleOfSlot.get(slot));
    }

    public boolean isRepoSlot(Slot slot) {
        return MODULE_REPO.equals(moduleOfSlot.get(slot));
    }

    /** 该槽位当前是否有面板承接（＝允许参与正常交互、不被清扫）。 */
    public boolean isHosted(Slot slot) {
        return moduleOfSlot.containsKey(slot);
    }

    /**
     * 该槽位能否作为 shift-click 的<strong>来源</strong>。
     *
     * <p>未登记（没有面板承接）与 {@link TransferRole#NONE} 的槽位一律不可。</p>
     */
    public boolean canShiftClickFrom(Slot slot) {
        return roleOf(slot) != TransferRole.NONE;
    }

    /**
     * 该槽位能否作为 shift-click 的<strong>目标</strong>。
     *
     * <p>只有 {@link TransferRole#BOTH} 可以：合成台矩阵（{@code SOURCE_ONLY}）与合成输出槽、
     * repo（{@code NONE}）都不接收 shift-click 填充——与旧终端 AE2 原生语义一致。</p>
     */
    public boolean canShiftClickInto(Slot slot) {
        return roleOf(slot) == TransferRole.BOTH;
    }

    /**
     * 某个来源槽位的 shift-click 目标列表。
     *
     * <p>规则对齐 AE2 原生：玩家侧来源 → 除玩家外的可接收模块（view cell / 存储 / 升级卡，按模块
     * 登记顺序）；非玩家侧来源 → 玩家背包/快捷栏。角色为 {@link TransferRole#NONE}（或未登记）的来源
     * 根本不该参与转移，直接返回空表。合成台矩阵按角色自然不会出现在目标里。</p>
     */
    public List<Slot> shiftClickTargetsFor(Slot source) {
        if (roleOf(source) == TransferRole.NONE) {
            return Collections.emptyList();
        }
        boolean fromPlayer = isPlayerSlot(source);
        // 组快照 + 索引遍历：完全不碰 LinkedHashMap 的迭代器/内部表，也不走 ArrayList.toArray()
        // （事故排查中发现这条链上出现过"单次调用里长时间不返回"的现象，见验收文档 K 节；
        //  快照只有个位数元素，索引遍历上限 512，代价恒定且可预期）。
        Group[] snapshot = groups.values().toArray(new Group[0]);
        List<Slot> targets = new ArrayList<Slot>();
        int groupLimit = Math.min(snapshot.length, 64);
        for (int gi = 0; gi < groupLimit && targets.size() < MAX_TARGET_SLOTS; gi++) {
            Group group = snapshot[gi];
            if (group.role != TransferRole.BOTH) {
                continue;
            }
            if (MODULE_PLAYER.equals(group.moduleId) == fromPlayer) {
                continue; // 同侧不作为目标
            }
            List<Slot> slots = group.slots;
            int count = Math.min(slots.size(), MAX_TARGET_SLOTS - targets.size());
            for (int i = 0; i < count; i++) {
                targets.add(slots.get(i));
            }
            if (slots.size() > count) {
                warnTooMany(group.moduleId, slots.size(), count);
            }
        }
        return targets;
    }

    /** 各组规模的可读描述（排障用：哪个模块的槽位表异常大）。 */
    public String describeGroupSizes() {
        StringBuilder sb = new StringBuilder("groups=").append(groups.size()).append(" [");
        int shown = 0;
        for (Map.Entry<String, Group> e : groups.entrySet()) {
            if (shown++ > 0) {
                sb.append(' ');
            }
            if (shown > 20) {
                sb.append("…");
                break;
            }
            sb.append(e.getKey()).append('=').append(e.getValue().slots.size());
        }
        return sb.append(']').toString();
    }

    /** 当前所有有面板承接的槽位数（测试/调试用）。 */
    public int hostedCount() {
        return moduleOfSlot.size();
    }

    /** 当前登记的模块 id 集合（按登记顺序）。 */
    public Set<String> moduleIds() {
        return Collections.unmodifiableSet(groups.keySet());
    }
}
