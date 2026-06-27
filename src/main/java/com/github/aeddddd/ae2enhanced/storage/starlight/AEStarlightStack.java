package com.github.aeddddd.ae2enhanced.storage.starlight;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IStorageChannel;
import appeng.util.item.AEStack;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;

/**
 * AE2 Starlight 堆叠的具体实现.
 * 每个实例代表一定数量的 Astral Sorcery Starlight.
 */
public final class AEStarlightStack extends AEStack<IAEStarlightStack> implements IAEStarlightStack {

    private static final StarlightStorageChannel CHANNEL = new StarlightStorageChannel();

    public AEStarlightStack() {
    }

    public static IAEStarlightStack create(long amount) {
        if (amount <= 0) {
            return createEmpty();
        }
        AEStarlightStack stack = new AEStarlightStack();
        stack.setStackSize(amount);
        return stack;
    }

    public static IAEStarlightStack createEmpty() {
        return new AEStarlightStack();
    }

    @Override
    public void add(IAEStarlightStack other) {
        if (other != null) {
            this.incStackSize(other.getStackSize());
            this.incCountRequestable(other.getCountRequestable());
        }
    }

    @Override
    public IAEStarlightStack empty() {
        return new AEStarlightStack();
    }

    public static IAEStarlightStack fromNBT(NBTTagCompound nbt) {
        AEStarlightStack stack = new AEStarlightStack();
        stack.setStackSize(nbt.getLong("Count"));
        stack.setCountRequestable(nbt.getLong("Req"));
        stack.setCraftable(nbt.getBoolean("Craft"));
        return stack;
    }

    public static IAEStarlightStack fromPacket(ByteBuf buf) throws IOException {
        AEStarlightStack stack = new AEStarlightStack();
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
    public IStorageChannel<IAEStarlightStack> getChannel() {
        return CHANNEL;
    }

    @Override
    public ItemStack asItemStackRepresentation() {
        return com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop.createStack();
    }

    @Override
    public boolean fuzzyComparison(IAEStarlightStack other, FuzzyMode mode) {
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
    public IAEStarlightStack copy() {
        AEStarlightStack copy = new AEStarlightStack();
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
