package com.github.aeddddd.ae2enhanced.omnitool;

import com.github.aeddddd.ae2enhanced.util.reflection.VanillaReflectionHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;

/**
 * 共形不变荷升级：保护工具实体不被烧毁、不消失、可被立即拾取。
 */
public final class ConformalChargeHandler {

    private ConformalChargeHandler() {}

    public static boolean onEntityItemUpdate(EntityItem entityItem) {
        ItemStack stack = entityItem.getItem();
        if (OmniToolUpgrades.hasConformalCharge(stack)) {
            if (!entityItem.getEntityData().getBoolean(OmniToolNBT.CONFORMAL_INIT)) {
                entityItem.getEntityData().setBoolean(OmniToolNBT.CONFORMAL_INIT, true);
                VanillaReflectionHelper.setImmuneToFire(entityItem, true);
                entityItem.setEntityInvulnerable(true);
                entityItem.setNoDespawn();
            }
            entityItem.setNoPickupDelay();
        }
        return false;
    }
}
