package com.github.aeddddd.ae2enhanced.centralinterface;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 中枢 ME 接口的<b>流体缓冲</b>：网络暂不收下的产物在这里等下一次注入.
 *
 * <p>它在流体链路里的位置：{@link PhysicalDispatcher} 只把流体送往两个去处——留回机器里，
 * 或留在这里。网络暂时收不下（没有流体存储 / 存储已满 / AE 能量不足 / 区块未加载……）时，
 * 流体在这里等下一个 tick 的重试，只要流体通道后来能收，就会自己落进流体终端。
 * 把流体转成流体假物品等于让它<b>离开流体通道</b>，玩家在流体终端里就再也看不到它。</p>
 *
 * <p><b>持久化</b>：内容写入 NBT（键 {@code centralFluidBuffer}），chunk 卸载 /
 * 服务器重启后继续重试，不会因为设备暂时失效而丢流体。</p>
 *
 * <p><b>容量</b>：同一流体的数量不设上限（用 int 存 mB 会溢出，这里直接累计 FluidStack 的
 * amount）。条目数（= 不同流体种类）超过 {@link #MAX_ENTRIES} 只留痕提示，<b>不拒绝入队</b>：
 * 缓冲是这些流体唯一的落脚点，拒绝入队等于把它们交给"转物品"或"丢弃"两条真正的消失路径；
 * 实际条目数由机器真实产出的流体种类数封顶。</p>
 */
public class CentralFluidBuffer {

    /** 不同流体种类的提示阈值：超过只留痕，不拒绝入队。 */
    public static final int MAX_ENTRIES = 32;
    /**
     * 单个条目重试次数的提示阈值.
     *
     * <p>重试由设备的 tick 驱动，而设备会按 {@code TickRateModulation} 在 1~5 tick 之间调制，
     * 因此 200 次大约相当于 10~50 秒。达到阈值只记一次"仍然收不下"的日志，
     * 条目不换去处——缓冲之外只有转物品或丢弃两条路，对已经从机器里抽出来的流体都是消失。</p>
     */
    public static final int MAX_RETRY_ATTEMPTS = 200;

    private static final String NBT_LIST = "centralFluidBuffer";
    private static final String NBT_FLUID = "Fluid";
    private static final String NBT_ATTEMPTS = "Attempts";

    private final List<Entry> entries = new ArrayList<>();
    private final int maxEntries;
    private final int maxAttempts;

    public CentralFluidBuffer() {
        this(MAX_ENTRIES, MAX_RETRY_ATTEMPTS);
    }

    public CentralFluidBuffer(int maxEntries, int maxAttempts) {
        this.maxEntries = Math.max(1, maxEntries);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /**
     * 把网络拒收的流体放进缓冲（同种流体合并，恒收下）.
     */
    public void add(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
            // 无内容的流体没有东西可保管，直接忽略（调用方不会因此丢掉任何流体）。
            // 上游 safeInjectFluids / pushFluidsToTarget 已过滤空流体，这里只是兜底。
            return;
        }
        for (Entry entry : this.entries) {
            if (entry.fluid.isFluidEqual(fluid)) {
                // 数量与 NBT 都并入已有条目；重试计数<b>不清零</b>：
                // 清零会让"流体通道长期收不下"的条目靠不断到货一直停留在阈值以下，日志失去意义。
                entry.fluid.amount += fluid.amount;
                if (fluid.tag != null) {
                    entry.fluid.tag = fluid.tag.copy();
                }
                return;
            }
        }
        if (this.entries.size() >= this.maxEntries) {
            // 超过阈值只留痕：这里的流体已经离开机器，缓冲是它唯一的落脚点。
            AE2Enhanced.LOGGER.warn("[AE2E] CentralInterface fluid buffer already holds {} fluid types (threshold {});"
                    + " keeping holding them instead of dropping the fluid", this.entries.size(), this.maxEntries);
        }
        this.entries.add(new Entry(fluid.copy()));
    }

    /** 当前条目快照（重试循环遍历用；条目对象本身可继续操作）. */
    public List<Entry> entries() {
        return new ArrayList<>(this.entries);
    }

    public boolean remove(Entry entry) {
        return this.entries.remove(entry);
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public int size() {
        return this.entries.size();
    }

    public int maxAttempts() {
        return this.maxAttempts;
    }

    public long totalAmount() {
        long total = 0;
        for (Entry entry : this.entries) {
            total += entry.fluid.amount;
        }
        return total;
    }

    public void clear() {
        this.entries.clear();
    }

    public void writeToNBT(NBTTagCompound data) {
        NBTTagList list = new NBTTagList();
        for (Entry entry : this.entries) {
            if (entry.fluid == null || entry.fluid.getFluid() == null || entry.fluid.amount <= 0) {
                continue;
            }
            NBTTagCompound tag = new NBTTagCompound();
            entry.fluid.writeToNBT(tag);
            tag.setInteger(NBT_ATTEMPTS, entry.attempts);
            list.appendTag(tag);
        }
        if (list.tagCount() > 0) {
            data.setTag(NBT_LIST, list);
        } else {
            data.removeTag(NBT_LIST);
        }
    }

    public void readFromNBT(NBTTagCompound data) {
        this.entries.clear();
        if (data == null || !data.hasKey(NBT_LIST, 9)) {
            return;
        }
        NBTTagList list = data.getTagList(NBT_LIST, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag);
            if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
                continue;
            }
            Entry entry = new Entry(fluid);
            entry.attempts = tag.getInteger(NBT_ATTEMPTS);
            this.entries.add(entry);
        }
    }

    /**
     * 一个待重试的流体条目.
     *
     * <p>{@link #fluid} 的数量随重试结果收缩（部分被接受时减少），归零即应移除。</p>
     */
    public static final class Entry {

        private FluidStack fluid;
        private int attempts;

        Entry(FluidStack fluid) {
            this.fluid = fluid;
        }

        public FluidStack fluid() {
            return this.fluid;
        }

        public void setFluid(FluidStack fluid) {
            this.fluid = fluid;
        }

        public int attempts() {
            return this.attempts;
        }

        public void countAttempt() {
            this.attempts++;
        }
    }
}
