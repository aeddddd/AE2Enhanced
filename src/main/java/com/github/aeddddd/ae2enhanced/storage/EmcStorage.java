package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.item.ItemStack;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/** EMC 存储业务使用的 ProjectE 出站端口。 */
public interface EmcStorage {

    void subtractEmcBig(Object provider, BigInteger value);
    BigInteger getEmcBig(Object provider);
    boolean subtractEmcOffline(UUID owner, BigInteger value);
    List<ItemStack> getKnowledge(Object provider);
    long getEmcValue(ItemStack stack);
    boolean isAvailable();
    boolean shouldDupeWithNBT(ItemStack stack);
    Object getKnowledgeProvider(UUID owner);
    void sync(Object provider, net.minecraft.entity.player.EntityPlayerMP player);
}
