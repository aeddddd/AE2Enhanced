package com.github.aeddddd.ae2enhanced.storage.codec;

import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import com.github.aeddddd.ae2enhanced.storage.StorageConstants;
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
        // 与 ItemDescriptor.toNBT 一致:未注册物品没有 registryName,回退为 "minecraft:air"
        ResourceLocation regName = descriptor.getItem().getRegistryName();
        String id = regName != null ? regName.toString() : "minecraft:air";
        byte[] idBytes = id.getBytes("UTF-8");
        out.writeInt(idBytes.length);
        out.write(idBytes);
        out.writeShort(descriptor.getMeta());

        // getNbtRaw: 序列化只读遍历,不做防御性深拷贝(契约见 ItemDescriptor.getNbtRaw)
        NBTTagCompound nbt = descriptor.getNbtRaw();
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
            // 磁盘读取用 64MB 上限(原为网络同款 2MB,超限会锁死整个分区);
            // 网络同步的 2MB 限制与此无关,超限物品仅终端显示降级
            nbt = CompressedStreamTools.read(in, new NBTSizeTracker(StorageConstants.MAX_NBT_PAYLOAD_BYTES));
        }

        Item item = Item.REGISTRY.getObject(new ResourceLocation(id));
        if (item == null) {
            return null;
        }
        return ItemDescriptor.fromRaw(item, meta, nbt);
    }
}
