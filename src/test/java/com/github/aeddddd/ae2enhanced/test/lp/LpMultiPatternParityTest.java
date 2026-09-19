package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.test.support.PlanView;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.ReusePatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 多样板接管(LP 多分支)测试(移植自 1.20.1 DagMultiPatternTest):镜像原生多分支
 * "分支 1 尽力 → 分支 2"语义,但按供给容量整批——修复"多样板 key × 极大数量"的
 * O(数量) 下单陷阱(原生多分支逐份循环 + 每份新建子模拟库存,1e10 量级需数小时).
 * <ul>
 * <li>原料充足:分支 1 全满足,逐字段 parity;</li>
 * <li>分支 1 原料有限:分支 1 尽力 + 分支 2 补足,逐字段 parity;</li>
 * <li>两分支均不足:缺料语义 parity(原生模拟趟 = 乐观幻影生产,缺料在分支 1 原料层);</li>
 * <li>极大数量(1e12):LP 整批完成且分支分配正确(原生此时为小时级,不可比);</li>
 * <li>返还输入(CrT reuse)交叉:分支 1 自返还 times-1 记账、容量封顶溢出到分支 2
 * (原 DagMultiBranchContainerTest 并入).</li>
 * </ul>
 * <p>注意:原生多样板分支会原地改写请求对象的 stackSize,两次运行必须用独立副本.</p>
 */
public class LpMultiPatternParityTest {

    private static IAEItemStack block(net.minecraft.block.Block b) {
        return AEItemStack.fromItemStack(new ItemStack(b));
    }

    private static IAEItemStack mult(IAEItemStack template, long multiplier) {
        IAEItemStack copy = template.copy();
        copy.setStackSize(template.getStackSize() * multiplier);
        return copy;
    }

    private static final class Env {
        final SimulationEnv env;
        final ICraftingPatternDetails branch1;
        final ICraftingPatternDetails branch2;
        final IAEItemStack r; // 请求物
        final IAEItemStack c1; // 分支 1 原料
        final IAEItemStack c2; // 分支 2 原料

        Env(SimulationEnv env, ICraftingPatternDetails branch1, ICraftingPatternDetails branch2,
                IAEItemStack r, IAEItemStack c1, IAEItemStack c2) {
            this.env = env;
            this.branch1 = branch1;
            this.branch2 = branch2;
            this.r = r;
            this.c1 = c1;
            this.c2 = c2;
        }
    }

    /** R(stone) ← M(cobble);M 有两个干净分支:C1(sand)、C2(dirt). */
    private static Env multiEnv(long stockC1, long stockC2) {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack r = block(Blocks.STONE);
        IAEItemStack m = block(Blocks.COBBLESTONE);
        IAEItemStack c1 = block(Blocks.SAND);
        IAEItemStack c2 = block(Blocks.DIRT);
        ICraftingPatternDetails p1 = env.addPattern(
                new ProcessingPatternBuilder(m).addPreciseInput(1, c1).build());
        ICraftingPatternDetails p2 = env.addPattern(
                new ProcessingPatternBuilder(m).addPreciseInput(1, c2).build());
        env.addPattern(new ProcessingPatternBuilder(r).addPreciseInput(1, m).build());
        env.addStoredItem(mult(c1, stockC1));
        env.addStoredItem(mult(c2, stockC2));
        return new Env(env, p1, p2, r, c1, c2);
    }

    private static PlanView[] bothPlans(Env e, long amount) {
        IAEItemStack request = mult(e.r, amount);
        CraftingJob nativeJob = e.env.runNative(request.copy());
        CraftingJob lpJob = e.env.runLp(request.copy());
        return new PlanView[] { PlanView.of(nativeJob), PlanView.of(lpJob) };
    }

    @Test
    public void dualBranchAbundantParity() {
        Env e = multiEnv(1_000_000, 1_000_000);
        PlanView[] plans = bothPlans(e, 1000);
        PlanView nativePlan = plans[0];
        PlanView lpPlan = plans[1];

        assertThat(nativePlan.simulation()).isFalse();
        assertThat(lpPlan.simulation()).isFalse();
        // 分支 1 全做(分支 2 不参与)
        assertThat(nativePlan.patternTimes().get(e.branch1)).isEqualTo(1000L);
        assertThat(nativePlan.patternTimes().get(e.branch2)).isNull();
        assertThat(lpPlan.patternTimes()).isEqualTo(nativePlan.patternTimes());
        assertThat(lpPlan.usedItems()).isEqualTo(nativePlan.usedItems());
        assertThat(lpPlan.missingItems()).isEqualTo(nativePlan.missingItems());
    }

    @Test
    public void dualBranchScarceParity() {
        Env e = multiEnv(400, 1_000_000);
        PlanView[] plans = bothPlans(e, 1000);
        PlanView nativePlan = plans[0];
        PlanView lpPlan = plans[1];

        assertThat(nativePlan.simulation()).isFalse();
        assertThat(lpPlan.simulation()).isFalse();
        // 分支 1 尽力 400,分支 2 补足 600
        assertThat(nativePlan.patternTimes().get(e.branch1)).isEqualTo(400L);
        assertThat(nativePlan.patternTimes().get(e.branch2)).isEqualTo(600L);
        assertThat(lpPlan.patternTimes()).isEqualTo(nativePlan.patternTimes());
        assertThat(lpPlan.usedItems()).isEqualTo(nativePlan.usedItems());
        assertThat(lpPlan.missingItems()).isEqualTo(nativePlan.missingItems());
    }

