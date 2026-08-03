package com.github.aeddddd.ae2enhanced.util;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RecursiveCraftingHelper} 单元测试.
 * <p>IPatternDetails / IInput / ICraftingService 均为 AE2 接口,直接用 Mockito mock;
 * GenericStack 与 AEItemKey 使用真实实例（需要原版 + AE2 key type 引导）.</p>
 */
class RecursiveCraftingHelperTest {

    private static AEItemKey keyA;
    private static AEItemKey keyB;
    private static AEItemKey keyC;

    @BeforeAll
    static void bootstrap() {
        AE2KeyTypeTestBootstrap.bootstrap();
        keyA = AEItemKey.of(new ItemStack(Items.APPLE));
        keyB = AEItemKey.of(new ItemStack(Items.BEETROOT));
        keyC = AEItemKey.of(new ItemStack(Items.CARROT));
    }

    // ========== 测试辅助 ==========

    private static IPatternDetails.IInput input(GenericStack primary, long multiplier) {
        IPatternDetails.IInput input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { primary });
        when(input.getMultiplier()).thenReturn(multiplier);
        return input;
    }

    private static IPatternDetails pattern(IPatternDetails.IInput[] inputs, GenericStack[] outputs) {
        IPatternDetails details = mock(IPatternDetails.class);
        when(details.getInputs()).thenReturn(inputs);
        when(details.getOutputs()).thenReturn(outputs);
        return details;
    }

    // ========== findSelfRefKey ==========

    @Test
    void findSelfRefKeyNetPositive() {
        // A + 2B -> 2A：净增殖型自引用
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] {
                        input(new GenericStack(keyA, 1), 1),
                        input(new GenericStack(keyB, 2), 1) },
                new GenericStack[] { new GenericStack(keyA, 2) });
        assertThat(RecursiveCraftingHelper.findSelfRefKey(details)).isEqualTo(keyA);
    }

    @Test
    void findSelfRefKeyCatalyst() {
        // A + B -> A + C：产出等于投入（催化剂型）也视为自引用
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] {
                        input(new GenericStack(keyA, 1), 1),
                        input(new GenericStack(keyB, 1), 1) },
                new GenericStack[] { new GenericStack(keyA, 1), new GenericStack(keyC, 1) });
        assertThat(RecursiveCraftingHelper.findSelfRefKey(details)).isEqualTo(keyA);
    }

    @Test
    void findSelfRefKeyNetConsumptionReturnsNull() {
        // 2A -> A：净消耗型,不算自引用
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyA, 2), 1) },
                new GenericStack[] { new GenericStack(keyA, 1) });
        assertThat(RecursiveCraftingHelper.findSelfRefKey(details)).isNull();
    }

    @Test
    void findSelfRefKeyNoSelfReference() {
        // 原料与产物无交集
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyB, 1), 1) },
                new GenericStack[] { new GenericStack(keyC, 1) });
        assertThat(RecursiveCraftingHelper.findSelfRefKey(details)).isNull();
    }

    @Test
    void findSelfRefKeyAppliesMultiplier() {
        // 投入 1A × multiplier 3 = 3A,产出仅 2A：净消耗
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyA, 1), 3) },
                new GenericStack[] { new GenericStack(keyA, 2) });
        assertThat(RecursiveCraftingHelper.findSelfRefKey(details)).isNull();
    }

    @Test
    void findSelfRefKeySumsMultipleOutputs() {
        // 多个输出槽合计：1A -> 1A + 1A
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyA, 1), 1) },
                new GenericStack[] { new GenericStack(keyA, 1), new GenericStack(keyA, 1) });
        assertThat(RecursiveCraftingHelper.findSelfRefKey(details)).isEqualTo(keyA);
    }

    @Test
    void findSelfRefKeySkipsEmptyPossibleInputs() {
        IPatternDetails.IInput empty = mock(IPatternDetails.IInput.class);
        when(empty.getPossibleInputs()).thenReturn(new GenericStack[0]);
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] { empty },
                new GenericStack[] { new GenericStack(keyA, 1) });
        assertThat(RecursiveCraftingHelper.findSelfRefKey(details)).isNull();
    }

    @Test
    void findSelfRefKeySkipsNonPositiveAmount() {
        // 主候选数量 <= 0 的输入槽被跳过
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyA, 0), 1) },
                new GenericStack[] { new GenericStack(keyA, 5) });
        assertThat(RecursiveCraftingHelper.findSelfRefKey(details)).isNull();
    }

    // ========== selfInputPerCraft / selfOutputPerCraft ==========

    @Test
    void selfInputPerCraftSumsMatchingSlots() {
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] {
                        input(new GenericStack(keyA, 2), 3),
                        input(new GenericStack(keyA, 1), 1),
                        input(new GenericStack(keyB, 9), 1) },
                new GenericStack[0]);
        // 2*3 + 1*1 = 7,B 不计入
        assertThat(RecursiveCraftingHelper.selfInputPerCraft(details, keyA)).isEqualTo(7L);
        assertThat(RecursiveCraftingHelper.selfInputPerCraft(details, keyB)).isEqualTo(9L);
        assertThat(RecursiveCraftingHelper.selfInputPerCraft(details, keyC)).isZero();
    }

    @Test
    void selfOutputPerCraftSumsMatching() {
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[0],
                new GenericStack[] {
                        new GenericStack(keyA, 2),
                        new GenericStack(keyA, 3),
                        new GenericStack(keyB, 7) });
        assertThat(RecursiveCraftingHelper.selfOutputPerCraft(details, keyA)).isEqualTo(5L);
        assertThat(RecursiveCraftingHelper.selfOutputPerCraft(details, keyC)).isZero();
    }

    // ========== isNetPositiveSelfRef ==========

    @Test
    void isNetPositiveSelfRefTrue() {
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyA, 1), 1) },
                new GenericStack[] { new GenericStack(keyA, 2) });
        assertThat(RecursiveCraftingHelper.isNetPositiveSelfRef(details, keyA)).isTrue();
    }

    @Test
    void isNetPositiveSelfRefCatalystIsFalse() {
        // 产出 == 投入：不是净产出（严格大于）
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyA, 1), 1) },
                new GenericStack[] { new GenericStack(keyA, 1) });
        assertThat(RecursiveCraftingHelper.isNetPositiveSelfRef(details, keyA)).isFalse();
    }

    @Test
    void isNetPositiveSelfRefNoInputIsFalse() {
        // 原料中不含 what：投入为 0,直接 false
        IPatternDetails details = pattern(
                new IPatternDetails.IInput[0],
                new GenericStack[] { new GenericStack(keyA, 2) });
        assertThat(RecursiveCraftingHelper.isNetPositiveSelfRef(details, keyA)).isFalse();
    }

    // ========== isOnlyCandidateSelfRef ==========

    @Test
    void isOnlyCandidateSelfRefNoPattern() {
        ICraftingService service = mock(ICraftingService.class);
        when(service.getCraftingFor(keyA)).thenReturn(List.of());
        assertThat(RecursiveCraftingHelper.isOnlyCandidateSelfRef(service, keyA)).isFalse();
    }

    @Test
    void isOnlyCandidateSelfRefMultiplePatterns() {
        IPatternDetails selfRef = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyA, 1), 1) },
                new GenericStack[] { new GenericStack(keyA, 2) });
        ICraftingService service = mock(ICraftingService.class);
        // 多候选样板：不接管,保持原生择优
        when(service.getCraftingFor(keyA)).thenReturn(List.of(selfRef, selfRef));
        assertThat(RecursiveCraftingHelper.isOnlyCandidateSelfRef(service, keyA)).isFalse();
    }

    @Test
    void isOnlyCandidateSelfRefSingleNetPositive() {
        IPatternDetails selfRef = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyA, 1), 1) },
                new GenericStack[] { new GenericStack(keyA, 2) });
        ICraftingService service = mock(ICraftingService.class);
        when(service.getCraftingFor(keyA)).thenReturn(List.of(selfRef));
        assertThat(RecursiveCraftingHelper.isOnlyCandidateSelfRef(service, keyA)).isTrue();
    }

    @Test
    void isOnlyCandidateSelfRefSingleOrdinary() {
        IPatternDetails ordinary = pattern(
                new IPatternDetails.IInput[] { input(new GenericStack(keyB, 1), 1) },
                new GenericStack[] { new GenericStack(keyA, 2) });
        ICraftingService service = mock(ICraftingService.class);
        when(service.getCraftingFor(keyA)).thenReturn(List.of(ordinary));
        assertThat(RecursiveCraftingHelper.isOnlyCandidateSelfRef(service, keyA)).isFalse();
    }
}
