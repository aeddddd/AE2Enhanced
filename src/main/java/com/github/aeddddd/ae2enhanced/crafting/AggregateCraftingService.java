package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerNull;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.compat.MMCEAdditionReflect;
import com.github.aeddddd.ae2enhanced.crafting.compat.RCIntelligentBlockingReflect;
import com.github.aeddddd.ae2enhanced.craftingapi.SpecialCraftingPorts;
import com.github.aeddddd.ae2enhanced.observation.Observations;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CPU 聚合发配的普通 Java 实现。 */
public final class AggregateCraftingService {

    private static final Map<String, Long> DEBUG_THROTTLE = new HashMap<>();
    private static final boolean DISABLE_AGGREGATE = Boolean.getBoolean("ae2e.disableAggregate");

    private AggregateCraftingService() {
    }

    public static void begin(CraftingCpuExecutionContext context) {
        context.clearQuotaPushedThisTick();
        if (Observations.isEnabled("perf")) {
            Observations.Timer timer = Observations.timer("ae2.craftingcpu.executeCrafting");
            context.executeStartNanos(timer.shouldSample() ? System.nanoTime() : -1L);
        } else {
            context.executeStartNanos(-1L);
        }
    }

    public static void end(CraftingCpuExecutionContext context) {
        if (context.executeStartNanos() >= 0L) {
            Observations.timer("ae2.craftingcpu.executeCrafting")
                    .record(System.nanoTime() - context.executeStartNanos());
            context.executeStartNanos(-1L);
        }
    }

