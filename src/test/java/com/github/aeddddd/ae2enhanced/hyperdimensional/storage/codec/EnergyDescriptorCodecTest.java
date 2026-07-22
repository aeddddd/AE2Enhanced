package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.EnergyDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link EnergyDescriptorCodec} 单元测试.
 */
class EnergyDescriptorCodecTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testBinaryRoundTrip() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        EnergyDescriptorCodec.INSTANCE.write(new DataOutputStream(buffer), EnergyDescriptor.INSTANCE);

        EnergyDescriptor restored = EnergyDescriptorCodec.INSTANCE
                .read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));

        // 能量描述符为单例,解码结果必须是同一实例
        assertSame(EnergyDescriptor.INSTANCE, restored);
    }

    @Test
    void testWriteEmitsMarkerString() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        EnergyDescriptorCodec.INSTANCE.write(out, EnergyDescriptor.INSTANCE);
        out.flush();

        // 写入内容应以格式标记字符串开头
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        assertEquals("ae2enhanced:energy", in.readUTF());
    }

    @Test
    void testMultipleSequentialWritesAreReadable() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        EnergyDescriptorCodec.INSTANCE.write(out, EnergyDescriptor.INSTANCE);
        EnergyDescriptorCodec.INSTANCE.write(out, EnergyDescriptor.INSTANCE);
        out.flush();

        // 同一流中的多条记录可依次独立解码
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        assertSame(EnergyDescriptor.INSTANCE, EnergyDescriptorCodec.INSTANCE.read(in));
        assertSame(EnergyDescriptor.INSTANCE, EnergyDescriptorCodec.INSTANCE.read(in));
    }
}
