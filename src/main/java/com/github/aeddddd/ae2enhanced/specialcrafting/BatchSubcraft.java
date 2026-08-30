package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.MECraftingInventory;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.MeaningfulItemIterator;

import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;

/**
 * 批量树子合成助手:复刻原生 {@code CraftingTreeNode.request} /
 * {@code CraftingTreeProcess.request} 的计划语义(库存提取 → 发射台 →
 * 样板分支 → 失败退款),但消除两处逐单位循环爆炸:
 * <ol>
 * <li><b>多生产者分支</b>:原生对每个待合成单位都复制一次全库存快照并递归
 * 子请求(O(单位数 × 库存条目),病态规模下单步烧掉整单预算);批量版按
 * ceilDiv 整批尝试,失败丢弃子库存并减半重试(O(log n) 批次数),保持
 * "分支 1 尽力 → 分支 2"的顺序与原子提交语义;</li>
 * <li><b>单生产者自引用/容器(getTimes==1)</b>:原生逐单位循环;批量版按
 * 输入库存容量整批,自引用 dup 随库存增长自然形成指数批次(1,2,4,...),
 * 保持种子自举语义.</li>
 * </ol>
 * 仅供环求解器的环外子合成与批量环步模拟使用:非模拟语义(不足即
 * {@link CraftBranchFailure}),与原生一致,由调用方回落/转环盲降级.
 */
public final class BatchSubcraft {

    private BatchSubcraft() {
    }

    /**
     * 批量版 {@code CraftingTreeProcess.request}:惰性构建输入子节点后,
     * 逐子节点批量请求(perCraft × times),注入容器物与产出,累计 crafts.
     */
    public static void requestStep(CraftingTreeProcess pro, ICraftingGrid cc, CraftingJob job,
            MECraftingInventory inv, long times, IActionSource src)
            throws CraftBranchFailure, InterruptedException {
        if (times <= 0) {
            return;
        }
        Ae2CraftingReflect.processAddProcess(pro);
        ICraftingPatternDetails details = Ae2CraftingReflect.getProcessDetails(pro);
        for (Object2LongArrayMap.Entry<CraftingTreeNode> entry : Ae2CraftingReflect.getProcessNodes(pro)
                .object2LongEntrySet()) {
            // IO 守卫(求解器侧)已保证 perCraft × times 可表示
            requestNode(entry.getKey(), cc, job, inv, entry.getLongValue() * times, src);
        }
        // 容器物:子请求提取阶段经 processAddContainer 累积,此处注入并清空
        // (复刻原生 request 尾部;计数口径与原生"按提取调用次数"一致)
        List<IAEItemStack> containers = Ae2CraftingReflect.processDrainContainers(pro);
        if (containers != null) {
            for (IAEItemStack container : containers) {
                inv.injectItems(container, Actionable.MODULATE, src);
            }
        }
        for (IAEItemStack out : details.getCondensedOutputs()) {
            if (out == null) {
                continue;
            }
            IAEItemStack o = out.copy();
            o.setStackSize(o.getStackSize() * times);
            inv.injectItems(o, Actionable.MODULATE, src);
        }
        Ae2CraftingReflect.setProcessCrafts(pro, Ae2CraftingReflect.getProcessCrafts(pro) + times);
    }

