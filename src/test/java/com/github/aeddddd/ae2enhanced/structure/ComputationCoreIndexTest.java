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
 * {@link ComputationCoreIndex} 单元测试.
 */
class ComputationCoreIndexTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testInitiallyEmpty() {
        assertTrue(new ComputationCoreIndex().getAll().isEmpty());
    }

    @Test
    void testAddAndGetAll() {
        ComputationCoreIndex index = new ComputationCoreIndex();
        index.add(new BlockPos(10, 20, 30));
        index.add(new BlockPos(-1, 0, 1));

        assertEquals(2, index.getAll().size());
        assertTrue(index.getAll().contains(new BlockPos(10, 20, 30)));
        assertTrue(index.getAll().contains(new BlockPos(-1, 0, 1)));
    }

    @Test
    void testAddDuplicate() {
        ComputationCoreIndex index = new ComputationCoreIndex();
        index.add(new BlockPos(10, 20, 30));
        index.add(new BlockPos(10, 20, 30));
        assertEquals(1, index.getAll().size());
    }

    @Test
    void testRemove() {
        ComputationCoreIndex index = new ComputationCoreIndex();
        index.add(new BlockPos(10, 20, 30));
        index.add(new BlockPos(40, 50, 60));

        index.remove(new BlockPos(10, 20, 30));
        assertEquals(1, index.getAll().size());
        assertFalse(index.getAll().contains(new BlockPos(10, 20, 30)));

        // 移除不存在的坐标不影响集合
        index.remove(new BlockPos(70, 80, 90));
        assertEquals(1, index.getAll().size());
    }

    @Test
    void testAddStoresImmutableCopy() {
        ComputationCoreIndex index = new ComputationCoreIndex();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        index.add(mutable);
        // 修改传入的可变坐标不应影响索引中保存的位置
        mutable.set(9, 9, 9);
        assertTrue(index.getAll().contains(new BlockPos(1, 2, 3)));
        assertFalse(index.getAll().contains(new BlockPos(9, 9, 9)));
    }

    @Test
    void testSaveLoadRoundTrip() {
        ComputationCoreIndex index = new ComputationCoreIndex();
        index.add(new BlockPos(10, 20, 30));
        index.add(new BlockPos(-1, 0, 1));
        index.add(new BlockPos(0, -60, 7));

        CompoundTag tag = index.save(new CompoundTag());
        ComputationCoreIndex loaded = new ComputationCoreIndex(tag);

        assertEquals(index.getAll(), loaded.getAll());
    }

    @Test
    void testLoadClearsExistingEntries() {
        ComputationCoreIndex index = new ComputationCoreIndex();
        index.add(new BlockPos(10, 20, 30));
        CompoundTag tag = index.save(new CompoundTag());

        ComputationCoreIndex loaded = new ComputationCoreIndex(tag);
        loaded.add(new BlockPos(1, 1, 1));
        assertEquals(2, loaded.getAll().size());

        loaded.load(tag);
        assertEquals(1, loaded.getAll().size());
        assertTrue(loaded.getAll().contains(new BlockPos(10, 20, 30)));
    }

    @Test
    void testLoadFromEmptyTag() {
        ComputationCoreIndex index = new ComputationCoreIndex();
        index.add(new BlockPos(10, 20, 30));
        index.load(new CompoundTag());
        assertTrue(index.getAll().isEmpty());
    }

    @Test
    void testGetAllIsUnmodifiable() {
        ComputationCoreIndex index = new ComputationCoreIndex();
        index.add(new BlockPos(10, 20, 30));
        assertThrows(UnsupportedOperationException.class,
                () -> index.getAll().remove(new BlockPos(10, 20, 30)));
    }
}
