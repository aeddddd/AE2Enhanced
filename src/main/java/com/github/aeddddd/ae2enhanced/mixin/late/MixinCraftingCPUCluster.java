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

    // ---- 日志频率控制 ----
    private static boolean batchLogged = false;
    private static long lastErrorLog = 0;
    private static final long ERROR_LOG_COOLDOWN_MS = 5000;

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

        // TaskProgress.value 字段
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
            if (tasks.isEmpty()) return;

            Object waitingFor = waitingForField.get(cpu);
            Method waitingForAdd = getWaitingForAdd(waitingFor);
            int remainingOps = remOpsField.getInt(cpu);

            boolean anyBatch = false;

            for (Map.Entry<ICraftingPatternDetails, Object> entry : new ArrayList<>(tasks.entrySet())) {
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
                            taskProgressValueField.setLong(progress, 0);
                            remainingOps -= remaining;

                            for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                                if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;
                                IAEItemStack expected = outputTemplate.copy();
                                expected.setStackSize(outputTemplate.getStackSize() * remaining);
                                waitingForAdd.invoke(waitingFor, expected);
                                postChangeMethod.invoke(cpu, expected.copy());
                            }

                            anyBatch = true;
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

            if (anyBatch && !batchLogged) {
                batchLogged = true;
                System.out.println("[AE2E] Batch crafting activated for virtual patterns");
            }
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastErrorLog > ERROR_LOG_COOLDOWN_MS) {
                lastErrorLog = now;
                System.err.println("[AE2E] batchProcessVirtualTasks error (suppressing for 5s): " + e);
                e.printStackTrace();
            }
        }
    }
}
