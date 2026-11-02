package com.github.aeddddd.ae2enhanced.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NBTTagCompound;
import com.github.aeddddd.ae2enhanced.block.ControllerIndex;
import net.minecraft.util.math.BlockPos;

/**
 * {@link ControllerIndex} 的集合语义、脏标记与 NBT 序列化测试。
 * 静态 get(World) 依赖服务端运行环境，不在本测试范围内。
 */
public class ControllerIndexTest {

    @org.junit.jupiter.api.BeforeAll
    public static void boot() {
        com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap.boot();
    }

    private static final BlockPos POS_A = new BlockPos(10, 64, -20);
    private static final BlockPos POS_B = new BlockPos(-5, 70, 300);

    /** add 后 getAll 可见；remove 后消失。 */
    @Test
    public void testAddContainsRemove() {
        ControllerIndex index = new ControllerIndex();

        index.add(POS_A);
        index.add(POS_B);

        assertThat(index.getAll()).containsExactlyInAnyOrder(POS_A, POS_B);

        index.remove(POS_A);
        assertThat(index.getAll()).containsExactly(POS_B);
    }

    /** 重复 add 同一位置是幂等的（Set 语义，不重复计数）。 */
    @Test
    public void testAddDuplicateIsIdempotent() {
        ControllerIndex index = new ControllerIndex();

        index.add(POS_A);
        index.add(POS_A);
        index.add(new BlockPos(10, 64, -20)); // 等值但不同实例

        assertThat(index.getAll()).hasSize(1);
    }

    /** 脏标记：新实例不脏；真实变更（新增/删除存在的项）置脏。 */
    @Test
    public void testRealChangesMarkDirty() {
        ControllerIndex index = new ControllerIndex();
        assertThat(index.isDirty()).isFalse();

        index.add(POS_A);
        assertThat(index.isDirty()).isTrue();

        index.setDirty(false);
        index.remove(POS_A);
        assertThat(index.isDirty()).isTrue();
    }

    /** 脏标记：重复 add 已存在的位置、remove 不存在的位置均不产生脏标记。 */
    @Test
    public void testNoopChangesDoNotMarkDirty() {
        ControllerIndex index = new ControllerIndex();
        index.add(POS_A);

        index.setDirty(false);
        index.add(POS_A);
        assertThat(index.isDirty()).isFalse();

        index.remove(POS_B); // 不存在
        assertThat(index.isDirty()).isFalse();
    }

    /** getAll 返回不可修改集合，尝试修改抛 UnsupportedOperationException。 */
    @Test
    public void testGetAllIsUnmodifiable() {
        ControllerIndex index = new ControllerIndex();
        index.add(POS_A);

        Set<BlockPos> all = index.getAll();
        assertThatThrownBy(() -> all.add(POS_B))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> all.remove(POS_A))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** NBT 往返：多个 BlockPos（含负坐标）全部读回。 */
    @Test
    public void testNbtRoundTrip() {
        ControllerIndex original = new ControllerIndex();
        original.add(POS_A);
        original.add(POS_B);
        original.add(new BlockPos(-29999999, 0, 29999999));

        ControllerIndex restored = new ControllerIndex();
        restored.readFromNBT(original.writeToNBT(new NBTTagCompound()));

        assertThat(restored.getAll()).containsExactlyInAnyOrder(
                POS_A, POS_B, new BlockPos(-29999999, 0, 29999999));
    }

    /** 读取空 tag 得到空索引；readFromNBT 会清空之前的内容。 */
    @Test
    public void testReadFromEmptyTagClearsPreviousContent() {
        ControllerIndex index = new ControllerIndex();
        index.add(POS_A);

        index.readFromNBT(new NBTTagCompound());

        assertThat(index.getAll()).isEmpty();
    }

    @Test
    public void chunkLookupIsRestoredFromNbtAndSelfHealsReplacedController() {
        ControllerIndex index = new ControllerIndex();
        index.add(POS_A);
        ControllerIndex restored = new ControllerIndex();
        restored.readFromNBT(index.writeToNBT(new NBTTagCompound()));
        net.minecraft.world.World world = org.mockito.Mockito.mock(net.minecraft.world.World.class);
        org.mockito.Mockito.when(world.isBlockLoaded(POS_A)).thenReturn(true);
        org.mockito.Mockito.when(world.getBlockState(POS_A)).thenReturn(net.minecraft.init.Blocks.STONE.getDefaultState());
        net.minecraft.util.math.ChunkPos chunk = new net.minecraft.util.math.ChunkPos(POS_A);

        assertThat(restored.getInChunk(world, chunk, net.minecraft.init.Blocks.STONE)).containsExactly(POS_A);
        assertThat(restored.getInChunk(world, chunk, net.minecraft.init.Blocks.DIRT)).isEmpty();
        assertThat(restored.getAll()).isEmpty();
        assertThat(restored.isDirty()).isTrue();
    }
}
