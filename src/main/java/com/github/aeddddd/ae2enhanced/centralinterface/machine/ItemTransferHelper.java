package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerUtils;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 目标机器的物品 IO 助手（对称于 {@code FluidTransferHelper}）.
 *
 * <p>本类<b>无状态</b>，全部通过参数传递；所有 handler 调用都应经此，不得再各自写遍历循环。</p>
 */
public final class ItemTransferHelper {

    private ItemTransferHelper() {
    }

    // ---- 推料 ----

    /**
     * 两阶段原子推料：先对全部物品做纯模拟，全部能放下才真正插入。
     *
     * <p>与 {@code IRemoteHandler#pushMaterials} 的原子性契约一致：返回失败时不得在目标内
     * 留下任何本批材料（模拟阶段通过、实际阶段意外失败时会按记录回退已插入部分）。</p>
     *
     * <p><b>台账</b>：只有真实插入阶段才记账（{@link PushLedger}），逐槽记录"实际放进去了多少"。
     * 模拟阶段不产生任何记录——否则台账会把"打算放的数量"当成"已放的数量"，回退时反而多取。
     * 失败（原子回退）时台账为空：目标内没有本批材料，回退无事可做。</p>
     */
    public static PushOutcome push(ISlotCatalog catalog, List<ItemStack> batch) {
        if (catalog == null) {
            return PushOutcome.failure(batch);
        }
        List<ItemStack> pending = CatalogBuilder.nonEmpty(batch);
        if (pending.isEmpty()) {
            return PushOutcome.success();
        }
        List<SlotRef> targets = catalog.pushTargets();
        if (targets.isEmpty()) {
            return PushOutcome.failure(pending);
        }

        // 阶段 1：纯模拟。任一物品放不下即整体失败，不做任何真实修改，也不记台账。
        for (ItemStack stack : pending) {
            if (!distribute(targets, stack, true, null).isEmpty()) {
                return PushOutcome.failure(pending);
            }
        }

        // 阶段 2：真实插入，并逐槽记录实际放入量以便回退与后续精确取回。
        List<PushLedger.Entry> insertions = new ArrayList<>();
        for (ItemStack stack : pending) {
            if (distribute(targets, stack, false, insertions).isEmpty()) {
                continue;
            }
            // 理论上模拟通过后不应发生；某些机器 simulate 与 modulate 语义不一致时会走到这里。
            undo(insertions);
            return PushOutcome.failure(pending);
        }
        return PushOutcome.success(PushLedger.of(insertions));
    }

    /**
     * 只回收"本批材料快照"内的物品（无台账时的口径）。
     *
     * <p>不按"无条件清空全部输入槽"处理，否则会带走机器自己从相邻容器
     * 拉来的料、以及玩家预置的物品。</p>
     *
     * <p><b>注意</b>：本口径按"槽内物品匹配本批快照"整槽取走，因此当槽内混有机器原有的
     * 同型物品时会多取。只有拿不到台账（handler 自行推料、
     * 或未经过框架推料）时才回退到这里。</p>
     */
    public static List<ItemStack> revertBatch(ISlotCatalog catalog, List<ItemStack> batchInputs) {
        return revertBatch(catalog, batchInputs, null);
    }

    /**
     * 回退本批已推送的物品：<b>有台账时逐槽精确取回</b>，无台账时退回"本批材料快照"口径。
     *
     * <p>台账口径（{@link PushLedger}）逐条处理真实插入记录：每个槽最多取回台账记录的数量，
     * 且以该槽现存量为上限、物品身份必须匹配。因此即使槽内是"机器原有 32 个 + 本批 64 个"
     * 的合并堆叠，也只取回本批的 64 个，机器原有的 32 个留在原处。</p>
     *
     * @param ledger 本批推料台账；{@code null} 或空表示拿不到台账，退化为快照口径
     */
    public static List<ItemStack> revertBatch(ISlotCatalog catalog, List<ItemStack> batchInputs, PushLedger ledger) {
        if (catalog == null) {
            return new ArrayList<>();
        }
        if (ledger != null && !ledger.isEmpty()) {
            return revertByLedger(catalog, ledger);
        }
        return revertBySnapshot(catalog, batchInputs);
    }

