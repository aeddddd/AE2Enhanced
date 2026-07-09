package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec;

import appeng.api.stacks.AEFluidKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.FluidDescriptor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * FluidDescriptor 自定义二进制编解码器。
 * 使用 {@link NbtIo} 读写 AE2 流体 key 的完整 NBT。
 */
public class FluidDescriptorCodec implements DescriptorCodec<FluidDescriptor> {

    public static final FluidDescriptorCodec INSTANCE = new FluidDescriptorCodec();

    private FluidDescriptorCodec() {
    }

    @Override
    public void write(DataOutput out, FluidDescriptor descriptor) throws IOException {
        NbtIo.write(descriptor.toNBT(), out);
    }

    @Override
    public FluidDescriptor read(DataInput in) throws IOException {
        CompoundTag tag = NbtIo.read(in);
        if (tag == null) {
            return null;
        }
        AEFluidKey key = AEFluidKey.fromTag(tag);
        if (key == null) {
            return null;
        }
        return new FluidDescriptor(key);
    }
}
