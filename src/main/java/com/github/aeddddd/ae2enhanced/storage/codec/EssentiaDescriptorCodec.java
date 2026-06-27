package com.github.aeddddd.ae2enhanced.storage.codec;

import com.github.aeddddd.ae2enhanced.storage.EssentiaDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * EssentiaDescriptor 自定义二进制编解码器.
 */
public class EssentiaDescriptorCodec implements DescriptorCodec<EssentiaDescriptor> {

    public static final EssentiaDescriptorCodec INSTANCE = new EssentiaDescriptorCodec();

    private EssentiaDescriptorCodec() {}

    @Override
    public void write(DataOutput out, EssentiaDescriptor descriptor) throws IOException {
        String tag = descriptor.getAspectTag();
        byte[] tagBytes = tag.getBytes("UTF-8");
        out.writeInt(tagBytes.length);
        out.write(tagBytes);
    }

    @Override
    public EssentiaDescriptor read(DataInput in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        String aspectTag = new String(bytes, "UTF-8");
        if (aspectTag.isEmpty()) {
            return null;
        }
        return new EssentiaDescriptor(aspectTag);
    }
}
