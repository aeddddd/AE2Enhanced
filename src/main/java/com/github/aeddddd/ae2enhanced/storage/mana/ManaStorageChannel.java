package com.github.aeddddd.ae2enhanced.storage.mana;

import appeng.api.storage.data.IItemList;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Botania Mana 存储通道的实现.
 * 1 stackSize = 1 Mana, transferFactor = 1.
 */
public final class ManaStorageChannel implements IManaStorageChannel {

    @Override
    public int transferFactor() {
        return 1;
    }

    @Override
    public int getUnitsPerByte() {
        return 8;
    }

    @Override
    public IItemList<IAEManaStack> createList() {
        return new ManaList();
    }

    @Override
    @Nullable
    public IAEManaStack createStack(@Nonnull Object input) {
        if (input instanceof Number) {
            return AEManaStack.create(((Number) input).longValue());
        }
        if (input instanceof IAEManaStack) {
            return ((IAEManaStack) input).copy();
        }
        return null;
    }

    @Override
    @Nullable
    public IAEManaStack readFromPacket(@Nonnull ByteBuf buf) throws IOException {
        return AEManaStack.fromPacket(buf);
    }

    @Override
    @Nullable
    public IAEManaStack createFromNBT(@Nonnull NBTTagCompound nbt) {
        return AEManaStack.fromNBT(nbt);
    }
}
