package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.GenericKeyDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GenericKeyDescriptorCodec} 单元测试.
 * <p>编码依赖 {@code AEKey.toTagGeneric}/{@code AEKey.fromTagGeneric},
 * 需先引导 AE2 key type 注册表.</p>
 */
class GenericKeyDescriptorCodecTest {

    @BeforeAll
    static void bootstrap() {
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    /**
     * 将描述符编码为字节数组再解码,返回解码结果.
     */
    private static GenericKeyDescriptor roundTrip(GenericKeyDescriptor descriptor) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        GenericKeyDescriptorCodec.INSTANCE.write(out, descriptor);
        out.flush();

        return GenericKeyDescriptorCodec.INSTANCE
                .read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));
    }

    @Test
    void testRoundTripItemKey() throws IOException {
        GenericKeyDescriptor descriptor = new GenericKeyDescriptor(AEItemKey.of(Items.STONE));

        GenericKeyDescriptor restored = roundTrip(descriptor);

        assertNotNull(restored);
        assertEquals(descriptor, restored);
        assertEquals(descriptor.getAEKey(), restored.getAEKey());
    }

    @Test
    void testRoundTripFluidKeyWithNbt() throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", 1);
        GenericKeyDescriptor descriptor = new GenericKeyDescriptor(AEFluidKey.of(Fluids.WATER, tag));

        GenericKeyDescriptor restored = roundTrip(descriptor);

        assertNotNull(restored);
        assertEquals(descriptor, restored);
    }

    @Test
    void testMixedSequentialEntries() throws IOException {
        // 通用格式包含类型信息,同一流中可混合不同 key type 并依次恢复
        GenericKeyDescriptor item = new GenericKeyDescriptor(AEItemKey.of(Items.DIRT));
        GenericKeyDescriptor fluid = new GenericKeyDescriptor(AEFluidKey.of(Fluids.LAVA));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        GenericKeyDescriptorCodec.INSTANCE.write(out, item);
        GenericKeyDescriptorCodec.INSTANCE.write(out, fluid);
        out.flush();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        assertEquals(item, GenericKeyDescriptorCodec.INSTANCE.read(in));
        assertEquals(fluid, GenericKeyDescriptorCodec.INSTANCE.read(in));
    }

    @Test
    void testReadTagWithoutChannelMarkerReturnsNull() throws IOException {
        // 缺少 #c 类型标记的 NBT 无法被 fromTagGeneric 解析,codec 返回 null
        CompoundTag tag = AEItemKey.of(Items.STONE).toTag();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        net.minecraft.nbt.NbtIo.write(tag, out);
        out.flush();

        GenericKeyDescriptor restored = GenericKeyDescriptorCodec.INSTANCE
                .read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));

        org.junit.jupiter.api.Assertions.assertNull(restored);
    }

    @Test
    void testReadEmptyStreamThrowsEOF() {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        assertThrows(EOFException.class, () -> GenericKeyDescriptorCodec.INSTANCE.read(in));
    }
}
