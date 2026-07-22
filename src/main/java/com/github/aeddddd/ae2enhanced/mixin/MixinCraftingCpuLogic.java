package com.github.aeddddd.ae2enhanced.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.client.gui.GuiConstants;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCpuLogicAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ElapsedTimeTrackerAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ExecutingCraftingJobAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.TaskProgressAccessor;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity;
import com.github.aeddddd.ae2enhanced.util.MathUtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 {@link CraftingCpuLogic#executeCrafting} 头部注入装配枢纽的批量合成处理,
 * 复刻主分支 1.12 对装配枢纽任务的虚拟/真实轨道分批处理.
 * <p>记账方式严格对齐 AE2 1.20.1 原生语义：凡是经网络回流的产物（最终产物、容器物）
 * 都先登记进 {@code job.waitingFor},再由控制器注入 ME 网络,经 CraftingService 路由回
 * {@link CraftingCpuLogic#insert} 完成 waitingFor 核销、remainingAmount 扣减与 finishJob；
 * 立即可用的中间产物与返还的催化剂直接放入 CPU 库存并同步 timeTracker.
 * 只有订单完成路径走通,CPU 才不会永久卡死.</p>
 * <p>批量失败（原料不足等）时不设置 batchBusy,让原生逐份路径自然回退.</p>
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
            GenericStack finalOutput = jobAccessor.getFinalOutput();

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
                            AssemblyControllerBlockEntity.PatternBatchInfo info = hub.getPatternBatchInfo(details);
                            boolean done;
                            if (info.virtual) {
                                done = ae2e$processVirtualBatch(cluster, hub, details, inventory, waitingFor,
                                        timeTracker, finalOutput, progress, remaining, batchSize, level);
                            } else {
                                done = ae2e$processRealBatch(cluster, hub, details, inventory, waitingFor,
                                        timeTracker, progress, remaining, batchSize, level);
                            }
                            if (done) {
                                changed = true;
                                hub.setBatchBusy(true);
                                hub.resetBatchCooldown();
                            }
                            // 批量失败不设置 batchBusy,让原生逐份 pushPattern 路径自然回退
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

    /**
     * 虚拟轨道：无剩余物的样板（普通合成、处理样板）.
     * <p>原料从 CPU 库存批量扣除；最终产物登记 waitingFor 后放入枢纽缓冲,
     * 由控制器注入网络完成原生记账；中间产物直接放入 CPU 库存供嵌套配方使用.</p>
     */
    @Unique
    private boolean ae2e$processVirtualBatch(CraftingCPUCluster cluster, AssemblyControllerBlockEntity hub,
            IPatternDetails details, ListCraftingInventory inventory, ListCraftingInventory waitingFor,
            ElapsedTimeTracker timeTracker, GenericStack finalOutput, Object progress, long remaining,
            long batchSize, Level level) {
        try {
            IPatternDetails.IInput[] inputs = details.getInputs();
            if (inputs == null) {
                return false;
            }

            // 1) 逐槽匹配实际可用的模板 key（与原生 getValidItemTemplates 相同的模糊匹配）
            AEKey[] keys = new AEKey[inputs.length];
            long[] fixed = new long[inputs.length];
            long[] per = new long[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] == null) {
                    continue;
                }
                GenericStack template = ae2e$matchTemplate(inventory, inputs[i], level);
                if (template == null || template.amount() <= 0) {
                    return false;
                }
                keys[i] = template.what();
                per[i] = MathUtils.safeMultiply(template.amount(), inputs[i].getMultiplier());
                if (per[i] <= 0) {
                    return false;
                }
            }

            // 2) 预估网络缓冲占用（仅最终产物走缓冲）
            int networkStacks = 0;
            for (GenericStack output : details.getOutputs()) {
                if (output != null && output.amount() > 0 && finalOutput != null && output.what().matches(finalOutput)) {
                    networkStacks++;
                }
            }
            if (networkStacks > 0 && !hub.canAcceptRealBatch(networkStacks)) {
                return false;
            }

            // 3) 同 key 合并校验可用量,不足则缩减批量（避免同 key 多槽重复计数）
            long actual = ae2e$shrinkToAvailable(inventory, keys, fixed, per, batchSize);
            if (actual <= 0) {
                return false;
            }

            // 4) 一次性扣料
            ae2e$extractMerged(inventory, keys, fixed, per, actual);

            // 5) 产物交付
            // 递归合成（产物同时是原料,如 A+2B=2A）时,先计算本批次消耗的该 key 数量：
            // 等量产物回留 CPU 库存作为下一批次的种子,只有净产出才经网络回流记账,
            // 否则第一批后 CPU 库存耗尽,后续批次将永远缺料卡死.
            long consumedSelf = 0;
            if (finalOutput != null) {
                for (int i = 0; i < keys.length; i++) {
                    if (keys[i] != null && keys[i].equals(finalOutput.what())) {
                        consumedSelf += per[i] * actual;
                    }
                }
            }
            for (GenericStack output : details.getOutputs()) {
                if (output == null || output.amount() <= 0) {
                    continue;
                }
                long total = MathUtils.safeMultiply(output.amount(), actual);
                if (total <= 0) {
                    continue;
                }
                if (finalOutput != null && output.what().matches(finalOutput)) {
                    // 仅当本样板还有后续批次时才回留种子；最后一批全部经网络回流,
                    // 种子随净产出一并自动返回网络,避免残留在 CPU 库存中
                    boolean moreRuns = remaining - actual > 0;
                    long retain = moreRuns ? Math.min(consumedSelf, total) : 0;
                    long net = total - retain;
                    if (retain > 0) {
                        // 种子回留 CPU 库存,维持递归合成链
                        inventory.insert(output.what(), retain, Actionable.MODULATE);
                    }
                    if (net > 0) {
                        // 净产出：登记 waitingFor,注入网络时由 CraftingCpuLogic.insert 核销并完成订单
                        waitingFor.insert(output.what(), net, Actionable.MODULATE);
                        hub.addPendingOutput(new GenericStack(output.what(), net));
                    }
                } else {
                    // 中间产物：直接进入 CPU 库存,嵌套配方立即可用
                    inventory.insert(output.what(), total, Actionable.MODULATE);
                    ae2e$decrementItems(timeTracker, total, output.what().getType());
                }
            }

            ae2e$setTaskValue(progress, remaining - actual);
            cluster.markDirty();
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error(GuiConstants.LOGGER_PREFIX + " Virtual batch failed", e);
            return false;
        }
    }

    /**
     * 真实轨道：存在剩余物的合成样板（容器物、催化剂、耐久转换）.
     * <p>剩余物按实际提取的 key 逐项判定：真催化剂（剩余物与输入完全一致）借用 1 份并立即
     * 返还 CPU 库存；消耗性转换（同物品不同 NBT,如耐久损耗）强制逐份处理；普通容器物
     * 按批量产出.产物与容器物一律登记 waitingFor 后经网络回流完成原生记账.</p>
     */
    @Unique
    private boolean ae2e$processRealBatch(CraftingCPUCluster cluster, AssemblyControllerBlockEntity hub,
            IPatternDetails details, ListCraftingInventory inventory, ListCraftingInventory waitingFor,
            ElapsedTimeTracker timeTracker, Object progress, long remaining, long batchSize, Level level) {
        try {
            IPatternDetails.IInput[] inputs = details.getInputs();
            if (inputs == null) {
                return false;
            }

            // 1) 逐槽匹配模板并按实际 key 分类剩余物
            AEKey[] keys = new AEKey[inputs.length];
            AEKey[] remainders = new AEKey[inputs.length];
            long[] containersPerCraft = new long[inputs.length];
            boolean[] catalyst = new boolean[inputs.length];
            boolean[] transform = new boolean[inputs.length];
            boolean hasTransform = false;
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] == null) {
                    continue;
                }
                GenericStack template = ae2e$matchTemplate(inventory, inputs[i], level);
                if (template == null || template.amount() <= 0) {
                    return false;
                }
                AEKey key = template.what();
                keys[i] = key;
                containersPerCraft[i] = inputs[i].getMultiplier();
                AEKey rem = inputs[i].getRemainingKey(key);
                remainders[i] = rem;
                if (rem != null) {
                    if (rem.equals(key)) {
                        // 真催化剂：不消耗,仅借用 1 份
                        catalyst[i] = true;
                    } else if (rem instanceof AEItemKey remItem && key instanceof AEItemKey inItem
                            && remItem.getItem() == inItem.getItem()) {
                        // 消耗性转换（如耐久损耗）：同物品不同 NBT,禁止批量
                        transform[i] = true;
                        hasTransform = true;
                    }
                }
            }
            if (hasTransform) {
                batchSize = 1;
            }

            // 2) 计算每槽需求量：催化剂/转换槽固定 1 份,其余按批量
            long[] fixed = new long[inputs.length];
            long[] per = new long[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                if (keys[i] == null) {
                    continue;
                }
                if (catalyst[i] || transform[i]) {
                    fixed[i] = 1;
                } else {
                    GenericStack template = ae2e$matchTemplate(inventory, inputs[i], level);
                    if (template == null) {
                        return false;
                    }
                    per[i] = MathUtils.safeMultiply(template.amount(), inputs[i].getMultiplier());
                    if (per[i] <= 0) {
                        return false;
                    }
                }
            }

            // 3) 预估缓冲占用：产物 + 经网络回流的剩余物（催化剂直返库存不占缓冲）
            int stacks = 0;
            for (GenericStack output : details.getOutputs()) {
                if (output != null && output.amount() > 0) {
                    stacks++;
                }
            }
            for (int i = 0; i < inputs.length; i++) {
                if (remainders[i] != null && !catalyst[i]) {
                    stacks++;
                }
            }
            if (!hub.canAcceptRealBatch(stacks)) {
                return false;
            }

            // 4) 缩减并扣料
            long actual = ae2e$shrinkToAvailable(inventory, keys, fixed, per, batchSize);
            if (actual <= 0) {
                return false;
            }
            ae2e$extractMerged(inventory, keys, fixed, per, actual);

            // 5) 产物：登记 waitingFor 后进缓冲,注入网络时由 CPU insert 核销并完成订单
            for (GenericStack output : details.getOutputs()) {
                if (output == null || output.amount() <= 0) {
                    continue;
                }
                long total = MathUtils.safeMultiply(output.amount(), actual);
                if (total <= 0) {
                    continue;
                }
                waitingFor.insert(output.what(), total, Actionable.MODULATE);
                hub.addPendingOutput(new GenericStack(output.what(), total));
            }

            // 6) 剩余物
            for (int i = 0; i < inputs.length; i++) {
                if (remainders[i] == null || keys[i] == null) {
                    continue;
                }
                if (catalyst[i]) {
                    // 真催化剂：借用后立即返还 CPU 库存
                    inventory.insert(keys[i], 1, Actionable.MODULATE);
                    ae2e$decrementItems(timeTracker, 1, keys[i].getType());
                } else if (transform[i]) {
                    // 消耗性转换：逐份产出转换后物品（actual 已被强制为 1）
                    waitingFor.insert(remainders[i], 1, Actionable.MODULATE);
                    hub.addPendingOutput(new GenericStack(remainders[i], 1));
                } else {
                    // 普通容器物：每份输入模板留下 1 个,按批量产出
                    long total = MathUtils.safeMultiply(containersPerCraft[i], actual);
                    if (total > 0) {
                        waitingFor.insert(remainders[i], total, Actionable.MODULATE);
                        hub.addPendingOutput(new GenericStack(remainders[i], total));
                    }
                }
            }

            ae2e$setTaskValue(progress, remaining - actual);
            cluster.markDirty();
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error(GuiConstants.LOGGER_PREFIX + " Real batch failed", e);
            return false;
        }
    }

    /**
     * 为输入槽匹配实际可用的模板：遍历候选物品,按 IGNORE_ALL 模糊匹配 CPU 库存中
     * 实际存在的 key（与原生 {@code CraftingCpuHelper#getValidItemTemplates} 一致）.
     * 返回的 GenericStack 数量为该候选模板的单份数量（actualKey + possibleAmount）.
     */
    @Unique
    private static GenericStack ae2e$matchTemplate(ListCraftingInventory inventory, IPatternDetails.IInput input,
            Level level) {
        for (GenericStack possible : input.getPossibleInputs()) {
            for (AEKey fuzz : inventory.findFuzzyTemplates(possible.what())) {
                if (input.isValid(fuzz, level)) {
                    return new GenericStack(fuzz, possible.amount());
                }
            }
        }
        return null;
    }

    /**
     * 同 key 合并需求后校验 CPU 库存,返回可执行的最大批量.
     * fixed 为不随批量变化的需求（催化剂/转换槽）,per 为单份批量需求.
     */
    @Unique
    private static long ae2e$shrinkToAvailable(ListCraftingInventory inventory, AEKey[] keys, long[] fixed,
            long[] per, long batchSize) {
        Map<AEKey, long[]> merged = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == null) {
                continue;
            }
            long[] acc = merged.computeIfAbsent(keys[i], k -> new long[2]);
            acc[0] += fixed[i];
            acc[1] += per[i];
        }
        long actual = batchSize;
        for (Map.Entry<AEKey, long[]> e : merged.entrySet()) {
            long f = e.getValue()[0];
            long p = e.getValue()[1];
            long need = ae2e$safeAdd(f, MathUtils.safeMultiply(p, actual));
            long available = inventory.extract(e.getKey(), need, Actionable.SIMULATE);
            if (available < need) {
                if (p <= 0) {
                    return 0;
                }
                long max = (available - f) / p;
                if (max <= 0) {
                    return 0;
                }
                actual = Math.min(actual, max);
            }
        }
        return actual;
    }

    /**
     * 按合并后的需求一次性从 CPU 库存扣料.
     */
    @Unique
    private static void ae2e$extractMerged(ListCraftingInventory inventory, AEKey[] keys, long[] fixed, long[] per,
            long batchSize) {
        Map<AEKey, Long> merged = new LinkedHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == null) {
                continue;
            }
            long need = ae2e$safeAdd(fixed[i], MathUtils.safeMultiply(per[i], batchSize));
            merged.merge(keys[i], need, Long::sum);
        }
        for (Map.Entry<AEKey, Long> e : merged.entrySet()) {
            inventory.extract(e.getKey(), e.getValue(), Actionable.MODULATE);
        }
    }

    @Unique
    private static long ae2e$safeAdd(long a, long b) {
        if (a >= Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    @Unique
    private static void ae2e$decrementItems(ElapsedTimeTracker tracker, long amount, appeng.api.stacks.AEKeyType type) {
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
