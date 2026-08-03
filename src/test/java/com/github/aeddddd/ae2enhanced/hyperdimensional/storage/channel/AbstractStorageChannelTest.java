package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter.ItemStorageAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractStorageChannel} 单元测试,通过具体的 {@link ItemStorageChannel} 验证公共逻辑.
 * <p>NBT 加载依赖 {@code AEKey.fromTagGeneric},需先引导 AE2 key type 注册表.</p>
 */
class AbstractStorageChannelTest {

    @BeforeAll
    static void bootstrap() {
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    private final ItemStorageChannel channel = new ItemStorageChannel();
    private final AEItemKey stone = AEItemKey.of(Items.STONE);

    @Test
    void testGetAdapter() {
        assertSame(channel.getAdapter(), channel.getAdapter());
        assertTrue(channel.getAdapter() instanceof ItemStorageAdapter);
    }

    @Test
    void testInsertExtractDelegateToAdapter() {
        assertEquals(64L, channel.insert(stone, 64L, Actionable.MODULATE));
        assertEquals(20L, channel.extract(stone, 20L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(44L), channel.getContents().get(stone));
        assertEquals(BigInteger.valueOf(44L), channel.getEntries().get(stone));
    }

    @Test
    void testGetAvailableStacks() {
        channel.insert(stone, 32L, Actionable.MODULATE);

        KeyCounter counter = new KeyCounter();
        channel.getAvailableStacks(counter);

        assertEquals(32L, counter.get(stone));
    }

    @Test
    void testSetWithMatchingKey() {
        channel.set(stone, BigInteger.valueOf(77L));
        assertEquals(BigInteger.valueOf(77L), channel.getContents().get(stone));
    }

    @Test
    void testSetWithMismatchedKeyIgnored() {
        // 能量 key 不属于物品通道,set 应静默忽略
        channel.set(EnergyKey.INSTANCE, BigInteger.valueOf(10L));
        assertTrue(channel.getContents().isEmpty());
    }

    @Test
    void testLoadFromReplacesContents() {
        channel.insert(stone, 10L, Actionable.MODULATE);

        Map<AEKey, BigInteger> data = new HashMap<>();
        data.put(AEItemKey.of(Items.DIRT), BigInteger.valueOf(5L));
        channel.loadFrom(data);

        assertEquals(1, channel.getContents().size());
        assertEquals(BigInteger.valueOf(5L), channel.getContents().get(AEItemKey.of(Items.DIRT)));
    }

    @Test
    void testPersistWritesContentsList() {
        channel.insert(stone, 64L, Actionable.MODULATE);

        CompoundTag tag = new CompoundTag();
        channel.persist(tag);

        assertTrue(tag.contains("contents", Tag.TAG_LIST));
        ListTag list = tag.getList("contents", Tag.TAG_COMPOUND);
        assertEquals(1, list.size());
        CompoundTag entry = list.getCompound(0);
        assertTrue(entry.contains("key", Tag.TAG_COMPOUND));
        assertEquals("64", entry.getString("amount"));
    }

    @Test
    void testLoadReadsGenericTaggedEntries() {
        // load 依赖 AEKey.fromTagGeneric,因此 key 必须带 #c 类型标记
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("key", stone.toTagGeneric());
        entry.putString("amount", "42");
        list.add(entry);
        tag.put("contents", list);

        channel.load(tag);

        assertEquals(BigInteger.valueOf(42L), channel.getContents().get(stone));
    }

    @Test
    void testLoadSkipsInvalidEntries() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();

        // 缺少 amount 的条目被跳过
        CompoundTag missingAmount = new CompoundTag();
        missingAmount.put("key", stone.toTagGeneric());
        list.add(missingAmount);

        // 非正数量被过滤
        CompoundTag zeroAmount = new CompoundTag();
        zeroAmount.put("key", stone.toTagGeneric());
        zeroAmount.putString("amount", "0");
        list.add(zeroAmount);

        // key 缺少 #c 标记,fromTagGeneric 返回 null,条目被跳过
        CompoundTag noChannelMarker = new CompoundTag();
        noChannelMarker.put("key", stone.toTag());
        noChannelMarker.putString("amount", "10");
        list.add(noChannelMarker);

        // 唯一有效条目
        CompoundTag valid = new CompoundTag();
        valid.put("key", AEItemKey.of(Items.DIRT).toTagGeneric());
        valid.putString("amount", "7");
        list.add(valid);

        tag.put("contents", list);
        channel.load(tag);

        assertEquals(1, channel.getContents().size());
        assertEquals(BigInteger.valueOf(7L), channel.getContents().get(AEItemKey.of(Items.DIRT)));
    }

    @Test
    void testLoadWithoutContentsTagKeepsExisting() {
        channel.insert(stone, 10L, Actionable.MODULATE);

        // 没有 contents 标签时 load 直接返回,不清空已有内容
        channel.load(new CompoundTag());

        assertEquals(BigInteger.valueOf(10L), channel.getContents().get(stone));
    }

    @Test
    void testPersistLoadRoundTrip() {
        // 回归:persist 必须写 toTagGeneric(含 #c 标记),否则 load 会静默丢弃全部条目
        channel.insert(stone, 64L, Actionable.MODULATE);
        channel.insert(AEItemKey.of(Items.DIRT), 7L, Actionable.MODULATE);

        CompoundTag tag = new CompoundTag();
        channel.persist(tag);

        // 持久化的 key 必须携带 #c 类型标记
        ListTag list = tag.getList("contents", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag keyTag = list.getCompound(i).getCompound("key");
            assertTrue(keyTag.contains("#c", Tag.TAG_STRING), "持久化的 key 缺少 #c 类型标记");
        }

        ItemStorageChannel restored = new ItemStorageChannel();
        restored.load(tag);

        assertEquals(channel.getContents(), restored.getContents());
    }

    @Test
    void testEnergyChannelLoadFallback() {
        // 能量 key type 未注册进 AE2 注册表,fromTagGeneric 无法解析;
        // 能量通道应按通道类型兜底为 EnergyKey 单例
        EnergyStorageChannel energy = new EnergyStorageChannel();
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("key", new CompoundTag()); // 旧版能量描述符 toNBT 为空标签
        entry.putString("amount", "12345");
        list.add(entry);
        tag.put("contents", list);

        energy.load(tag);

        assertEquals(BigInteger.valueOf(12345L), energy.getContents().get(EnergyKey.INSTANCE));
    }

    @Test
    void testEnergyChannelPersistLoadRoundTrip() {
        EnergyStorageChannel energy = new EnergyStorageChannel();
        energy.set(EnergyKey.INSTANCE, BigInteger.valueOf(999L));

        CompoundTag tag = new CompoundTag();
        energy.persist(tag);

        EnergyStorageChannel restored = new EnergyStorageChannel();
        restored.load(tag);

        assertEquals(BigInteger.valueOf(999L), restored.getContents().get(EnergyKey.INSTANCE));
    }
}
