package com.github.aeddddd.ae2enhanced.test.craftingplan;

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
import com.github.aeddddd.ae2enhanced.test.specialcrafting.PlanView;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.SimulationEnv;

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

    /** E1:θ 边界需求 Long.MAX → 需求封顶执行,超出部分根键记缺,环样板按封顶需求照常规划. */
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

        PlanView plan = PlanView.of(env.runDag(mult(e, Long.MAX_VALUE)));

        assertThat(plan.simulation()).as("缺料计划").isTrue();
        // LP 语义:超出封顶(999999991776627712)的部分在根键记缺
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(e)))
                .as("超出封顶部分根键记缺").isEqualTo(8223372045078148096L);
        Map<ICraftingPatternDetails, Long> times = plan.patternTimes();
        // θ 环按封顶需求正常规划(种子库存计入交付)
        assertThat(times.get(pX)).isEqualTo(499999995888313792L);
        assertThat(times.get(pY)).isEqualTo(499999995888313792L);
        assertThat(times.get(pC)).isEqualTo(499999995888313792L);
        // 环外分支按封顶需求规划
        assertThat(times.get(pE)).isEqualTo(999999991776627712L);
        assertThat(times.get(pD)).isEqualTo(999999991776627712L);
    }

    /** E2:自增殖边界 2X→3X 需求 Long.MAX → 需求封顶执行,超出部分根键记缺. */
    @Test
    public void testSelfDupBoundaryAstronomicalDemandMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack d = block(Blocks.DIRT);
        ICraftingPatternDetails pD = env.addPattern(
                new ProcessingPatternBuilder(d).addPreciseInput(1, x).build());
        ICraftingPatternDetails pDup = env.addPattern(
                new ProcessingPatternBuilder(mult(x, 3)).addPreciseInput(2, x).build());
        env.addStoredItem(mult(x, 2)); // 种子 = inPer

        PlanView plan = PlanView.of(env.runDag(mult(d, Long.MAX_VALUE)));

        assertThat(plan.simulation()).as("缺料计划").isTrue();
        // LP 语义:超出封顶的部分在根键记缺
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(d)))
                .isEqualTo(8223372045078148096L);
        Map<ICraftingPatternDetails, Long> times = plan.patternTimes();
        assertThat(times.get(pDup)).as("dup 样板按封顶需求规划").isEqualTo(999999991776627712L);
        assertThat(times.get(pD)).isEqualTo(999999991776627712L);
    }

    /**
     * E3:普通路径批量产出样的 ceilDiv 饱和——请求 Long.MAX、每次产 4:
     * 旧实现 (a+b-1)/b 回绕成负数导致子需求被钳为 0(错误地"无缺料").
     * LP 语义:需求封顶执行,原料赤字与超出封顶部分(根键)如实上报.
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

        PlanView plan = PlanView.of(env.runDag(mult(c, Long.MAX_VALUE)));

        long cappedTimes = 249999997944156928L; // 封顶需求 ÷ 4 批量
        assertThat(plan.patternTimes().get(pB)).isEqualTo(cappedTimes);
        assertThat(plan.simulation()).as("原料不足须报缺料").isTrue();
        // 原料赤字 + 超出封顶部分(根键)同时上报
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(a)))
                .isEqualTo(249999997944155936L);
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(c)))
                .isEqualTo(8223372045078148096L);
    }

    /**
     * E5:自增殖边界 1X→2X(inPer=1)需求 exact Long.MAX → 产出 2×crafts 超 long 不可表示.
     * (旧的贷款守卫用 inPer 判定,inPer=1 时恰好漏过 exact Long.MAX:
     * 产出回绕成负数 → 结算失败 → 整单回落原生)
     * LP 语义:需求封顶执行,超出部分根键记缺,dup 样板按封顶需求规划.
     */
    @Test
    public void testSelfDupUnitInputAstronomicalDemandMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack d = block(Blocks.DIRT);
        ICraftingPatternDetails pD = env.addPattern(
                new ProcessingPatternBuilder(d).addPreciseInput(1, x).build());
        ICraftingPatternDetails pDup = env.addPattern(
                new ProcessingPatternBuilder(mult(x, 2)).addPreciseInput(1, x).build());
        env.addStoredItem(x); // 种子 1

        PlanView plan = PlanView.of(env.runDag(mult(d, Long.MAX_VALUE)));

        assertThat(plan.simulation()).as("缺料计划").isTrue();
        // LP 语义:超出封顶的部分在根键记缺
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(d)))
                .isEqualTo(8223372045078148096L);
        Map<ICraftingPatternDetails, Long> times = plan.patternTimes();
        assertThat(times.get(pDup)).as("dup 样板按封顶需求规划").isEqualTo(999999991776627712L);
        assertThat(times.get(pD)).isEqualTo(999999991776627712L);
    }

    /** E4:根级 θ 环请求 exact Long.MAX → 特殊路径 O(1) 缺料(不回落原生,环样板零调用). */
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

        PlanView plan = PlanView.of(env.runDag(mult(c, Long.MAX_VALUE)));

        assertThat(plan.simulation()).as("缺料计划").isTrue();
        assertThat(SpecialPlanMarker.isSpecial(plan.job())).as("缺料计划不标记特殊").isFalse();
        assertThat(plan.missingItems().get(RecursiveCraftingHelper.canon(c)))
                .isEqualTo(Long.MAX_VALUE);
        Map<ICraftingPatternDetails, Long> times = plan.patternTimes();
        assertThat(times.getOrDefault(pX, 0L)).isEqualTo(0L);
        assertThat(times.getOrDefault(pY, 0L)).isEqualTo(0L);
        assertThat(times.getOrDefault(pC, 0L)).isEqualTo(0L);
    }
}
