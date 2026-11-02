package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * {@link VirtualCostExtractor} 测试。
 *
 * <p>物品成本一律经 {@code itemSource}（CPU 内部缓存）路由，因此 {@code IStorageGrid}
 * 参数在本测试中用不到，直接传 null；IAEItemStack / IEnergySource 均为接口，直接 mock。</p>
 */
public class VirtualCostExtractorTest {

    private IEnergySource energy;
    private IActionSource actionSource;
    private IMEInventory<IAEItemStack> itemSource;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        this.energy = mock(IEnergySource.class);
        this.actionSource = mock(IActionSource.class);
        this.itemSource = mock(IMEInventory.class);
    }

    // ------------------------------------------------------------------
    // 能量
    // ------------------------------------------------------------------

    /** amount <= 0 时直接成功，不触碰能量源。 */
    @Test
    public void testEnergyNonPositiveAmountIsFree() {
        assertThat(VirtualCostExtractor.simulateExtractEnergy(energy, 0)).isTrue();
        assertThat(VirtualCostExtractor.simulateExtractEnergy(energy, -5)).isTrue();
        assertThat(VirtualCostExtractor.extractEnergy(energy, 0, actionSource)).isTrue();
        verifyZeroInteractions(energy);
    }

    /** epsilon 边界：实际可取 >= amount - 0.0001 即视为充足。 */
    @Test
    public void testSimulateExtractEnergyEpsilonBoundary() {
        double boundary = 100.0 - 0.0001;
        // 恰好等于边界 → 充足
        when(energy.extractAEPower(100.0, Actionable.SIMULATE, PowerMultiplier.CONFIG))
                .thenReturn(boundary);
        assertThat(VirtualCostExtractor.simulateExtractEnergy(energy, 100.0)).isTrue();

        // 低于边界 → 不足
        when(energy.extractAEPower(100.0, Actionable.SIMULATE, PowerMultiplier.CONFIG))
                .thenReturn(boundary - 0.001);
        assertThat(VirtualCostExtractor.simulateExtractEnergy(energy, 100.0)).isFalse();
    }

    /** extractEnergy 以 MODULATE 实际扣除，语义与 simulate 一致。 */
    @Test
    public void testExtractEnergyModulate() {
        when(energy.extractAEPower(50.0, Actionable.MODULATE, PowerMultiplier.CONFIG))
                .thenReturn(50.0);
        assertThat(VirtualCostExtractor.extractEnergy(energy, 50.0, actionSource)).isTrue();
        verify(energy).extractAEPower(50.0, Actionable.MODULATE, PowerMultiplier.CONFIG);
    }

    /** queryAvailableEnergy 直通：以 Double.MAX_VALUE 模拟提取并返回实际值。 */
    @Test
    public void testQueryAvailableEnergyPassthrough() {
        when(energy.extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.CONFIG))
                .thenReturn(1234.5);

        assertThat(VirtualCostExtractor.queryAvailableEnergy(energy)).isEqualTo(1234.5);
        verify(energy).extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.CONFIG);
    }

    // ------------------------------------------------------------------
    // 物品成本：simulateExtract
    // ------------------------------------------------------------------

    /** 全部成本满足 → true；走 itemSource 且为 SIMULATE。 */
    @Test
    public void testSimulateExtractAllSatisfied() {
        IAEItemStack c1 = cost(5);
        IAEItemStack c2 = cost(3);
        IAEItemStack got1 = extracted(5);
        IAEItemStack got2 = extracted(3);
        when(itemSource.extractItems(c1, Actionable.SIMULATE, actionSource)).thenReturn(got1);
        when(itemSource.extractItems(c2, Actionable.SIMULATE, actionSource)).thenReturn(got2);

        assertThat(VirtualCostExtractor.simulateExtract(null, Arrays.asList(c1, c2), actionSource, itemSource))
                .isTrue();
    }

    /** 任一成本部分满足 → false。 */
    @Test
    public void testSimulateExtractPartialReturnsFalse() {
        IAEItemStack c1 = cost(5);
        IAEItemStack partial = extracted(4);
        when(itemSource.extractItems(c1, Actionable.SIMULATE, actionSource)).thenReturn(partial);

        assertThat(VirtualCostExtractor.simulateExtract(null, Collections.singletonList(c1), actionSource, itemSource))
                .isFalse();
    }

    /** 提取返回 null（完全没有）→ false。 */
    @Test
    public void testSimulateExtractNullReturnsFalse() {
        IAEItemStack c1 = cost(5);
        when(itemSource.extractItems(c1, Actionable.SIMULATE, actionSource)).thenReturn(null);

        assertThat(VirtualCostExtractor.simulateExtract(null, Collections.singletonList(c1), actionSource, itemSource))
                .isFalse();
    }

    /** 空成本（null 或 size <= 0）被跳过，不触碰 itemSource。 */
    @Test
    public void testEmptyCostsSkipped() {
        IAEItemStack zero = cost(0);
        assertThat(VirtualCostExtractor.simulateExtract(null,
                Arrays.asList(null, zero), actionSource, itemSource)).isTrue();
        assertThat(VirtualCostExtractor.extractAll(null,
                Arrays.asList(null, zero), actionSource, itemSource)).isEmpty();
        verifyZeroInteractions(itemSource);
    }

    // ------------------------------------------------------------------
    // 物品成本：extractAll + 回滚
    // ------------------------------------------------------------------

    /** 全部满足 → 返回已提取清单（各成本的 copy），以 MODULATE 提取。 */
    @Test
    public void testExtractAllSuccessReturnsCopies() {
        IAEItemStack c1 = cost(5);
        IAEItemStack c2 = cost(3);
        IAEItemStack copy1 = extracted(5);
        IAEItemStack copy2 = extracted(3);
        IAEItemStack got1 = extracted(5);
        IAEItemStack got2 = extracted(3);
        when(c1.copy()).thenReturn(copy1);
        when(c2.copy()).thenReturn(copy2);
        when(itemSource.extractItems(c1, Actionable.MODULATE, actionSource)).thenReturn(got1);
        when(itemSource.extractItems(c2, Actionable.MODULATE, actionSource)).thenReturn(got2);

        List<IAEStack> extracted = VirtualCostExtractor.extractAll(null,
                Arrays.asList(c1, c2), actionSource, itemSource);

        assertThat(extracted).containsExactly(copy1, copy2);
    }

    /** 部分满足 → 返回 null，且已提取部分回滚到 itemSource。 */
    @Test
    public void testExtractAllPartialFailureRollsBack() {
        IAEItemStack c1 = cost(5);
        IAEItemStack c2 = cost(3);
        IAEItemStack copy1 = extracted(5);
        IAEItemStack got1 = extracted(5);
        IAEItemStack got2partial = extracted(1);
        when(c1.copy()).thenReturn(copy1);
        when(itemSource.extractItems(c1, Actionable.MODULATE, actionSource)).thenReturn(got1);
        // 第二项只能取到 1 < 3
        when(itemSource.extractItems(c2, Actionable.MODULATE, actionSource)).thenReturn(got2partial);

        List<IAEStack> result = VirtualCostExtractor.extractAll(null,
                Arrays.asList(c1, c2), actionSource, itemSource);

        assertThat(result).isNull();
        // 已提取的 c1 与当前条目实际取到的 1 个都必须回滚
        verify(itemSource).injectItems(copy1, Actionable.MODULATE, actionSource);
        verify(itemSource).injectItems(got2partial, Actionable.MODULATE, actionSource);
    }

    /** 回滚注入自身部分成功时，实际余量必须返回给调用方，不能按整份成功忽略。 */
    @Test
    public void testRollbackReturnsActualInjectionRemainder() {
        IAEItemStack original = extracted(5);
        IAEItemStack remainder = extracted(2);
        when(itemSource.injectItems(original, Actionable.MODULATE, actionSource)).thenReturn(remainder);

        List<IAEStack> remaining = VirtualCostExtractor.rollbackExtracted(null,
                Collections.singletonList(original), actionSource, itemSource);

        assertThat(remaining).containsExactly(remainder);
    }

    /** 当前条目部分提取且回滚又被拒收时，用异常把余量交给引擎保管。 */
    @Test
    public void testExtractAllPartialRollbackOverflowIsNotSilentlyDropped() {
        IAEItemStack requested = cost(3);
        IAEItemStack partial = extracted(1);
        when(itemSource.extractItems(requested, Actionable.MODULATE, actionSource)).thenReturn(partial);
        when(itemSource.injectItems(partial, Actionable.MODULATE, actionSource)).thenReturn(partial);

        try {
            VirtualCostExtractor.extractAll(null, Collections.singletonList(requested), actionSource, itemSource);
            throw new AssertionError("Expected rollback overflow");
        } catch (VirtualCostExtractor.RollbackException expected) {
            assertThat(expected.getRemaining()).containsExactly(partial);
        }
    }

    /** rollbackExtracted 对 null / 空清单无操作。 */
    @Test
    public void testRollbackExtractedNoOpForEmpty() {
        VirtualCostExtractor.rollbackExtracted(null, null, actionSource, itemSource);
        VirtualCostExtractor.rollbackExtracted(null, Collections.emptyList(), actionSource, itemSource);
        verifyZeroInteractions(itemSource);
    }

    /** rollbackExtracted 将清单逐项注入 itemSource。 */
    @Test
    public void testRollbackExtractedInjectsEachStack() {
        IAEItemStack s1 = extracted(5);
        IAEItemStack s2 = extracted(3);

        VirtualCostExtractor.rollbackExtracted(null, Arrays.asList(s1, s2), actionSource, itemSource);

        verify(itemSource).injectItems(s1, Actionable.MODULATE, actionSource);
        verify(itemSource).injectItems(s2, Actionable.MODULATE, actionSource);
    }

    // ------------------------------------------------------------------
    // queryAvailable
    // ------------------------------------------------------------------

    /** queryAvailable 以 Long.MAX_VALUE 模拟提取，返回实际可取数量。 */
    @Test
    public void testQueryAvailableMaxValueSemantics() {
        IAEItemStack c = cost(1);
        IAEItemStack request = mock(IAEItemStack.class);
        IAEItemStack avail = extracted(42);
        when(c.copy()).thenReturn(request);
        when(itemSource.extractItems(request, Actionable.SIMULATE, actionSource))
                .thenReturn(avail);

        long available = VirtualCostExtractor.queryAvailable(null, c, actionSource, itemSource);

        assertThat(available).isEqualTo(42);
        // 请求被放大到 Long.MAX_VALUE 以一次模拟得出可用量
        verify(request).setStackSize(Long.MAX_VALUE);
    }

    /** 模拟提取返回 null → 可用量为 0。 */
    @Test
    public void testQueryAvailableNullExtractReturnsZero() {
        IAEItemStack c = cost(1);
        IAEItemStack request = mock(IAEItemStack.class);
        when(c.copy()).thenReturn(request);
        when(itemSource.extractItems(request, Actionable.SIMULATE, actionSource)).thenReturn(null);

        assertThat(VirtualCostExtractor.queryAvailable(null, c, actionSource, itemSource)).isEqualTo(0);
    }

    /** 空成本直接返回 0，不触碰 itemSource。 */
    @Test
    public void testQueryAvailableEmptyCostReturnsZero() {
        assertThat(VirtualCostExtractor.queryAvailable(null, null, actionSource, itemSource)).isEqualTo(0);
        assertThat(VirtualCostExtractor.queryAvailable(null, cost(0), actionSource, itemSource)).isEqualTo(0);
        verifyZeroInteractions(itemSource);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 构造指定尺寸的 IAEItemStack 成本 mock。 */
    private static IAEItemStack cost(long size) {
        IAEItemStack stack = mock(IAEItemStack.class);
        when(stack.getStackSize()).thenReturn(size);
        return stack;
    }

    /** 构造"实际提取到 size"的 IAEItemStack mock。 */
    private static IAEItemStack extracted(long size) {
        return cost(size);
    }
}
