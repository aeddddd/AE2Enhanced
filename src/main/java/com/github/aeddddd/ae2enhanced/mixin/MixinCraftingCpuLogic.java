package com.github.aeddddd.ae2enhanced.mixin;

import java.util.ArrayList;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.client.gui.GuiConstants;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCpuLogicAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ExecutingCraftingJobAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ElapsedTimeTrackerAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.TaskProgressAccessor;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity;
import com.github.aeddddd.ae2enhanced.util.MathUtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 {@link CraftingCpuLogic#executeCrafting} 头部注入批量处理逻辑，
 * 完整复刻主分支对装配枢纽任务的虚拟/真实合成分批处理。
 * <p>处理完成后，原方法继续执行；已完成的装配枢纽任务 value 已被扣减至 0，
 * 原方法会跳过这些任务，从而避免重复处理。</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public class MixinCraftingCpuLogic {

    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false)
    private void ae2e$batchProcessAssemblyHubTasks(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        try {
            CraftingCpuLogic logic = (CraftingCpuLogic) (Object) this;
            CraftingCpuLogicAccessor logicAccessor = (CraftingCpuLogicAccessor) logic;
            CraftingCPUCluster cluster = logicAccessor.getCluster();
            ExecutingCraftingJob job = logicAccessor.getJob();
            if (job == null) {
                return;
            }
            ExecutingCraftingJobAccessor jobAccessor = (ExecutingCraftingJobAccessor) job;
            @SuppressWarnings("unchecked")
            Map<IPatternDetails, ?> tasks = (Map<IPatternDetails, ?>) jobAccessor.getTasks();
            ListCraftingInventory inventory = logic.getInventory();
            ListCraftingInventory waitingFor = jobAccessor.getWaitingFor();
            ElapsedTimeTracker timeTracker = jobAccessor.getTimeTracker();

            boolean changed;
            int iterations = 0;
            do {
                changed = false;
                for (Map.Entry<IPatternDetails, ?> entry : new ArrayList<>(tasks.entrySet())) {
                    IPatternDetails details = entry.getKey();
                    Object progress = entry.getValue();
                    long remaining = ae2e$getTaskValue(progress);
                    if (remaining <= 0) {
                        continue;
                    }

                    Iterable<ICraftingProvider> providers = craftingService.getProviders(details);
                    if (providers == null) {
                        continue;
                    }

                    for (ICraftingProvider provider : providers) {
                        AssemblyControllerBlockEntity hub = null;
                        if (provider instanceof AssemblyControllerBlockEntity controller) {
                            hub = controller;
                        } else if (provider instanceof MultiblockMeInterfaceBlockEntity meInterface) {
                            var controller = meInterface.getController();
                            if (controller instanceof AssemblyControllerBlockEntity) {
                                hub = (AssemblyControllerBlockEntity) controller;
                            }
                        }
                        if (hub == null) {
                            continue;
                        }
                        if (!hub.isFormed() || !hub.canBatch()) {
                            continue;
                        }

                        long cap = hub.getParallelCap();
                        long batchSize = (cap >= Long.MAX_VALUE / 2) ? remaining : Math.min(remaining, cap);
                        if (batchSize <= 0) {
                            continue;
                        }

                        hub.setCurrentActionSource(cluster.getSrc());
                        try {
                            AssemblyControllerBlockEntity.PatternBatchInfo info = hub.getPatternBatchInfo(details, inventory, cluster.getSrc());
                            if (info == null) {
                                continue;
                            }
                            boolean isVirtual = info.recipe == null || hub.isVirtualPattern(details);

                            if (isVirtual) {
                                if (ae2e$processVirtualBatch(logic, cluster, hub, details, inventory, waitingFor, timeTracker, progress, remaining, batchSize)) {
                                    changed = true;
                                }
                            } else {
                                if (info.transformSlots != null && info.transformSlots.cardinality() > 0) {
                                    batchSize = 1;
                                }
                                int estimatedStacks = 1;
                                for (int i = 0; i < info.slotTemplates.length; i++) {
                                    if (info.slotTemplates[i] != null && (info.catalystSlots == null || !info.catalystSlots.get(i))) {
                                        estimatedStacks++;
                                    }
                                }
                                if (!hub.canAcceptRealBatch(estimatedStacks)) {
                                    continue;
                                }
                                if (ae2e$processRealBatch(logic, cluster, hub, details, inventory, waitingFor, timeTracker, info, progress, remaining, batchSize, level)) {
                                    changed = true;
                                }
                            }
                            hub.setBatchBusy(true);
                            hub.resetBatchCooldown();
                        } finally {
                            hub.setCurrentActionSource(null);
                        }
                        break;
                    }
                }
                iterations++;
            } while (changed && iterations < GuiConstants.MAX_BATCH_ITERATIONS);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error(GuiConstants.LOGGER_PREFIX + " batchProcessAssemblyHubTasks failed", e);
        }
    }

    @Unique
    private boolean ae2e$processVirtualBatch(CraftingCpuLogic logic, CraftingCPUCluster cluster,
            AssemblyControllerBlockEntity hub, IPatternDetails details, ListCraftingInventory inventory,
            ListCraftingInventory waitingFor, ElapsedTimeTracker timeTracker, Object progress, long remaining,
            long batchSize) {
        try {
            IPatternDetails.IInput[] inputs = details.getInputs();
            if (inputs == null) {
                return false;
            }
            for (IPatternDetails.IInput input : inputs) {
                if (input == null) {
                    continue;
                }
                GenericStack[] possible = input.getPossibleInputs();
                if (possible.length == 0) {
                    continue;
                }
                AEKey key = possible[0].what();
                long perCraft = possible[0].amount();
                long totalNeed = MathUtils.safeMultiply(perCraft, batchSize);
                if (totalNeed <= 0) {
                    return false;
                }
                long available = inventory.extract(key, totalNeed, Actionable.SIMULATE);
                if (available < totalNeed) {
                    return false;
                }
            }
            for (IPatternDetails.IInput input : inputs) {
                if (input == null) {
                    continue;
                }
                GenericStack[] possible = input.getPossibleInputs();
                if (possible.length == 0) {
                    continue;
                }
                AEKey key = possible[0].what();
                long perCraft = possible[0].amount();
                long totalNeed = MathUtils.safeMultiply(perCraft, batchSize);
                inventory.extract(key, totalNeed, Actionable.MODULATE);
            }

            long totalOutputItems = 0;
            for (GenericStack output : details.getOutputs()) {
                if (output == null || output.amount() <= 0) {
                    continue;
                }
                long totalCount = MathUtils.safeMultiply(output.amount(), batchSize);
                if (totalCount <= 0) {
                    continue;
                }
                totalOutputItems += totalCount;
                inventory.insert(output.what(), totalCount, Actionable.MODULATE);
                waitingFor.extract(output.what(), totalCount, Actionable.MODULATE);
                ae2e$decrementItems(timeTracker, totalCount, output.what().getType());
            }

            ae2e$setTaskValue(progress, remaining - batchSize);
            ae2e$decrementRemainingAmount(details, totalOutputItems);
            cluster.markDirty();
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error(GuiConstants.LOGGER_PREFIX + " Virtual batch failed", e);
            return false;
        }
    }

    @Unique
    private boolean ae2e$processRealBatch(CraftingCpuLogic logic, CraftingCPUCluster cluster,
            AssemblyControllerBlockEntity hub, IPatternDetails details, ListCraftingInventory inventory,
            ListCraftingInventory waitingFor, ElapsedTimeTracker timeTracker,
            AssemblyControllerBlockEntity.PatternBatchInfo info, Object progress, long remaining, long batchSize,
            Level level) {
        try {
            IPatternDetails.IInput[] inputs = details.getInputs();
            if (inputs == null || info.slotTemplates == null) {
                return false;
            }

            long actualBatchSize = batchSize;

            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] == null || info.slotTemplates[i] == null) {
                    continue;
                }
                AEKey key = info.slotTemplates[i];
                long perCraft = inputs[i].getPossibleInputs()[0].amount();
                long needCount = (info.catalystSlots != null && info.catalystSlots.get(i)) || (info.transformSlots != null && info.transformSlots.get(i))
                        ? 1
                        : MathUtils.safeMultiply(perCraft, actualBatchSize);
                long available = inventory.extract(key, needCount, Actionable.SIMULATE);
                if (available < needCount) {
                    if (actualBatchSize > 1) {
                        long maxBatch = available / perCraft;
                        if (maxBatch <= 0) {
                            return false;
                        }
                        actualBatchSize = maxBatch;
                    } else {
                        return false;
                    }
                }
            }
            if (actualBatchSize <= 0) {
                return false;
            }

            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] == null || info.slotTemplates[i] == null) {
                    continue;
                }
                AEKey key = info.slotTemplates[i];
                long perCraft = inputs[i].getPossibleInputs()[0].amount();
                long needCount = (info.catalystSlots != null && info.catalystSlots.get(i)) || (info.transformSlots != null && info.transformSlots.get(i))
                        ? 1
                        : MathUtils.safeMultiply(perCraft, actualBatchSize);
                inventory.extract(key, needCount, Actionable.MODULATE);
            }

            NonNullList<ItemStack> craftItems = NonNullList.withSize(9, ItemStack.EMPTY);
            for (int i = 0; i < info.slotTemplates.length && i < 9; i++) {
                if (info.slotTemplates[i] instanceof AEItemKey itemKey) {
                    craftItems.set(i, itemKey.toStack());
                }
            }
            TransientCraftingContainer container = new TransientCraftingContainer(null, 3, 3);
            for (int i = 0; i < craftItems.size(); i++) {
                container.setItem(i, craftItems.get(i));
            }
            ItemStack output = info.recipe.assemble(container, level.registryAccess());
            NonNullList<ItemStack> remainingItems = info.recipe.getRemainingItems(container);

            long totalOutputItems = 0;
            if (!output.isEmpty()) {
                ItemStack batchOutput = output.copy();
                long count = MathUtils.safeMultiply(output.getCount(), actualBatchSize);
                if (count > Integer.MAX_VALUE) {
                    count = Integer.MAX_VALUE;
                }
                batchOutput.setCount((int) count);
                hub.addPendingOutput(batchOutput);
                AEKey outputKey = AEItemKey.of(batchOutput);
                if (outputKey != null) {
                    waitingFor.extract(outputKey, count, Actionable.MODULATE);
                    ae2e$decrementItems(timeTracker, count, outputKey.getType());
                }
                totalOutputItems += count;
            }
            for (int i = 0; i < remainingItems.size(); i++) {
                ItemStack rem = remainingItems.get(i);
                if (rem.isEmpty()) {
                    continue;
                }
                if (info.catalystSlots != null && info.catalystSlots.get(i)) {
                    AEKey key = info.slotTemplates[i];
                    if (key != null) {
                        inventory.insert(key, 1, Actionable.MODULATE);
                    }
                } else {
                    long count = MathUtils.safeMultiply(rem.getCount(), actualBatchSize);
                    if (count > Integer.MAX_VALUE) {
                        count = Integer.MAX_VALUE;
                    }
                    ItemStack batchRem = rem.copy();
                    batchRem.setCount((int) count);
                    hub.addPendingOutput(batchRem);
                    AEKey remKey = AEItemKey.of(batchRem);
                    if (remKey != null) {
                        waitingFor.extract(remKey, count, Actionable.MODULATE);
                        ae2e$decrementItems(timeTracker, count, remKey.getType());
                    }
                    totalOutputItems += count;
                }
            }

            ae2e$setTaskValue(progress, remaining - actualBatchSize);
            ae2e$decrementRemainingAmount(details, totalOutputItems);
            cluster.markDirty();
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error(GuiConstants.LOGGER_PREFIX + " Real batch failed", e);
            return false;
        }
    }

    @Unique
    private void ae2e$decrementRemainingAmount(IPatternDetails details, long amount) {
        CraftingCpuLogic logic = (CraftingCpuLogic) (Object) this;
        CraftingCpuLogicAccessor logicAccessor = (CraftingCpuLogicAccessor) logic;
        ExecutingCraftingJob job = logicAccessor.getJob();
        if (job == null) {
            return;
        }
        ExecutingCraftingJobAccessor jobAccessor = (ExecutingCraftingJobAccessor) job;
        GenericStack finalOutput = jobAccessor.getFinalOutput();
        if (finalOutput == null) {
            return;
        }
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.amount() <= 0) {
                continue;
            }
            if (output.what().equals(finalOutput.what())) {
                long current = jobAccessor.getRemainingAmount();
                long decrement = Math.min(amount, current);
                jobAccessor.setRemainingAmount(current - decrement);
                break;
            }
        }
    }

    @Unique
    private static void ae2e$decrementItems(ElapsedTimeTracker tracker, long amount, AEKeyType type) {
        ((ElapsedTimeTrackerAccessor) tracker).invokeDecrementItems(amount, type);
    }

    @Unique
    private static long ae2e$getTaskValue(Object progress) {
        return ((TaskProgressAccessor) progress).getValue();
    }

    @Unique
    private static void ae2e$setTaskValue(Object progress, long value) {
        ((TaskProgressAccessor) progress).setValue(value);
    }
}