    public static boolean dispatch(CraftingCpuAdapter cpu, boolean coreCluster, ICraftingMedium medium,
            ICraftingPatternDetails details, InventoryCrafting table, IEnergyGrid energy,
            ICreativeEnergyAccess creative, PushCall original, PushCall extractionFallback, PushCall scaledPush) {
        CraftingCpuExecutionContext context = cpu.context();
        long quotaAllowance = Long.MAX_VALUE;
        boolean quotaTracked = false;
        if (context.specialMarked()) {
            Map<ICraftingPatternDetails, Long> snapshot = context.remainingSnapshot();
            if (snapshot != null) {
                long allowance = SpecialCraftingPorts.get().pushAllowance(context, details, cpu.finalOutput(), snapshot);
                if (allowance != Long.MAX_VALUE) {
                    quotaTracked = true;
                    long pushed = context.quotaPushedThisTick() == null ? 0L
                            : context.quotaPushedThisTick().getOrDefault(details, 0L);
                    quotaAllowance = allowance - pushed;
                }
            }
        }
        long batch = 1;
        if (!DISABLE_AGGREGATE && !details.isCraftable()) {
            try {
                if (isBatchTarget(medium)) {
                    batch = computeBatch(cpu, details, table, medium);
                }
            } catch (Throwable throwable) {
                AE2Enhanced.LOGGER.error("[AE2E] batch aggregate check failed: {}", throwable.toString(), throwable);
                batch = 1;
            }
        }
        if (batch > 1) {
            if (!coreCluster) {
                batch = Math.min(batch, Math.max(1L, cpu.remainingOperations()));
            }
            Long ceiling = context.batchCeilings().get(medium);
            if (ceiling != null) {
                batch = Math.min(batch, ceiling);
            }
            if (quotaAllowance != Long.MAX_VALUE) {
                batch = Math.min(batch, Math.max(1L, quotaAllowance));
            }
        }

        double perSet = 0;
        boolean creativeEnergy = energy != null && creative.ae2e$hasCreativeEnergy(energy);
        if (batch > 1 && !creativeEnergy) {
            for (IAEItemStack input : details.getInputs()) {
                if (input != null) {
                    perSet += (double) input.getStackSize();
                }
            }
            if (perSet > 0 && energy != null) {
                double affordable = energy.extractAEPower(perSet * (batch - 1), Actionable.SIMULATE,
                        PowerMultiplier.CONFIG);
                long affordableBatch = 1 + (long) (affordable / perSet);
                if (affordableBatch < batch) {
                    batch = affordableBatch;
                }
            }
        }
        if (batch <= 1) {
            return original.call(medium, details, table);
        }

        IMEInventory<IAEItemStack> inventory = cpu.inventory();
        List<IAEItemStack> extras = new ArrayList<>();
        boolean extractedAll = true;
        for (IAEItemStack input : details.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack need = input.copy();
            need.setStackSize(Math.multiplyExact(input.getStackSize(), batch - 1));
            IAEItemStack got = inventory.extractItems(need, Actionable.MODULATE, cpu.machineSource());
            long gotSize = got == null ? 0 : got.getStackSize();
            if (gotSize > 0) {
                extras.add(got);
                cpu.access().ae2e$postChange(got.copy(), cpu.machineSource());
            }
            if (gotSize < need.getStackSize()) {
                extractedAll = false;
                break;
            }
        }
        if (!extractedAll) {
            for (IAEItemStack extra : extras) {
                inventory.injectItems(extra, Actionable.MODULATE, cpu.machineSource());
            }
            debug(medium, "extras-extraction-shortfall");
            return extractionFallback.call(medium, details, table);
        }

        if (!creativeEnergy && perSet > 0 && energy != null) {
            energy.extractAEPower(perSet * (batch - 1), Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
        boolean result = scaledPush.call(medium, details, scaleTable(table, batch));
        if (!result) {
            context.batchCeilings().put(medium, Math.max(1L, batch / 2));
            for (IAEItemStack extra : extras) {
                inventory.injectItems(extra, Actionable.MODULATE, cpu.machineSource());
            }
            debug(medium, "push-failed batch=" + batch);
            return false;
        }

        if (!coreCluster) {
            cpu.remainingOperations(cpu.remainingOperations()
                    - (int) Math.min(batch - 1, cpu.remainingOperations()));
        }
        Long ceiling = context.batchCeilings().get(medium);
        if (ceiling != null && batch >= ceiling && ceiling < (1L << 60)) {
            context.batchCeilings().put(medium, Math.min(ceiling * 2, 1L << 60));
        }
        if (quotaTracked) {
            context.recordQuotaPushed(details, batch);
        }
        correctCounts(cpu, details, batch - 1);
        return true;
    }

    private static boolean isBatchTarget(ICraftingMedium medium) {
        if (MMCEAdditionReflect.isBatchablePatternProvider(medium)
                || MMCEAdditionReflect.isPatternAssembly(medium)) {
            return true;
        }
        DualityInterface duality;
        if (medium instanceof DualityInterface) {
            duality = (DualityInterface) medium;
        } else if (medium instanceof IInterfaceHost) {
            duality = ((IInterfaceHost) medium).getInterfaceDuality();
        } else {
            return false;
        }
        if (duality == null) {
            debug(medium, "duality-null");
            return false;
        }
        boolean allowed = duality.getConfigManager().getSetting(Settings.BLOCK) != YesNo.YES
                || RCIntelligentBlockingReflect.isIntelligentBlockingOpen(duality);
        if (!allowed) {
            debug(medium, "blocking-on-without-intelligent-blocking");
        }
        return allowed;
    }

    private static long computeBatch(CraftingCpuAdapter cpu, ICraftingPatternDetails details,
            InventoryCrafting table, ICraftingMedium medium) {
        if (cpu.tasks().get(details) == null) {
            debug(medium, "no-task-progress");
            return 1;
        }
        long remaining = cpu.taskRemaining(details);
        if (remaining <= 1) {
            return 1;
        }
        long batch = remaining;
        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (!stack.isEmpty()) {
                long perSlot = effectiveSlotAmount(stack);
                if (perSlot > 0) {
                    batch = Math.min(batch, Integer.MAX_VALUE / perSlot);
                }
            }
        }
        if (batch <= 1) {
            return 1;
        }
        IMEInventory<IAEItemStack> inventory = cpu.inventory();
        for (IAEItemStack input : details.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            batch = Math.min(batch, Long.MAX_VALUE / input.getStackSize());
            if (batch <= 1) return 1;
            IAEItemStack probe = input.copy();
            probe.setStackSize(Math.multiplyExact(input.getStackSize(), batch));
            IAEItemStack available = inventory.extractItems(probe, Actionable.SIMULATE, cpu.machineSource());
            long availableSize = available == null ? 0 : available.getStackSize();
            batch = Math.min(batch, availableSize / input.getStackSize());
            if (batch <= 1) {
                debug(medium, "materials-insufficient per=" + input.getStackSize() + " avail=" + availableSize);
                return 1;
            }
        }
        for (IAEItemStack output : details.getCondensedOutputs()) {
            if (output != null && output.getStackSize() > 0) {
                batch = Math.min(batch, Long.MAX_VALUE / output.getStackSize());
                if (batch <= 1) return 1;
            }
        }
        return batch;
    }

    private static long effectiveSlotAmount(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            if (tag.hasKey("FluidStack", 10)) {
                return tag.getCompoundTag("FluidStack").getInteger("Amount");
            }
            if (tag.hasKey("GasStack", 10)) {
                return tag.getCompoundTag("GasStack").getInteger("amount");
            }
        }
        return stack.getCount();
    }

