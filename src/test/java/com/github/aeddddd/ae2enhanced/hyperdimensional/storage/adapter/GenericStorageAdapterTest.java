package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannelConstants;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.GenericKeyDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.GenericKeyDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GenericStorageAdapter} 单元测试,同时覆盖
 * {@link AbstractStorageAdapter#loadFromDescriptors} 的过滤逻辑.
 */
class GenericStorageAdapterTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private final GenericStorageAdapter adapter = new GenericStorageAdapter(AEKeyType.fluids());
    private final AEFluidKey water = AEFluidKey.of(Fluids.WATER);

    @Test
    void testGetCodecAndKeyType() {
        assertSame(GenericKeyDescriptorCodec.INSTANCE, adapter.getCodec());
        assertSame(AEKeyType.fluids(), adapter.getKeyType());
    }

    @Test
    void testCreateDescriptor() {
        assertEquals(new GenericKeyDescriptor(water), adapter.createDescriptor(water));
    }

    @Test
    void testCastMatchesKeyType() {
        // 仅接受与构造时一致的 key type
        assertSame(water, adapter.cast(water));
        assertNull(adapter.cast(AEItemKey.of(Items.STONE)));
        assertNull(adapter.cast(null));
    }

    @Test
    void testCreateResultReturnsRequestKey() {
        assertSame(water, adapter.createResult(water, BigInteger.TEN));
    }

    @Test
    void testInsertExtract() {
        assertEquals(500L, adapter.insert(water, 500L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(500L), adapter.getEntries().get(water));

        assertEquals(200L, adapter.extract(water, 200L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(300L), adapter.getEntries().get(water));
    }

    @Test
    void testLoadFromDescriptorsFiltersInvalid() {
        GenericKeyDescriptor valid = new GenericKeyDescriptor(water);
        GenericKeyDescriptor lava = new GenericKeyDescriptor(AEFluidKey.of(Fluids.LAVA));
        GenericKeyDescriptor dirt = new GenericKeyDescriptor(AEItemKey.of(Items.DIRT));

        Map<GenericKeyDescriptor, BigInteger> data = new HashMap<>();
        data.put(valid, BigInteger.valueOf(7L));
        data.put(lava, BigInteger.ZERO); // 零被过滤
        data.put(dirt, StorageChannelConstants.CAPACITY_PER_KEY.add(BigInteger.ONE)); // 超容量被过滤

        adapter.loadFromDescriptors(data);

        assertEquals(1, adapter.getStorageMap().size());
        assertEquals(BigInteger.valueOf(7L), adapter.getEntries().get(water));
    }

    @Test
    void testLoadFromDescriptorsReplacesExisting() {
        adapter.insert(water, 10L, Actionable.MODULATE);

        Map<GenericKeyDescriptor, BigInteger> data = new HashMap<>();
        data.put(new GenericKeyDescriptor(AEFluidKey.of(Fluids.LAVA)), BigInteger.valueOf(3L));
        adapter.loadFromDescriptors(data);

        assertEquals(1, adapter.getStorageMap().size());
        assertEquals(BigInteger.valueOf(3L), adapter.getEntries().get(AEFluidKey.of(Fluids.LAVA)));
    }

    @Test
    void testLoadFromDescriptorsNullClearsStorage() {
        adapter.insert(water, 10L, Actionable.MODULATE);
        adapter.loadFromDescriptors(null);
        assertTrue(adapter.getStorageMap().isEmpty());
    }
}
