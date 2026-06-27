package com.github.aeddddd.ae2enhanced.util;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/**
 * 在玩家身上查找 Omni Terminal 的共享工具类.
 * 搜索顺序：主手 → 副手 → 物品栏 → Baubles(反射,避免硬引用).
 */
public class OmniTerminalFinder {

    public static ItemStack findOmniTerminal(EntityPlayer player) {
        ItemStack main = player.getHeldItemMainhand();
        if (main.getItem() instanceof ItemOmniWirelessTerminal) {
            return main;
        }
        ItemStack off = player.getHeldItemOffhand();
        if (off.getItem() instanceof ItemOmniWirelessTerminal) {
            return off;
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.getItem() instanceof ItemOmniWirelessTerminal) {
                return stack;
            }
        }
        return findInBaubles(player);
    }

    public static int findSlotIndex(EntityPlayer player, ItemStack target) {
        if (player.getHeldItemMainhand() == target) {
            return player.inventory.currentItem;
        }
        if (player.getHeldItemOffhand() == target) {
            return 40;
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            if (player.inventory.getStackInSlot(i) == target) {
                return i;
            }
        }
        return findBaubleSlot(player, target);
    }

    public static boolean isInBaublesSlot(EntityPlayer player, ItemStack target) {
        return findBaubleSlot(player, target) >= 0;
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
                if (!stack.isEmpty() && stack.getItem() instanceof ItemOmniWirelessTerminal) {
                    return stack;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to search Baubles for OmniTerminal", e);
        }
        return ItemStack.EMPTY;
    }

    private static int findBaubleSlot(EntityPlayer player, ItemStack target) {
        if (!Loader.isModLoaded("baubles")) {
            return -1;
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
                if (stack == target) {
                    return i;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to find Bauble slot for OmniTerminal", e);
        }
        return -1;
    }
}
