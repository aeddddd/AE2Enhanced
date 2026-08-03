package com.github.aeddddd.ae2enhanced.util;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link BlockEntityRemovalHelper} 单元测试.
 * <p>逻辑为纯静态集合（WeakHashMap）操作,BlockEntity 用 Mockito mock 即可.</p>
 */
class BlockEntityRemovalHelperTest {

    @Test
    void unmarkedBlockEntityIsNotBroken() {
        BlockEntity be = mock(BlockEntity.class);
        assertThat(BlockEntityRemovalHelper.isBlockBeingBroken(be)).isFalse();
    }

    @Test
    void markedBlockEntityIsBroken() {
        BlockEntity be = mock(BlockEntity.class);
        BlockEntityRemovalHelper.markForBreak(be);
        assertThat(BlockEntityRemovalHelper.isBlockBeingBroken(be)).isTrue();
    }

    @Test
    void markIsPerInstance() {
        // 标记一个实例不影响其他实例
        BlockEntity marked = mock(BlockEntity.class);
        BlockEntity other = mock(BlockEntity.class);
        BlockEntityRemovalHelper.markForBreak(marked);
        assertThat(BlockEntityRemovalHelper.isBlockBeingBroken(other)).isFalse();
    }

    @Test
    void nullBlockEntityIsIgnored() {
        // null 参数不应抛异常,且判断结果为 false
        BlockEntityRemovalHelper.markForBreak(null);
        assertThat(BlockEntityRemovalHelper.isBlockBeingBroken(null)).isFalse();
    }
}
