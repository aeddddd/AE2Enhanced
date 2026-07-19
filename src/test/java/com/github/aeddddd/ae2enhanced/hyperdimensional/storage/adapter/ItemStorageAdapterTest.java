package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannelConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ItemStorageAdapter} 单元测试，同时覆盖 {@link AbstractStorageAdapter} 的公共存取逻辑。
 */
class ItemStorageAdapterTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private final ItemStorageAdapter adapter = new ItemStorageAdapter();
    private final AEItemKey stone = AEItemKey.of(Items.STONE);

    @Test
    void testInsertModulateStoresAmount() {
        assertEquals(100L, adapter.insert(stone, 100L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(100L), adapter.getEntries().get(stone));
    }

    @Test
    void testInsertSimulateDoesNotModify() {
        adapter.insert(stone, 100L, Actionable.MODULATE);

        assertEquals(50L, adapter.insert(stone, 50L, Actionable.SIMULATE));
        // 模拟插入后内容保持不变
        assertEquals(BigInteger.valueOf(100L), adapter.getEntries().get(stone));
    }

    @Test
    void testInsertAccumulates() {
        adapter.insert(stone, 100L, Actionable.MODULATE);
        adapter.insert(stone, 20L, Actionable.MODULATE);
        assertEquals(BigInteger.valueOf(120L), adapter.getEntries().get(stone));
    }

    @Test
    void testInsertRejectsInvalidInput() {
        assertEquals(0L, adapter.insert(null, 10L, Actionable.MODULATE));
        assertEquals(0L, adapter.insert(stone, 0L, Actionable.MODULATE));
        assertEquals(0L, adapter.insert(stone, -5L, Actionable.MODULATE));
        // 类型不匹配的 key 被 cast 拒绝
        assertEquals(0L, adapter.insert(EnergyKey.INSTANCE, 10L, Actionable.MODULATE));
        assertTrue(adapter.getStorageMap().isEmpty());
    }

    @Test
    void testInsertRespectsCapacity() {
        adapter.set(stone, StorageChannelConstants.CAPACITY_PER_KEY.subtract(BigInteger.ONE));
        // 剩余容量仅 1，实际只能存入 1
        assertEquals(1L, adapter.insert(stone, 100L, Actionable.MODULATE));
        assertEquals(StorageChannelConstants.CAPACITY_PER_KEY, adapter.getEntries().get(stone));
        // 已满时无法再插入
        assertEquals(0L, adapter.insert(stone, 100L, Actionable.MODULATE));
        assertEquals(0L, adapter.insert(stone, 100L, Actionable.SIMULATE));
    }

    @Test
    void testNbtVariantsStoredSeparately() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", 1);
        AEItemKey taggedStone = AEItemKey.of(Items.STONE, tag);

        adapter.insert(stone, 10L, Actionable.MODULATE);
        adapter.insert(taggedStone, 5L, Actionable.MODULATE);

        assertEquals(2, adapter.getStorageMap().size());
        assertEquals(BigInteger.valueOf(10L), adapter.getEntries().get(stone));
        assertEquals(BigInteger.valueOf(5L), adapter.getEntries().get(taggedStone));
    }

    @Test
    void testExtractModulateRemovesAmount() {
        adapter.set(stone, BigInteger.valueOf(100L));
        assertEquals(30L, adapter.extract(stone, 30L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(70L), adapter.getEntries().get(stone));
    }

    @Test
    void testExtractCappedAtStoredAmount() {
        adapter.set(stone, BigInteger.valueOf(30L));
        // 请求数量超过存量时按存量返回
        assertEquals(30L, adapter.extract(stone, 50L, Actionable.MODULATE));
        // 取空后 key 从存储中移除
        assertTrue(adapter.getStorageMap().isEmpty());
    }

    @Test
    void testExtractSimulateDoesNotModify() {
        adapter.set(stone, BigInteger.valueOf(30L));

        assertEquals(30L, adapter.extract(stone, 50L, Actionable.SIMULATE));
        assertEquals(BigInteger.valueOf(30L), adapter.getEntries().get(stone));
    }

    @Test
    void testExtractRejectsInvalidInput() {
        adapter.set(stone, BigInteger.valueOf(30L));

        assertEquals(0L, adapter.extract(null, 10L, Actionable.MODULATE));
        assertEquals(0L, adapter.extract(stone, 0L, Actionable.MODULATE));
        assertEquals(0L, adapter.extract(stone, -1L, Actionable.MODULATE));
        assertEquals(0L, adapter.extract(EnergyKey.INSTANCE, 10L, Actionable.MODULATE));
        assertEquals(0L, adapter.extract(AEItemKey.of(Items.DIRT), 10L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(30L), adapter.getEntries().get(stone));
    }

    @Test
    void testSetValidAmount() {
        adapter.set(stone, BigInteger.valueOf(42L));
        assertEquals(BigInteger.valueOf(42L), adapter.getEntries().get(stone));
    }

    @Test
    void testSetAtCapacity() {
        adapter.set(stone, StorageChannelConstants.CAPACITY_PER_KEY);
        assertEquals(StorageChannelConstants.CAPACITY_PER_KEY, adapter.getEntries().get(stone));
    }

    @Test
    void testSetInvalidAmountRemovesEntry() {
        adapter.set(stone, BigInteger.valueOf(10L));

        // 零、负数、超容量、null 均视为非法，会移除条目
        adapter.set(stone, BigInteger.ZERO);
        assertTrue(adapter.getStorageMap().isEmpty());

        adapter.set(stone, BigInteger.valueOf(10L));
        adapter.set(stone, BigInteger.valueOf(-1L));
        assertTrue(adapter.getStorageMap().isEmpty());

        adapter.set(stone, BigInteger.valueOf(10L));
        adapter.set(stone, StorageChannelConstants.CAPACITY_PER_KEY.add(BigInteger.ONE));
        assertTrue(adapter.getStorageMap().isEmpty());

        adapter.set(stone, BigInteger.valueOf(10L));
        adapter.set(stone, null);
        assertTrue(adapter.getStorageMap().isEmpty());
    }

    @Test
    void testLoadFromFiltersInvalidEntries() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", 1);
        AEItemKey taggedStone = AEItemKey.of(Items.STONE, tag);

        Map<AEKey, BigInteger> data = new HashMap<>();
        data.put(stone, BigInteger.valueOf(7L));
        data.put(taggedStone, BigInteger.ZERO); // 零被过滤
        data.put(EnergyKey.INSTANCE, BigInteger.valueOf(3L)); // 类型不匹配被过滤
        data.put(AEItemKey.of(Items.DIRT), StorageChannelConstants.CAPACITY_PER_KEY.add(BigInteger.ONE)); // 超容量被过滤

        adapter.loadFrom(data);

        assertEquals(1, adapter.getStorageMap().size());
        assertEquals(BigInteger.valueOf(7L), adapter.getEntries().get(stone));
    }

    @Test
    void testLoadFromNullClearsStorage() {
        adapter.set(stone, BigInteger.valueOf(10L));
        adapter.loadFrom(null);
        assertTrue(adapter.getStorageMap().isEmpty());
    }

    @Test
    void testLoadFromReplacesExistingContent() {
        adapter.set(stone, BigInteger.valueOf(10L));

        Map<AEKey, BigInteger> data = new HashMap<>();
        data.put(AEItemKey.of(Items.DIRT), BigInteger.valueOf(5L));
        adapter.loadFrom(data);

        assertEquals(1, adapter.getStorageMap().size());
        assertEquals(BigInteger.valueOf(5L), adapter.getEntries().get(AEItemKey.of(Items.DIRT)));
    }

    @Test
    void testGetAvailableStacksWritesToCounter() {
        adapter.set(stone, BigInteger.valueOf(64L));
        adapter.set(AEItemKey.of(Items.DIRT), BigInteger.valueOf(32L));

        KeyCounter counter = new KeyCounter();
        adapter.getAvailableStacks(counter);

        assertEquals(64L, counter.get(stone));
        assertEquals(32L, counter.get(AEItemKey.of(Items.DIRT)));
    }

    @Test
    void testGetAvailableStacksClampsToLongMax() {
        adapter.set(stone, StorageChannelConstants.CAPACITY_PER_KEY);

        KeyCounter counter = new KeyCounter();
        adapter.getAvailableStacks(counter);

        // 超出 long 上限的数量在对外暴露时钳制到 Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, counter.get(stone));
    }

    @Test
    void testReturnedMapsAreUnmodifiable() {
        adapter.set(stone, BigInteger.valueOf(1L));
        assertThrows(UnsupportedOperationException.class, () -> adapter.getStorageMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> adapter.getEntries().clear());
    }

    @Test
    void testNullKeyOperationsAreSafe() {
        // cast 对 null 应返回 null，相关操作安全无异常
        assertEquals(0L, adapter.insert(null, 1L, Actionable.MODULATE));
        assertEquals(0L, adapter.extract(null, 1L, Actionable.MODULATE));
        adapter.set(null, BigInteger.ONE);
        assertTrue(adapter.getStorageMap().isEmpty());
    }
}
