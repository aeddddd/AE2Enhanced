package com.github.aeddddd.ae2enhanced.storage.codec;

import com.github.aeddddd.ae2enhanced.storage.EnergyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * EnergyDescriptor 自定义二进制编解码器(单例,无实际字段).
 */
public class EnergyDescriptorCodec implements DescriptorCodec<EnergyDescriptor> {

    public static final EnergyDescriptorCodec INSTANCE = new EnergyDescriptorCodec();

    private EnergyDescriptorCodec() {}

    @Override
    public void write(DataOutput out, EnergyDescriptor descriptor) throws IOException {
        // 单例,无字段
    }

    @Override
    public EnergyDescriptor read(DataInput in) throws IOException {
        return EnergyDescriptor.INSTANCE;
    }
}
