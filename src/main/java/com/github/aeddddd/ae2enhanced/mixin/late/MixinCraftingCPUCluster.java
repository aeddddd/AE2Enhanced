package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
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
    private static Method postCraftingStatusChange;
    private static Method postChange;
    private static Field taskProgressValueField;
    private static Method completeJobMethod;
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
                System.out.println("[AE2E] completeJob() invoked via updateCraftingLogic (tasks empty)");
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

                    List<ICraftingMedium> mediums = cache.getMediums(details);
                    if (mediums == null || mediums.isEmpty()) continue;

                    for (ICraftingMedium medium : mediums) {
                        if (!(medium instanceof TileAssemblyMeInterface)) continue;

                        TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                        if (controller == null || !controller.isVirtualPattern(details)) continue;

                        appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                        controller.setCurrentActionSource(source);
                        try {
                            boolean success = controller.executeBatch(details, remaining);
                            if (success) {
                                toRemove.add(details);
                                remainingItemCount -= remaining;
                                changed = true;

                                for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                                    if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;
                                    IAEItemStack expected = outputTemplate.copy();
                                    expected.setStackSize(outputTemplate.getStackSize() * remaining);

                                    // 通知监听器（终端等）网络物品变化
                                    postChange.invoke(cpu, expected.copy(), source);
                                    // 通知 crafting grid 状态变化
                                    postCraftingStatusChange.invoke(cpu, expected.copy());
                                }

                                System.out.println("[AE2E] BATCH: removed task remaining=" + remaining);
                            }
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
        } catch (Exception e) {
            System.err.println("[AE2E] batchProcessVirtualTasks error: " + e);
            e.printStackTrace();
        }
    }
}
