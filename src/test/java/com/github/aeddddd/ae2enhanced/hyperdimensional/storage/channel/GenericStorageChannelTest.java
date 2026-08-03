package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GenericStorageChannel} 单元测试.
 * <p>NBT 往返依赖 {@code AEKey.fromTagGeneric},需先引导 AE2 key type 注册表.</p>
 */
class GenericStorageChannelTest {

    @BeforeAll
    static void bootstrap() {
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    private final GenericStorageChannel channel = new GenericStorageChannel(AEKeyType.fluids());
    private final AEFluidKey water = AEFluidKey.of(Fluids.WATER);

    @Test
    void testGetKeyTypeMatchesConstructorArgument() {
        assertSame(AEKeyType.fluids(), channel.getKeyType());
        assertSame(AEKeyType.items(), new GenericStorageChannel(AEKeyType.items()).getKeyType());
    }

    @Test
    void testInsertExtractMatchingKeyType() {
        assertEquals(1000L, channel.insert(water, 1000L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(1000L), channel.getContents().get(water));

        assertEquals(250L, channel.extract(water, 250L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(750L), channel.getContents().get(water));
    }

    @Test
    void testRejectsOtherKeyType() {
        // 通用通道按构造时的 key type 过滤,物品 key 被拒绝
        assertEquals(0L, channel.insert(AEItemKey.of(Items.STONE), 10L, Actionable.MODULATE));
        assertEquals(0L, channel.extract(AEItemKey.of(Items.STONE), 10L, Actionable.MODULATE));
        assertTrue(channel.getContents().isEmpty());
    }

    @Test
    void testGetAvailableStacks() {
        channel.insert(water, 500L, Actionable.MODULATE);

        KeyCounter counter = new KeyCounter();
        channel.getAvailableStacks(counter);

        assertEquals(500L, counter.get(water));
    }

    @Test
    void testNbtPersistLoadRoundTrip() {
        // 通用描述符的 toNBT 带 #c 类型标记,persist -> load 可完整往返
        channel.insert(water, 777L, Actionable.MODULATE);

        CompoundTag tag = new CompoundTag();
        channel.persist(tag);

        GenericStorageChannel restored = new GenericStorageChannel(AEKeyType.fluids());
        restored.load(tag);

        assertEquals(BigInteger.valueOf(777L), restored.getContents().get(water));
    }
}
