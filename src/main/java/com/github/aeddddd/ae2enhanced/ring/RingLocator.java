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

    /**
     * 统计玩家身上飞升凭证数量(物品栏 getSizeInventory 已含主手/副手/护甲,另加 Baubles).
     * 供 Vethea 维度切换整合器计算冗余存档副本数.
     */
    public static int countAscended(EntityPlayer player) {
        int count = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            if (isAscendedCredential(player.inventory.getStackInSlot(i))) {
                count++;
            }
        }
        if (resolveBaubles()) {
            try {
                Object handler = GET_BAUBLES_HANDLER.invoke(null, player);
                if (handler != null) {
                    int slots = (int) GET_SLOTS.invoke(handler);
                    for (int i = 0; i < slots; i++) {
                        if (isAscendedCredential((ItemStack) GET_STACK_IN_SLOT.invoke(handler, i))) {
                            count++;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return count;
    }

    private static boolean isAscendedCredential(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemNetworkLinkCredential
                && RingNBT.isAscended(stack);
    }

    private static ItemStack findInBaubles(EntityPlayer player) {
        if (!resolveBaubles()) {
            return ItemStack.EMPTY;
        }
        try {
            Object handler = GET_BAUBLES_HANDLER.invoke(null, player);
            if (handler == null) return ItemStack.EMPTY;
            int slots = (int) GET_SLOTS.invoke(handler);
            for (int i = 0; i < slots; i++) {
                ItemStack stack = (ItemStack) GET_STACK_IN_SLOT.invoke(handler, i);
                if (!stack.isEmpty() && stack.getItem() instanceof ItemNetworkLinkCredential) {
                    return stack;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to search Baubles for NetworkLinkRing", e);
        }
        return ItemStack.EMPTY;
    }

    // Baubles 反射成员静态缓存: findRing 每 tick × 每玩家都会被调用,
    // 不能容忍 Class.forName / getMethod 的重复开销. 仅缓存 Class/Method 引用,
    // 不向本类常量池引入 Baubles 类型,保持缺席时的安全加载.
    private static boolean baublesResolved = false;
    private static java.lang.reflect.Method GET_BAUBLES_HANDLER;
    private static java.lang.reflect.Method GET_SLOTS;
    private static java.lang.reflect.Method GET_STACK_IN_SLOT;

    private static synchronized boolean resolveBaubles() {
        if (baublesResolved) {
            return GET_BAUBLES_HANDLER != null;
        }
        baublesResolved = true;
        if (!Loader.isModLoaded("baubles")) {
            return false;
        }
        try {
            GET_BAUBLES_HANDLER = Class.forName("baubles.api.BaublesApi")
                    .getMethod("getBaublesHandler", EntityPlayer.class);
            Class<?> handlerClass = Class.forName("baubles.api.cap.IBaublesItemHandler");
            GET_SLOTS = handlerClass.getMethod("getSlots");
            GET_STACK_IN_SLOT = handlerClass.getMethod("getStackInSlot", int.class);
        } catch (Throwable t) {
            GET_BAUBLES_HANDLER = null;
            GET_SLOTS = null;
            GET_STACK_IN_SLOT = null;
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to resolve Baubles API, Baubles ring slots disabled", t);
        }
        return GET_BAUBLES_HANDLER != null;
    }
}
