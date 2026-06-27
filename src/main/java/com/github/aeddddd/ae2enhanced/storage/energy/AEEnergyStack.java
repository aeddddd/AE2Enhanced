package com.github.aeddddd.ae2enhanced.storage.energy;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEStack;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;

/**
 * AE2 能量堆叠的具体实现.
 * 每个实例代表一定数量的 RF(Redstone Flux).
 */
public final class AEEnergyStack extends AEStack<IAEEnergyStack> implements IAEEnergyStack {

    private static final EnergyStorageChannel CHANNEL = new EnergyStorageChannel();

    public AEEnergyStack() {
    }

    public static IAEEnergyStack create(long amount) {
        if (amount <= 0) {
            return createEmpty();
        }
        AEEnergyStack stack = new AEEnergyStack();
        stack.setStackSize(amount);
        return stack;
    }

    public static IAEEnergyStack createEmpty() {
        return new AEEnergyStack();
    }

    @Override
    public void add(IAEEnergyStack other) {
        if (other != null) {
            this.incStackSize(other.getStackSize());
            this.incCountRequestable(other.getCountRequestable());
        }
    }

    @Override
    public IAEEnergyStack empty() {
        return new AEEnergyStack();
    }

    public static IAEEnergyStack fromNBT(NBTTagCompound nbt) {
        AEEnergyStack stack = new AEEnergyStack();
        stack.setStackSize(nbt.getLong("Count"));
        stack.setCountRequestable(nbt.getLong("Req"));
        stack.setCraftable(nbt.getBoolean("Craft"));
        return stack;
    }

    public static IAEEnergyStack fromPacket(ByteBuf buf) throws IOException {
        AEEnergyStack stack = new AEEnergyStack();
        stack.setStackSize(buf.readLong());
        stack.setCountRequestable(buf.readLong());
        stack.setCraftable(buf.readBoolean());
        return stack;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("Count", this.getStackSize());
        nbt.setLong("Req", this.getCountRequestable());
        nbt.setBoolean("Craft", this.isCraftable());
    }

    @Override
    public void writeToPacket(ByteBuf buf) throws IOException {
        buf.writeLong(this.getStackSize());
        buf.writeLong(this.getCountRequestable());
        buf.writeBoolean(this.isCraftable());
    }

    @Override
    public IStorageChannel<IAEEnergyStack> getChannel() {
        return CHANNEL;
    }

    @Override
    public ItemStack asItemStackRepresentation() {
        return com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop.createStack();
    }

    @Override
    public boolean fuzzyComparison(IAEEnergyStack other, FuzzyMode mode) {
        return other != null;
    }

    @Override
    public boolean isItem() {
        return false;
    }

    @Override
    public boolean isFluid() {
        return false;
    }

    @Override
    public IAEEnergyStack copy() {
        AEEnergyStack copy = new AEEnergyStack();
        copy.setStackSize(this.getStackSize());
        copy.setCountRequestable(this.getCountRequestable());
        copy.setCraftable(this.isCraftable());
        return copy;
    }

    @Override
    protected boolean hasTagCompound() {
        return false;
    }
}
