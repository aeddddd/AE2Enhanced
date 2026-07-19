package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.ItemDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ItemDescriptorCodec} 单元测试。
 */
class ItemDescriptorCodecTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    /**
     * 将描述符编码为字节数组再解码，返回解码结果。
     */
    private static ItemDescriptor roundTrip(ItemDescriptor descriptor) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        ItemDescriptorCodec.INSTANCE.write(out, descriptor);
        out.flush();

        return ItemDescriptorCodec.INSTANCE.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));
    }

    @Test
    void testRoundTripWithoutNbt() throws IOException {
        ItemDescriptor descriptor = new ItemDescriptor(AEItemKey.of(Items.STONE));

        ItemDescriptor restored = roundTrip(descriptor);

        assertNotNull(restored);
        assertEquals(descriptor, restored);
        assertEquals(descriptor.hashCode(), restored.hashCode());
    }

    @Test
    void testRoundTripWithNbt() throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putString("custom", "value");
        tag.putInt("count", 7);
        ItemDescriptor descriptor = new ItemDescriptor(AEItemKey.of(Items.DIAMOND_SWORD, tag));

        ItemDescriptor restored = roundTrip(descriptor);

        assertNotNull(restored);
        assertEquals(descriptor, restored);
        assertEquals("value", restored.getNbt().getString("custom"));
        assertEquals(7, restored.getNbt().getInt("count"));
    }

    @Test
    void testMultipleSequentialEntries() throws IOException {
        ItemDescriptor stone = new ItemDescriptor(AEItemKey.of(Items.STONE));
        ItemDescriptor dirt = new ItemDescriptor(AEItemKey.of(Items.DIRT));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        ItemDescriptorCodec.INSTANCE.write(out, stone);
        ItemDescriptorCodec.INSTANCE.write(out, dirt);
        out.flush();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        assertEquals(stone, ItemDescriptorCodec.INSTANCE.read(in));
        assertEquals(dirt, ItemDescriptorCodec.INSTANCE.read(in));
    }

    @Test
    void testReadEmptyStreamThrowsEOF() {
        // NbtIo.read 对空输入抛出 EOFException，codec 不做额外吞没
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        assertThrows(java.io.EOFException.class, () -> ItemDescriptorCodec.INSTANCE.read(in));
    }
}
