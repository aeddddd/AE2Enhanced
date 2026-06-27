package com.github.aeddddd.ae2enhanced.util;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.networking.security.IActionSource;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import net.minecraft.item.ItemStack;

/**
 * E2b-2：通用物品推送帮助类.
 * 封装“从网络提取并插入目标容器”的完整事务流程.
 */
public final class ItemPushHelper {

    private ItemPushHelper() {}

    /**
     * 将指定物品从网络推送到目标容器.
     *
     * @param adaptor 目标容器的 InventoryAdaptor
     * @param energy  能量网格(用于驱动提取)
     * @param inv     网络物品存储
     * @param org     要推送的物品原型(含目标类型与数量信息)
     * @param maxSend 最大推送数量
     * @param source  动作来源
     * @return 实际推送的数量
     */
    public static long pushItemIntoTarget(InventoryAdaptor adaptor, IEnergyGrid energy,
                                          IMEMonitor<IAEItemStack> inv, IAEItemStack org,
                                          long maxSend, IActionSource source) {
        ItemStack inputStack = org.getCachedItemStack(org.getStackSize());
        ItemStack remaining = adaptor.simulateAdd(inputStack);
        if (!remaining.isEmpty()) {
            org.setCachedItemStack(remaining);
            if (remaining == inputStack) {
                return 0;
            }
        }
        long canFit = Math.min(maxSend, org.getStackSize() - (long) remaining.getCount());
        if (canFit <= 0) return 0;

        IAEItemStack ais = org.copy();
        ais.setStackSize(canFit);
        IAEItemStack itemsToAdd = Platform.poweredExtraction(energy, inv, ais, source);
        if (itemsToAdd != null) {
            inputStack.setCount((int) Math.min(Integer.MAX_VALUE, itemsToAdd.getStackSize()));
            ItemStack failed = adaptor.addItems(inputStack);
            if (!failed.isEmpty()) {
                ais.setStackSize(failed.getCount());
                inv.injectItems(ais, Actionable.MODULATE, source);
                return itemsToAdd.getStackSize() - failed.getCount();
            }
            return itemsToAdd.getStackSize();
        } else {
            org.setCachedItemStack(inputStack);
            return 0;
        }
    }
}
