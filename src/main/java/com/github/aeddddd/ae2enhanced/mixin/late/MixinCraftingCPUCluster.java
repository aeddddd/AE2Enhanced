package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface;
import net.minecraft.inventory.InventoryCrafting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;

@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public class MixinCraftingCPUCluster {

    /**
     * 拦截 CraftingCPUCluster.executeCrafting() 中对 pushPattern 的调用。
     * 如果 provider 是 Assembly Hub 且样板已缓存为虚拟合成，
     * 尝试批量执行剩余全部份数。
     */
    private static boolean debugInjectLogged = false;
    private static int debugWrapCount = 0;

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void onExecuteCrafting(CallbackInfo ci) {
        if (!debugInjectLogged) {
            debugInjectLogged = true;
            System.out.println("[AE2E-DEBUG] MixinCraftingCPUCluster.executeCrafting inject WORKING");
        }
    }

    @WrapOperation(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"
            )
    )
    private boolean wrapPushPattern(ICraftingMedium provider, ICraftingPatternDetails details, InventoryCrafting table, Operation<Boolean> original) {
        debugWrapCount++;
        if (provider instanceof TileAssemblyMeInterface) {
            TileAssemblyController controller = ((TileAssemblyMeInterface) provider).getController();
            if (controller != null) {
                CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
                appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                controller.setCurrentActionSource(source);
                try {
                    boolean isVirtual = controller.isVirtualPattern(details);
                    long remaining = getRemainingValue(details);
                    if (debugWrapCount <= 3) {
                        System.out.println("[AE2E-DEBUG] wrapPushPattern #" + debugWrapCount
                            + " isVirtual=" + isVirtual + " remaining=" + remaining
                            + " provider=" + provider.getClass().getName());
                    }
                    if (isVirtual && remaining > 0) {
                        boolean success = controller.executeBatch(details, remaining);
                        System.out.println("[AE2E-DEBUG] BATCH #" + debugWrapCount + ": remaining=" + remaining + " success=" + success);
                        if (success) {
                            setRemainingValue(details, 0);
                            return true;
                        }
                    }
                    return original.call(provider, details, table);
                } finally {
                    controller.setCurrentActionSource(null);
                }
            }
        }
        return original.call(provider, details, table);
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

}
