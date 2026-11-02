package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.nbt.NBTTagCompound;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/** 未注册资源的不透明描述符：仅参与磁盘往返，不进入网络可用物品视图。 */
public final class PreservedDescriptor implements Descriptor {
    private final byte[] bytes;

    public PreservedDescriptor(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public static void writeLegacyItem(NBTTagCompound tag, DataOutput out) throws IOException {
        writeId(tag.getString("id"), out);
        out.writeShort(tag.getShort("Damage"));
        writeNestedNbt(tag, out);
    }

    public static void writeLegacyFluid(NBTTagCompound tag, DataOutput out) throws IOException {
        writeId(tag.getString("id"), out);
        writeNestedNbt(tag, out);
    }

    private static void writeId(String id, DataOutput out) throws IOException {
        if (id.isEmpty()) throw new IOException("Empty legacy descriptor id");
        byte[] bytes = id.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void writeNestedNbt(NBTTagCompound tag, DataOutput out) throws IOException {
        out.writeByte(tag.hasKey("tag", 10) ? 1 : 0);
        if (tag.hasKey("tag", 10)) {
            net.minecraft.nbt.CompressedStreamTools.write(tag.getCompoundTag("tag"), out);
        }
    }

    public void write(DataOutput out) throws IOException {
        out.write(bytes);
    }

    @Override
    public NBTTagCompound toNBT() {
        return new NBTTagCompound();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PreservedDescriptor
                && Arrays.equals(this.bytes, ((PreservedDescriptor) other).bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.bytes);
    }
}
