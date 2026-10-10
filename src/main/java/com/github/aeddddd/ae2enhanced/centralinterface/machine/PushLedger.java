package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次物理推料的逐槽台账：记录"我们实际往哪个槽放进了多少个什么物品".
 *
 * <p><b>为什么必须有它</b>：目标容器会把新放入的同型物品自动并入槽内已有的堆叠。
 * 例如槽内原本有 32 个，我们推入 64 个，槽内就是 96 个；此时若按"槽内物品匹配本批材料"
 * 整槽取走，就会把机器原有的 32 个一并偷走。台账按槽记下本批实际放入量，回退时
 * 以"台账数量"与"该槽现存量"的较小值为上限，多余的部分（机器原有的、或已被机器消耗后
 * 剩下的）留在机器里。</p>
 *
 * <p>台账只服务于<b>一次</b>发配的失败/超时回退：{@code TargetSession} 在 {@code reset()} 时丢弃，
 * 不写入 NBT（它是纯运行时数据，重启后即将回退的批次本身也已不存在）。</p>
 *
 * <p>本类不可变。{@link Entry} 持有推料当时的 {@link SlotRef} 与物品身份快照，
 * 回退时由 {@link ItemTransferHelper} 在重建的目录中重新定位槽位。</p>
 */
public final class PushLedger {

    /** 空台账：模拟阶段、空批次或未发生任何真实插入时使用。 */
    public static final PushLedger EMPTY = new PushLedger(Collections.emptyList());

    private final List<Entry> entries;

    PushLedger(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** 由一组插入记录构造台账；空集合返回 {@link #EMPTY}。 */
    static PushLedger of(List<Entry> entries) {
        return entries == null || entries.isEmpty() ? EMPTY : new PushLedger(entries);
    }

    /** 逐槽插入记录，顺序与真实插入顺序一致。 */
    public List<Entry> entries() {
        return this.entries;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** 记录条数（= 被写入的槽位次数）。 */
    public int size() {
        return this.entries.size();
    }

    /** 本批实际放入的总件数（仅用于日志与诊断）。 */
    public int totalCount() {
        int total = 0;
        for (Entry entry : this.entries) {
            total += entry.count();
        }
        return total;
    }

    @Override
    public String toString() {
        return "PushLedger{slots=" + this.entries.size() + ", total=" + totalCount() + "}";
    }

    /**
     * 单次真实插入记录.
     *
     * <p>{@link #stack()} 是插入当时的物品身份快照，其 {@code count} 即实际放入数量
     * （不是请求数量：容器可能只收下一部分）。</p>
     */
    public static final class Entry {

        private final SlotRef slot;
        private final ItemStack stack;

        Entry(SlotRef slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack.copy();
        }

        /** 推料时使用的槽位引用（回退时用于重新定位，见 {@link #face()} / {@link #index()}）。 */
        public SlotRef slot() {
            return this.slot;
        }

        /** 实际放入的物品身份快照（count = 实际放入数量）。 */
        public ItemStack stack() {
            return this.stack;
        }

        /** 实际放入数量。 */
        public int count() {
            return this.stack.getCount();
        }

        public EnumFacing face() {
            return this.slot.face();
        }

        public int index() {
            return this.slot.index();
        }

        @Override
        public String toString() {
            return this.slot + " x" + count();
        }
    }
}
