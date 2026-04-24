package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cache.CraftingGridCache;
import appeng.api.networking.energy.IEnergyGrid;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public class MixinCraftingCPUCluster {

    // ---- 反射缓存 ----
    private static Field tasksField;
    private static Field remOpsField;
    private static Field remItemCountField;
    private static Field isCompleteField;
    private static Field waitingForField;
    private static Method postCraftingStatusChange;
    private static Method postChange;
    private static Field taskProgressValueField;
    private static Method completeJobMethod;
    private static boolean reflectionReady = false;
    private static boolean reflectionFailed = false;

    // ---- 诊断计数器 ----
    private static int batchCallCount = 0;
    private static int batchSuccessCount = 0;
    private static int batchFailCount = 0;

    private static void tryInitReflection() {
        if (reflectionReady || reflectionFailed) return;
        try {
            tasksField = CraftingCPUCluster.class.getDeclaredField("tasks");
            tasksField.setAccessible(true);
            remOpsField = CraftingCPUCluster.class.getDeclaredField("remainingOperations");
            remOpsField.setAccessible(true);
            remItemCountField = CraftingCPUCluster.class.getDeclaredField("remainingItemCount");
            remItemCountField.setAccessible(true);
            isCompleteField = CraftingCPUCluster.class.getDeclaredField("isComplete");
            isCompleteField.setAccessible(true);
            waitingForField = CraftingCPUCluster.class.getDeclaredField("waitingFor");
            waitingForField.setAccessible(true);
            postCraftingStatusChange = CraftingCPUCluster.class.getDeclaredMethod("postCraftingStatusChange", IAEItemStack.class);
            postCraftingStatusChange.setAccessible(true);
            postChange = CraftingCPUCluster.class.getDeclaredMethod("postChange", IAEItemStack.class, appeng.api.networking.security.IActionSource.class);
            postChange.setAccessible(true);
            Class<?> taskProgressClass = Class.forName("appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress");
            taskProgressValueField = taskProgressClass.getDeclaredField("value");
            taskProgressValueField.setAccessible(true);
            completeJobMethod = CraftingCPUCluster.class.getDeclaredMethod("completeJob");
            completeJobMethod.setAccessible(true);
            reflectionReady = true;
        } catch (Exception e) {
            reflectionFailed = true;
            AE2Enhanced.LOGGER.error("[AE2E] Mixin reflection init failed, batch crafting disabled. " +
                "This usually means AE2-UEL class/field names have changed. Details: {}", e.toString());
        }
    }

    /**
     * 在 updateCraftingLogic 开头检查：如果 tasks 已空但 isComplete 仍为 false，
     * 说明所有合成（包括被 AE2 正常处理的非虚拟配方）已完成，手动调用 completeJob() 结束任务。
     */
    @Inject(method = "updateCraftingLogic", at = @At("HEAD"))
    private void onUpdateCraftingLogicHead(IGrid grid, IEnergyGrid eg, CraftingGridCache cache, CallbackInfo ci) {
        if (reflectionFailed) return;
        try {
            tryInitReflection();
            if (!reflectionReady) return;
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            boolean isComplete = isCompleteField.getBoolean(cpu);

            if (!isComplete && tasks.isEmpty()) {
                completeJobMethod.invoke(cpu);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] onUpdateCraftingLogicHead unexpected error: {}", e.toString());
        }
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void batchProcessVirtualTasks(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        if (reflectionFailed) return;

        CraftingCPUCluster cpu = null;
        boolean anyOurTask = false;
        int virtualTasksFound = 0;
        int virtualTasksExecuted = 0;

        try {
            tryInitReflection();
            if (!reflectionReady) return;
            cpu = (CraftingCPUCluster) (Object) this;

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            if (tasks.isEmpty()) return;

            @SuppressWarnings("unchecked")
            IItemList<IAEItemStack> waitingFor = (IItemList<IAEItemStack>) waitingForField.get(cpu);

            // 循环遍历直到没有更多 entry 能被 batch，处理套娃合成的链式依赖
            boolean changed;
            int doWhileIterations = 0;
            do {
                changed = false;
                for (Map.Entry<ICraftingPatternDetails, Object> entry : new ArrayList<>(tasks.entrySet())) {
                    ICraftingPatternDetails details = entry.getKey();
                    Object progress = entry.getValue();

                    long remaining = taskProgressValueField.getLong(progress);
                    if (remaining <= 0) continue;

                    // 带有替代品的配方走原生路径，避免 SIMULATE 与模糊匹配不一致
                    if (details.canSubstitute()) continue;

                    List<ICraftingMedium> mediums = cache.getMediums(details);
                    if (mediums == null || mediums.isEmpty()) continue;

                    for (ICraftingMedium medium : mediums) {
                        if (!(medium instanceof TileAssemblyMeInterface)) continue;
                        anyOurTask = true;

                        TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                        if (controller == null || !controller.isVirtualPattern(details)) continue;

                        // 速度升级冷却检查：冷却未结束则跳过，留给下次 tick
                        if (!controller.canBatch()) continue;
                        virtualTasksFound++;

                        // 并行度限制：无限制时一次性处理全部，有限制时每批最多 cap 个
                        long cap = controller.getParallelCap();
                        long batchSize = (cap >= Long.MAX_VALUE / 2) ? remaining : Math.min(remaining, cap);

                        appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                        controller.setCurrentActionSource(source);
                        try {
                            // 直接操作 CPU inventory 的内部列表，保证嵌套配方时产物能被上层 canCraft() 识别
                            appeng.crafting.MECraftingInventory meInv = (appeng.crafting.MECraftingInventory) cpu.getInventory();
                            IItemList<IAEItemStack> itemList = meInv.getItemList();
                            appeng.api.config.Actionable SIMULATE = appeng.api.config.Actionable.SIMULATE;
                            appeng.api.config.Actionable MODULATE = appeng.api.config.Actionable.MODULATE;

                            // 1. SIMULATE 检查原料是否足够
                            boolean canExtract = true;
                            for (IAEItemStack inputTemplate : details.getCondensedInputs()) {
                                if (inputTemplate == null || inputTemplate.getStackSize() <= 0) continue;
                                long totalNeed = inputTemplate.getStackSize() * batchSize;
                                if (totalNeed <= 0) { canExtract = false; break; }
                                IAEItemStack need = inputTemplate.copy();
                                need.setStackSize(totalNeed);
                                IAEItemStack simResult = meInv.extractItems(need, SIMULATE, source);
                                if (simResult == null || simResult.getStackSize() < totalNeed) {
                                    canExtract = false;
                                    break;
                                }
                            }
                            if (!canExtract) {
                                continue; // 原料不足，留给 AE2 正常流程或下次循环
                            }

                            // 2. MODULATE 扣除原料并通知监听器
                            for (IAEItemStack inputTemplate : details.getCondensedInputs()) {
                                if (inputTemplate == null || inputTemplate.getStackSize() <= 0) continue;
                                long totalNeed = inputTemplate.getStackSize() * batchSize;
                                IAEItemStack need = inputTemplate.copy();
                                need.setStackSize(totalNeed);
                                IAEItemStack extracted = meInv.extractItems(need, MODULATE, source);
                                if (extracted != null && extracted.getStackSize() > 0) {
                                    IAEItemStack diff = extracted.copy();
                                    diff.setStackSize(-diff.getStackSize());
                                    postChange.invoke(cpu, diff, source);
                                    postCraftingStatusChange.invoke(cpu, diff);
                                }
                            }

                            // 3. 将产物加入 inventory 内部列表、清理 waitingFor 残留、通知监听器
                            long totalOutputItems = 0;
                            for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                                if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;
                                long totalCount = outputTemplate.getStackSize() * batchSize;
                                if (totalCount <= 0) continue;
                                totalOutputItems += totalCount;

                                IAEItemStack product = outputTemplate.copy();
                                product.setStackSize(totalCount);
                                itemList.add(product);
                                postChange.invoke(cpu, product.copy(), source);
                                postCraftingStatusChange.invoke(cpu, product.copy());

                                // 清理 waitingFor 中可能由之前回退到原生逻辑时添加的残留条目
                                if (waitingFor != null) {
                                    IAEItemStack waiting = waitingFor.findPrecise(outputTemplate);
                                    if (waiting != null) {
                                        waiting.decStackSize(totalCount);
                                        if (waiting.getStackSize() <= 0) {
                                            waiting.setStackSize(0);
                                        }
                                    }
                                }
                            }

                            // 4. 更新 taskProgress：直接改写 value，让原生 executeCrafting 在下一轮自动移除（value<=0）或继续处理
                            long newRemaining = remaining - batchSize;
                            taskProgressValueField.setLong(progress, newRemaining);

                            // 标记控制器为 busy，防止原生 executeCrafting 在同一 tick 内再次处理该 entry
                            controller.setBatchBusy(true);

                            changed = true;
                            virtualTasksExecuted++;
                            // batch 成功执行后重置冷却（由速度升级决定冷却时长）
                            controller.resetBatchCooldown();

                            AE2Enhanced.LOGGER.debug("[AE2E Batch] Processed {}x {} -> remaining={}",
                                batchSize, details.getOutput(null, null).getDisplayName(), newRemaining);
                        } finally {
                            controller.setCurrentActionSource(null);
                        }
                        break;
                    }
                }
                doWhileIterations++;
            } while (changed && doWhileIterations < 1000);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] batchProcessVirtualTasks unexpected error: {}", e.toString());
        } finally {
            batchCallCount++;
            if (virtualTasksExecuted > 0) {
                batchSuccessCount += virtualTasksExecuted;
                AE2Enhanced.LOGGER.info("[AE2E Batch] Executed {} virtual task(s) (found {} candidates, total calls={}, successes={})",
                    virtualTasksExecuted, virtualTasksFound, batchCallCount, batchSuccessCount);
            } else if (anyOurTask && batchCallCount % 20 == 1) {
                batchFailCount++;
                AE2Enhanced.LOGGER.debug("[AE2E Batch] Call #{}: found our tasks but executed 0 (candidates={}, fails={}). " +
                    "Possible causes: insufficient CPU inventory, pattern not cached as virtual, or canSubstitute.",
                    batchCallCount, virtualTasksFound, batchFailCount);
            }
        }
    }
}