    /** 快照口径：整槽取走"匹配本批材料快照"的槽位内容（无台账时的降级路径）。 */
    private static List<ItemStack> revertBySnapshot(ISlotCatalog catalog, List<ItemStack> batchInputs) {
        List<ItemStack> reverted = new ArrayList<>();
        if (batchInputs == null || batchInputs.isEmpty()) {
            return reverted;
        }
        for (SlotRef slot : catalog.pushTargets()) {
            ItemStack inSlot = slot.peek();
            if (inSlot.isEmpty() || !HandlerUtils.isInputMaterial(inSlot, batchInputs)) {
                continue;
            }
            ItemStack got = slot.extract(inSlot.getCount(), false);
            if (!got.isEmpty()) {
                reverted.add(got);
            }
        }
        return reverted;
    }

    /**
     * 台账口径：逆序逐条取回本批实际放入的物品。
     *
     * <p>三条护栏，缺一不可：</p>
     * <ol>
     *   <li>槽位必须能在回退时的目录中重新定位（{@link #resolveSlot}），定位不到就跳过——
     *       宁可不取，也不拿一个"猜"出来的槽位开刀；</li>
     *   <li>槽内物品身份必须与台账一致，否则跳过（机器可能已消耗/换成别的东西）；</li>
     *   <li>取回量 = min(台账数量, 该槽现存量)，绝不整槽取走。</li>
     * </ol>
     */
    private static List<ItemStack> revertByLedger(ISlotCatalog catalog, PushLedger ledger) {
        List<ItemStack> reverted = new ArrayList<>();
        List<PushLedger.Entry> entries = ledger.entries();
        // 逆序回退：与 undo() 一致，后插入的先取回
        for (int i = entries.size() - 1; i >= 0; i--) {
            PushLedger.Entry entry = entries.get(i);
            SlotRef slot = resolveSlot(catalog, entry);
            if (slot == null) {
                continue;
            }
            ItemStack inSlot = slot.peek();
            if (inSlot.isEmpty() || !sameIdentity(inSlot, entry.stack())) {
                continue;
            }
            int amount = Math.min(entry.count(), inSlot.getCount());
            if (amount <= 0) {
                continue;
            }
            ItemStack got = slot.extract(amount, false);
            if (!got.isEmpty()) {
                reverted.add(got);
            }
        }
        return reverted;
    }

    /**
     * 在（回退时重建的）目录中重新定位台账记录的槽位.
     *
     * <p>目录是每次读写前新建的一次性快照，且部分机器的 {@code IItemHandler} 包装对象每次
     * 取用都会新建（如 Thermal Expansion 的 {@code InventoryArrayItemHandler}），因此不能依赖
     * {@link SlotRef} 实例本身，只能按稳定特征逐级降级匹配：</p>
     * <ol>
     *   <li>同一 handler 实例 + 下标 + 同一面；</li>
     *   <li>同一 handler 实例 + 下标（侧面暴露方式变了，但底层槽位没变）；</li>
     *   <li>同 (面, 下标, 角色)——仅当命中唯一时才采用：一个目录里可能有多个面为 null
     *       的包装对象（如 ActuallyAdditions 的主库存 + 各基座），命中多个即无法确定，
     *       此时返回 null 放弃该条记录。</li>
     * </ol>
     */
    private static SlotRef resolveSlot(ISlotCatalog catalog, PushLedger.Entry entry) {
        SlotRef origin = entry.slot();
        List<SlotRef> slots = catalog.slots();

        for (SlotRef slot : slots) {
            if (slot.handler() == origin.handler() && slot.index() == origin.index()
                    && slot.face() == origin.face()) {
                return slot;
            }
        }
        for (SlotRef slot : slots) {
            if (slot.handler() == origin.handler() && slot.index() == origin.index()) {
                return slot;
            }
        }

        SlotRef unique = null;
        for (SlotRef slot : slots) {
            if (slot.index() != origin.index() || slot.face() != origin.face()
                    || slot.role() != origin.role()) {
                continue;
            }
            if (unique != null) {
                return null;
            }
            unique = slot;
        }
        return unique;
    }

    /** 物品身份（item + metadata + NBT）是否一致。 */
    private static boolean sameIdentity(ItemStack actual, ItemStack expected) {
        return ItemStack.areItemsEqual(actual, expected)
                && ItemStack.areItemStackTagsEqual(actual, expected);
    }