    /**
     * 批量版 {@code CraftingTreeNode.request}:提取库存 → 发射台 → 样板分支,
     * 不足时退款并抛 {@link CraftBranchFailure}(非模拟语义).
     */
    private static void requestNode(CraftingTreeNode node, ICraftingGrid cc, CraftingJob job,
            MECraftingInventory inv, long l, IActionSource src)
            throws CraftBranchFailure, InterruptedException {
        if (l <= 0) {
            return;
        }
        Ae2CraftingReflect.nodeAddNode(node);
        Ae2CraftingReflect.handlePausing(job);
        IAEItemStack what = Ae2CraftingReflect.getNodeWhat(node);

        List<IAEItemStack> thingsUsed = new ArrayList<>();
        long remaining = extractPhase(node, job, inv, what, l, src, thingsUsed);
        if (remaining <= 0) {
            return;
        }

        // 发射台:提取后剩余量免费满足(原生同语义,不展开样板分支)
        if (cc.canEmitFor(what)) {
            Ae2CraftingReflect.setNodeEmitted(node, remaining);
            addBytes(node, remaining);
            return;
        }

        List<CraftingTreeProcess> processes = Ae2CraftingReflect.getNodeProcesses(node);
        if (processes.size() == 1) {
            CraftingTreeProcess pro = processes.get(0);
            while (remaining > 0) {
                long outPer = amountCrafted(Ae2CraftingReflect.getProcessDetails(pro), what);
                long times = timesFor(Ae2CraftingReflect.getProcessDetails(pro), remaining, outPer);
                if (times == 1 && remaining > outPer) {
                    // getTimes==1(自引用/容器)的批量优化:按输入库存容量整批——
                    // 自引用 dup 随库存增长形成指数批次(种子自举),容器场景
                    // 容量即大值一批完成;容量为 0 时退化为 1(与原生逐单位一致,
                    // 失败由原生子请求抛出)
                    long capacity = capacityByStock(pro, inv);
                    times = Math.max(1, Math.min(ceilDiv(remaining, outPer), capacity));
                }
                // 原生单生产者分支不捕获 CraftBranchFailure,原样上抛
                requestStep(pro, cc, job, inv, times, src);
                long got = extractMadeWhat(pro, inv, what, remaining, src);
                if (got <= 0) {
                    break; // possible=false:该分支无法再产出
                }
                addBytes(node, got);
                remaining -= got;
            }
        } else if (processes.size() > 1) {
            for (CraftingTreeProcess pro : processes) {
                long times = -1;
                while (remaining > 0) {
                    long outPer = amountCrafted(Ae2CraftingReflect.getProcessDetails(pro), what);
                    if (times < 0) {
                        times = Math.min(ceilDiv(remaining, outPer),
                                Math.max(1, capacityByStock(pro, inv)));
                    }
                    // 批次原子提交(复刻原生 subInv+commit,粒度从 1 单位放大到整批)
                    MECraftingInventory subInv = new MECraftingInventory(inv, true, true, true);
                    long got = 0;
                    boolean committed = false;
                    try {
                        requestStep(pro, cc, job, subInv, times, src);
                        got = extractMadeWhat(pro, subInv, what, remaining, src);
                        if (got > 0 && subInv.commit(src)) {
                            committed = true;
                        }
                    } catch (CraftBranchFailure fail) {
                        // 批次过大(容量高估/深层不足):丢弃子库存,减半重试
                    }
                    if (!committed) {
                        if (times <= 1) {
                            break; // 单批也失败:分支尽,尝下一分支
                        }
                        times = Math.max(1, times / 2);
                        continue;
                    }
                    addBytes(node, got);
                    remaining -= got;
                    times = -1; // 按新剩余量重新评估批次
                }
            }
        }

        if (remaining > 0) {
            // 非模拟语义:退款本节点实取并抛分支失败(复刻原生;求解器据此回落)
            for (IAEItemStack o : thingsUsed) {
                Ae2CraftingReflect.jobRefund(job, o.copy());
                o.setStackSize(-o.getStackSize());
                Ae2CraftingReflect.getNodeUsed(node).add(o);
            }
            throw new CraftBranchFailure(what, remaining);
        }
    }

    /**
     * 提取阶段(复刻原生 request 前半段):可合成父样板走替代/模糊路径,
     * 其余精确提取;实取记 used(bytes + checkUse),带容器物回记父 process.
     *
     * @return 提取后仍缺的数量
     */
    private static long extractPhase(CraftingTreeNode node, CraftingJob job, MECraftingInventory inv,
            IAEItemStack what, long l, IActionSource src, List<IAEItemStack> thingsUsed) {
        IItemList<IAEItemStack> inventoryList = inv.getItemList();
        CraftingTreeProcess parent = Ae2CraftingReflect.getNodeParent(node);
        if (Ae2CraftingReflect.getNodeSlot(node) >= 0 && parent != null
                && Ae2CraftingReflect.getProcessDetails(parent).isCraftable()) {
            ICraftingPatternDetails parentDetails = Ae2CraftingReflect.getProcessDetails(parent);
            boolean damageable = what.getItem().isDamageable()
                    || Platform.isGTDamageableItem(what.getItem());
            LinkedList<IAEItemStack> itemList = new LinkedList<>();
            if (parentDetails.canSubstitute()) {
                for (IAEItemStack sub : parentDetails.getSubstituteInputs(
                        Ae2CraftingReflect.getNodeSlot(node))) {
                    if (sub == null) {
                        continue;
                    }
                    if (damageable) {
                        collectFuzzy(inventoryList, what, itemList);
                    }
                    IAEItemStack found = inventoryList.findPrecise(sub);
                    if (found == null || found.getStackSize() <= 0) {
                        continue;
                    }
                    itemList.add(found);
                }
            } else if (damageable) {
                collectFuzzy(inventoryList, what, itemList);
            } else {
                IAEItemStack found = inventoryList.findPrecise(what);
                if (found != null && found.getStackSize() > 0) {
                    itemList.add(found);
                }
            }
            for (IAEItemStack candidate : itemList) {
                if (!parentDetails.isValidItemForSlot(Ae2CraftingReflect.getNodeSlot(node),
                        candidate.getDefinition(), Ae2CraftingReflect.getWorld(job))) {
                    continue;
                }
                IAEItemStack request = candidate.copy();
                request.setStackSize(l);
                IAEItemStack available = inv.extractItems(request, Actionable.MODULATE, src);
                if (available == null) {
                    continue;
                }
                recordContainer(parent, available);
                recordUsed(node, job, available, thingsUsed);
                l -= available.getStackSize();
                if (l == 0) {
                    return 0;
                }
            }
        } else {
            IAEItemStack request = what.copy();
            request.setStackSize(l);
            IAEItemStack available = inv.extractItems(request, Actionable.MODULATE, src);
            if (available != null) {
                recordUsed(node, job, available, thingsUsed);
                l -= available.getStackSize();
            }
        }
        return l;
    }

