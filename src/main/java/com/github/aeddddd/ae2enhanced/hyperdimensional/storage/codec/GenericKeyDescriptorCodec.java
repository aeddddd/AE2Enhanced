package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import appeng.api.stacks.AEKey;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.GenericKeyDescriptor;

/**
 * 通用 AEKey 描述符编解码器。
 */
public class GenericKeyDescriptorCodec implements DescriptorCodec<GenericKeyDescriptor> {

    public static final GenericKeyDescriptorCodec INSTANCE = new GenericKeyDescriptorCodec();

    private GenericKeyDescriptorCodec() {
    }

    @Override
    public void write(DataOutput out, GenericKeyDescriptor descriptor) throws IOException {
        CompoundTag tag = descriptor.toNBT();
        NbtIo.write(tag, out);
    }

    @Override
    public GenericKeyDescriptor read(DataInput in) throws IOException {
        CompoundTag tag = NbtIo.read(in);
        if (tag == null) {
            return null;
        }
        AEKey key = AEKey.fromTagGeneric(tag);
        if (key == null) {
            return null;
        }
        return new GenericKeyDescriptor(key);
    }
}
