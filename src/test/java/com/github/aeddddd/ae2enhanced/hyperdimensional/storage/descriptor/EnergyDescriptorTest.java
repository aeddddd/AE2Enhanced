package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnergyDescriptor} 单元测试。
 */
class EnergyDescriptorTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testSingletonEquality() {
        // 单例与自身相等
        assertEquals(EnergyDescriptor.INSTANCE, EnergyDescriptor.INSTANCE);
        assertTrue(EnergyDescriptor.INSTANCE.equals(EnergyDescriptor.INSTANCE));
    }

    @Test
    void testNotEqualToOtherTypes() {
        assertFalse(EnergyDescriptor.INSTANCE.equals(null));
        assertFalse(EnergyDescriptor.INSTANCE.equals("energy"));
        assertFalse(EnergyDescriptor.INSTANCE.equals(new Object()));
    }

    @Test
    void testHashCodeIsStable() {
        assertEquals(EnergyDescriptor.INSTANCE.hashCode(), EnergyDescriptor.INSTANCE.hashCode());
    }

    @Test
    void testToNBTIsEmpty() {
        // 能量描述符无状态，序列化为空标签
        assertTrue(EnergyDescriptor.INSTANCE.toNBT().isEmpty());
    }

    @Test
    void testGetAEKeyReturnsEnergyKeySingleton() {
        assertSame(EnergyKey.INSTANCE, EnergyDescriptor.INSTANCE.getAEKey());
    }
}
