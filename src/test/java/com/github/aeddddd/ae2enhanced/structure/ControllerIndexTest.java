package com.github.aeddddd.ae2enhanced.structure;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ControllerIndex} 单元测试.
 */
class ControllerIndexTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testInitiallyEmpty() {
        assertTrue(new ControllerIndex().getAll().isEmpty());
    }

    @Test
    void testAddAndGetAll() {
        ControllerIndex index = new ControllerIndex();
        index.add(new BlockPos(1, 2, 3));
        index.add(new BlockPos(-5, 64, 100));

        assertEquals(2, index.getAll().size());
        assertTrue(index.getAll().contains(new BlockPos(1, 2, 3)));
        assertTrue(index.getAll().contains(new BlockPos(-5, 64, 100)));
    }

    @Test
    void testAddDuplicate() {
        ControllerIndex index = new ControllerIndex();
        index.add(new BlockPos(1, 2, 3));
        index.add(new BlockPos(1, 2, 3));
        assertEquals(1, index.getAll().size());
    }

    @Test
    void testRemove() {
        ControllerIndex index = new ControllerIndex();
        index.add(new BlockPos(1, 2, 3));
        index.add(new BlockPos(4, 5, 6));

        index.remove(new BlockPos(1, 2, 3));
        assertEquals(1, index.getAll().size());
        assertFalse(index.getAll().contains(new BlockPos(1, 2, 3)));

        // 移除不存在的坐标不影响集合
        index.remove(new BlockPos(7, 8, 9));
        assertEquals(1, index.getAll().size());
    }

    @Test
    void testAddStoresImmutableCopy() {
        ControllerIndex index = new ControllerIndex();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        index.add(mutable);
        // 修改传入的可变坐标不应影响索引中保存的位置
        mutable.set(9, 9, 9);
        assertTrue(index.getAll().contains(new BlockPos(1, 2, 3)));
        assertFalse(index.getAll().contains(new BlockPos(9, 9, 9)));
    }

    @Test
    void testSaveLoadRoundTrip() {
        ControllerIndex index = new ControllerIndex();
        index.add(new BlockPos(1, 2, 3));
        index.add(new BlockPos(-5, 64, 100));
        index.add(new BlockPos(0, -64, 0));

        CompoundTag tag = index.save(new CompoundTag());
        ControllerIndex loaded = new ControllerIndex(tag);

        assertEquals(index.getAll(), loaded.getAll());
    }

    @Test
    void testSaveEmptyRoundTrip() {
        ControllerIndex index = new ControllerIndex();
        CompoundTag tag = index.save(new CompoundTag());
        ControllerIndex loaded = new ControllerIndex(tag);
        assertTrue(loaded.getAll().isEmpty());
    }

    @Test
    void testLoadClearsExistingEntries() {
        ControllerIndex index = new ControllerIndex();
        index.add(new BlockPos(1, 2, 3));
        index.add(new BlockPos(4, 5, 6));
        CompoundTag tag = index.save(new CompoundTag());

        ControllerIndex loaded = new ControllerIndex(tag);
        loaded.add(new BlockPos(7, 8, 9));
        assertEquals(3, loaded.getAll().size());

        // 重新加载应先清空再填充
        loaded.load(tag);
        assertEquals(2, loaded.getAll().size());
        assertFalse(loaded.getAll().contains(new BlockPos(7, 8, 9)));
    }

    @Test
    void testLoadFromEmptyTag() {
        ControllerIndex index = new ControllerIndex();
        index.add(new BlockPos(1, 2, 3));
        // 空标签不含 controllers 列表,加载后应为空
        index.load(new CompoundTag());
        assertTrue(index.getAll().isEmpty());
    }

    @Test
    void testGetAllIsUnmodifiable() {
        ControllerIndex index = new ControllerIndex();
        index.add(new BlockPos(1, 2, 3));
        assertThrows(UnsupportedOperationException.class,
                () -> index.getAll().add(new BlockPos(4, 5, 6)));
    }
}
