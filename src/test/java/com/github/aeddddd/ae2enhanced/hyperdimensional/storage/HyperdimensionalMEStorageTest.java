package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageMounts;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;

import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HyperdimensionalMEStorage} 单元测试.
 */
class HyperdimensionalMEStorageTest {

    @BeforeAll
    static void bootstrap() {
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    private final HyperdimensionalStorage storage = new HyperdimensionalStorage(UUID.randomUUID());
    private final IActionSource source = mock(IActionSource.class);
    private final HyperdimensionalMEStorage meStorage = new HyperdimensionalMEStorage(storage, source);
    private final AEItemKey stone = AEItemKey.of(Items.STONE);

    @Test
    void testAccessors() {
        assertSame(storage, meStorage.getInternalStorage());
        assertSame(source, meStorage.getSource());
        assertNotNull(meStorage.getDescription());
    }

    @Test
    void testInsertRejectsInvalidInput() {
        assertEquals(0L, meStorage.insert(null, 10L, Actionable.MODULATE, source));
        assertEquals(0L, meStorage.insert(stone, 0L, Actionable.MODULATE, source));
        assertEquals(0L, meStorage.insert(stone, -1L, Actionable.MODULATE, source));
    }

    @Test
    void testInsertWithoutChannelReturnsZero() {
        // 构造一个 key type 未注册任何通道的 key,insert 应返回 0
        AEKeyType unknownType = mock(AEKeyType.class);
        AEKey unknownKey = mock(AEKey.class);
        when(unknownKey.getType()).thenReturn(unknownType);

        assertEquals(0L, meStorage.insert(unknownKey, 10L, Actionable.MODULATE, source));
        assertEquals(0L, meStorage.extract(unknownKey, 10L, Actionable.MODULATE, source));
    }

    @Test
    void testInsertExtractDelegatesToStorage() {
        assertEquals(64L, meStorage.insert(stone, 64L, Actionable.MODULATE, source));
        assertEquals(64L, storage.getContents().get(stone).longValue());

        assertEquals(20L, meStorage.extract(stone, 20L, Actionable.MODULATE, source));
        assertEquals(44L, storage.getContents().get(stone).longValue());
    }

    @Test
    void testExtractRejectsInvalidInput() {
        storage.insert(stone, 10L, Actionable.MODULATE);

        assertEquals(0L, meStorage.extract(null, 10L, Actionable.MODULATE, source));
        assertEquals(0L, meStorage.extract(stone, 0L, Actionable.MODULATE, source));
        assertEquals(0L, meStorage.extract(stone, -1L, Actionable.MODULATE, source));
    }

    @Test
    void testGetAvailableStacksDelegatesToStorage() {
        storage.insert(stone, 32L, Actionable.MODULATE);

        KeyCounter counter = new KeyCounter();
        meStorage.getAvailableStacks(counter);

        assertEquals(32L, counter.get(stone));
    }

    @Test
    void testMountInventoriesMountsSelf() {
        IStorageMounts mounts = mock(IStorageMounts.class);
        meStorage.mountInventories(mounts);
        verify(mounts).mount(meStorage);
    }
}
