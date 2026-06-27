package com.github.aeddddd.ae2enhanced.omnitool;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;

/**
 * 共形不变荷升级：保护工具实体不被烧毁、不消失、可被立即拾取。
 */
public final class ConformalChargeHandler {

    private ConformalChargeHandler() {}

    private static final java.lang.reflect.Field ENTITY_IMMUNE_TO_FIRE;

    static {
        java.lang.reflect.Field f = null;
        try {
            f = Entity.class.getDeclaredField("isImmuneToFire");
            f.setAccessible(true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to cache isImmuneToFire field", e);
        }
        ENTITY_IMMUNE_TO_FIRE = f;
    }

    public static boolean onEntityItemUpdate(EntityItem entityItem) {
        ItemStack stack = entityItem.getItem();
        if (OmniToolUpgrades.hasConformalCharge(stack)) {
            if (!entityItem.getEntityData().getBoolean(OmniToolNBT.CONFORMAL_INIT)) {
                entityItem.getEntityData().setBoolean(OmniToolNBT.CONFORMAL_INIT, true);
                if (ENTITY_IMMUNE_TO_FIRE != null) {
                    try {
                        ENTITY_IMMUNE_TO_FIRE.setBoolean(entityItem, true);
                    } catch (Exception ignored) {}
                }
                entityItem.setEntityInvulnerable(true);
                entityItem.setNoDespawn();
            }
            entityItem.setNoPickupDelay();
        }
        return false;
    }
}
