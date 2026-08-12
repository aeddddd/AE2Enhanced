package com.github.aeddddd.ae2enhanced.ring;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemNetworkLinkCredential;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/**
 * 在玩家身上定位第一枚生效的网络链接指环.
 * 搜索顺序：主手 → 副手 → 物品栏 → Baubles(反射,避免硬引用).
 * 同时仅第一枚找到的指环生效,多枚不叠加.
 */
public final class RingLocator {

    private RingLocator() {}

    public static ItemStack findRing(EntityPlayer player) {
        ItemStack main = player.getHeldItemMainhand();
        if (main.getItem() instanceof ItemNetworkLinkCredential) {
            return main;
        }
        ItemStack off = player.getHeldItemOffhand();
        if (off.getItem() instanceof ItemNetworkLinkCredential) {
            return off;
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.getItem() instanceof ItemNetworkLinkCredential) {
                return stack;
            }
        }
        return findInBaubles(player);
    }

    public static boolean hasRing(EntityPlayer player) {
        return !findRing(player).isEmpty();
    }

    private static ItemStack findInBaubles(EntityPlayer player) {
        if (!Loader.isModLoaded("baubles")) {
            return ItemStack.EMPTY;
        }
        try {
            Object handler = Class.forName("baubles.api.BaublesApi")
                    .getMethod("getBaublesHandler", EntityPlayer.class)
                    .invoke(null, player);
            int slots = (int) handler.getClass().getMethod("getSlots").invoke(handler);
            for (int i = 0; i < slots; i++) {
                ItemStack stack = (ItemStack) handler.getClass()
                        .getMethod("getStackInSlot", int.class)
                        .invoke(handler, i);
                if (!stack.isEmpty() && stack.getItem() instanceof ItemNetworkLinkCredential) {
                    return stack;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to search Baubles for NetworkLinkRing", e);
        }
        return ItemStack.EMPTY;
    }
}