    /** 模糊(忽略耐久/NBT)收集库存候选——仅损伤类物品路径使用(复刻原生). */
    private static void collectFuzzy(IItemList<IAEItemStack> inventoryList, IAEItemStack what,
            LinkedList<IAEItemStack> itemList) {
        MeaningfulItemIterator<IAEItemStack> it = new MeaningfulItemIterator<>(
                inventoryList.findFuzzy(what, FuzzyMode.IGNORE_ALL));
        while (it.hasNext()) {
            IAEItemStack i = it.next();
            if (i.getStackSize() > 0) {
                itemList.add(i);
            }
        }
    }

    /** 实取记账:checkUse 成功才记 used 与 bytes(复刻原生;失败退款依赖 thingsUsed). */
    private static void recordUsed(CraftingTreeNode node, CraftingJob job, IAEItemStack available,
            List<IAEItemStack> thingsUsed) {
        IAEItemStack is = Ae2CraftingReflect.jobCheckUse(job, available);
        if (is != null) {
            thingsUsed.add(is.copy());
            Ae2CraftingReflect.getNodeUsed(node).add(is);
        }
        addBytes(node, available.getStackSize());
    }

    /** 带容器物实取时回记父 process(复刻原生 addContainers 路径). */
    private static void recordContainer(CraftingTreeProcess parent, IAEItemStack available) {
        if (!available.getItem().hasContainerItem(available.getDefinition())) {
            return;
        }
        ItemStack containerStack = Platform.getContainerItem(available.createItemStack());
        IAEItemStack container = AEItemStack.fromItemStack(containerStack);
        if (container != null) {
            Ae2CraftingReflect.processAddContainer(parent, container);
        }
    }

    /** 从库存提取已合成产出(madeWhat),返回实际取得量. */
    private static long extractMadeWhat(CraftingTreeProcess pro, MECraftingInventory inv, IAEItemStack what,
            long amount, IActionSource src) {
        IAEItemStack probe = what.copy();
        probe.setStackSize(amount);
        IAEItemStack available = inv.extractItems(probe, Actionable.MODULATE, src);
        return available == null ? 0 : available.getStackSize();
    }

    /**
     * 复刻原生 CraftingTreeProcess.getTimes:任一输出等于某输入、或某输入的
     * 容器物等于输出(自引用/容器场景)→ 1;否则 ceilDiv.
     */
    private static long timesFor(ICraftingPatternDetails details, long remaining, long outPer) {
        for (IAEItemStack out : details.getCondensedOutputs()) {
            if (out == null) {
                continue;
            }
            for (IAEItemStack in : details.getCondensedInputs()) {
                if (in == null) {
                    continue;
                }
                if (out.equals(in) || in.getItem().hasContainerItem(out.getDefinition())) {
                    return 1;
                }
            }
        }
        return ceilDiv(remaining, outPer);
    }

    /**
     * 复刻原生 getAmountCrafted:先 isSameType 精确匹配,再同物品
     *(可损伤或同 meta)模糊匹配;找不到即原生 "Crafting Tree construction failed".
     */
    private static long amountCrafted(ICraftingPatternDetails details, IAEItemStack what) {
        for (IAEItemStack is : details.getCondensedOutputs()) {
            if (is != null && is.isSameType(what)) {
                return is.getStackSize();
            }
        }
        for (IAEItemStack is : details.getCondensedOutputs()) {
            if (is != null && is.getItem() == what.getItem()
                    && (is.getItem().isDamageable() || is.getItemDamage() == what.getItemDamage())) {
                return is.getStackSize();
            }
        }
        throw new IllegalStateException("Crafting Tree construction failed.");
    }

    /**
     * 输入库存容量(浅层):各 condensed 输入 库存/perCraft 的最小值.
     * 仅用于自引用/容器场景的批次封顶与多生产者分支的初始批次估计;
     * 低估是安全的(只会增加批次数),高估由子库存回滚+减半纠正.
     */
    private static long capacityByStock(CraftingTreeProcess pro, MECraftingInventory inv) {
        long capacity = Long.MAX_VALUE;
        for (IAEItemStack input : Ae2CraftingReflect.getProcessDetails(pro).getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            long avail = CycleSolver.invAmount(inv, input);
            capacity = Math.min(capacity, avail / input.getStackSize());
        }
        return capacity == Long.MAX_VALUE ? 0 : capacity;
    }

    private static long ceilDiv(long a, long b) {
        return a / b + (a % b != 0 ? 1 : 0);
    }

    private static void addBytes(CraftingTreeNode node, long delta) {
        Ae2CraftingReflect.setNodeBytes(node, Ae2CraftingReflect.getNodeBytes(node) + delta);
    }
}