    @Test
    public void dualBranchMissingParity() {
        Env e = multiEnv(400, 300);
        PlanView[] plans = bothPlans(e, 1000);
        PlanView nativePlan = plans[0];
        PlanView lpPlan = plans[1];

        // 缺料语义(原生模拟趟 = 乐观幻影生产):分支 1 包揽全部 1000 次
        // (400 真实 + 600 幻影),分支 2 不参与,缺料记在分支 1 原料层 c1×600
        assertThat(nativePlan.simulation()).isTrue();
        assertThat(lpPlan.simulation()).isTrue();
        assertThat(nativePlan.patternTimes().get(e.branch1)).isEqualTo(1000L);
        assertThat(nativePlan.patternTimes().get(e.branch2)).isNull();
        Map<IAEItemStack, Long> nativeMissing = nativePlan.missingItems();
        IAEItemStack c1Key = e.c1.copy();
        c1Key.setStackSize(0);
        assertThat(nativeMissing.get(c1Key)).isEqualTo(600L);
        assertThat(lpPlan.patternTimes()).isEqualTo(nativePlan.patternTimes());
        assertThat(lpPlan.usedItems()).isEqualTo(nativePlan.usedItems());
        assertThat(lpPlan.missingItems()).isEqualTo(nativePlan.missingItems());
    }

    @Test
    public void multiPatternHugeAmount() {
        Env e = multiEnv(400_000_000_000L, 4_000_000_000_000L);
        long t0 = System.nanoTime();
        PlanView lpPlan = PlanView.of(e.env.runLp(mult(e.r, 1_000_000_000_000L)));
        long dagMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(lpPlan.simulation()).as("LP 应可行").isFalse();
        // 分支 1 尽力 4e11,分支 2 补足 6e11
        assertThat(lpPlan.patternTimes().get(e.branch1)).isEqualTo(400_000_000_000L);
        assertThat(lpPlan.patternTimes().get(e.branch2)).isEqualTo(600_000_000_000L);
        IAEItemStack c1Key = e.c1.copy();
        c1Key.setStackSize(0);
        IAEItemStack c2Key = e.c2.copy();
        c2Key.setStackSize(0);
        assertThat(lpPlan.usedItems().get(c1Key)).isEqualTo(400_000_000_000L);
        assertThat(lpPlan.usedItems().get(c2Key)).isEqualTo(600_000_000_000L);
        System.out.printf("[Scale] 多样板(LP 接管): 数量=1e12, 耗时=%,d ms%n", dagMs);
        assertThat(dagMs).as("多样板接管后应按量 O(1)").isLessThan(2_000);
    }

    /**
     * 多样板 + 返还输入:分支 1 的输入为配方级不消耗(CrT reuse)——
     * 不再回落原生;分支 1 包揽全部需求,自返还 times-1 记账:
     * 提取 5、返还 4、网络净消耗 = 1(种子).
     */
    @Test
    public void testMultiBranchWithReusedInput() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack y = block(Blocks.COBBLESTONE);
        // 分支 1:1A(reuse)→1Y;分支 2:1A→1Y(普通消耗)
        ICraftingPatternDetails p1 = new ReusePatternBuilder(y)
                .addPreciseInput(1, a).reused(a).build();
        ICraftingPatternDetails p2 = new ReusePatternBuilder(y)
                .addPreciseInput(1, a).build();
        env.addPattern(p1);
        env.addPattern(p2);
        env.addStoredItem(mult(a, 10));

        PlanView view = PlanView.of(env.runLp(mult(y, 5)));
        assertThat(view.simulation()).as("多样板+返还输入应走 LP 成功").isFalse();
        assertThat(view.missingItems()).isEmpty();
        assertThat(view.patternTimes().getOrDefault(p1, -1L))
                .as("分支 1 包揽(分支序 = 原生尝试序)").isEqualTo(5L);
        // 分支 1 自返还 times-1:5 次消耗提取 5,回记 4 → used = 净消耗 1 + 种子 4?
        // 记账口径:used = 网络实取 = 5(返还经 synthetic 抵扣模拟库存,不再重复实取)
        assertThat(view.usedItems().getOrDefault(a, 0L)).isEqualTo(5L);
    }

    /**
     * 返还输入 + 容量封顶:分支 1(A 只有 3 个库存,容量封顶)溢出到分支 2(普通消耗),
     * 两分支的返还/消耗各自记账.
     */
    @Test
    public void testMultiBranchCapacitySpillover() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack c = block(Blocks.DIRT);
        IAEItemStack y = block(Blocks.COBBLESTONE);
        // 分支 1:1A(reuse)→1Y(A 容量封顶);分支 2:1C→1Y
        ICraftingPatternDetails p1 = new ReusePatternBuilder(y)
                .addPreciseInput(1, a).reused(a).build();
        ICraftingPatternDetails p2 = new ReusePatternBuilder(y)
                .addPreciseInput(1, c).build();
        env.addPattern(p1);
        env.addPattern(p2);
        env.addStoredItem(mult(a, 3));
        env.addStoredItem(mult(c, 100));

        PlanView view = PlanView.of(env.runLp(mult(y, 5)));
        assertThat(view.simulation()).isFalse();
        assertThat(view.missingItems()).isEmpty();
        assertThat(view.patternTimes().getOrDefault(p1, -1L)).isEqualTo(3L);
        assertThat(view.patternTimes().getOrDefault(p2, -1L)).isEqualTo(2L);
        assertThat(view.usedItems().getOrDefault(a, 0L)).isEqualTo(3L);
        assertThat(view.usedItems().getOrDefault(c, 0L)).isEqualTo(2L);
    }
}
