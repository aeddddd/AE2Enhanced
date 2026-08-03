package com.github.aeddddd.ae2enhanced.test.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyPatternSavedData;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link AssemblyPatternSavedData} 单元测试:按位置存取与 NBT 往返.
 */
class AssemblyPatternSavedDataTest {

    static {
        MinecraftTestBootstrap.bootstrap();
    }

    /** 非 ServerLevel(如客户端 level)→ get 返回 null. */
    @Test
    void testGetReturnsNullForNonServerLevel() {
        assertThat(AssemblyPatternSavedData.get(mock(Level.class))).isNull();
    }

    /** 未写入的位置返回空标签. */
    @Test
    void testGetPatternsDefaultsToEmpty() {
        var data = new AssemblyPatternSavedData();
        assertThat(data.getPatterns(new BlockPos(1, 2, 3)).isEmpty()).isTrue();
    }

    /** 写入后可按位置读回;键格式为 x,y,z. */
    @Test
    void testSetAndGetPatterns() {
        var data = new AssemblyPatternSavedData();
        var pos = new BlockPos(10, 64, -20);
        var tag = new CompoundTag();
        tag.putInt("Size", 516);

        data.setPatterns(pos, tag);
        assertThat(data.getPatterns(pos).getInt("Size")).isEqualTo(516);

        // 不同位置互不影响
        assertThat(data.getPatterns(new BlockPos(10, 64, -21)).isEmpty()).isTrue();
    }

    /** 写入时复制:后续修改原标签不影响已存数据. */
    @Test
    void testSetPatternsCopiesInput() {
        var data = new AssemblyPatternSavedData();
        var pos = BlockPos.ZERO;
        var tag = new CompoundTag();
        tag.putInt("Size", 100);

        data.setPatterns(pos, tag);
        tag.putInt("Size", 999);

        assertThat(data.getPatterns(pos).getInt("Size")).isEqualTo(100);
    }

    /** 读取时复制:修改返回值不影响已存数据. */
    @Test
    void testGetPatternsReturnsCopy() {
        var data = new AssemblyPatternSavedData();
        var pos = BlockPos.ZERO;
        var tag = new CompoundTag();
        tag.putInt("Size", 100);
        data.setPatterns(pos, tag);

        var read = data.getPatterns(pos);
        read.putInt("Size", 999);

        assertThat(data.getPatterns(pos).getInt("Size")).isEqualTo(100);
    }

    /** removePatterns 移除后读回空标签. */
    @Test
    void testRemovePatterns() {
        var data = new AssemblyPatternSavedData();
        var pos = new BlockPos(1, 2, 3);
        var tag = new CompoundTag();
        tag.putInt("Size", 516);
        data.setPatterns(pos, tag);

        data.removePatterns(pos);
        assertThat(data.getPatterns(pos).isEmpty()).isTrue();
    }

    /** save → load 往返保留全部位置数据. */
    @Test
    void testSaveLoadRoundTrip() {
        var data = new AssemblyPatternSavedData();
        var pos1 = new BlockPos(1, 2, 3);
        var pos2 = new BlockPos(-4, 70, 8);
        var tag1 = new CompoundTag();
        tag1.putInt("Size", 516);
        var tag2 = new CompoundTag();
        tag2.putInt("Size", 10206);
        data.setPatterns(pos1, tag1);
        data.setPatterns(pos2, tag2);

        var saved = data.save(new CompoundTag());
        var loaded = AssemblyPatternSavedData.load(saved);

        assertThat(loaded.getPatterns(pos1).getInt("Size")).isEqualTo(516);
        assertThat(loaded.getPatterns(pos2).getInt("Size")).isEqualTo(10206);
    }

    /** load 对缺失 patterns 标签的 NBT 容忍(等价全新实例). */
    @Test
    void testLoadWithoutPatternsKey() {
        var loaded = AssemblyPatternSavedData.load(new CompoundTag());
        assertThat(loaded.getPatterns(BlockPos.ZERO).isEmpty()).isTrue();
    }

    /** setPatterns/removePatterns 标脏(SavedData 保存语义). */
    @Test
    void testDirtyFlag() {
        var data = new AssemblyPatternSavedData();
        assertThat(data.isDirty()).isFalse();

        data.setPatterns(BlockPos.ZERO, new CompoundTag());
        assertThat(data.isDirty()).isTrue();

        var data2 = new AssemblyPatternSavedData();
        data2.removePatterns(BlockPos.ZERO);
        assertThat(data2.isDirty()).isTrue();
    }
}
