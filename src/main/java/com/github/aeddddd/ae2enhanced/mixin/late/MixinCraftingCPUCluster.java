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

    private static boolean debugLogged = false;

    /**
     * 在 executeCrafting 开头直接批量处理所有虚拟合成任务。
     * 由于 @At(INVOKE) 在运行时无法匹配 pushPattern 调用点，
     * 改为在方法头遍历 tasks map，直接完成 batch 注入并同步 AE2 内部状态。
     */
    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void batchProcessVirtualTasks(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        try {
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;

            // 反射获取 tasks
            Field tasksField = CraftingCPUCluster.class.getDeclaredField("tasks");
            tasksField.setAccessible(true);
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);

            // 反射获取 waitingFor
            Field waitingForField = CraftingCPUCluster.class.getDeclaredField("waitingFor");
            waitingForField.setAccessible(true);
            Object waitingFor = waitingForField.get(cpu);
            Method waitingForAdd = waitingFor.getClass().getMethod("addStorage", Object.class);

            // 反射获取 remainingOperations
            Field remOpsField = CraftingCPUCluster.class.getDeclaredField("remainingOperations");
            remOpsField.setAccessible(true);
            int remainingOps = remOpsField.getInt(cpu);

            // 反射获取 postCraftingStatusChange
            Method postChangeMethod = CraftingCPUCluster.class.getDeclaredMethod("postCraftingStatusChange", IAEItemStack.class);
            postChangeMethod.setAccessible(true);

            // 遍历所有任务，批量处理虚拟合成
            for (Map.Entry<ICraftingPatternDetails, Object> entry : new ArrayList<>(tasks.entrySet())) {
                ICraftingPatternDetails details = entry.getKey();
                Object progress = entry.getValue();

                Field valueField = progress.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                long remaining = valueField.getLong(progress);
                if (remaining <= 0) continue;

                // 获取该 pattern 对应的 mediums
                List<ICraftingMedium> mediums = cache.getMediums(details);
                if (mediums == null || mediums.isEmpty()) continue;

                for (ICraftingMedium medium : mediums) {
                    if (!(medium instanceof TileAssemblyMeInterface)) continue;

                    TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                    if (controller == null || !controller.isVirtualPattern(details)) continue;

                    // 使用 CraftingCPU 的 ActionSource 注入，使 AE2 能正确追踪产物
                    appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                    controller.setCurrentActionSource(source);
                    try {
                        boolean success = controller.executeBatch(details, remaining);
                        if (success) {
                            // 将 TaskProgress.value 设为 0，让 executeCrafting 后续逻辑跳过该任务
                            valueField.setLong(progress, 0);

                            // 同步 remainingOperations
                            remainingOps -= remaining;

                            // 同步 waitingFor：添加所有预期产出
                            for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                                if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;
                                IAEItemStack expected = outputTemplate.copy();
                                expected.setStackSize(outputTemplate.getStackSize() * remaining);
                                waitingForAdd.invoke(waitingFor, expected);
                                postChangeMethod.invoke(cpu, expected.copy());
                            }

                            if (!debugLogged) {
                                debugLogged = true;
                                System.out.println("[AE2E] BATCH success: remaining=" + remaining + " tasks=" + tasks.size());
                            }
                        }
                    } finally {
                        controller.setCurrentActionSource(null);
                    }
                    break; // 该 pattern 已处理，不需要尝试其他 medium
                }
            }

            remOpsField.setInt(cpu, remainingOps);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
