package com.github.aeddddd.ae2enhanced.mixin.late;

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
    private static Field waitingForField;
    private static Field remOpsField;
    private static Method postChangeMethod;
    private static Field taskProgressValueField;
    private static Method waitingForAddMethod;
    private static boolean reflectionReady = false;

    // ---- 调试日志控制 ----
    private static int debugLogCount = 0;
    private static final int MAX_DEBUG_LOGS = 30;

    private static void debugLog(String msg) {
        if (debugLogCount < MAX_DEBUG_LOGS) {
            debugLogCount++;
            System.out.println("[AE2E-DEBUG] " + msg);
        }
    }

    private static void initReflection() throws Exception {
        if (reflectionReady) return;
        tasksField = CraftingCPUCluster.class.getDeclaredField("tasks");
        tasksField.setAccessible(true);
        waitingForField = CraftingCPUCluster.class.getDeclaredField("waitingFor");
        waitingForField.setAccessible(true);
        remOpsField = CraftingCPUCluster.class.getDeclaredField("remainingOperations");
        remOpsField.setAccessible(true);
        postChangeMethod = CraftingCPUCluster.class.getDeclaredMethod("postCraftingStatusChange", IAEItemStack.class);
        postChangeMethod.setAccessible(true);
        Class<?> taskProgressClass = Class.forName("appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress");
        taskProgressValueField = taskProgressClass.getDeclaredField("value");
        taskProgressValueField.setAccessible(true);
        reflectionReady = true;
    }

    private static Method getWaitingForAdd(Object waitingFor) throws NoSuchMethodException {
        if (waitingForAddMethod == null) {
            waitingForAddMethod = waitingFor.getClass().getMethod("addStorage", IAEItemStack.class);
        }
        return waitingForAddMethod;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void batchProcessVirtualTasks(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        try {
            initReflection();
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;

            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            debugLog("tasks.size=" + tasks.size());
            if (tasks.isEmpty()) return;

            Object waitingFor = waitingForField.get(cpu);
            Method waitingForAdd = getWaitingForAdd(waitingFor);
            int remainingOps = remOpsField.getInt(cpu);

            for (Map.Entry<ICraftingPatternDetails, Object> entry : new ArrayList<>(tasks.entrySet())) {
                ICraftingPatternDetails details = entry.getKey();
                Object progress = entry.getValue();
                long remaining = taskProgressValueField.getLong(progress);
                debugLog("task remaining=" + remaining);
                if (remaining <= 0) continue;

                List<ICraftingMedium> mediums = cache.getMediums(details);
                debugLog("mediums.size=" + (mediums == null ? 0 : mediums.size()));
                if (mediums == null || mediums.isEmpty()) continue;

                for (ICraftingMedium medium : mediums) {
                    debugLog("medium class=" + medium.getClass().getName());
                    if (!(medium instanceof TileAssemblyMeInterface)) continue;

                    TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                    debugLog("controller=" + controller + " pos=" + (controller != null ? controller.getPos() : null));
                    if (controller == null) continue;

                    boolean isVirtual = controller.isVirtualPattern(details);
                    debugLog("isVirtual=" + isVirtual);
                    if (!isVirtual) continue;

                    appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                    controller.setCurrentActionSource(source);
                    try {
                        boolean success = controller.executeBatch(details, remaining);
                        debugLog("executeBatch remaining=" + remaining + " success=" + success);
                        if (success) {
                            taskProgressValueField.setLong(progress, 0);
                            remainingOps -= remaining;

                            for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                                if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;
                                IAEItemStack expected = outputTemplate.copy();
                                expected.setStackSize(outputTemplate.getStackSize() * remaining);
                                waitingForAdd.invoke(waitingFor, expected);
                                postChangeMethod.invoke(cpu, expected.copy());
                            }

                            System.out.println("[AE2E] BATCH success: remaining=" + remaining);
                        }
                    } finally {
                        controller.setCurrentActionSource(null);
                    }
                    break;
                }
            }

            if (remainingOps != remOpsField.getInt(cpu)) {
                remOpsField.setInt(cpu, remainingOps);
            }
        } catch (Exception e) {
            debugLog("EXCEPTION: " + e.getClass().getName() + " " + e.getMessage());
            e.printStackTrace();
        }
    }
}
