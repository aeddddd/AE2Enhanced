package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.item.ItemStack;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** EMC 出站端口定位器；未安装集成适配器时保持空实现语义。 */
public final class EmcStoragePort {

    private static volatile EmcStorage delegate;

    private EmcStoragePort() {
    }

    public static void register(EmcStorage storage) {
        delegate = storage;
    }

    public static void subtractEmcBig(Object provider, BigInteger value) {
        if (delegate != null) {
            delegate.subtractEmcBig(provider, value);
        }
    }

    public static BigInteger getEmcBig(Object provider) {
        return delegate == null ? BigInteger.ZERO : delegate.getEmcBig(provider);
    }

    public static boolean subtractEmcOffline(UUID owner, BigInteger value) {
        return delegate != null && delegate.subtractEmcOffline(owner, value);
    }

    public static List<ItemStack> getKnowledge(Object provider) {
        return delegate == null ? Collections.emptyList() : delegate.getKnowledge(provider);
    }

    public static long getEmcValue(ItemStack stack) {
        return delegate == null ? 0L : delegate.getEmcValue(stack);
    }

    public static boolean isAvailable() {
        return delegate != null && delegate.isAvailable();
    }

    public static boolean shouldDupeWithNBT(ItemStack stack) {
        return delegate != null && delegate.shouldDupeWithNBT(stack);
    }

    public static Object getKnowledgeProvider(UUID owner) {
        return delegate == null ? null : delegate.getKnowledgeProvider(owner);
    }

    public static void sync(Object provider, net.minecraft.entity.player.EntityPlayerMP player) {
        if (delegate != null) {
            delegate.sync(provider, player);
        }
    }
}
