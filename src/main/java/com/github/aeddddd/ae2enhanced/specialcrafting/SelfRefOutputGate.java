package com.github.aeddddd.ae2enhanced.specialcrafting;

import javax.annotation.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialCpuAccess;

/**
 * 自消耗 job 的最终产出交付门控（执行层,移植自 1.20.1 的 SelfRefOutputGate）.
 * <p><b>问题</b>:原生 {@code CraftingCPUCluster.injectItems} 中,任何匹配 finalOutput 的回流
 * 物品会立即经 {@code myLastLink.injectItems} 交付并扣减 finalOutput.自引用/循环链计划中
 * 请求物既是产出又是输入——种子产出的第一批物品被直接交付而非喂给下一份合成,
 * 导致链条饿死:无法并行爬坡、多轮中断、任务永远无法完成.</p>
 * <p><b>门控策略</b>:仅对被 {@link SpecialCraftingRuntime} 标记的集群（= 正在执行特殊计划,
 * 构造上必然自消耗）,回流的最终产出先存入 CPU 库存（喂给后续合成）;当所有任务已推送
 * 且该 key 无在途量时,一次性从库存交付剩余 finalOutput 并完成 job
 * （语义与原生一致:link 拒收余量由 finishJob 后的 storeItems 返回网络）.</p>
 * <p>普通 job 与普通 CPU 的集群永不被标记,本门控对它们零影响.</p>
 */
public final class SelfRefOutputGate {

    /**
     * 门控结果.
     */
    public static final class GateResult {
        public final boolean handled;
        @Nullable
        public final IAEItemStack leftover;

        private GateResult(boolean handled, @Nullable IAEItemStack leftover) {
            this.handled = handled;
            this.leftover = leftover;
        }
    }

    private static final GateResult NOT_HANDLED = new GateResult(false, null);

    /** 收官受阻告警限频间隔(ms). */
    private static final long STUCK_WARN_INTERVAL_MS = 30_000L;

    /** cluster → 上次收官受阻告警时间(弱键,集群回收自动清理). */
    private static final java.util.Map<CraftingCPUCluster, Long> LAST_STUCK_WARN = java.util.Collections
            .synchronizedMap(new java.util.WeakHashMap<>());

    private SelfRefOutputGate() {
    }

    /**
     * 门控 {@code CraftingCPUCluster.injectItems}.
     *
     * @return handled=false 时不接管（走原生）;handled=true 时调用方以 leftover 为返回值.
     */
    public static GateResult handleInsert(CraftingCPUCluster cluster, IAEItemStack input, Actionable type,
            IActionSource src) {
        if (input == null || !SpecialCraftingRuntime.isSpecialCluster(cluster) || !cluster.isBusy()) {
            return NOT_HANDLED;
        }
        ISpecialCpuAccess acc = (ISpecialCpuAccess) (Object) cluster;
        IAEItemStack finalOutput = acc.ae2e$finalOutput();
        if (finalOutput == null || !finalOutput.equals(input)) {
            return NOT_HANDLED;
        }
        IItemList<IAEItemStack> waitingFor = acc.ae2e$waitingFor();
        IAEItemStack waiting = waitingFor.findPrecise(input);
        if (waiting == null || waiting.getStackSize() <= 0) {
            return NOT_HANDLED; // 无在途等待:与原生一致走普通存储
        }

        long accept = Math.min(waiting.getStackSize(), input.getStackSize());
        if (type == Actionable.SIMULATE) {
            // 门控接受(进入 CPU 库存):与原生 finalOutput 分支的"全收"语义一致
            IAEItemStack leftover = input.copy();
            leftover.decStackSize(accept);
            return new GateResult(true, leftover.getStackSize() > 0 ? leftover : null);
        }

        // MODULATE:与原生一致的 waitingFor/存量/通知记账,但产出先入 CPU 库存而非交付
        IAEItemStack what = input.copy();
        acc.ae2e$postChange(what, src);
        waiting.decStackSize(accept);
        IAEItemStack acceptedStack = what.copy();
        acceptedStack.setStackSize(accept);
        acc.ae2e$updateRemainingItemCount(acceptedStack);
        acc.ae2e$markDirtyCluster();
        IAEItemStack statusDiff = acceptedStack.copy();
        statusDiff.setStackSize(-accept);
        acc.ae2e$postCraftingStatusChange(statusDiff);

        // 门控核心:最终产出先入 CPU 库存(喂给后续合成),而非直接交付
        IMEInventory<IAEItemStack> inventory = cluster.getInventory();
        inventory.injectItems(acceptedStack, Actionable.MODULATE, src);

        IAEItemStack leftover = what.copy();
        leftover.decStackSize(accept);

        trySettle(cluster, acc, finalOutput, src);
        return new GateResult(true, leftover.getStackSize() > 0 ? leftover : null);
    }

