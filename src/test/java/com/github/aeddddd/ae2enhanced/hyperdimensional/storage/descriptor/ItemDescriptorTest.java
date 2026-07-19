package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link ItemDescriptor} 单元测试。
 */
class ItemDescriptorTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testEqualityForSameItem() {
        ItemDescriptor a = new ItemDescriptor(AEItemKey.of(Items.STONE));
        ItemDescriptor b = new ItemDescriptor(AEItemKey.of(Items.STONE));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testInequalityForDifferentItems() {
        ItemDescriptor stone = new ItemDescriptor(AEItemKey.of(Items.STONE));
        ItemDescriptor dirt = new ItemDescriptor(AEItemKey.of(Items.DIRT));

        assertNotEquals(stone, dirt);
    }

    @Test
    void testInequalityAgainstOtherTypes() {
        ItemDescriptor descriptor = new ItemDescriptor(AEItemKey.of(Items.STONE));
        assertNotEquals(descriptor, new Object());
        assertFalse(descriptor.equals(null));
    }

    @Test
    void testNbtAffectsEquality() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("custom", 1);

        ItemDescriptor plain = new ItemDescriptor(AEItemKey.of(Items.STONE));
        ItemDescriptor taggedA = new ItemDescriptor(AEItemKey.of(Items.STONE, tag.copy()));
        ItemDescriptor taggedB = new ItemDescriptor(AEItemKey.of(Items.STONE, tag.copy()));

        // 相同物品相同 NBT 相等
        assertEquals(taggedA, taggedB);
        assertEquals(taggedA.hashCode(), taggedB.hashCode());
        // 有/无 NBT 不相等
        assertNotEquals(plain, taggedA);
    }

    @Test
    void testDifferentNbtNotEqual() {
        CompoundTag tagA = new CompoundTag();
        tagA.putInt("custom", 1);
        CompoundTag tagB = new CompoundTag();
        tagB.putInt("custom", 2);

        ItemDescriptor a = new ItemDescriptor(AEItemKey.of(Items.STONE, tagA));
        ItemDescriptor b = new ItemDescriptor(AEItemKey.of(Items.STONE, tagB));
        assertNotEquals(a, b);
    }

    @Test
    void testConstructorCopiesNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("custom", 1);
        ItemDescriptor descriptor = new ItemDescriptor(AEItemKey.of(Items.STONE, tag));

        // 构造后修改外部标签不应影响描述符内部状态
        tag.putInt("custom", 99);
        assertEquals(1, descriptor.getNbt().getInt("custom"));
    }

    @Test
    void testFromRawCopiesNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("custom", 1);
        ItemDescriptor descriptor = ItemDescriptor.fromRaw(AEItemKey.of(Items.STONE), tag);

        tag.putInt("custom", 99);
        assertEquals(1, descriptor.getNbt().getInt("custom"));
    }

    @Test
    void testGetNbtForPlainItem() {
        ItemDescriptor descriptor = new ItemDescriptor(AEItemKey.of(Items.STONE));
        assertNull(descriptor.getNbt());
    }

    @Test
    void testGetKeys() {
        AEItemKey key = AEItemKey.of(Items.STONE);
        ItemDescriptor descriptor = new ItemDescriptor(key);

        assertSame(key, descriptor.getAEItemKey());
        assertSame(key, descriptor.getAEKey());
    }

    @Test
    void testToNBTRoundTrip() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", "test");
        ItemDescriptor descriptor = new ItemDescriptor(AEItemKey.of(Items.STONE, tag));

        CompoundTag nbt = descriptor.toNBT();
        AEItemKey restored = AEItemKey.fromTag(nbt);

        assertNotNull(restored);
        assertEquals(descriptor, new ItemDescriptor(restored));
    }

    @Test
    void testToNBTRoundTripWithoutTag() {
        ItemDescriptor descriptor = new ItemDescriptor(AEItemKey.of(Items.DIAMOND));
        AEItemKey restored = AEItemKey.fromTag(descriptor.toNBT());

        assertNotNull(restored);
        assertEquals(descriptor, new ItemDescriptor(restored));
    }
}