    private static InventoryCrafting scaleTable(InventoryCrafting table, long batch) {
        int size = table.getSizeInventory();
        int dimension = (int) Math.ceil(Math.sqrt(size));
        dimension = Math.max(3, Math.min(10, dimension));
        InventoryCrafting scaled = new InventoryCrafting(new ContainerNull(), dimension, dimension);
        for (int i = 0; i < size && i < scaled.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            if (!scalePacketAmount(copy, batch)) {
                copy.setCount((int) Math.multiplyExact(stack.getCount(), batch));
            }
            scaled.setInventorySlotContents(i, copy);
        }
        return scaled;
    }

    private static boolean scalePacketAmount(ItemStack stack, long batch) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return false;
        }
        if (tag.hasKey("FluidStack", 10)) {
            NBTTagCompound fluid = tag.getCompoundTag("FluidStack");
            long amount = Math.multiplyExact((long) fluid.getInteger("Amount"), batch);
            fluid.setInteger("Amount", (int) amount);
            return true;
        }
        if (tag.hasKey("GasStack", 10)) {
            NBTTagCompound gas = tag.getCompoundTag("GasStack");
            long amount = Math.multiplyExact((long) gas.getInteger("amount"), batch);
            gas.setInteger("amount", (int) amount);
            return true;
        }
        return false;
    }

    private static void correctCounts(CraftingCpuAdapter cpu, ICraftingPatternDetails details, long extra) {
        cpu.correctTaskRemaining(details, extra);
        if (cpu.waitingFor() == null) {
            return;
        }
        for (IAEItemStack output : details.getCondensedOutputs()) {
            if (output == null || output.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack extraOutput = output.copy();
            extraOutput.setStackSize(Math.multiplyExact(output.getStackSize(), extra));
            cpu.access().ae2e$postChange(extraOutput.copy(), cpu.machineSource());
            cpu.waitingFor().add(extraOutput);
            cpu.access().ae2e$postCraftingStatusChange(extraOutput.copy());
        }
    }

    private static void debug(ICraftingMedium medium, String reason) {
        if (!Observations.isEnabled("batchaggregate")) {
            return;
        }
        String key = (medium == null ? "null" : medium.getClass().getName()) + "|" + reason;
        long now = System.currentTimeMillis();
        synchronized (DEBUG_THROTTLE) {
            Long last = DEBUG_THROTTLE.get(key);
            if (last != null && now - last < 5000) {
                return;
            }
            DEBUG_THROTTLE.put(key, now);
        }
        AE2Enhanced.LOGGER.info("[AE2E] batch aggregate debug: medium={} reason={}",
                medium == null ? "null" : medium.getClass().getName(), reason);
    }

    @FunctionalInterface
    public interface PushCall {
        boolean call(ICraftingMedium medium, ICraftingPatternDetails details, InventoryCrafting table);
    }
}
