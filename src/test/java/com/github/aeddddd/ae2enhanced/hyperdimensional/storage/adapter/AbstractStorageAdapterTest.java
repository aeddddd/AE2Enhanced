package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.ItemDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.ItemDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AbstractStorageAdapter} 单元测试.
 * <p>insert / extract / set / loadFrom 等公共存取逻辑已由
 * {@link ItemStorageAdapterTest} 覆盖,本类聚焦文件持久化委托
 * （{@code loadFromFile} / {@code saveToFile}）与元数据访问.</p>
 */
class AbstractStorageAdapterTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private final ItemStorageAdapter adapter = new ItemStorageAdapter();
    private final AEItemKey stone = AEItemKey.of(Items.STONE);

    @Test
    void testGetCodecAndKeyType() {
        assertSame(ItemDescriptorCodec.INSTANCE, adapter.getCodec());
        assertSame(AEKeyType.items(), adapter.getKeyType());
    }

    @Test
    void testLoadFromFileWithNullIsNoOp() {
        adapter.insert(stone, 10L, Actionable.MODULATE);
        adapter.loadFromFile(null);
        // null 文件不清空也不修改现有内容
        assertEquals(BigInteger.valueOf(10L), adapter.getEntries().get(stone));
    }

    @Test
    void testLoadFromFileInSafeModeKeepsStorage() {
        adapter.insert(stone, 10L, Actionable.MODULATE);

        HyperdimensionalStorageFile file = mock(HyperdimensionalStorageFile.class);
        when(file.isSafeMode()).thenReturn(true);
        adapter.loadFromFile(file);

        // 安全模式下直接返回,不清空现有内容,也不触发 section 加载
        assertEquals(BigInteger.valueOf(10L), adapter.getEntries().get(stone));
        verify(file, never()).loadSection(any(), anyByte(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLoadFromFileClearsAndDelegates() {
        adapter.insert(stone, 10L, Actionable.MODULATE);

        HyperdimensionalStorageFile file = mock(HyperdimensionalStorageFile.class);
        when(file.isSafeMode()).thenReturn(false);
        adapter.loadFromFile(file);

        // 加载前会先清空内存中的内容
        assertTrue(adapter.getStorageMap().isEmpty());
        // 以物品 key type、旧版类型字节 1 与物品 codec 委托给文件层
        verify(file).loadSection(eq(AEKeyType.items()), eq((byte) 1),
                eq(ItemDescriptorCodec.INSTANCE), any(BiConsumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLoadFromFileConsumerWritesIntoStorage() {
        HyperdimensionalStorageFile file = mock(HyperdimensionalStorageFile.class);
        when(file.isSafeMode()).thenReturn(false);
        adapter.loadFromFile(file);

        // 捕获传给文件层的 consumer,模拟文件层回调写入条目
        ArgumentCaptor<BiConsumer<ItemDescriptor, BigInteger>> captor = ArgumentCaptor
                .forClass(BiConsumer.class);
        verify(file).loadSection(eq(AEKeyType.items()), eq((byte) 1),
                eq(ItemDescriptorCodec.INSTANCE), captor.capture());
        captor.getValue().accept(new ItemDescriptor(stone), BigInteger.valueOf(33L));

        assertEquals(BigInteger.valueOf(33L), adapter.getEntries().get(stone));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSaveToFileWithNullIsNoOp() {
        // null 文件直接返回,不抛异常
        adapter.insert(stone, 10L, Actionable.MODULATE);
        adapter.saveToFile(null);
        assertEquals(BigInteger.valueOf(10L), adapter.getEntries().get(stone));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSaveToFileInSafeModeDoesNotSave() {
        HyperdimensionalStorageFile file = mock(HyperdimensionalStorageFile.class);
        when(file.isSafeMode()).thenReturn(true);

        adapter.saveToFile(file);

        verify(file, never()).saveSection(any(), anyInt(), any(), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSaveToFilePassesCurrentDirtyGeneration() {
        adapter.insert(stone, 10L, Actionable.MODULATE);

        HyperdimensionalStorageFile file = mock(HyperdimensionalStorageFile.class);
        when(file.isSafeMode()).thenReturn(false);
        when(file.getDirtyGeneration(AEKeyType.items())).thenReturn(7);

        adapter.saveToFile(file);

        // 保存时使用保存开始时捕获的脏代际
        verify(file).saveSection(eq(AEKeyType.items()), eq(7),
                eq(ItemDescriptorCodec.INSTANCE), any(Map.class));
    }
}
