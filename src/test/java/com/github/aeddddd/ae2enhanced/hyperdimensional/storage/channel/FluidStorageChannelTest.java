package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link FluidStorageChannel} 单元测试.
 */
class FluidStorageChannelTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private final FluidStorageChannel channel = new FluidStorageChannel();
    private final AEFluidKey water = AEFluidKey.of(Fluids.WATER);

    @Test
    void testGetKeyType() {
        assertSame(AEKeyType.fluids(), channel.getKeyType());
    }

    @Test
    void testInsertExtractFluidKey() {
        assertEquals(1000L, channel.insert(water, 1000L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(1000L), channel.getContents().get(water));

        assertEquals(400L, channel.extract(water, 400L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(600L), channel.getContents().get(water));
    }

    @Test
    void testRejectsItemKey() {
        // 非流体 key 被流体通道拒绝
        assertEquals(0L, channel.insert(AEItemKey.of(Items.STONE), 10L, Actionable.MODULATE));
        assertEquals(0L, channel.extract(AEItemKey.of(Items.STONE), 10L, Actionable.MODULATE));
    }

    @Test
    void testGetAvailableStacks() {
        channel.insert(water, 500L, Actionable.MODULATE);

        KeyCounter counter = new KeyCounter();
        channel.getAvailableStacks(counter);

        assertEquals(500L, counter.get(water));
    }
}
