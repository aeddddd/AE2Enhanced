package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannelConstants;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.EnergyDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HyperdimensionalEnergyStorageAdapter} 单元测试.
 */
class HyperdimensionalEnergyStorageAdapterTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private final HyperdimensionalEnergyStorageAdapter adapter = new HyperdimensionalEnergyStorageAdapter();

    @Test
    void testCreateDescriptorReturnsSingleton() {
        // 能量只有一种类型,描述符始终为单例
        assertSame(EnergyDescriptor.INSTANCE, adapter.createDescriptor(EnergyKey.INSTANCE));
    }

    @Test
    void testCastAcceptsEnergyKeyOnly() {
        assertSame(EnergyKey.INSTANCE, adapter.cast(EnergyKey.INSTANCE));
        // 其他 AE key 类型返回 null
        assertEquals(null, adapter.cast(AEItemKey.of(Items.STONE)));
    }

    @Test
    void testInsertExtractEnergy() {
        assertEquals(1000L, adapter.insert(EnergyKey.INSTANCE, 1000L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(1000L), adapter.getEntries().get(EnergyKey.INSTANCE));

        assertEquals(400L, adapter.extract(EnergyKey.INSTANCE, 400L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(600L), adapter.getEntries().get(EnergyKey.INSTANCE));
    }

    @Test
    void testSimulateDoesNotModify() {
        adapter.insert(EnergyKey.INSTANCE, 500L, Actionable.MODULATE);

        assertEquals(500L, adapter.extract(EnergyKey.INSTANCE, 500L, Actionable.SIMULATE));
        assertEquals(200L, adapter.insert(EnergyKey.INSTANCE, 200L, Actionable.SIMULATE));
        assertEquals(BigInteger.valueOf(500L), adapter.getEntries().get(EnergyKey.INSTANCE));
    }

    @Test
    void testInsertRejectsNonEnergyKey() {
        assertEquals(0L, adapter.insert(AEItemKey.of(Items.STONE), 10L, Actionable.MODULATE));
        assertTrue(adapter.getStorageMap().isEmpty());
    }

    @Test
    void testInsertBeyondLongMax() {
        // 内部使用 BigInteger 存储,允许分多次累计超过 Long.MAX_VALUE 的总量
        adapter.set(EnergyKey.INSTANCE, BigInteger.valueOf(Long.MAX_VALUE));
        assertEquals(1L, adapter.insert(EnergyKey.INSTANCE, 1L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                adapter.getEntries().get(EnergyKey.INSTANCE));
    }

    @Test
    void testInsertRespectsCapacity() {
        adapter.set(EnergyKey.INSTANCE, StorageChannelConstants.CAPACITY_PER_KEY.subtract(BigInteger.TEN));
        assertEquals(10L, adapter.insert(EnergyKey.INSTANCE, 100L, Actionable.MODULATE));
        assertEquals(0L, adapter.insert(EnergyKey.INSTANCE, 1L, Actionable.MODULATE));
    }

    @Test
    void testGetAvailableStacks() {
        adapter.set(EnergyKey.INSTANCE, BigInteger.valueOf(12345L));

        KeyCounter counter = new KeyCounter();
        adapter.getAvailableStacks(counter);

        assertEquals(12345L, counter.get(EnergyKey.INSTANCE));
    }

    @Test
    void testExtractEmptiesRemovesEntry() {
        adapter.set(EnergyKey.INSTANCE, BigInteger.valueOf(10L));
        assertEquals(10L, adapter.extract(EnergyKey.INSTANCE, 10L, Actionable.MODULATE));
        assertTrue(adapter.getStorageMap().isEmpty());
    }
}
