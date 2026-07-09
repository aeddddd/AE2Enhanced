package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec;

import appeng.api.stacks.AEItemKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.ItemDescriptor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * ItemDescriptor 自定义二进制编解码器。
 * 使用 {@link NbtIo} 读写 AE2 物品 key 的完整 NBT。
 */
public class ItemDescriptorCodec implements DescriptorCodec<ItemDescriptor> {

    public static final ItemDescriptorCodec INSTANCE = new ItemDescriptorCodec();

    private ItemDescriptorCodec() {
    }

    @Override
    public void write(DataOutput out, ItemDescriptor descriptor) throws IOException {
        NbtIo.write(descriptor.toNBT(), out);
    }

    @Override
    public ItemDescriptor read(DataInput in) throws IOException {
        CompoundTag tag = NbtIo.read(in);
        if (tag == null) {
            return null;
        }
        AEItemKey key = AEItemKey.fromTag(tag);
        if (key == null) {
            return null;
        }
        return new ItemDescriptor(key);
    }
}
