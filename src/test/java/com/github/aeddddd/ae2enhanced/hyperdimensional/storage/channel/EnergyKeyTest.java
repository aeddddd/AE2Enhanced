package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnergyKey} 单元测试.
 */
class EnergyKeyTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testTypeAndIdentity() {
        // 能量 key 为单例,且 type 始终为内部 ENERGY_KEY_TYPE
        assertSame(EnergyKey.ENERGY_KEY_TYPE, EnergyKey.INSTANCE.getType());
        assertSame(EnergyKey.INSTANCE, EnergyKey.INSTANCE.dropSecondary());
        assertEquals(EnergyKey.ID, EnergyKey.INSTANCE.getId());
        assertEquals(EnergyKey.ID, EnergyKey.INSTANCE.getPrimaryKey());
    }

    @Test
    void testToTagContainsId() {
        CompoundTag tag = EnergyKey.INSTANCE.toTag();
        assertEquals("ae2enhanced:energy", tag.getString("id"));
    }

    @Test
    void testKeyTypeLoadFromTagReturnsSingleton() {
        // 无论输入何种 NBT,key type 都恢复为单例
        assertSame(EnergyKey.INSTANCE, EnergyKey.ENERGY_KEY_TYPE.loadKeyFromTag(new CompoundTag()));
        assertSame(EnergyKey.INSTANCE, EnergyKey.ENERGY_KEY_TYPE.loadKeyFromTag(EnergyKey.INSTANCE.toTag()));
    }

    @Test
    void testPacketRoundTrip() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        EnergyKey.INSTANCE.writeToPacket(buf);

        assertSame(EnergyKey.INSTANCE, EnergyKey.ENERGY_KEY_TYPE.readFromPacket(buf));
    }

    @Test
    void testWrapForDisplayIsEmpty() {
        // 能量没有可展示的物品形态
        assertSame(ItemStack.EMPTY, EnergyKey.INSTANCE.wrapForDisplayOrFilter());
    }

    @Test
    void testAddDropsDoesNothing() {
        List<ItemStack> drops = new ArrayList<>();
        EnergyKey.INSTANCE.addDrops(1000L, drops, null, null);
        assertTrue(drops.isEmpty());
    }

    @Test
    void testDisplayNameIsTranslatable() {
        // 显示名为本地化组件,包含指定的翻译键
        String key = ((net.minecraft.network.chat.contents.TranslatableContents) EnergyKey.INSTANCE
                .getDisplayName().getContents()).getKey();
        assertEquals("gui.ae2enhanced.hyperdimensional.channel.energy", key);
    }
}
