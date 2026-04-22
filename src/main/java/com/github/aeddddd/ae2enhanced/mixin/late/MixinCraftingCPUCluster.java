package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cache.CraftingGridCache;
import appeng.api.networking.energy.IEnergyGrid;
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
    private static Field meInventoryItemListField;
    private static boolean reflectionReady = false;

    private static void initReflection() throws Exception {
        if (reflectionReady) return;
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
        Class<?> meCraftingInvClass = Class.forName("appeng.crafting.MECraftingInventory");
        meInventoryItemListField = meCraftingInvClass.getDeclaredField("itemList");
        meInventoryItemListField.setAccessible(true);
        reflectionReady = true;
    }

    /**
     * 在 updateCraftingLogic 开头检查：如果 tasks 已空但 isComplete 仍为 false，
     * 说明所有合成（包括被 AE2 正常处理的非虚拟配方）已完成，手动调用 completeJob() 结束任务。
     */
    @Inject(method = "updateCraftingLogic", at = @At("HEAD"))
    private void onUpdateCraftingLogicHead(IGrid grid, IEnergyGrid eg, CraftingGridCache cache, CallbackInfo ci) {
        try {
            initReflection();
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            boolean isComplete = isCompleteField.getBoolean(cpu);

            if (!isComplete && tasks.isEmpty()) {
                completeJobMethod.invoke(cpu);
            }
        } catch (Exception e) {
            System.err.println("[AE2E] onUpdateCraftingLogicHead error: " + e);
            e.printStackTrace();
        }
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void batchProcessVirtualTasks(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        try {
            initReflection();
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            if (tasks.isEmpty()) return;

            long remainingItemCount = remItemCountField.getLong(cpu);
            int remainingOperations = remOpsField.getInt(cpu);
            @SuppressWarnings("unchecked")
            IItemList<IAEItemStack> waitingFor = (IItemList<IAEItemStack>) waitingForField.get(cpu);
            List<ICraftingPatternDetails> toRemove = new ArrayList<>();

            // 循环遍历直到没有更多 entry 能被 batch，处理套娃合成的链式依赖
            boolean changed;
            do {
                changed = false;
                for (Map.Entry<ICraftingPatternDetails, Object> entry : new ArrayList<>(tasks.entrySet())) {
                    if (toRemove.contains(entry.getKey())) continue;

                    ICraftingPatternDetails details = entry.getKey();
                    Object progress = entry.getValue();

                    long remaining = taskProgressValueField.getLong(progress);
                    if (remaining <= 0) continue;

                    // 跳过带有替代品的配方：AE2 原生 canCraft 对 canSubstitute 使用逐槽模糊匹配，
                    // 而我们的 SIMULATE 使用 condensedInputs 总量精确匹配，可能不一致导致误判
                    if (details.canSubstitute()) continue;

                    List<ICraftingMedium> mediums = cache.getMediums(details);
                    if (mediums == null || mediums.isEmpty()) continue;

                    for (ICraftingMedium medium : mediums) {
                        if (!(medium instanceof TileAssemblyMeInterface)) continue;

                        TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                        if (controller == null || !controller.isVirtualPattern(details)) continue;

                        appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                        controller.setCurrentActionSource(source);
                        try {
                            // 直接操作 CPU inventory 的内部列表，保证嵌套配方时产物能被上层 canCraft() 识别
                            IMEInventory<IAEItemStack> cpuInventory = cpu.getInventory();
                            @SuppressWarnings("unchecked")
                            IItemList<IAEItemStack> itemList = (IItemList<IAEItemStack>) meInventoryItemListField.get(cpuInventory);
                            appeng.api.config.Actionable SIMULATE = appeng.api.config.Actionable.SIMULATE;
                            appeng.api.config.Actionable MODULATE = appeng.api.config.Actionable.MODULATE;

                            // 1. SIMULATE 检查原料是否足够
                            boolean canExtract = true;
                            for (IAEItemStack inputTemplate : details.getCondensedInputs()) {
                                if (inputTemplate == null || inputTemplate.getStackSize() <= 0) continue;
                                long totalNeed = inputTemplate.getStackSize() * remaining;
                                if (totalNeed <= 0) { canExtract = false; break; }
                                IAEItemStack need = inputTemplate.copy();
                                need.setStackSize(totalNeed);
                                IAEItemStack simResult = cpuInventory.extractItems(need, SIMULATE, source);
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
                                long totalNeed = inputTemplate.getStackSize() * remaining;
                                IAEItemStack need = inputTemplate.copy();
                                need.setStackSize(totalNeed);
                                IAEItemStack extracted = cpuInventory.extractItems(need, MODULATE, source);
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
                                long totalCount = outputTemplate.getStackSize() * remaining;
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

                            // 4. 移除 entry 并同步计数器
                            toRemove.add(details);
                            remainingItemCount -= totalOutputItems;
                            int ops = (int) Math.min(remaining, Integer.MAX_VALUE);
                            if (remainingOperations > 0) {
                                remainingOperations = Math.max(0, remainingOperations - ops);
                            }
                            changed = true;
                        } finally {
                            controller.setCurrentActionSource(null);
                        }
                        break;
                    }
                }
            } while (changed);

            for (ICraftingPatternDetails key : toRemove) {
                tasks.remove(key);
            }

            if (remainingItemCount != remItemCountField.getLong(cpu)) {
                remItemCountField.setLong(cpu, remainingItemCount);
            }
            if (remainingOperations != remOpsField.getInt(cpu)) {
                remOpsField.setInt(cpu, remainingOperations);
            }
        } catch (Exception e) {
            System.err.println("[AE2E] batchProcessVirtualTasks error: " + e);
            e.printStackTrace();
        }
    }
}
