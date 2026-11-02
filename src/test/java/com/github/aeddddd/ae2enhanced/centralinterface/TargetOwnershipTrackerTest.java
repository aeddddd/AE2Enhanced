package com.github.aeddddd.ae2enhanced.centralinterface;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TargetOwnershipTracker} 每服务器所有权跟踪器的语义测试。
 * 每个用例使用独立 tracker，不替换全局服务器 scope。
 */
public class TargetOwnershipTrackerTest {

    private DualityCentralInterface ownerA;
    private DualityCentralInterface ownerB;
    private TargetBinding binding;
    private TargetOwnershipTracker tracker;

    @BeforeEach
    public void setUp() {
        tracker = new TargetOwnershipTracker();
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
        assertThat(tracker.tryAcquire(binding, ownerA)).isTrue();
        assertThat(tracker.isOwner(binding, ownerA)).isTrue();
        assertThat(tracker.isOwner(binding, ownerB)).isFalse();
    }

    /** 同一 owner 重复 acquire 幂等返回 true。 */
    @Test
    public void testAcquireSameOwnerIdempotent() {
        assertThat(tracker.tryAcquire(binding, ownerA)).isTrue();
        assertThat(tracker.tryAcquire(binding, ownerA)).isTrue();
        assertThat(tracker.isOwner(binding, ownerA)).isTrue();
    }

    /** 已被他人持有的目标 acquire 失败，且原所有权不受影响。 */
    @Test
    public void testAcquireHeldByOtherFails() {
        tracker.tryAcquire(binding, ownerA);

        assertThat(tracker.tryAcquire(binding, ownerB)).isFalse();
        assertThat(tracker.isOwner(binding, ownerA)).isTrue();
    }

    /** 非所有者调用 release 无效。 */
    @Test
    public void testReleaseByNonOwnerIsNoOp() {
        tracker.tryAcquire(binding, ownerA);

        tracker.release(binding, ownerB);

        assertThat(tracker.isOwner(binding, ownerA)).isTrue();
        assertThat(tracker.tryAcquire(binding, ownerB)).isFalse();
    }

    /** 所有者释放后目标回到空闲，可被他人获取。 */
    @Test
    public void testReleaseByOwnerFreesTarget() {
        tracker.tryAcquire(binding, ownerA);
        tracker.release(binding, ownerA);

        assertThat(tracker.isOwner(binding, ownerA)).isFalse();
        assertThat(tracker.tryAcquire(binding, ownerB)).isTrue();
    }

    /** 对未持有目标调用 release 不抛异常也不影响状态。 */
    @Test
    public void testReleaseUnheldTargetIsNoOp() {
        tracker.release(binding, ownerA);
        assertThat(tracker.tryAcquire(binding, ownerA)).isTrue();
    }

    /** releaseAll 只释放指定 owner 的全部目标，不影响其它 owner。 */
    @Test
    public void testReleaseAll() {
        TargetBinding b1 = new TargetBinding(new BlockPos(1, 0, 0), 0, "a:b");
        TargetBinding b2 = new TargetBinding(new BlockPos(2, 0, 0), 0, "a:b");
        TargetBinding b3 = new TargetBinding(new BlockPos(3, 0, 0), 0, "a:b");
        tracker.tryAcquire(b1, ownerA);
        tracker.tryAcquire(b2, ownerA);
        tracker.tryAcquire(b3, ownerB);

        tracker.releaseAll(ownerA);

        assertThat(tracker.isOwner(b1, ownerA)).isFalse();
        assertThat(tracker.isOwner(b2, ownerA)).isFalse();
        assertThat(tracker.isOwner(b3, ownerB)).isTrue();
        // 已释放的目标可被他人重新获取
        assertThat(tracker.tryAcquire(b1, ownerB)).isTrue();
    }

    /** 不同 binding（仅坐标不同）互不干扰。 */
    @Test
    public void testDifferentBindingsIndependent() {
        TargetBinding other = new TargetBinding(new BlockPos(9, 9, 9), 0, "minecraft:furnace");
        tracker.tryAcquire(binding, ownerA);
        assertThat(tracker.tryAcquire(other, ownerB)).isTrue();
    }
}
