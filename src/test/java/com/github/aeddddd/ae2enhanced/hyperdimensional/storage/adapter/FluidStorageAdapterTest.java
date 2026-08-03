package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.FluidDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.FluidDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link FluidStorageAdapter} 单元测试.
 */
class FluidStorageAdapterTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private final FluidStorageAdapter adapter = new FluidStorageAdapter();
    private final AEFluidKey water = AEFluidKey.of(Fluids.WATER);

    @Test
    void testGetCodecAndKeyType() {
        assertSame(FluidDescriptorCodec.INSTANCE, adapter.getCodec());
        assertSame(AEKeyType.fluids(), adapter.getKeyType());
    }

    @Test
    void testCreateDescriptor() {
        assertEquals(new FluidDescriptor(water), adapter.createDescriptor(water));
    }

    @Test
    void testCastAcceptsFluidKeyOnly() {
        assertSame(water, adapter.cast(water));
        // 物品 key 与 null 均返回 null
        assertNull(adapter.cast(AEItemKey.of(Items.STONE)));
        assertNull(adapter.cast(null));
    }

    @Test
    void testCreateResultReturnsRequestKey() {
        // AE2 1.20.1 中 key 不携带数量,结果 key 即请求 key
        assertSame(water, adapter.createResult(water, BigInteger.TEN));
    }

    @Test
    void testInsertExtractFluid() {
        assertEquals(1000L, adapter.insert(water, 1000L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(1000L), adapter.getEntries().get(water));

        assertEquals(400L, adapter.extract(water, 400L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(600L), adapter.getEntries().get(water));
    }

    @Test
    void testNbtVariantsStoredSeparately() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", 1);
        AEFluidKey taggedWater = AEFluidKey.of(Fluids.WATER, tag);

        adapter.insert(water, 10L, Actionable.MODULATE);
        adapter.insert(taggedWater, 5L, Actionable.MODULATE);

        assertEquals(2, adapter.getStorageMap().size());
        assertEquals(BigInteger.valueOf(10L), adapter.getEntries().get(water));
        assertEquals(BigInteger.valueOf(5L), adapter.getEntries().get(taggedWater));
    }

    @Test
    void testDifferentFluidsStoredSeparately() {
        AEFluidKey lava = AEFluidKey.of(Fluids.LAVA);

        adapter.insert(water, 10L, Actionable.MODULATE);
        adapter.insert(lava, 20L, Actionable.MODULATE);

        KeyCounter counter = new KeyCounter();
        adapter.getAvailableStacks(counter);
        assertEquals(10L, counter.get(water));
        assertEquals(20L, counter.get(lava));
    }
}