    /**
     * 发配前回收目标输出槽的残留内容.
     *
     * <ul>
     *   <li>角色已知：清空 {@link SlotRole#OUTPUT} 槽中不属于本批输入的内容；</li>
     *   <li>角色未知（降级）：只清匹配 {@code expectedOutputs} 的内容
     *       （该阶段 session 尚无输入快照，"输入材料"分支恒不成立）。</li>
     * </ul>
     *
     * <p>绝不触碰 {@link SlotRole#UPGRADE} 等槽位。</p>
     */
    public static List<ItemStack> clearOutputs(ISlotCatalog catalog, List<ItemStack> batchInputs,
                                               IAEItemStack[] expectedOutputs) {
        List<ItemStack> cleared = new ArrayList<>();
        if (catalog == null) {
            return cleared;
        }
        boolean rolesKnown = catalog.rolesKnown();
        for (SlotRef slot : catalog.slots()) {
            if (rolesKnown && !slot.role().producesOutput()) {
                continue;
            }
            ItemStack inSlot = slot.peek();
            if (inSlot.isEmpty()) {
                continue;
            }
            // 角色未知时的清空判据 = "上一轮可能残留的东西"：
            // 匹配本样板预期产物，或匹配本批材料（上一轮未消耗完的输入）。
            // 升级卡/电池等既非产物也非材料，因此天然被跳过。
            if (!rolesKnown
                    && !ProductFilter.matchesExpected(inSlot, expectedOutputs)
                    && !HandlerUtils.isInputMaterial(inSlot, batchInputs)) {
                continue;
            }
            ItemStack got = slot.extract(inSlot.getCount(), false);
            if (!got.isEmpty()) {
                cleared.add(got);
            }
        }
        return cleared;
    }

    // ---- 收集 ----

