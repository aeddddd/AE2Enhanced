package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnergyStorageChannel} 单元测试.
 */
class EnergyStorageChannelTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private final EnergyStorageChannel channel = new EnergyStorageChannel();

    @Test
    void testGetKeyType() {
        assertSame(EnergyKey.ENERGY_KEY_TYPE, channel.getKeyType());
    }

    @Test
    void testInsertExtractEnergy() {
        assertEquals(1000L, channel.insert(EnergyKey.INSTANCE, 1000L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(1000L), channel.getContents().get(EnergyKey.INSTANCE));

        assertEquals(400L, channel.extract(EnergyKey.INSTANCE, 400L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(600L), channel.getContents().get(EnergyKey.INSTANCE));
    }

    @Test
    void testRejectsItemKey() {
        assertEquals(0L, channel.insert(AEItemKey.of(Items.STONE), 10L, Actionable.MODULATE));
        assertTrue(channel.getContents().isEmpty());
    }

    @Test
    void testGetAvailableStacksIsNoOp() {
        // 能量为内部 key type,即使有存量也不暴露给 AE2 网络统计
        channel.insert(EnergyKey.INSTANCE, 1000L, Actionable.MODULATE);

        KeyCounter counter = new KeyCounter();
        channel.getAvailableStacks(counter);

        assertEquals(0L, counter.get(EnergyKey.INSTANCE));
        assertTrue(counter.isEmpty());
    }
}
