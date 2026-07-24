package com.github.aeddddd.ae2enhanced.omnitool;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 共形不变荷升级：保护工具掉落物实体不被烧毁、不消失、可被立即拾取。
 */
public final class ConformalChargeHandler {

    private ConformalChargeHandler() {}

    public static boolean onEntityItemUpdate(ItemEntity entityItem) {
        ItemStack stack = entityItem.getItem();
        if (OmniToolUpgrades.hasConformalCharge(stack)) {
            CompoundTag data = entityItem.getPersistentData();
            if (!data.getBoolean(OmniToolNBT.CONFORMAL_INIT)) {
                data.putBoolean(OmniToolNBT.CONFORMAL_INIT, true);
                entityItem.setInvulnerable(true);
                entityItem.setUnlimitedLifetime();
            }
            entityItem.clearFire();
            entityItem.setNoPickUpDelay();
        }
        return false;
    }
}
