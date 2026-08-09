package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;

/**
 * {@link TargetOwnershipTracker} 全局所有权跟踪器的语义测试。
 * 每个用例前通过 {@link TargetOwnershipTracker#resetForTesting()} 隔离全局单例状态。
 */
public class TargetOwnershipTrackerTest {

    private DualityCentralInterface ownerA;
    private DualityCentralInterface ownerB;
    private TargetBinding binding;

    @BeforeEach
    public void setUp() {
        TargetOwnershipTracker.resetForTesting();
        this.ownerA = mock(DualityCentralInterface.class);
        this.ownerB = mock(DualityCentralInterface.class);
        // mock 默认 isAlive()=false 会被 tryAcquire 视为失效 owner 回收，stub 为存活以保持互斥语义
        when(this.ownerA.isAlive()).thenReturn(true);
        when(this.ownerB.isAlive()).thenReturn(true);
        this.binding = new TargetBinding(new BlockPos(1, 2, 3), 0, "minecraft:furnace");
    }

    /** 空闲目标可获取，获取后 isOwner 成立。 */
    @Test
    public void testAcquireFreeTarget() {
        assertThat(TargetOwnershipTracker.instance().tryAcquire(binding, ownerA)).isTrue();
        assertThat(TargetOwnershipTracker.instance().isOwner(binding, ownerA)).isTrue();
        assertThat(TargetOwnershipTracker.instance().isOwner(binding, ownerB)).isFalse();
    }

    /** 同一 owner 重复 acquire 幂等返回 true。 */
    @Test
    public void testAcquireSameOwnerIdempotent() {
        assertThat(TargetOwnershipTracker.instance().tryAcquire(binding, ownerA)).isTrue();
        assertThat(TargetOwnershipTracker.instance().tryAcquire(binding, ownerA)).isTrue();
        assertThat(TargetOwnershipTracker.instance().isOwner(binding, ownerA)).isTrue();
    }

    /** 已被他人持有的目标 acquire 失败，且原所有权不受影响。 */
    @Test
    public void testAcquireHeldByOtherFails() {
        TargetOwnershipTracker.instance().tryAcquire(binding, ownerA);

        assertThat(TargetOwnershipTracker.instance().tryAcquire(binding, ownerB)).isFalse();
        assertThat(TargetOwnershipTracker.instance().isOwner(binding, ownerA)).isTrue();
    }

    /** 非所有者调用 release 无效。 */
    @Test
    public void testReleaseByNonOwnerIsNoOp() {
        TargetOwnershipTracker.instance().tryAcquire(binding, ownerA);

        TargetOwnershipTracker.instance().release(binding, ownerB);

        assertThat(TargetOwnershipTracker.instance().isOwner(binding, ownerA)).isTrue();
        assertThat(TargetOwnershipTracker.instance().tryAcquire(binding, ownerB)).isFalse();
    }

    /** 所有者释放后目标回到空闲，可被他人获取。 */
    @Test
    public void testReleaseByOwnerFreesTarget() {
        TargetOwnershipTracker.instance().tryAcquire(binding, ownerA);
        TargetOwnershipTracker.instance().release(binding, ownerA);

        assertThat(TargetOwnershipTracker.instance().isOwner(binding, ownerA)).isFalse();
        assertThat(TargetOwnershipTracker.instance().tryAcquire(binding, ownerB)).isTrue();
    }

    /** 对未持有目标调用 release 不抛异常也不影响状态。 */
    @Test
    public void testReleaseUnheldTargetIsNoOp() {
        TargetOwnershipTracker.instance().release(binding, ownerA);
        assertThat(TargetOwnershipTracker.instance().tryAcquire(binding, ownerA)).isTrue();
    }

    /** releaseAll 只释放指定 owner 的全部目标，不影响其它 owner。 */
    @Test
    public void testReleaseAll() {
        TargetBinding b1 = new TargetBinding(new BlockPos(1, 0, 0), 0, "a:b");
        TargetBinding b2 = new TargetBinding(new BlockPos(2, 0, 0), 0, "a:b");
        TargetBinding b3 = new TargetBinding(new BlockPos(3, 0, 0), 0, "a:b");
        TargetOwnershipTracker.instance().tryAcquire(b1, ownerA);
        TargetOwnershipTracker.instance().tryAcquire(b2, ownerA);
        TargetOwnershipTracker.instance().tryAcquire(b3, ownerB);

        TargetOwnershipTracker.instance().releaseAll(ownerA);

        assertThat(TargetOwnershipTracker.instance().isOwner(b1, ownerA)).isFalse();
        assertThat(TargetOwnershipTracker.instance().isOwner(b2, ownerA)).isFalse();
        assertThat(TargetOwnershipTracker.instance().isOwner(b3, ownerB)).isTrue();
        // 已释放的目标可被他人重新获取
        assertThat(TargetOwnershipTracker.instance().tryAcquire(b1, ownerB)).isTrue();
    }

    /** 不同 binding（仅坐标不同）互不干扰。 */
    @Test
    public void testDifferentBindingsIndependent() {
        TargetBinding other = new TargetBinding(new BlockPos(9, 9, 9), 0, "minecraft:furnace");
        TargetOwnershipTracker.instance().tryAcquire(binding, ownerA);
        assertThat(TargetOwnershipTracker.instance().tryAcquire(other, ownerB)).isTrue();
    }
}
