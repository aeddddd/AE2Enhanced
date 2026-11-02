package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuAccess;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuPort;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuState;

import javax.annotation.Nullable;

/** 自引用 job 的最终产出交付门控。 */
public final class SelfRefOutputGate {

    public static GateResult handleInsert(CraftingCpuPort cpu, IAEItemStack input, Actionable type,
            IActionSource src) {
        if (input == null || !cpu.context().specialMarked()) {
            return NOT_HANDLED;
        }
        CraftingCpuAccess access = cpu.access();
        if (!isBusyReadOnly(access)) {
            return NOT_HANDLED;
        }
        IAEItemStack finalOutput = access.ae2e$finalOutput();
        if (finalOutput == null || !finalOutput.equals(input)) {
            return NOT_HANDLED;
        }
        IItemList<IAEItemStack> waitingFor = access.ae2e$waitingFor();
        IAEItemStack waiting = waitingFor.findPrecise(input);
        if (waiting == null || waiting.getStackSize() <= 0) {
            return NOT_HANDLED;
        }
        long accept = Math.min(waiting.getStackSize(), input.getStackSize());
        if (type == Actionable.SIMULATE) {
            IAEItemStack leftover = input.copy();
            leftover.decStackSize(accept);
            return new GateResult(true, leftover.getStackSize() > 0 ? leftover : null);
        }

        IAEItemStack what = input.copy();
        access.ae2e$postChange(what, src);
        waiting.decStackSize(accept);
        IAEItemStack acceptedStack = what.copy();
        acceptedStack.setStackSize(accept);
        access.ae2e$updateRemainingItemCount(acceptedStack);
        access.ae2e$markDirtyCluster();
        IAEItemStack statusDiff = acceptedStack.copy();
        statusDiff.setStackSize(-accept);
        access.ae2e$postCraftingStatusChange(statusDiff);

        IMEInventory<IAEItemStack> inventory = access.ae2e$inventory();
        inventory.injectItems(acceptedStack, Actionable.MODULATE, src);
        IAEItemStack leftover = what.copy();
        leftover.decStackSize(accept);
        trySettle(access, cpu.context(), finalOutput, src);
        return new GateResult(true, leftover.getStackSize() > 0 ? leftover : null);
    }

    private static final GateResult NOT_HANDLED = new GateResult(false, null);
    private static final long STUCK_WARN_INTERVAL_MS = 30_000L;

    private SelfRefOutputGate() {
    }

    public static GateResult handleInsert(CraftingCPUCluster cluster, IAEItemStack input, Actionable type,
            IActionSource src) {
        return handleInsert(CraftingCpuPort.of(cluster), input, type, src);
    }

    public static void tickSettle(CraftingCpuPort cpu, IActionSource source) {
        if (!cpu.context().specialMarked() || !isBusyReadOnly(cpu.access())) {
            return;
        }
        IAEItemStack finalOutput = cpu.access().ae2e$finalOutput();
        if (finalOutput != null) {
            trySettle(cpu.access(), cpu.context(), finalOutput, source);
        }
    }

    public static void tickSettle(CraftingCPUCluster cluster) {
        CraftingCpuPort cpu = CraftingCpuPort.of(cluster);
        tickSettle(cpu, cluster.getActionSource());
    }

    private static boolean isBusyReadOnly(CraftingCpuAccess access) {
        if (!access.ae2e$waitingFor().isEmpty()) {
            return true;
        }
        return !allTasksDone(access);
    }

    private static boolean allTasksDone(CraftingCpuAccess access) {
        for (Object progress : access.ae2e$tasks().values()) {
            if (access.ae2e$taskValue(progress) > 0) {
                return false;
            }
        }
        return true;
    }

    private static void trySettle(CraftingCpuAccess access, CraftingCpuState context,
            IAEItemStack finalOutput, IActionSource src) {
        if (!allTasksDone(access)) {
            return;
        }
        IAEItemStack inFlight = access.ae2e$waitingFor().findPrecise(finalOutput);
        if (inFlight != null && inFlight.getStackSize() > 0) {
            return;
        }
        long remaining = finalOutput.getStackSize();
        if (remaining <= 0) {
            return;
        }
        IMEInventory<IAEItemStack> inventory = access.ae2e$inventory();
        IAEItemStack probe = finalOutput.copy();
        probe.setStackSize(Long.MAX_VALUE);
        IAEItemStack heldStack = inventory.extractItems(probe, Actionable.SIMULATE, src);
        long held = heldStack == null ? 0 : heldStack.getStackSize();
        if (held < remaining) {
            long now = System.currentTimeMillis();
            if (now - context.lastStuckWarning() >= STUCK_WARN_INTERVAL_MS) {
                context.lastStuckWarning(now);
                AE2Enhanced.LOGGER.warn("[特殊配方] 门控收官受阻: {} 待交付 {} 但 CPU 库存 {}(每 30s 重报,可手动取消任务)",
                        finalOutput, remaining, held);
            }
            return;
        }
        long deliver = remaining;
        IAEItemStack deliverStack = finalOutput.copy();
        deliverStack.setStackSize(deliver);
        ICraftingLink link = access.ae2e$myLastLink();
        long delivered = 0;
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
                inventory.injectItems(rejected, Actionable.MODULATE, src);
            }
        }
        finalOutput.setStackSize(0);
        SpecialLog.info("[特殊配方] 门控收官: {} 直付 {},库存兜底 {}", finalOutput, delivered,
                deliver - delivered);
        access.ae2e$completeJob();
        access.ae2e$updateCPU();
    }

    public static final class GateResult extends com.github.aeddddd.ae2enhanced.craftingapi.CraftingGateResult {
        private GateResult(boolean handled, @Nullable IAEItemStack leftover) {
            super(handled, leftover);
        }
    }

}
