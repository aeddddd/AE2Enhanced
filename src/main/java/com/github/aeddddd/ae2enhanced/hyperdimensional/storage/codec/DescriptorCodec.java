package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.Descriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 超维度仓储中枢 Descriptor 自定义二进制编解码器接口。
 *
 * @param <D> 描述符类型
 */
public interface DescriptorCodec<D extends Descriptor> {

    /**
     * 将描述符写入二进制输出流。
     */
    void write(DataOutput out, D descriptor) throws IOException;

    /**
     * 从二进制输入流读取描述符。
     *
     * @return 描述符，解析失败可返回 null
     */
    D read(DataInput in) throws IOException;
}
