package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.FluidDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FluidDescriptorCodec} 单元测试.
 */
class FluidDescriptorCodecTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    /**
     * 将描述符编码为字节数组再解码,返回解码结果.
     */
    private static FluidDescriptor roundTrip(FluidDescriptor descriptor) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        FluidDescriptorCodec.INSTANCE.write(out, descriptor);
        out.flush();

        return FluidDescriptorCodec.INSTANCE.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));
    }

    @Test
    void testRoundTripWithoutNbt() throws IOException {
        FluidDescriptor descriptor = new FluidDescriptor(AEFluidKey.of(Fluids.WATER));

        FluidDescriptor restored = roundTrip(descriptor);

        assertNotNull(restored);
        assertEquals(descriptor, restored);
        assertEquals(descriptor.hashCode(), restored.hashCode());
        assertNull(restored.getNbt());
    }

    @Test
    void testRoundTripWithNbt() throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putString("custom", "value");
        tag.putInt("count", 7);
        FluidDescriptor descriptor = new FluidDescriptor(AEFluidKey.of(Fluids.LAVA, tag));

        FluidDescriptor restored = roundTrip(descriptor);

        assertNotNull(restored);
        assertEquals(descriptor, restored);
        assertEquals("value", restored.getNbt().getString("custom"));
        assertEquals(7, restored.getNbt().getInt("count"));
    }

    @Test
    void testMultipleSequentialEntries() throws IOException {
        FluidDescriptor water = new FluidDescriptor(AEFluidKey.of(Fluids.WATER));
        FluidDescriptor lava = new FluidDescriptor(AEFluidKey.of(Fluids.LAVA));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        FluidDescriptorCodec.INSTANCE.write(out, water);
        FluidDescriptorCodec.INSTANCE.write(out, lava);
        out.flush();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        assertEquals(water, FluidDescriptorCodec.INSTANCE.read(in));
        assertEquals(lava, FluidDescriptorCodec.INSTANCE.read(in));
    }

    @Test
    void testReadUnknownFluidReturnsNull() throws IOException {
        // 构造一个流体 id 不存在的 NBT,AEFluidKey.fromTag 解析失败时 codec 返回 null
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "ae2enhanced:not_exist_fluid");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        net.minecraft.nbt.NbtIo.write(tag, out);
        out.flush();

        FluidDescriptor restored = FluidDescriptorCodec.INSTANCE
                .read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));

        assertNull(restored);
    }

    @Test
    void testReadEmptyStreamThrowsEOF() {
        // NbtIo.read 对空输入抛出 EOFException,codec 不做额外吞没
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        assertThrows(EOFException.class, () -> FluidDescriptorCodec.INSTANCE.read(in));
    }
}
