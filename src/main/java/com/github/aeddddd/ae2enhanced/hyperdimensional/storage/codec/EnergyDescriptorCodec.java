package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.EnergyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * EnergyDescriptor 自定义二进制编解码器（单例，无实际字段）。
 * 仅写入一个标记字符串用于格式校验。
 */
public class EnergyDescriptorCodec implements DescriptorCodec<EnergyDescriptor> {

    public static final EnergyDescriptorCodec INSTANCE = new EnergyDescriptorCodec();
    private static final String ENERGY_MARKER = "ae2enhanced:energy";

    private EnergyDescriptorCodec() {
    }

    @Override
    public void write(DataOutput out, EnergyDescriptor descriptor) throws IOException {
        out.writeUTF(ENERGY_MARKER);
    }

    @Override
    public EnergyDescriptor read(DataInput in) throws IOException {
        in.readUTF(); // 读取并丢弃标记字符串
        return EnergyDescriptor.INSTANCE;
    }
}
