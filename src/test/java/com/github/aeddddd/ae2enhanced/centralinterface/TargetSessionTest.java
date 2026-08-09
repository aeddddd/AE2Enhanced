package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link TargetSession} 状态机测试。
 *
 * <p>覆盖 IDLE → PUSHING → PROCESSING → COLLECTING → (finish: IDLE | PROCESSING)
 * 完整生命周期、UNAVAILABLE 分支、非法迁移异常、reset 清理语义、
 * 超时与推料 grace 边界、以及跨 owner 的 beginPush 互斥。</p>
 */
public class TargetSessionTest {

    private DualityCentralInterface owner;
    private TargetBinding binding;
    private TargetSession session;

    @BeforeAll
    public static void boot() {
        // 用例中会构造 ItemStack / AEItemStack，需无头引导
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    public void setUp() {
        TargetOwnershipTracker.resetForTesting();
        this.owner = mock(DualityCentralInterface.class);
        // mock 默认 isAlive()=false 会被 tryAcquire 视为失效 owner 回收，stub 为存活以保持互斥语义
        when(this.owner.isAlive()).thenReturn(true);
        this.binding = new TargetBinding(new BlockPos(1, 2, 3), 0, "minecraft:furnace");
        this.session = new TargetSession(binding, owner);
    }

    /** 新 session 初始为 IDLE，运行时字段均为空值。 */
    @Test
    public void testInitialStateIsIdle() {
        assertThat(session.isIdle()).isTrue();
        assertThat(session.getState()).isEqualTo(TargetState.IDLE);
        assertThat(session.getBinding()).isSameAs(binding);
        assertThat(session.getExpectedOutputs()).isNull();
        assertThat(session.getInputs()).isEmpty();
        assertThat(session.getPushedFluids()).isEmpty();
        assertThat(session.getInputFluids()).isEmpty();
        assertThat(session.getPushTick()).isEqualTo(-1L);
        assertThat(session.getStartProcessAttempts()).isEqualTo(0);
        assertThat(session.getRecipeCache()).isNull();
    }

    /** beginPush 获取所有权并进入 PUSHING；传入的流体列表被拷贝，外部修改不影响 session。 */
    @Test
    public void testBeginPushAcquiresOwnership() {
        List<net.minecraftforge.fluids.FluidStack> fluids = new ArrayList<>();

        assertThat(session.beginPush(fluids)).isTrue();

        assertThat(session.getState()).isEqualTo(TargetState.PUSHING);
        assertThat(TargetOwnershipTracker.instance().isOwner(binding, owner)).isTrue();
        // null 入参视为空列表
        assertThat(session.getPushedFluids()).isNotNull().isEmpty();
    }

    /** 同一 binding 被另一 owner 持有时，第二个 session 的 beginPush 返回 false 且保持 IDLE。 */
    @Test
    public void testBeginPushMutualExclusionAcrossOwners() {
        DualityCentralInterface other = mock(DualityCentralInterface.class);
        when(other.isAlive()).thenReturn(true);
        TargetSession otherSession = new TargetSession(binding, other);

        assertThat(session.beginPush(null)).isTrue();
        assertThat(otherSession.beginPush(null)).isFalse();

        assertThat(otherSession.isIdle()).isTrue();
        assertThat(TargetOwnershipTracker.instance().isOwner(binding, owner)).isTrue();
    }

    /** IDLE 下 commitPush 抛 IllegalStateException。 */
    @Test
    public void testCommitPushFromIdleThrows() {
        assertThatThrownBy(() -> session.commitPush(null, null, null, 0L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.isIdle()).isTrue();
    }

    /** IDLE 下 beginCollect 抛 IllegalStateException。 */
    @Test
    public void testBeginCollectFromIdleThrows() {
        assertThatThrownBy(() -> session.beginCollect())
                .isInstanceOf(IllegalStateException.class);
    }

    /** 非 COLLECTING 状态下 finishCollect 抛 IllegalStateException。 */
    @Test
    public void testFinishCollectFromNonCollectingThrows() {
        assertThatThrownBy(() -> session.finishCollect(true))
                .isInstanceOf(IllegalStateException.class);

        session.beginPush(null);
        session.commitPush(null, null, null, 10L);
        assertThatThrownBy(() -> session.finishCollect(false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.isProcessing()).isTrue();
    }

    /**
     * 完整状态机：IDLE → PUSHING → PROCESSING → COLLECTING →(未完成)→ PROCESSING
     * → COLLECTING →(完成)→ IDLE；commitPush 快照语义（拷贝输入，不受外部修改影响）。
     */
    @Test
    public void testFullStateMachineCycle() {
        assertThat(session.beginPush(null)).isTrue();

        IAEItemStack[] outputs = { AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 2)) };
        List<ItemStack> inputs = new ArrayList<>();
        inputs.add(new ItemStack(Items.WHEAT, 1));
        session.commitPush(outputs, inputs, null, 100L);

        assertThat(session.isProcessing()).isTrue();
        assertThat(session.getStartTime()).isEqualTo(100L);
        assertThat(session.getPushTick()).isEqualTo(100L);
        assertThat(session.getLastStartProcessTick()).isEqualTo(-1L);
        assertThat(session.getStartProcessAttempts()).isEqualTo(0);
        assertThat(session.getPushedFluids()).isEmpty();
        assertThat(session.getExpectedOutputs()).hasSize(1);
        assertThat(session.getInputs()).hasSize(1);

        // 快照语义：修改原数组/列表不影响 session 内部数据
        outputs[0] = null;
        inputs.clear();
        assertThat(session.getExpectedOutputs()[0]).isNotNull();
        assertThat(session.getInputs()).hasSize(1);

        // 收集未完成 → 回到 PROCESSING，数据保留
        session.beginCollect();
        assertThat(session.isCollecting()).isTrue();
        session.finishCollect(false);
        assertThat(session.isProcessing()).isTrue();
        assertThat(session.getExpectedOutputs()).isNotNull();
        assertThat(session.getStartTime()).isEqualTo(100L);

        // 收集完成 → reset 回 IDLE，数据清空，所有权释放
        session.beginCollect();
        session.finishCollect(true);
        assertThat(session.isIdle()).isTrue();
        assertThat(session.getExpectedOutputs()).isNull();
        assertThat(session.getInputs()).isEmpty();
        assertThat(TargetOwnershipTracker.instance().isOwner(binding, owner)).isFalse();
    }

    /** reset 清理全部运行时字段并释放所有权，释放后其它 owner 可获取。 */
    @Test
    public void testResetClearsAllRuntimeFields() {
        session.beginPush(null);
        session.commitPush(null, null, null, 50L);
        session.setRecipeCache(new Object());
        session.incrementStartProcessAttempts();
        session.setLastStartProcessTick(77L);

        session.reset();

        assertThat(session.isIdle()).isTrue();
        assertThat(session.getStartTime()).isEqualTo(0L);
        assertThat(session.getExpectedOutputs()).isNull();
        assertThat(session.getInputs()).isEmpty();
        assertThat(session.getPushedFluids()).isEmpty();
        assertThat(session.getInputFluids()).isEmpty();
        assertThat(session.getPushTick()).isEqualTo(-1L);
        assertThat(session.getLastStartProcessTick()).isEqualTo(-1L);
        assertThat(session.getStartProcessAttempts()).isEqualTo(0);
        assertThat(session.getRecipeCache()).isNull();

        DualityCentralInterface other = mock(DualityCentralInterface.class);
        assertThat(TargetOwnershipTracker.instance().tryAcquire(binding, other)).isTrue();
    }

    /** setUnavailable 释放所有权并置 UNAVAILABLE；recoverFromUnavailable 仅从 UNAVAILABLE 恢复为 IDLE。 */
    @Test
    public void testUnavailableAndRecover() {
        session.beginPush(null);
        session.commitPush(null, null, null, 10L);

        session.setUnavailable();
        assertThat(session.isUnavailable()).isTrue();
        assertThat(TargetOwnershipTracker.instance().isOwner(binding, owner)).isFalse();

        session.recoverFromUnavailable();
        assertThat(session.isIdle()).isTrue();
    }

    /** 非 UNAVAILABLE 状态下 recoverFromUnavailable 是无操作。 */
    @Test
    public void testRecoverFromNonUnavailableIsNoOp() {
        session.recoverFromUnavailable();
        assertThat(session.isIdle()).isTrue();

        session.beginPush(null);
        session.recoverFromUnavailable();
        assertThat(session.getState()).isEqualTo(TargetState.PUSHING);
    }

    /** isTimedOut 仅在 PROCESSING / COLLECTING 生效；边界为 elapsed >= timeoutTicks。 */
    @Test
    public void testIsTimedOutOnlyInProcessingOrCollecting() {
        // IDLE / PUSHING 永不超时
        assertThat(session.isTimedOut(Long.MAX_VALUE, 1)).isFalse();
        session.beginPush(null);
        assertThat(session.isTimedOut(Long.MAX_VALUE, 1)).isFalse();

        session.commitPush(null, null, null, 100L);
        assertThat(session.isTimedOut(149, 50)).isFalse(); // elapsed 49 < 50
        assertThat(session.isTimedOut(150, 50)).isTrue();  // elapsed 50 >= 50 边界

        session.beginCollect();
        assertThat(session.isTimedOut(150, 50)).isTrue();

        session.finishCollect(true);
        session.beginPush(null);
        session.commitPush(null, null, null, 100L);
        session.setUnavailable();
        assertThat(session.isTimedOut(150, 50)).isFalse();
    }

    /** isPushGraceElapsed：pushTick < 0 恒 true；否则要求 currentWorldTime 严格大于 pushTick + grace。 */
    @Test
    public void testIsPushGraceElapsedBoundary() {
        // 初始 pushTick = -1，恒为 true
        assertThat(session.isPushGraceElapsed(0, 10)).isTrue();

        session.beginPush(null);
        // beginPush 不设置 pushTick，仍为 -1
        assertThat(session.isPushGraceElapsed(1000, 10)).isTrue();

        session.commitPush(null, null, null, 100L);
        assertThat(session.isPushGraceElapsed(110, 10)).isFalse(); // 110 > 110 不成立
        assertThat(session.isPushGraceElapsed(111, 10)).isTrue();
    }
}
