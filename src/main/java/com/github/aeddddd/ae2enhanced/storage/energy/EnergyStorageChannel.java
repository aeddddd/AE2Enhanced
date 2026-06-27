package com.github.aeddddd.ae2enhanced.storage.energy;

import appeng.api.storage.data.IItemList;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

/**
 * RF 能量存储通道的实现.
 * 1 stackSize = 1 RF,transferFactor = 1.
 */
public final class EnergyStorageChannel implements IEnergyStorageChannel {

    @Override
    public int transferFactor() {
        return 1;
    }

    @Override
    public int getUnitsPerByte() {
        return 8;
    }

    @Override
    public IItemList<IAEEnergyStack> createList() {
        return new EnergyList();
    }

    @Override
    @Nullable
    public IAEEnergyStack createStack(@Nonnull Object input) {
        if (input instanceof Number) {
            return AEEnergyStack.create(((Number) input).longValue());
        }
        if (input instanceof IAEEnergyStack) {
            return ((IAEEnergyStack) input).copy();
        }
        return null;
    }

    @Override
    @Nullable
    public IAEEnergyStack readFromPacket(@Nonnull ByteBuf buf) throws IOException {
        return AEEnergyStack.fromPacket(buf);
    }

    @Override
    @Nullable
    public IAEEnergyStack createFromNBT(@Nonnull NBTTagCompound nbt) {
        return AEEnergyStack.fromNBT(nbt);
    }
}
