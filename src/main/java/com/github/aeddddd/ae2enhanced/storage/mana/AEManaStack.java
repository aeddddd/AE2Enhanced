package com.github.aeddddd.ae2enhanced.storage.mana;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IStorageChannel;
import appeng.util.item.AEStack;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;

/**
 * AE2 Mana 堆叠的具体实现.
 * 每个实例代表一定数量的 Botania Mana.
 */
public final class AEManaStack extends AEStack<IAEManaStack> implements IAEManaStack {

    private static final ManaStorageChannel CHANNEL = new ManaStorageChannel();

    public AEManaStack() {
    }

    public static IAEManaStack create(long amount) {
        if (amount <= 0) {
            return createEmpty();
        }
        AEManaStack stack = new AEManaStack();
        stack.setStackSize(amount);
        return stack;
    }

    public static IAEManaStack createEmpty() {
        return new AEManaStack();
    }

    @Override
    public void add(IAEManaStack other) {
        if (other != null) {
            this.incStackSize(other.getStackSize());
            this.incCountRequestable(other.getCountRequestable());
        }
    }

    @Override
    public IAEManaStack empty() {
        return new AEManaStack();
    }

    public static IAEManaStack fromNBT(NBTTagCompound nbt) {
        AEManaStack stack = new AEManaStack();
        stack.setStackSize(nbt.getLong("Count"));
        stack.setCountRequestable(nbt.getLong("Req"));
        stack.setCraftable(nbt.getBoolean("Craft"));
        return stack;
    }

    public static IAEManaStack fromPacket(ByteBuf buf) throws IOException {
        AEManaStack stack = new AEManaStack();
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
    public IStorageChannel<IAEManaStack> getChannel() {
        return CHANNEL;
    }

    @Override
    public ItemStack asItemStackRepresentation() {
        return com.github.aeddddd.ae2enhanced.item.ItemManaDrop.createStack();
    }

    @Override
    public boolean fuzzyComparison(IAEManaStack other, FuzzyMode mode) {
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
    public IAEManaStack copy() {
        AEManaStack copy = new AEManaStack();
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
