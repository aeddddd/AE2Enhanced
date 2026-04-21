package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface;
import net.minecraft.inventory.InventoryCrafting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public class MixinCraftingCPUCluster {

    /**
     * 拦截 CraftingCPUCluster.executeCrafting() 中对 pushPattern 的调用。
     * 如果 provider 是 Assembly Hub 且样板已缓存为虚拟合成，
     * 尝试批量执行剩余全部份数。
     */
    @Redirect(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"
            )
    )
    private boolean redirectPushPattern(ICraftingMedium provider, ICraftingPatternDetails details, InventoryCrafting table) {
        if (provider instanceof TileAssemblyMeInterface) {
            TileAssemblyController controller = ((TileAssemblyMeInterface) provider).getController();
            if (controller != null && controller.isVirtualPattern(details)) {
                long remaining = getRemainingValue(details);
                if (remaining > 0) {
                    boolean success = controller.executeBatch(details, remaining);
                    if (success) {
                        setRemainingValue(details, 0);
                        addExpectedOutputsToWaitingFor(details, remaining);
                        return true;
                    }
                }
            }
        }
        // 回退到标准 pushPattern（1 份）
        return provider.pushPattern(details, table);
    }

    /**
     * 通过反射获取 CraftingCPUCluster.tasks 中指定 pattern 的剩余份数。
     */
    @SuppressWarnings("unchecked")
    private long getRemainingValue(ICraftingPatternDetails details) {
        try {
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
            Field tasksField = CraftingCPUCluster.class.getDeclaredField("tasks");
            tasksField.setAccessible(true);
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);

            Object progress = tasks.get(details);
            if (progress != null) {
                Field valueField = progress.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                return valueField.getLong(progress);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * 通过反射设置 CraftingCPUCluster.tasks 中指定 pattern 的剩余份数。
     */
    @SuppressWarnings("unchecked")
    private void setRemainingValue(ICraftingPatternDetails details, long value) {
        try {
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
            Field tasksField = CraftingCPUCluster.class.getDeclaredField("tasks");
            tasksField.setAccessible(true);
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);

            Object progress = tasks.get(details);
            if (progress != null) {
                Field valueField = progress.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                valueField.setLong(progress, value);
            }
        } catch (Exception ignored) {}
    }

    /**
     * 将预期产物添加到 CraftingCPUCluster.waitingFor，使 AE2 能正确追踪任务完成。
     */
    private void addExpectedOutputsToWaitingFor(ICraftingPatternDetails details, long batchSize) {
        try {
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;

            // 获取 waitingFor 字段
            Field waitingForField = CraftingCPUCluster.class.getDeclaredField("waitingFor");
            waitingForField.setAccessible(true);
            Object waitingFor = waitingForField.get(cpu);

            // 获取 postCraftingStatusChange 方法
            Method postMethod = CraftingCPUCluster.class.getDeclaredMethod("postCraftingStatusChange", IAEItemStack.class);
            postMethod.setAccessible(true);

            // 获取 add 方法（IItemList.add）
            Method addMethod = null;
            for (Method m : waitingFor.getClass().getMethods()) {
                if (m.getName().equals("add") && m.getParameterCount() == 1) {
                    addMethod = m;
                    break;
                }
            }
            if (addMethod == null) return;

            for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;

                long totalCount = outputTemplate.getStackSize() * batchSize;
                if (totalCount <= 0) continue;

                IAEItemStack expected = outputTemplate.copy();
                expected.setStackSize(totalCount);

                // 添加到 waitingFor
                addMethod.invoke(waitingFor, expected);
                // 通知 AE2 状态变化
                postMethod.invoke(cpu, expected.copy());
            }
        } catch (Exception ignored) {}
    }
}
