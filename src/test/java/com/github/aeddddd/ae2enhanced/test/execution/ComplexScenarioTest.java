package com.github.aeddddd.ae2enhanced.test.execution;

import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.block;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.item;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult;
import static com.github.aeddddd.ae2enhanced.test.support.PlanAssert.assertThatPlan;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;
import com.github.aeddddd.ae2enhanced.test.support.PlanView;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * H 组:复杂组合场景(1.12.2 移植版)——自引用与循环链并存、环外输入子合成、
 * 分数速率、多环竞争、ceil 边界、天文数字、多产物自引用、候选迭代等.
 * <p>1.20.1 的流体用例(H8)不单独移植:1.12.2 的 ae2fc 流体样板以 FluidDrop
 * 假物品形式存在,与物品 key 走同一代码路径.</p>
 */
public class ComplexScenarioTest {

    /** H1:自引用样板与循环链并存时,自引用(净产最优)接管.
     * 全额生产语义:根库存不抵交付,净需 10 → dup×10(库存 1 作点火种子,期末返还). */
    @Test
    public void testSelfRefTakesPriorityOverCycle() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        ICraftingPatternDetails dup = env.addPattern(
                new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(stone);

        PlanView plan = PlanView.of(env.runLp(mult(stone, 10)));
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(dup, 10) // 只用自引用样板,不走循环链(全额生产 10)
                .usedMatch(stone)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).isTrue();
    }

    /** H2:循环链的环外输入本身需要子合成(不含环成员)→ 原生子合成正常展开. */
    @Test
    public void testCycleWithCraftableExternalInput() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack dirt = block(Blocks.DIRT);
        IAEItemStack sand = block(Blocks.SAND);
        ICraftingPatternDetails p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2))
                .addPreciseInput(1, stone)
                .addPreciseInput(1, dirt)
                .build());
        ICraftingPatternDetails p1 = env.addPattern(
                new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        ICraftingPatternDetails pDirt = env.addPattern(
                new ProcessingPatternBuilder(mult(dirt, 2)).addPreciseInput(1, sand).build());
        env.addStoredItem(stone); // 种子
        env.addStoredItem(mult(sand, 4)); // 无 dirt 库存,需从 sand 子合成

        PlanView plan = PlanView.of(env.runLp(mult(stone, 2)));
        Map<ICraftingPatternDetails, Long> expected = new LinkedHashMap<>();
        expected.put(p0, 2L);
        expected.put(p1, 4L);
        expected.put(pDirt, 1L);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(expected) // 全额生产 2:p0×2 + p1×4,dirt 缺口 2 由 pDirt×1 补
                .usedMatch(stone, sand)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).isTrue();
    }

    /** H4:中性环与增殖环并存(同一请求物)→ 中性环跳过,增殖环接管. */
    @Test
    public void testNeutralCycleSkippedForProductiveOne() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build()); // 中性环 A↔B
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        ICraftingPatternDetails p2 = env.addPattern(
                new ProcessingPatternBuilder(mult(sand, 2)).addPreciseInput(1, stone).build());
        ICraftingPatternDetails p3 = env.addPattern(
                new ProcessingPatternBuilder(stone).addPreciseInput(1, sand).build());
        env.addStoredItem(stone);

        PlanView plan = PlanView.of(env.runLp(mult(stone, 10)));
        Map<ICraftingPatternDetails, Long> expected = new LinkedHashMap<>();
        expected.put(p2, 10L);
        expected.put(p3, 20L);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(expected) // 只走增殖环(全额生产 10:10 轮 × [1,2])
                .usedMatch(stone)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).isTrue();
    }

    /** H5:两个仅共享 root 的独立增殖环 → LP 整单元联立求解,选执行数更少的
     * 砂环(净 +2/次 vs 石环 +1/次);全额生产 10 → 5 轮 × [1,3]. */
    @Test
    public void testTwoDisjointCyclesUnionRejectedButIterationSolves() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        env.addPattern(
                new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        env.addPattern(
                new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        ICraftingPatternDetails pSand = env
                .addPattern(new ProcessingPatternBuilder(mult(sand, 3)).addPreciseInput(1, stone).build());
        ICraftingPatternDetails pBack = env
                .addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, sand).build());
        env.addStoredItem(stone);

        PlanView plan = PlanView.of(env.runLp(mult(stone, 10)));
        Map<ICraftingPatternDetails, Long> expected = new LinkedHashMap<>();
        expected.put(pSand, 5L);
        expected.put(pBack, 15L);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(expected) // 砂环净 +2/次(执行数更少):石 1 - 5 + 15 - 10(交付) = 1(种子保留)
                .usedMatch(stone)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).isTrue();
    }

    /** H6:请求量非净增益整数倍 → ceil 多转一轮,余量执行结束返回网络.
     * 全额生产语义:净需 33/轮产 32 → 2 整轮(库存 32 作点火种子,期末返还). */
    @Test
    public void testTargetNotMultipleOfNetGain() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        IAEItemStack dirt = block(Blocks.DIRT);
        ICraftingPatternDetails p1 = env.addPattern(
                new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(mult(sand, 64))
                .addPreciseInput(16, stone)
                .addPreciseInput(16, cobble)
                .addPreciseInput(1, dirt)
                .build());
        ICraftingPatternDetails p3 = env.addPattern(new ProcessingPatternBuilder(mult(stone, 64))
                .addPreciseInput(64, sand)
                .addPreciseInput(1, dirt)
                .build());
        env.addStoredItem(mult(stone, 32)); // 恰够每轮种子(16+16)
        env.addStoredItem(mult(dirt, 100));

        PlanView plan = PlanView.of(env.runLp(mult(stone, 33)));
        Map<ICraftingPatternDetails, Long> expected = new LinkedHashMap<>();
        expected.put(p1, 32L);
        expected.put(p2, 2L);
        expected.put(p3, 2L);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(expected) // 全额生产 ceil(33/32) → 2 轮
                .usedMatch(mult(stone, 32), mult(dirt, 4)) // 种子全额实取,W 净消耗 4(2 轮 × 2)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).isTrue();
    }

    /** H7:环路径天文数字订单 → O(1) 缺料计划(不逐份展开,不溢出). */
    @Test
    public void testAstronomicalCycleOrderFallsBackToMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());
        env.addStoredItem(stone);

        // 无封顶语义(2026-09 起执行数上界改为真实闭包):×2 增益环下
        // Long.MAX−1 亦为可行订单——全额交付,不再"天文订单回落缺料"
        IAEItemStack huge = stone.copy();
        huge.setStackSize(Long.MAX_VALUE - 1);
        PlanView plan = PlanView.of(env.runLp(huge));
        assertThatPlan(plan).succeeded().missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).isTrue();
    }

    /** H12:广义自引用候选迭代——第一个候选种子不足,第二个可解(主产出均为请求物). */
    @Test
    public void testGeneralSelfRefCandidateIteration() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack dirt = block(Blocks.DIRT);
        IAEItemStack stick = item(Items.STICK);
        // B+A ← A 形式(主产出 B,催化剂 A 返还):p1 需 stone 种子,p2 需 dirt 种子
        env.addPattern(new ProcessingPatternBuilder(stick, stone).addPreciseInput(1, stone).build());
        ICraftingPatternDetails p2 = env.addPattern(
                new ProcessingPatternBuilder(stick, dirt).addPreciseInput(1, dirt).build());
        env.addStoredItem(dirt); // 只有 dirt 种子

        PlanView plan = PlanView.of(env.runLp(mult(stick, 10)));
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(p2, 10)
                .usedMatch(dirt)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).isTrue();
    }

    /** H13:四键环 + 分数速率 + 每轮辅材 + 辅材子合成:A+W→2B,B→C,2C→3D,2D→2A(t=[2,4,2,3],净产 4A/轮). */
    @Test
    public void testFourKeyCycleWithAuxSubcraft() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        IAEItemStack gravel = block(Blocks.GRAVEL);
        IAEItemStack dirt = block(Blocks.DIRT);
        IAEItemStack flint = item(Items.FLINT);
        ICraftingPatternDetails p0 = env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2))
                .addPreciseInput(1, stone)
                .addPreciseInput(1, dirt)
                .build());
        ICraftingPatternDetails p1 = env.addPattern(
                new ProcessingPatternBuilder(sand).addPreciseInput(1, cobble).build());
        ICraftingPatternDetails p2 = env.addPattern(
                new ProcessingPatternBuilder(mult(gravel, 3)).addPreciseInput(2, sand).build());
        ICraftingPatternDetails p3 = env.addPattern(
                new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(2, gravel).build());
        ICraftingPatternDetails pW = env.addPattern(
                new ProcessingPatternBuilder(mult(dirt, 2)).addPreciseInput(1, flint).build());
        env.addStoredItem(mult(stone, 2)); // 前缀种子(t0=2 → A 种子 2)
        env.addStoredItem(dirt); // W 库存 1,缺口 1 由子合成补
        env.addStoredItem(flint);

        PlanView plan = PlanView.of(env.runLp(mult(stone, 4)));
        Map<ICraftingPatternDetails, Long> expected = new LinkedHashMap<>();
        expected.put(p0, 2L);
        expected.put(p1, 4L);
        expected.put(p2, 2L);
        expected.put(p3, 3L);
        expected.put(pW, 1L);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(expected) // 全额生产 4 → 1 整轮 [2,4,2,3](净产 4A/轮) + W 子合成
                .usedMatch(mult(stone, 2), dirt, flint)
                .missingMatch();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).isTrue();
    }
}
