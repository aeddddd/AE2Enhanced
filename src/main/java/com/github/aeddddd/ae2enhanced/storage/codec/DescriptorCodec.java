package com.github.aeddddd.ae2enhanced.storage.codec;

import com.github.aeddddd.ae2enhanced.storage.Descriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 超维度仓储中枢 Descriptor 自定义二进制编解码器接口.
 */
public interface DescriptorCodec<D extends Descriptor> {

    void write(DataOutput out, D descriptor) throws IOException;

    D read(DataInput in) throws IOException;
}