    /**
     * 每 tick 收官尝试（由 updateCraftingLogic HEAD 对被标记集群调用）.
     * <p>必要性:executeCrafting 中 value 归零的 task 条目要到下一次迭代才移除,
     * 最后一次门控回流时 tasks 可能仍含零值条目,导致 handleInsert 内的 trySettle
     * 不触发;此后若无新的回流,收官将永远不会发生.每 tick 兜底确保收官.</p>
     */
    public static void tickSettle(CraftingCPUCluster cluster) {
        if (!SpecialCraftingRuntime.isSpecialCluster(cluster) || !cluster.isBusy()) {
            return;
        }
        ISpecialCpuAccess acc = (ISpecialCpuAccess) (Object) cluster;
        IAEItemStack finalOutput = acc.ae2e$finalOutput();
        if (finalOutput == null) {
            return;
        }
        trySettle(cluster, acc, finalOutput, cluster.getActionSource());
    }

    /**
     * 任务是否全部推送完毕（含 value 已归零但尚未被 executeCrafting 移除的条目）.
     */
    private static boolean allTasksDone(ISpecialCpuAccess acc) {
        for (Object progress : acc.ae2e$tasks().values()) {
            if (((com.github.aeddddd.ae2enhanced.mixin.late.accessor.ITaskProgressAccessor) progress)
                    .ae2e$getValue() > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 收官结算:所有任务已推送且最终产出 key 无在途量时,从库存一次性交付.
     */
    private static void trySettle(CraftingCPUCluster cluster, ISpecialCpuAccess acc, IAEItemStack finalOutput,
            IActionSource src) {
        if (!allTasksDone(acc)) {
            return;
        }
        IAEItemStack inFlight = acc.ae2e$waitingFor().findPrecise(finalOutput);
        if (inFlight != null && inFlight.getStackSize() > 0) {
            return;
        }
        long remaining = finalOutput.getStackSize();
        if (remaining <= 0) {
            return;
        }
        IMEInventory<IAEItemStack> inventory = cluster.getInventory();
        IAEItemStack probe = finalOutput.copy();
        probe.setStackSize(Long.MAX_VALUE);
        IAEItemStack heldStack = inventory.extractItems(probe, Actionable.SIMULATE, src);
        long held = heldStack == null ? 0 : heldStack.getStackSize();
        long deliver = Math.min(remaining, held);
        if (deliver <= 0) {
            // 异常终态(产出丢失:机器 void/样板被破坏等)——与原生 stuck-craft 一致
            // 保持等待由玩家手动取消;告警限频 30s,避免 tickSettle 每 tick 刷屏
            long now = System.currentTimeMillis();
            Long lastWarn = LAST_STUCK_WARN.get(cluster);
            if (lastWarn == null || now - lastWarn >= STUCK_WARN_INTERVAL_MS) {
                LAST_STUCK_WARN.put(cluster, now);
                AE2Enhanced.LOGGER.warn("[特殊配方] 门控收官受阻: {} 待交付 {} 但 CPU 库存 {}(每 30s 重报,可手动取消任务)",
                        finalOutput, remaining, held);
            }
            return;
        }
        IAEItemStack deliverStack = finalOutput.copy();
        deliverStack.setStackSize(deliver);
        ICraftingLink link = acc.ae2e$myLastLink();
        long delivered = 0;
        // standalone(玩家终端提交)任务的原生 link 交付恒拒收;机器任务的 link 也可能已满.
        // 先用 SIMULATE 探测:可收则提取直付;拒收则一律留在 CPU 库存,由 completeJob→
        // storeItems 兜底送入网络存储(不能在此同步插网络:本方法运行在回流调用栈内,
        // 同步插入有重入风险).
        boolean linkAccepts = false;
        if (link != null) {
            IAEItemStack probeOne = finalOutput.copy();
            probeOne.setStackSize(1);
            IAEItemStack probeLeft = ((CraftingLink) link).injectItems(probeOne, Actionable.SIMULATE);
            linkAccepts = probeLeft == null || probeLeft.getStackSize() < 1;
        }
        if (linkAccepts) {
            inventory.extractItems(deliverStack.copy(), Actionable.MODULATE, src);
            IAEItemStack rejected = ((CraftingLink) link).injectItems(deliverStack, Actionable.MODULATE);
            delivered = deliver - (rejected == null ? 0 : rejected.getStackSize());
            if (rejected != null && rejected.getStackSize() > 0) {
                // 与原生一致:忽略 link 拒收余量,还回库存(执行结束随 storeItems 返回网络)
                inventory.injectItems(rejected, Actionable.MODULATE, src);
            }
        }
        // 拒收部分随 storeItems 入网络,语义上视为交付完成
        finalOutput.setStackSize(0);
        SpecialLog.info("[特殊配方] 门控收官: {} 直付 {},库存兜底 {}", finalOutput, delivered,
                deliver - delivered);
        acc.ae2e$completeJob();
        acc.ae2e$updateCPU();
    }
}
