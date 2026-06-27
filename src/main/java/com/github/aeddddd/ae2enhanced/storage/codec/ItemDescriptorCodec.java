package com.github.aeddddd.ae2enhanced.storage.codec;

import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.util.ResourceLocation;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * ItemDescriptor 自定义二进制编解码器.
 */
public class ItemDescriptorCodec implements DescriptorCodec<ItemDescriptor> {

    public static final ItemDescriptorCodec INSTANCE = new ItemDescriptorCodec();

    private ItemDescriptorCodec() {}

    @Override
    public void write(DataOutput out, ItemDescriptor descriptor) throws IOException {
        String id = descriptor.getItem().getRegistryName().toString();
        byte[] idBytes = id.getBytes("UTF-8");
        out.writeInt(idBytes.length);
        out.write(idBytes);
        out.writeShort(descriptor.getMeta());

        NBTTagCompound nbt = descriptor.getNbt();
        if (nbt != null) {
            out.writeByte(1);
            CompressedStreamTools.write(nbt, out);
        } else {
            out.writeByte(0);
        }
    }

    @Override
    public ItemDescriptor read(DataInput in) throws IOException {
        int idLen = in.readInt();
        byte[] idBytes = new byte[idLen];
        in.readFully(idBytes);
        String id = new String(idBytes, "UTF-8");

        short meta = in.readShort();
        boolean hasNbt = in.readByte() != 0;
        NBTTagCompound nbt = null;
        if (hasNbt) {
            nbt = CompressedStreamTools.read(in, new NBTSizeTracker(2097152L));
        }

        Item item = Item.REGISTRY.getObject(new ResourceLocation(id));
        if (item == null) {
            return null;
        }
        return ItemDescriptor.fromRaw(item, meta, nbt);
    }
}
