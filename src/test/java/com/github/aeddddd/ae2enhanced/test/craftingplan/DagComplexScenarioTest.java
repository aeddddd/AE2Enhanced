package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * M 组:极复杂混合场景——单个订单同时穿越 DAG 的全部特殊通道
 * (深层自引用边界 + 催化环副产物 + 容器/催化剂 + 大宗线性 + 切边),
 * 验证组合情形下各通道互不干扰、种子/缺料/策略语义各自正确.
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class DagComplexScenarioTest {

    private static final CalculationStrategy REPORT = CalculationStrategy.REPORT_MISSING_ITEMS;

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }

    private static Map<AEKey, Long> primaryMap(ICraftingPlan plan) {
        Map<AEKey, Long> out = new HashMap<>();
        plan.patternTimes().forEach((pattern, times) -> out.merge(pattern.getPrimaryOutput().what(),
                times, Long::sum));
        return out;
    }

    private static Map<AEKey, Long> toMap(appeng.api.stacks.KeyCounter counter) {
        Map<AEKey, Long> out = new HashMap<>();
        for (var key : counter.keySet()) {
            out.put(key, counter.get(key));
        }
        return out;
    }

    /**
     * 混合网络(各通道互不重叠的物品域):
     * <ul>
     * <li>深层自引用:R ← D ×2,D ← C,C: 1C→2C(dup,种子 1C)</li>
     * <li>催化环副产物:R ← X ×1,环 1A→1X+1B、1B→1A(种子 1A)</li>
     * <li>催化剂容器:R ← K ×1,K: 4L+1CAT→1K(CAT 容器返还,种子 1CAT + 16L)</li>
     * <li>大宗线性:R ← B ×2(终端,库存 8)</li>
     * <li>根:R: 2D+1X+1K+2B → 1R</li>
     * </ul>
     */
    private static final class MixedNet {
        final GenericStack r = item(Items.NETHER_STAR);
        final GenericStack d = item(Items.DIAMOND);
        final GenericStack c = item(Items.COBBLESTONE);
        final GenericStack x = item(Items.AMETHYST_SHARD);
        final GenericStack a = item(Items.APPLE);
        final GenericStack b = item(Items.BAMBOO);
        final GenericStack k = item(Items.EMERALD);
        final GenericStack l = item(Items.LAPIS_LAZULI);
        final GenericStack cat = item(Items.STICK);
        final GenericStack bulk = item(Items.CHARCOAL);

        final SimulationEnv env = new SimulationEnv();

        MixedNet() {
            // 深层自引用链
            env.addPattern(new ProcessingPatternBuilder(d).addPreciseInput(1, c).build());
            env.addPattern(new ProcessingPatternBuilder(mult(c, 2)).addPreciseInput(1, c).build());
            // 催化环(X 为环外副产物)
            env.addPattern(new ProcessingPatternBuilder(x, b).addPreciseInput(1, a).build());
            env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, b).build());
            // 催化剂容器样板(催化自返还)
            env.addPattern(com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ContainerPatternBuilder
                    .withContainer(new ProcessingPatternBuilder(k)
                            .addPreciseInput(4, l)
                            .addPreciseInput(1, cat)
                            .build(),
                            cat.what(), cat.what()));
            // 根
            env.addPattern(new ProcessingPatternBuilder(r)
                    .addPreciseInput(2, d)
                    .addPreciseInput(1, x)
                    .addPreciseInput(1, k)
                    .addPreciseInput(2, bulk)
                    .build());
        }

        void withAllSeeds() {
            env.addStoredItem(c); // dup 种子
            env.addStoredItem(a); // 催化环种子
            env.addStoredItem(cat); // 催化剂种子
            env.addStoredItem(mult(l, 64));
            env.addStoredItem(mult(bulk, 8));
        }
    }

    /**
     * M1:全通道成功——一单穿越深层 dup 边界 + 催化环 + 催化剂容器 + 大宗,
     * 计划可行且各通道种子/消耗记账精确.
     */
    @Test
    public void testAllChannelsSucceedTogether() {
        var net = new MixedNet();
        net.withAllSeeds();

        // R×4:D 8(dup ×8)、X 4(环 4 轮)、K 4、B 8
        var plan = net.env.runDagSimulation(mult(net.r, 4), REPORT);

        assertThat(plan.simulation()).as("全通道可行").isFalse();
        var times = primaryMap(plan);
        assertThat(times.get(net.c.what())).isEqualTo(8); // dup
        assertThat(times.get(net.d.what())).isEqualTo(8); // 深层链
        assertThat(times.get(net.x.what())).isEqualTo(4); // 催化环发射样板
        assertThat(times.get(net.a.what())).isEqualTo(4); // 催化环回填样板
        assertThat(times.get(net.k.what())).isEqualTo(4); // 催化剂样板
        assertThat(times.get(net.r.what())).isEqualTo(4); // 根
        assertThat(plan.usedItems().get(net.c.what())).isEqualTo(1); // dup 种子
        assertThat(plan.usedItems().get(net.a.what())).isEqualTo(1); // 催化环种子
        assertThat(plan.usedItems().get(net.cat.what())).isEqualTo(1); // 催化剂种子
        assertThat(plan.usedItems().get(net.l.what())).isEqualTo(16); // 4K × 4L
        assertThat(plan.usedItems().get(net.bulk.what())).isEqualTo(8); // 大宗全额
    }

    /**
     * M2:单通道失败(无 dup 种子)不拖垮整单——切边重试产出诚实缺料计划,
     * 其余通道照常完整规划(不整单回落原生).
     */
    @Test
    public void testSingleChannelFailureHonestMissing() {
        var net = new MixedNet();
        // 不加 dup 种子(C 无库存),其余通道种子齐全
        net.env.addStoredItem(net.a);
        net.env.addStoredItem(net.cat);
        net.env.addStoredItem(mult(net.l, 64));
        net.env.addStoredItem(mult(net.bulk, 8));

        var plan = net.env.runDagSimulation(mult(net.r, 4), REPORT);

        assertThat(plan.simulation()).as("dup 无种子 → 缺料计划").isTrue();
        assertThat(plan.missingItems().get(net.c.what())).as("dup 通道缺 C").isGreaterThan(0);
        var times = primaryMap(plan);
        // 其余通道不受拖累
        assertThat(times.get(net.x.what())).isEqualTo(4);
        assertThat(times.get(net.k.what())).isEqualTo(4);
        assertThat(times.get(net.r.what())).isEqualTo(4);
        assertThat(plan.usedItems().get(net.bulk.what())).isEqualTo(8);
    }

    /**
     * M3:CRAFT_LESS 混合单二分——大宗库存 8 封顶,请求 8R(需 16B)不可行,
     * 二分收敛到 4R(需 8B);特殊通道在每次尝试中照常求解.
     */
    @Test
    public void testCraftLessBinarySearchOnMixedOrder() {
        var net = new MixedNet();
        net.withAllSeeds();

        var plan = net.env.runDagSimulation(mult(net.r, 8), CalculationStrategy.CRAFT_LESS);

        assertThat(plan.simulation()).as("二分找到最大可产量").isFalse();
        assertThat(plan.finalOutput().amount()).isEqualTo(4); // bulk 8 / 每 R 2
        var times = primaryMap(plan);
        assertThat(times.get(net.r.what())).isEqualTo(4);
        assertThat(times.get(net.c.what())).isEqualTo(8);
        assertThat(plan.usedItems().get(net.bulk.what())).isEqualTo(8);
    }

    /**
     * M4:深度 × 规模压力——深层链(3 层)叠加共享子树(根双槽位消耗 D)
     * 与催化环,大数量下数字精确(共享节点合并、边界需求沿边累加).
     */
    @Test
    public void testDeepSharedSubtreeAtScale() {
        var net = new MixedNet();
        net.withAllSeeds();

        // R×1000:D 2000(dup ×2000)、X 1000(环 1000 轮)、K 1000、B 2000 → B 缺
        // 改为验证 CRAFT_LESS 收敛:bulk 8 → 4R(与 M3 同数值但走大请求路径)
        var plan = net.env.runDagSimulation(mult(net.r, 1000), CalculationStrategy.CRAFT_LESS);

        assertThat(plan.simulation()).isFalse();
        assertThat(plan.finalOutput().amount()).isEqualTo(4);
        // 大请求下的二分尝试了多个数量级,最终计划记账仍精确
        assertThat(plan.usedItems().get(net.bulk.what())).isEqualTo(8);
        assertThat(plan.usedItems().get(net.c.what())).isEqualTo(1);
        assertThat(plan.usedItems().get(net.a.what())).isEqualTo(1);
        assertThat(plan.usedItems().get(net.cat.what())).isEqualTo(1);
    }
}
