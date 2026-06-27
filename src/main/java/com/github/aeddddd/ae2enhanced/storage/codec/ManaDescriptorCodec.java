package com.github.aeddddd.ae2enhanced.storage.codec;

import com.github.aeddddd.ae2enhanced.storage.ManaDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * ManaDescriptor 自定义二进制编解码器(单例,无实际字段).
 */
public class ManaDescriptorCodec implements DescriptorCodec<ManaDescriptor> {

    public static final ManaDescriptorCodec INSTANCE = new ManaDescriptorCodec();

    private ManaDescriptorCodec() {}

    @Override
    public void write(DataOutput out, ManaDescriptor descriptor) throws IOException {
        // 单例,无字段
    }

    @Override
    public ManaDescriptor read(DataInput in) throws IOException {
        return ManaDescriptor.INSTANCE;
    }
}
