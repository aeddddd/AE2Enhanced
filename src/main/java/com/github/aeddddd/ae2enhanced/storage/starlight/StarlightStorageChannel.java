package com.github.aeddddd.ae2enhanced.storage.starlight;

import appeng.api.storage.data.IItemList;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Astral Sorcery Starlight 存储通道的实现.
 * 1 stackSize = 1 Starlight unit, transferFactor = 1.
 */
public final class StarlightStorageChannel implements IStarlightStorageChannel {

    @Override
    public int transferFactor() {
        return 1;
    }

    @Override
    public int getUnitsPerByte() {
        return 8;
    }

    @Override
    public IItemList<IAEStarlightStack> createList() {
        return new StarlightList();
    }

    @Override
    @Nullable
    public IAEStarlightStack createStack(@Nonnull Object input) {
        if (input instanceof Number) {
            return AEStarlightStack.create(((Number) input).longValue());
        }
        if (input instanceof IAEStarlightStack) {
            return ((IAEStarlightStack) input).copy();
        }
        return null;
    }

    @Override
    @Nullable
    public IAEStarlightStack readFromPacket(@Nonnull ByteBuf buf) throws IOException {
        return AEStarlightStack.fromPacket(buf);
    }

    @Override
    @Nullable
    public IAEStarlightStack createFromNBT(@Nonnull NBTTagCompound nbt) {
        return AEStarlightStack.fromNBT(nbt);
    }
}
