package com.github.aeddddd.ae2enhanced.specialcrafting;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;

import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCpuLogicAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ElapsedTimeTrackerAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ExecutingCraftingJobAccessor;

/**
 * 自消耗 job 的最终产出交付门控（执行层）.
 * <p><b>问题</b>:原生 {@code CraftingCpuLogic.insert} 中,任何匹配 finalOutput 的回流
 * 物品会立即经 {@code job.link.insert} 交付并扣减 remainingAmount.自引用/循环链计划中
 * 请求物既是产出又是输入——种子产出的第一批物品被直接交付而非喂给下一份合成,
 * 导致链条饿死:无法并行爬坡、多轮中断、任务永远无法完成.</p>
 * <p><b>门控策略</b>:若 job 为"自消耗"（最终产出 key 仍是某任务样板的输入,
 * 由任务集现场推导,普通计划判定为 false 时零开销零影响）,回流的最终产出先存入
 * CPU 库存（喂给后续合成）;当所有任务已推送且该 key 无在途量时,一次性从库存
 * 交付 remainingAmount 并完成 job（语义与原生一致:忽略 link 拒收余量,
 * 余量（种子）由 {@code finishJob → storeItems} 返回网络）.</p>
 * <p><b>滞留识别</b>:任务推送完毕后 isSelfConsuming 不再可推导（任务集已空）,
 * 此时以"CPU 库存中已滞留 finalOutput key"作为曾被门控的判据——原生路径从不把
 * 最终产出放进 CPU 库存,该判据无歧义.</p>
 */
public final class SelfRefOutputGate {

    private SelfRefOutputGate() {
    }

    /**
     * 门控 {@code CraftingCpuLogic.insert}.
     *
     * @return null = 不接管（走原生）;否则为接受量（调用方 setReturnValue）.
     */
    @Nullable
    public static Long handleInsert(CraftingCpuLogic logic, AEKey what, long amount, Actionable type) {
        var logicAcc = (CraftingCpuLogicAccessor) logic;
        var job = logicAcc.getJob();
        if (job == null || what == null) {
            return null;
        }
        var jobAcc = (ExecutingCraftingJobAccessor) job;
        var finalOutput = jobAcc.getFinalOutput();
        if (finalOutput == null || !what.matches(finalOutput)) {
            return null;
        }

        var waitingForInv = jobAcc.getWaitingFor();
        var inventory = logic.getInventory();
        boolean selfConsuming = consumesFinalOutput(jobAcc, finalOutput.what());
        boolean hasRetained = inventory.extract(what, Long.MAX_VALUE, Actionable.SIMULATE) > 0;
        if (!selfConsuming && !hasRetained) {
            return null; // 非自消耗 job 且无门控滞留:原生路径
        }

        // 与原生一致的 waitingFor 记账与限量
        long waitingFor = waitingForInv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0L; // 无在途等待:与原生一致拒收(机器超产等)
        }
        long accepted = Math.min(amount, waitingFor);
        if (type == Actionable.MODULATE) {
            ((ElapsedTimeTrackerAccessor) jobAcc.getTimeTracker()).invokeDecrementItems(accepted, what.getType());
            waitingForInv.extract(what, accepted, Actionable.MODULATE);
            logicAcc.getCluster().markDirty();
            // 门控核心:最终产出先入 CPU 库存(喂给后续合成),而非直接交付
            inventory.insert(what, accepted, Actionable.MODULATE);
            trySettle(logic, logicAcc, jobAcc, what);
        }
        return accepted;
    }

    /**
     * 收官结算:所有任务已推送且最终产出 key 无在途量时,从库存一次性交付.
     */
    private static void trySettle(CraftingCpuLogic logic, CraftingCpuLogicAccessor logicAcc,
            ExecutingCraftingJobAccessor jobAcc, AEKey what) {
        if (!jobAcc.getTasks().isEmpty()) {
            return;
        }
        if (jobAcc.getWaitingFor().extract(what, Long.MAX_VALUE, Actionable.SIMULATE) > 0) {
            return;
        }
        long remaining = jobAcc.getRemainingAmount();
        if (remaining <= 0) {
            return;
        }
        var inventory = logic.getInventory();
        long deliver = Math.min(remaining, inventory.extract(what, Long.MAX_VALUE, Actionable.SIMULATE));
        if (deliver <= 0) {
            return;
        }
        inventory.extract(what, deliver, Actionable.MODULATE);
        jobAcc.getLink().insert(what, deliver, Actionable.MODULATE);
        // 与原生一致:忽略 link 拒收余量,直接按已交付扣减
        long newRemaining = Math.max(0, remaining - deliver);
        jobAcc.setRemainingAmount(newRemaining);
        var cluster = logicAcc.getCluster();
        if (newRemaining <= 0) {
            logicAcc.invokeFinishJob(true);
            cluster.updateOutput(null);
        } else {
            cluster.updateOutput(new GenericStack(what, newRemaining));
        }
    }

    /**
     * job 是否自消耗:最终产出 key 仍是某任务样板的（模糊匹配的）输入.
     */
    private static boolean consumesFinalOutput(ExecutingCraftingJobAccessor jobAcc, AEKey out) {
        for (var pattern : jobAcc.getTasks().keySet()) {
            for (var input : pattern.getInputs()) {
                for (var possible : input.getPossibleInputs()) {
                    if (out.matches(possible)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
