package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;
import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HyperdimensionalStorage} 单元测试。
 * <p>构造过程会遍历 {@code AEKeyTypes.getAll()}，需先引导 AE2 key type 注册表。</p>
 */
class HyperdimensionalStorageTest {

    @BeforeAll
    static void bootstrap() {
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    private HyperdimensionalStorage newStorage() {
        return new HyperdimensionalStorage(UUID.randomUUID());
    }

    @Test
    void testNexusId() {
        UUID id = UUID.randomUUID();
        assertEquals(id, new HyperdimensionalStorage(id).getNexusId());
    }

    @Test
    void testDefaultChannelsRegistered() {
        HyperdimensionalStorage storage = newStorage();

        assertNotNull(storage.getChannel(AEKeyType.items()));
        assertNotNull(storage.getChannel(AEKeyType.fluids()));
        assertNotNull(storage.getChannel(EnergyKey.ENERGY_KEY_TYPE));
        assertFalse(storage.getChannels().isEmpty());
    }

    @Test
    void testChannelsMapIsUnmodifiable() {
        HyperdimensionalStorage storage = newStorage();
        assertThrows(UnsupportedOperationException.class, () -> storage.getChannels().clear());
    }

    @Test
    void testInsertExtractItems() {
        HyperdimensionalStorage storage = newStorage();
        AEItemKey stone = AEItemKey.of(Items.STONE);

        assertEquals(64L, storage.insert(stone, 64L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(64L), storage.getContents().get(stone));

        assertEquals(20L, storage.extract(stone, 20L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(44L), storage.getContents().get(stone));
    }

    @Test
    void testInsertExtractEnergy() {
        HyperdimensionalStorage storage = newStorage();

        assertEquals(1000L, storage.insert(EnergyKey.INSTANCE, 1000L, Actionable.MODULATE));
        assertEquals(400L, storage.extract(EnergyKey.INSTANCE, 400L, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(600L), storage.getContents().get(EnergyKey.INSTANCE));
    }

    @Test
    void testSimulateDoesNotModifyContents() {
        HyperdimensionalStorage storage = newStorage();
        AEItemKey stone = AEItemKey.of(Items.STONE);

        assertEquals(64L, storage.insert(stone, 64L, Actionable.SIMULATE));
        assertTrue(storage.getContents().isEmpty());

        storage.insert(stone, 64L, Actionable.MODULATE);
        assertEquals(64L, storage.extract(stone, 64L, Actionable.SIMULATE));
        assertEquals(BigInteger.valueOf(64L), storage.getContents().get(stone));
    }

    @Test
    void testInsertRejectsInvalidInput() {
        HyperdimensionalStorage storage = newStorage();

        assertEquals(0L, storage.insert(null, 10L, Actionable.MODULATE));
        assertEquals(0L, storage.insert(AEItemKey.of(Items.STONE), 0L, Actionable.MODULATE));
        assertEquals(0L, storage.insert(AEItemKey.of(Items.STONE), -1L, Actionable.MODULATE));
        assertEquals(0L, storage.extract(null, 10L, Actionable.MODULATE));
        assertTrue(storage.getContents().isEmpty());
    }

    @Test
    void testGetContentsIsUnmodifiable() {
        HyperdimensionalStorage storage = newStorage();
        storage.insert(AEItemKey.of(Items.STONE), 1L, Actionable.MODULATE);
        assertThrows(UnsupportedOperationException.class, () -> storage.getContents().clear());
    }

    @Test
    void testGetAvailableStacksExcludesEnergy() {
        HyperdimensionalStorage storage = newStorage();
        AEItemKey stone = AEItemKey.of(Items.STONE);
        storage.insert(stone, 64L, Actionable.MODULATE);
        storage.insert(EnergyKey.INSTANCE, 1000L, Actionable.MODULATE);

        KeyCounter counter = new KeyCounter();
        storage.getAvailableStacks(counter);

        assertEquals(64L, counter.get(stone));
        // 能量为内部 key type，不暴露给 AE2 网络
        assertEquals(0L, counter.get(EnergyKey.INSTANCE));
    }

    @Test
    void testAvailableStacksCacheInvalidatedOnChange() {
        HyperdimensionalStorage storage = newStorage();
        AEItemKey stone = AEItemKey.of(Items.STONE);

        KeyCounter first = new KeyCounter();
        storage.getAvailableStacks(first);
        assertEquals(0L, first.get(stone));

        // 存储变化后缓存应自动失效并反映最新内容
        storage.insert(stone, 32L, Actionable.MODULATE);
        KeyCounter second = new KeyCounter();
        storage.getAvailableStacks(second);
        assertEquals(32L, second.get(stone));
    }

    @Test
    void testStorageListenerNotifiedOnModulate() {
        HyperdimensionalStorage storage = newStorage();
        AEItemKey stone = AEItemKey.of(Items.STONE);
        AtomicInteger calls = new AtomicInteger();
        HyperdimensionalStorage.StorageListener listener = s -> calls.incrementAndGet();

        storage.addListener(listener);
        storage.insert(stone, 1L, Actionable.MODULATE);
        assertEquals(1, calls.get());

        // 模拟操作不触发监听器
        storage.insert(stone, 1L, Actionable.SIMULATE);
        assertEquals(1, calls.get());

        // 移除监听器后不再触发
        storage.removeListener(listener);
        storage.insert(stone, 1L, Actionable.MODULATE);
        assertEquals(1, calls.get());
    }

    @Test
    void testChangeCallbackInvoked() {
        AtomicInteger calls = new AtomicInteger();
        HyperdimensionalStorage storage = new HyperdimensionalStorage(UUID.randomUUID(), s -> calls.incrementAndGet());

        storage.insert(AEItemKey.of(Items.STONE), 1L, Actionable.MODULATE);
        assertEquals(1, calls.get());
    }

    @Test
    void testSetDirectly() {
        HyperdimensionalStorage storage = newStorage();
        AEItemKey stone = AEItemKey.of(Items.STONE);

        storage.set(stone, BigInteger.valueOf(77L));
        assertEquals(BigInteger.valueOf(77L), storage.getContents().get(stone));
    }

    @Test
    void testSearchAvailableStacks() {
        HyperdimensionalStorage storage = newStorage();
        storage.insert(AEItemKey.of(Items.STONE), 10L, Actionable.MODULATE);
        storage.insert(AEItemKey.of(Items.DIRT), 20L, Actionable.MODULATE);

        // 空查询返回全部
        List<HyperdimensionalStorage.SearchEntry> all = storage.searchAvailableStacks(null);
        assertEquals(2, all.size());

        // 按注册名过滤（不区分大小写）
        List<HyperdimensionalStorage.SearchEntry> stoneOnly = storage.searchAvailableStacks("STONE");
        assertEquals(1, stoneOnly.size());
        assertEquals(AEItemKey.of(Items.STONE), stoneOnly.get(0).key());
        assertEquals(10L, stoneOnly.get(0).count());

        // 无匹配返回空列表
        assertTrue(storage.searchAvailableStacks("不存在的物品xyz").isEmpty());
    }

    @Test
    void testGetAvailableStacksPaged() {
        HyperdimensionalStorage storage = newStorage();
        storage.insert(AEItemKey.of(Items.STONE), 1L, Actionable.MODULATE);
        storage.insert(AEItemKey.of(Items.DIRT), 2L, Actionable.MODULATE);
        storage.insert(AEItemKey.of(Items.DIAMOND), 3L, Actionable.MODULATE);

        assertEquals(2, storage.getAvailableStacksPaged(0, 2).size());
        assertEquals(1, storage.getAvailableStacksPaged(1, 2).size());
        // 越界页返回空
        assertTrue(storage.getAvailableStacksPaged(2, 2).isEmpty());
        // pageSize <= 0 返回全部
        assertEquals(3, storage.getAvailableStacksPaged(0, 0).size());
    }

    @Test
    void testSafeModeAndDirtyFlagsWithoutFile() {
        HyperdimensionalStorage storage = newStorage();
        // 未绑定存储文件时不处于安全模式，也没有脏标记
        assertFalse(storage.isSafeMode());
        assertFalse(storage.isDirty());
    }
}
