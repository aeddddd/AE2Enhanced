package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraftforge.fml.ModList;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.ItemStorageChannel;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OptionalStorageManager} 单元测试.
 * <p>通过静态 mock {@link ModList} 模拟 Mod 加载状态.</p>
 */
class OptionalStorageManagerTest {

    @Test
    void testSingletonInstance() {
        assertSame(OptionalStorageManager.getInstance(), OptionalStorageManager.getInstance());
    }

    @Test
    void testSkipsUnloadedMods() {
        HyperdimensionalStorage storage = mock(HyperdimensionalStorage.class);
        ModList modList = mock(ModList.class);
        when(modList.isLoaded("ae2e_test_absent")).thenReturn(false);

        OptionalStorageManager manager = OptionalStorageManager.getInstance();
        manager.registerOptional("ae2e_test_absent", ItemStorageChannel::new);

        try (MockedStatic<ModList> mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(modList);
            manager.registerOptionalChannels(storage);
        }

        // 未加载的 Mod 不会触发通道注册
        verify(storage, never()).registerChannel(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testRegistersChannelForLoadedMod() {
        HyperdimensionalStorage storage = mock(HyperdimensionalStorage.class);
        ModList modList = mock(ModList.class);
        when(modList.isLoaded("ae2e_test_present")).thenReturn(true);

        StorageChannel<?> channel = new ItemStorageChannel();
        OptionalStorageManager manager = OptionalStorageManager.getInstance();
        manager.registerOptional("ae2e_test_present", () -> channel);

        try (MockedStatic<ModList> mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(modList);
            manager.registerOptionalChannels(storage);
        }

        verify(storage).registerChannel(channel);
    }

    @Test
    void testNullChannelFactoryResultIgnored() {
        HyperdimensionalStorage storage = mock(HyperdimensionalStorage.class);
        ModList modList = mock(ModList.class);
        when(modList.isLoaded("ae2e_test_null")).thenReturn(true);

        OptionalStorageManager manager = OptionalStorageManager.getInstance();
        manager.registerOptional("ae2e_test_null", () -> null);

        try (MockedStatic<ModList> mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(modList);
            manager.registerOptionalChannels(storage);
        }

        // 工厂返回 null 时不注册任何通道
        verify(storage, never()).registerChannel(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testThrowingFactoryDoesNotPropagate() {
        HyperdimensionalStorage storage = mock(HyperdimensionalStorage.class);
        ModList modList = mock(ModList.class);
        when(modList.isLoaded("ae2e_test_broken")).thenReturn(true);

        OptionalStorageManager manager = OptionalStorageManager.getInstance();
        manager.registerOptional("ae2e_test_broken", () -> {
            throw new IllegalStateException("模拟第三方集成初始化失败");
        });

        try (MockedStatic<ModList> mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(modList);
            // 工厂异常被捕获并记录,不向外抛出
            assertDoesNotThrow(() -> manager.registerOptionalChannels(storage));
        }

        verify(storage, never()).registerChannel(org.mockito.ArgumentMatchers.any());
    }
}
