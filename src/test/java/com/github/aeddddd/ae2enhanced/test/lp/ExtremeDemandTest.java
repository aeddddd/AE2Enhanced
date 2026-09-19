package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;
import com.github.aeddddd.ae2enhanced.test.support.PlanView;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * E 组:天文数字需求（接近 {@link Long#MAX_VALUE}）回归.
 * <p>历史病灶:各求解器的 ceilDiv 写作 (a + b - 1) / b,需求近 Long.MAX 时加法回绕成
 * 负数,被误判为"循环边界不可解"而<b>整单回落原生递归树</b>——在大网络上即用户观测到
 * 的"高请求计算速度很慢".</p>
 * <p>LP 语义:需求按求解器上界封顶执行(999999991776627712),超出封顶的部分
 * (8223372045078148096)在<b>根键</b> O(1) 记缺,环样板按封顶需求照常规划(不再零调用),
 * 原料赤字按库存扣减后如实上报.</p>
 */
public class ExtremeDemandTest {

    private static IAEItemStack block(net.minecraft.block.Block b) {
        return AEItemStack.fromItemStack(new ItemStack(b));
    }

    private static IAEItemStack mult(IAEItemStack template, long multiplier) {
        IAEItemStack copy = template.copy();
        copy.setStackSize(multiplier);
        return copy;
    }

    /** E1:θ 边界需求 Long.MAX → 无封顶语义(2026-09 起执行数上界改为真实闭包,
     * 不再按 1e18 封顶):全额交付,环样板按全量需求规划,无根键记缺. */
    @Test
    public void testThetaBoundaryAstronomicalDemandMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack c = block(Blocks.STONE);
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack y = block(Blocks.SAND);
        IAEItemStack d = block(Blocks.DIRT);
        IAEItemStack e = block(Blocks.GRAVEL);
        ICraftingPatternDetails pE = env.addPattern(
                new ProcessingPatternBuilder(e).addPreciseInput(1, d).build());
        ICraftingPatternDetails pD = env.addPattern(
                new ProcessingPatternBuilder(d).addPreciseInput(1, c).build());
        ICraftingPatternDetails pX = env.addPattern(
                new ProcessingPatternBuilder(x).addPreciseInput(1, c).build());
        ICraftingPatternDetails pY = env.addPattern(
                new ProcessingPatternBuilder(y).addPreciseInput(1, c).build());
        ICraftingPatternDetails pC = env.addPattern(new ProcessingPatternBuilder(mult(c, 4))
                .addPreciseInput(1, x)
                .addPreciseInput(1, y)
                .build());
        env.addStoredItem(mult(c, 8)); // 种子
        env.addStoredItem(y);

        PlanView plan = PlanView.of(env.runLp(mult(e, Long.MAX_VALUE)));

        // 全额交付:无根键记缺,非缺料计划
        assertThat(plan.simulation()).as("全额交付非缺料计划").isFalse();
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(e)))
                .as("全额交付无根键记缺").isNull();
        Map<ICraftingPatternDetails, Long> times = plan.patternTimes();
        // 5e17~9e18 量级下 double ULP=64~1024,求解器内部数值路径差异(重分解/
        // 终抛)会产生 ±数个 ULP 的等价顶点——断言容差取 4096(≫ULP,≪量级)
        org.assertj.core.data.Offset<Long> ulp = org.assertj.core.data.Offset.offset(16384L);
        // θ 环按全量需求规划(种子库存计入交付):c 侧流量 = Long.MAX/2
        assertThat(times.get(pX)).isCloseTo(4611686018427387904L, ulp);
        assertThat(times.get(pY)).isCloseTo(4611686018427387904L, ulp);
        assertThat(times.get(pC)).isCloseTo(4611686018427387904L, ulp);
        // 环外分支按全量需求规划
        assertThat(times.get(pE)).isEqualTo(Long.MAX_VALUE);
        assertThat(times.get(pD)).isEqualTo(Long.MAX_VALUE);
    }

    /** E2:自增殖边界 2X→3X 需求 Long.MAX → 无封顶全额交付. */
    @Test
    public void testSelfDupBoundaryAstronomicalDemandMissing() {
        // dup 比率 3/2,种子 = inPer(2)
        assertSelfDupAstronomicalDemand(3, 2, 2);
    }

    /**
     * E5:自增殖边界 1X→2X(inPer=1)需求 exact Long.MAX → 产出 2×crafts 超 long 不可表示.
     * (旧的贷款守卫用 inPer 判定,inPer=1 时恰好漏过 exact Long.MAX:
     * 产出回绕成负数 → 结算失败 → 整单回落原生)
     * 无封顶语义:全额交付,dup 样板按全量需求规划.
     */
    @Test
    public void testSelfDupUnitInputAstronomicalDemandMissing() {
        // dup 比率 2/1,种子 1
        assertSelfDupAstronomicalDemand(2, 1, 1);
    }

    /** E2/E5 共用:自增殖 dup 环(X⇄D 外支)需求 Long.MAX 的全额交付语义. */
    private static void assertSelfDupAstronomicalDemand(int dupOut, int dupIn, long seed) {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack d = block(Blocks.DIRT);
        ICraftingPatternDetails pD = env.addPattern(
                new ProcessingPatternBuilder(d).addPreciseInput(1, x).build());
        ICraftingPatternDetails pDup = env.addPattern(
                new ProcessingPatternBuilder(mult(x, dupOut)).addPreciseInput(dupIn, x).build());
        env.addStoredItem(mult(x, seed)); // 种子 = inPer

        PlanView plan = PlanView.of(env.runLp(mult(d, Long.MAX_VALUE)));

        // 全额交付:无根键记缺,非缺料计划
        assertThat(plan.simulation()).as("全额交付非缺料计划").isFalse();
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(d)))
                .as("全额交付无根键记缺").isNull();
        Map<ICraftingPatternDetails, Long> times = plan.patternTimes();
        assertThat(times.get(pDup)).as("dup 样板按全量需求规划").isEqualTo(Long.MAX_VALUE);
        assertThat(times.get(pD)).isEqualTo(Long.MAX_VALUE);
    }

    /**
     * E3:普通路径批量产出样的 ceilDiv 饱和——请求 Long.MAX、每次产 4:
     * 旧实现 (a+b-1)/b 回绕成负数导致子需求被钳为 0(错误地"无缺料").
     * 无封顶语义:按全量需求规划(次数 = Long.MAX÷4),原料赤字如实上报,
     * 根键全额交付无记缺.
     */
    @Test
    public void testNormalPathCeilDivSaturation() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        IAEItemStack c = block(Blocks.DIRT);
        env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, b).build());
        ICraftingPatternDetails pB = env.addPattern(
                new ProcessingPatternBuilder(mult(b, 4)).addPreciseInput(1, a).build());
        env.addStoredItem(mult(a, 1000));

        PlanView plan = PlanView.of(env.runLp(mult(c, Long.MAX_VALUE)));

        // 2.3e18 量级下 double ULP=256,容差取 4096(≫ULP,≪量级)
        org.assertj.core.data.Offset<Long> ulp = org.assertj.core.data.Offset.offset(4096L);
        long fullTimes = 2305843009213693952L; // Long.MAX ÷ 4 批量
        assertThat(plan.patternTimes().get(pB)).isCloseTo(fullTimes, ulp);
        assertThat(plan.simulation()).as("原料不足须报缺料").isTrue();
        // 原料赤字上报(= 全量消耗 − 库存 1000);根键全额交付无记缺
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(a)))
                .isCloseTo(2305843009213692952L, ulp);
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(c)))
                .as("根键全额交付无记缺").isNull();
    }

    /** E4:根级 θ 环请求 exact Long.MAX → 无封顶语义:全额交付(环按全量需求规划). */
    @Test
    public void testRootCycleAstronomicalDemandMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack c = block(Blocks.STONE);
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack y = block(Blocks.SAND);
        ICraftingPatternDetails pX = env.addPattern(
                new ProcessingPatternBuilder(x).addPreciseInput(1, c).build());
        ICraftingPatternDetails pY = env.addPattern(
                new ProcessingPatternBuilder(y).addPreciseInput(1, c).build());
        ICraftingPatternDetails pC = env.addPattern(new ProcessingPatternBuilder(mult(c, 4))
                .addPreciseInput(1, x)
                .addPreciseInput(1, y)
                .build());
        env.addStoredItem(mult(c, 8));
        env.addStoredItem(y);

        PlanView plan = PlanView.of(env.runLp(mult(c, Long.MAX_VALUE)));

        // 全额交付:无根键记缺,非缺料计划
        assertThat(plan.simulation()).as("全额交付非缺料计划").isFalse();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).as("LP 全程规划标记特殊").isTrue();
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(c)))
                .as("全额交付无根键记缺").isNull();
        // θ 环按全量需求规划:c 侧流量 ≈ Long.MAX/2(4.6e18 量级 ULP=512,容差 16384)
        org.assertj.core.data.Offset<Long> ulp = org.assertj.core.data.Offset.offset(16384L);
        Map<ICraftingPatternDetails, Long> times = plan.patternTimes();
        assertThat(times.get(pX)).isCloseTo(4611686018427387904L, ulp);
        assertThat(times.get(pY)).isCloseTo(4611686018427387904L, ulp);
        assertThat(times.get(pC)).isCloseTo(4611686018427387904L, ulp);
    }
}