    /**
     * 两阶段产物收集：阶段 1 按样板声明的预期产物精确收集，阶段 2 收剩余输出槽.
     *
     * <p>本批输入快照内的物品永不被当作产物。</p>
     */
    public static List<ItemStack> collectProducts(ISlotCatalog catalog, IAEItemStack[] expectedOutputs,
                                                  List<ItemStack> batchInputs) {
        List<ItemStack> collected = new ArrayList<>();
        if (catalog == null) {
            return collected;
        }
        List<SlotRef> slots = catalog.slots();
        ProductFilter filter = ProductFilter.of(expectedOutputs, batchInputs, catalog.rolesKnown());
        // SlotRef 无 equals 覆写，按同一目录对象内返回的实例做身份去重
        Set<SlotRef> drained = Collections.newSetFromMap(new IdentityHashMap<SlotRef, Boolean>());

        // 阶段 1：预期产物优先
        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null || expected.getStackSize() <= 0) {
                    continue;
                }
                ItemStack template = expected.createItemStack();
                if (template.isEmpty()) {
                    continue;
                }
                long remaining = expected.getStackSize();
                for (SlotRef slot : slots) {
                    if (remaining <= 0) {
                        break;
                    }
                    if (drained.contains(slot)) {
                        continue;
                    }
                    ItemStack inSlot = slot.peek();
                    if (inSlot.isEmpty() || HandlerUtils.isInputMaterial(inSlot, batchInputs)) {
                        continue;
                    }
                    if (!ProductFilter.matchesExpected(inSlot, new IAEItemStack[]{expected})) {
                        continue;
                    }
                    int want = (int) Math.min(remaining, inSlot.getCount());
                    ItemStack got = slot.extract(want, false);
                    if (got.isEmpty()) {
                        continue;
                    }
                    // 不做合并：同种物品分属不同阶段/槽位时保持为独立条目。
                    // 阶段 1 逐预期产物、阶段 2 逐槽，
                    // 合并会改变返回条数，进而改变调用方的记账与日志口径。
                    collected.add(got);
                    remaining -= got.getCount();
                    if (slot.peek().isEmpty()) {
                        drained.add(slot);
                    }
                }
            }
        }

        // 阶段 2：其余输出槽（副产物、余料、容器返还）
        for (SlotRef slot : slots) {
            if (drained.contains(slot)) {
                continue;
            }
            // 循环抽取直到槽位真正清空：部分 IItemHandler 的单次提取有上限
            boolean any = false;
            for (int guard = 0; guard < 64; guard++) {
                ItemStack inSlot = slot.peek();
                if (inSlot.isEmpty() || !filter.accepts(inSlot, slot.role())) {
                    break;
                }
                ItemStack got = slot.extract(inSlot.getCount(), false);
                if (got.isEmpty()) {
                    break;
                }
                collected.add(got);
                any = true;
            }
            if (any) {
                drained.add(slot);
            }
        }
        return collected;
    }

    // ---- 状态判定 ----

    /** 本批输入是否已被机器消耗完（没有任何推料目标槽仍持有本批材料）。 */
    public static boolean allBatchInputsConsumed(ISlotCatalog catalog, List<ItemStack> batchInputs) {
        if (catalog == null) {
            return true;
        }
        if (batchInputs == null || batchInputs.isEmpty()) {
            return true;
        }
        for (SlotRef slot : catalog.pushTargets()) {
            ItemStack inSlot = slot.peek();
            if (!inSlot.isEmpty() && HandlerUtils.isInputMaterial(inSlot, batchInputs)) {
                return false;
            }
        }
        return true;
    }

    /** 是否存在可抽取的"产物"（含可抽取性探测，避免"有值但抽不出"的槽误判）。 */
    public static boolean hasCollectableProduct(ISlotCatalog catalog, List<ItemStack> batchInputs) {
        if (catalog == null) {
            return false;
        }
        ProductFilter filter = ProductFilter.of(null, batchInputs, catalog.rolesKnown());
        for (SlotRef slot : catalog.slots()) {
            ItemStack inSlot = slot.peek();
            if (inSlot.isEmpty() || !filter.accepts(inSlot, slot.role())) {
                continue;
            }
            if (!slot.extract(1, true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---- 内部 ----

    /**
     * 把一个物品沿目标槽位列表依次插入。
     *
     * @param log 非 null 时记录每次成功插入（真实插入阶段才传非 null；模拟阶段必须传 null，
     *            否则台账会把"打算放的数量"当成"已放的数量"）
     * @return 未能放入的剩余部分
     */
    private static ItemStack distribute(List<SlotRef> targets, ItemStack stack, boolean simulate,
                                        List<PushLedger.Entry> log) {
        ItemStack remaining = stack.copy();
        for (SlotRef slot : targets) {
            if (remaining.isEmpty()) {
                break;
            }
            int before = remaining.getCount();
            ItemStack left = slot.insert(remaining, simulate);
            int placed = before - left.getCount();
            if (placed > 0 && log != null) {
                ItemStack record = remaining.copy();
                record.setCount(placed);
                log.add(new PushLedger.Entry(slot, record));
            }
            remaining = left;
        }
        return remaining;
    }

    /** 逆序回退已记录的插入（按台账数量与槽内现存量取较小值，避免误抽机器原有内容）。 */
    private static void undo(List<PushLedger.Entry> insertions) {
        for (int i = insertions.size() - 1; i >= 0; i--) {
            PushLedger.Entry insertion = insertions.get(i);
            ItemStack current = insertion.slot().peek();
            if (current.isEmpty() || !sameIdentity(current, insertion.stack())) {
                continue;
            }
            int amount = Math.min(insertion.count(), current.getCount());
            insertion.slot().extract(amount, false);
        }
    }

    /** 推料结果。 */
    public static final class PushOutcome {

        private final boolean success;
        private final List<ItemStack> unplaced;
        private final PushLedger ledger;

        private PushOutcome(boolean success, List<ItemStack> unplaced, PushLedger ledger) {
            this.success = success;
            this.unplaced = unplaced;
            this.ledger = ledger != null ? ledger : PushLedger.EMPTY;
        }

        public static PushOutcome success() {
            return new PushOutcome(true, Collections.emptyList(), PushLedger.EMPTY);
        }

        /** 成功且带逐槽台账（由 {@link #push} 在真实插入阶段记录）。 */
        public static PushOutcome success(PushLedger ledger) {
            return new PushOutcome(true, Collections.emptyList(), ledger);
        }

        public static PushOutcome failure(List<ItemStack> unplaced) {
            List<ItemStack> copy = new ArrayList<>();
            if (unplaced != null) {
                for (ItemStack stack : unplaced) {
                    if (stack != null && !stack.isEmpty()) {
                        copy.add(stack.copy());
                    }
                }
            }
            // 失败即已按台账（或无事可回）保证目标内没有本批材料，因此失败结果的台账恒为空。
            return new PushOutcome(false, Collections.unmodifiableList(copy), PushLedger.EMPTY);
        }

        public boolean isSuccess() {
            return this.success;
        }

        /** 失败时未能放入的物品（用于日志与回退）。 */
        public List<ItemStack> unplaced() {
            return this.unplaced;
        }

        /**
         * 本批实际放入的逐槽台账（不成功时为空）。
         *
         * <p>调用方应在推送成功后把它交给会话保存，回退时按台账精确取回本批物品
         * （槽内可能混有机器原有的同型物品，不能整槽取走）。</p>
         */
        public PushLedger ledger() {
            return this.ledger;
        }
    }
}
