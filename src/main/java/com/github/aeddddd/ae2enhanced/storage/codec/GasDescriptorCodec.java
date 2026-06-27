package com.github.aeddddd.ae2enhanced.storage.codec;

import com.github.aeddddd.ae2enhanced.storage.GasDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * GasDescriptor 自定义二进制编解码器.
 */
public class GasDescriptorCodec implements DescriptorCodec<GasDescriptor> {

    public static final GasDescriptorCodec INSTANCE = new GasDescriptorCodec();

    private GasDescriptorCodec() {}

    @Override
    public void write(DataOutput out, GasDescriptor descriptor) throws IOException {
        String name = descriptor.getGasName();
        byte[] nameBytes = name.getBytes("UTF-8");
        out.writeInt(nameBytes.length);
        out.write(nameBytes);
    }

    @Override
    public GasDescriptor read(DataInput in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        String gasName = new String(bytes, "UTF-8");
        if (gasName.isEmpty()) {
            return null;
        }
        return new GasDescriptor(gasName);
    }
}
