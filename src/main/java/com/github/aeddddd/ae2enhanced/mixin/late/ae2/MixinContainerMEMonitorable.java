package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.util.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

/**
 * 优化 ContainerMEMonitorable 的终端同步：在 func_75142_b 发送完成后清理 items 中的空 bucket,
 * 防止 ItemList 永久膨胀导致后续 tick 的 isEmpty() / 迭代产生不必要的开销.
 *
 * <p>本 Mixin 仅操作 {@link ContainerMEMonitorable#items}，且做以下防御：</p>
 * <ul>
 *   <li>若 items 与 monitor 的 storageList 是同一对象则跳过，避免误改网络缓存；</li>
 *   <li>每 20 tick 最多执行一次清理，降低并发修改风险；</li>
 *   <li>所有反射操作包裹 try-catch，失败时静默回退。</li>
 * </ul>
 */
@Mixin(value = ContainerMEMonitorable.class, remap = false)
public class MixinContainerMEMonitorable {

    @Shadow
    private IItemList<IAEItemStack> items;

    @Shadow
    private appeng.api.storage.IMEMonitor<IAEItemStack> monitor;

    private int ae2enhanced$cleanupCooldown = 0;

    @Inject(method = "func_75142_b", at = @At("TAIL"))
    private void ae2enhanced$cleanupItemList(CallbackInfo ci) {
        if (!Platform.isServer()) {
            return;
        }
        if (this.ae2enhanced$cleanupCooldown-- > 0) {
            return;
        }
        this.ae2enhanced$cleanupCooldown = 20;

        // 防御：永远不要清理网络 monitor 的 storageList
        if (this.monitor != null && this.items == this.monitor.getStorageList()) {
            return;
        }

        cleanupItemList(this.items);
    }

    private static void cleanupItemList(IItemList<IAEItemStack> itemList) {
        if (itemList == null) {
            return;
        }
        try {
            Object target = itemList;

            // Unwrap ItemListIgnoreCrafting if present
            if (target.getClass().getName().equals("appeng.util.inv.ItemListIgnoreCrafting")) {
                Field targetField = target.getClass().getDeclaredField("target");
                targetField.setAccessible(true);
                target = targetField.get(target);
            }

            if (!target.getClass().getName().equals("appeng.util.item.ItemList")) {
                return;
            }

            Field recordsField = target.getClass().getDeclaredField("records");
            recordsField.setAccessible(true);
            Map<?, ?> records = (Map<?, ?>) recordsField.get(target);
            if (records == null || records.isEmpty()) {
                return;
            }

            Method getRecordsMethod = null;
            Iterator<?> it = records.values().iterator();
            while (it.hasNext()) {
                Object variantList = it.next();
                if (getRecordsMethod == null) {
                    getRecordsMethod = variantList.getClass().getDeclaredMethod("getRecords");
                    getRecordsMethod.setAccessible(true);
                }
                Map<?, ?> variantRecords = (Map<?, ?>) getRecordsMethod.invoke(variantList);
                if (variantRecords != null && variantRecords.isEmpty()) {
                    it.remove();
                }
            }
        } catch (Exception e) {
            // 清理失败时不应崩溃，静默回退。
            // MeaningfulItemIterator 已经能跳过零数量条目，空 bucket 只影响极端情况下的性能。
        }
    }
}
