package com.github.aeddddd.ae2enhanced.test.support;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;

import com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;

/**
 * 1.12.2 计划视图（对应 1.20.1 测试中直接访问 ICraftingPlan 的读取面）.
 * <p>1.12.2 没有 ICraftingPlan 对象:patternTimes 经合成树遍历提取,
 * usedItems 经 populatePlan(stackSize>0 条目),missingItems 反射读取 job 的
 * missing 列表(与 dive 记账一致,区别于容器端的网络扣减语义).</p>
 */
public final class PlanView {

    private static final Field JOB_MISSING;

    static {
        try {
            JOB_MISSING = CraftingJob.class.getDeclaredField("missing");
            JOB_MISSING.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final CraftingJob job;

    private PlanView(CraftingJob job) {
        this.job = job;
    }

    public static PlanView of(CraftingJob job) {
        return new PlanView(job);
    }

    public CraftingJob job() {
        return this.job;
    }

    public boolean simulation() {
        return this.job.isSimulation();
    }

    public long bytes() {
        return this.job.getByteTotal();
    }

    public IAEItemStack finalOutput() {
        return this.job.getOutput();
    }

    /** 各样板的调用次数（合成树遍历,按 details 实例聚合）. */
    public Map<ICraftingPatternDetails, Long> patternTimes() {
        Map<ICraftingPatternDetails, Long> out = new LinkedHashMap<>();
        this.collect(this.job.getTree(), out);
        return out;
    }

    private void collect(CraftingTreeNode node, Map<ICraftingPatternDetails, Long> out) {
        if (node == null) {
            return;
        }
        for (CraftingTreeProcess pro : Ae2CraftingReflect.getNodeProcesses(node)) {
            long crafts = Ae2CraftingReflect.getProcessCrafts(pro);
            if (crafts > 0) {
                out.merge(Ae2CraftingReflect.getProcessDetails(pro), crafts, Long::sum);
            }
            for (CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pro).keySet()) {
                this.collect(child, out);
            }
        }
    }

    /**
     * 计划的库存消耗（populatePlan 中 stackSize>0 且非 requestable 的条目）.
     * 仅在计划成功(simulation=false)时有意义.
     */
    public Map<IAEItemStack, Long> usedItems() {
        IItemList<IAEItemStack> plan = newList();
        this.job.populatePlan(plan);
        Map<IAEItemStack, Long> out = new LinkedHashMap<>();
        for (IAEItemStack entry : plan) {
            if (entry.getStackSize() > 0) {
                IAEItemStack key = RecursiveCraftingHelper.canon(entry);
                out.merge(key, entry.getStackSize(), Long::sum);
            }
        }
        return out;
    }

    /** 缺失物品（反射读取 job 的 missing 列表,dive 记账,区别于容器端网络扣减语义）. */
    @SuppressWarnings("unchecked")
    public Map<IAEItemStack, Long> missingItems() {
        try {
            IItemList<IAEItemStack> missing = (IItemList<IAEItemStack>) JOB_MISSING.get(this.job);
            Map<IAEItemStack, Long> out = new LinkedHashMap<>();
            for (IAEItemStack entry : missing) {
                if (entry.getStackSize() > 0) {
                    IAEItemStack key = RecursiveCraftingHelper.canon(entry);
                    out.merge(key, entry.getStackSize(), Long::sum);
                }
            }
            return out;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static IItemList<IAEItemStack> newList() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();
    }
}
