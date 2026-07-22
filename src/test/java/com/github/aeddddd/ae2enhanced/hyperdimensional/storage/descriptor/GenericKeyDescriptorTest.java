package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GenericKeyDescriptor} 单元测试.
 * <p>NBT 往返依赖 AE2 key type 注册表,需先执行 {@link AE2KeyTypeTestBootstrap}.</p>
 */
class GenericKeyDescriptorTest {

    @BeforeAll
    static void bootstrap() {
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    @Test
    void testNullKeyRejected() {
        assertThrows(NullPointerException.class, () -> new GenericKeyDescriptor(null));
    }

    @Test
    void testEqualityBasedOnAEKey() {
        GenericKeyDescriptor a = new GenericKeyDescriptor(AEItemKey.of(Items.STONE));
        GenericKeyDescriptor b = new GenericKeyDescriptor(AEItemKey.of(Items.STONE));
        GenericKeyDescriptor other = new GenericKeyDescriptor(AEItemKey.of(Items.DIRT));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, other);
    }

    @Test
    void testInequalityAgainstOtherTypes() {
        GenericKeyDescriptor descriptor = new GenericKeyDescriptor(AEItemKey.of(Items.STONE));
        assertNotEquals(descriptor, new Object());
        assertFalse(descriptor.equals(null));
        assertTrue(descriptor.equals(descriptor));
    }

    @Test
    void testGetAEKey() {
        AEItemKey key = AEItemKey.of(Items.STONE);
        assertEquals(key, new GenericKeyDescriptor(key).getAEKey());
    }

    @Test
    void testItemKeyNbtRoundTrip() {
        GenericKeyDescriptor descriptor = new GenericKeyDescriptor(AEItemKey.of(Items.STONE));

        AEKey restored = AEKey.fromTagGeneric(descriptor.toNBT());

        assertNotNull(restored);
        assertEquals(descriptor.getAEKey(), restored);
        assertEquals(descriptor, new GenericKeyDescriptor(restored));
    }

    @Test
    void testFluidKeyNbtRoundTrip() {
        GenericKeyDescriptor descriptor = new GenericKeyDescriptor(AEFluidKey.of(Fluids.WATER));

        AEKey restored = AEKey.fromTagGeneric(descriptor.toNBT());

        assertNotNull(restored);
        assertEquals(descriptor.getAEKey(), restored);
    }

    @Test
    void testToStringContainsKey() {
        AEItemKey key = AEItemKey.of(Items.STONE);
        GenericKeyDescriptor descriptor = new GenericKeyDescriptor(key);
        assertTrue(descriptor.toString().contains(key.toString()));
    }
}
