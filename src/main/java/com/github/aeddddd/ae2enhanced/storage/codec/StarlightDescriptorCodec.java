package com.github.aeddddd.ae2enhanced.storage.codec;

import com.github.aeddddd.ae2enhanced.storage.StarlightDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * StarlightDescriptor 自定义二进制编解码器(单例,无实际字段).
 */
public class StarlightDescriptorCodec implements DescriptorCodec<StarlightDescriptor> {

    public static final StarlightDescriptorCodec INSTANCE = new StarlightDescriptorCodec();

    private StarlightDescriptorCodec() {}

    @Override
    public void write(DataOutput out, StarlightDescriptor descriptor) throws IOException {
        // 单例,无字段
    }

    @Override
    public StarlightDescriptor read(DataInput in) throws IOException {
        return StarlightDescriptor.INSTANCE;
    }
}
