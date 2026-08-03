package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link ItemStorageChannel} 单元测试.
 */
class ItemStorageChannelTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private final ItemStorageChannel channel = new ItemStorageChannel();

    @Test
    void testGetKeyType() {
        assertSame(AEKeyType.items(), channel.getKeyType());
    }

    @Test
    void testInsertItemKey() {
        AEItemKey stone = AEItemKey.of(Items.STONE);
        assertEquals(64L, channel.insert(stone, 64L, Actionable.MODULATE));
        assertEquals(64L, channel.extract(stone, 64L, Actionable.MODULATE));
    }

    @Test
    void testRejectsFluidKey() {
        // 非物品 key 被物品通道拒绝
        assertEquals(0L, channel.insert(AEFluidKey.of(Fluids.WATER), 10L, Actionable.MODULATE));
        assertEquals(0L, channel.extract(AEFluidKey.of(Fluids.WATER), 10L, Actionable.MODULATE));
    }
}
