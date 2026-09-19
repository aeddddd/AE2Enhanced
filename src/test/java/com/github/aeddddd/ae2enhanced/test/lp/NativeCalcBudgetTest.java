package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.specialcrafting.NativeCalcBudget;
import com.github.aeddddd.ae2enhanced.test.support.PlanView;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 原生回落计算预算的回归测试（生产看门狗事故）.
 * <p>事故链路:复杂大单回落原生递归计算永不结束 + RandomComplement 在 setJob 同步
 * {@code future.get()} 阻塞服务器线程 → 看门狗 180s 崩服.预算机制
 * （{@code crafting.nativeCalcBudgetMs}）超时时借原生取消语义中断计算,
 * 并把不完整计划钉为模拟态（绝不允许提交）.</p>
 * <p>注:心跳检查由 MixinCraftingJob 挂在原生 handlePausing 上,JUnit 环境无 mixin
 * 变换,故此处直接驱动 {@link NativeCalcBudget#checkDeadline} 验证机制逻辑;
 * 端到端（真实下单回落中断）需在开发/生产环境验证.</p>
 */
public class NativeCalcBudgetTest {

    private static IAEItemStack block(net.minecraft.block.Block b) {
        return AEItemStack.fromItemStack(new ItemStack(b));
    }

    private static IAEItemStack mult(IAEItemStack template, long multiplier) {
        IAEItemStack copy = template.copy();
        copy.setStackSize(template.getStackSize() * multiplier);
        return copy;
    }

    /**
     * 机制逻辑:超期预算 → checkDeadline 抛 InterruptedException(原生取消语义),
     * 且计划已被钉为模拟态(不完整计划绝不允许提交)、中断标记置位.
     */
    @Test
    public void testExpiredBudgetAbortsAndPinsSimulation() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
        env.addStoredItem(mult(a, 1_000_000L));

        CraftingJob job = env.newLpJob(mult(b, 10));
        // 挂载已超期的预算(等价于原生路径跑了整个预算时长)
        NativeCalcBudget.arm(job);
        ((com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingJobBudgetAccess) job)
                .ae2enhanced$armNativeCalcBudget(System.nanoTime() - 1);

        assertThat(job.isSimulation()).isFalse();
        assertThatThrownBy(() -> NativeCalcBudget.checkDeadline(job))
                .as("超预算必须借原生取消语义中断")
                .isInstanceOf(InterruptedException.class);
        assertThat(job.isSimulation()).as("中断前必须把计划钉为模拟态").isTrue();
        assertThat(NativeCalcBudget.warnIfAborted(job)).as("中断标记置位").isTrue();
    }

    /**
     * 机制逻辑:未超期预算 → checkDeadline 不中断、不改状态;
     * 未挂预算(普通 job 语义,deadline=0)同理.
     */
    @Test
    public void testLiveBudgetDoesNotAbort() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
        env.addStoredItem(mult(a, 1_000_000L));

        CraftingJob armed = env.newLpJob(mult(b, 10));
        NativeCalcBudget.arm(armed); // 挂载但未超期
        try {
            NativeCalcBudget.checkDeadline(armed);
        } catch (InterruptedException e) {
            throw new AssertionError("未超期不得中断", e);
        }
        assertThat(armed.isSimulation()).isFalse();
        assertThat(NativeCalcBudget.warnIfAborted(armed)).isFalse();

        // 未挂预算(deadline=0):任何时刻都不中断
        CraftingJob unarmed = env.newLpJob(mult(b, 10));
        try {
            NativeCalcBudget.checkDeadline(unarmed);
        } catch (InterruptedException e) {
            throw new AssertionError("未挂预算不得中断", e);
        }
        assertThat(unarmed.isSimulation()).isFalse();
    }

    /**
     * 对照:正常 LP 计划（无回落）不受预算影响——预算只在原生路径挂载.
     */
    @Test
    public void testBudgetDoesNotAffectDagPath() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
        env.addStoredItem(mult(a, 1_000_000L));

        int old = AE2EnhancedConfig.crafting.nativeCalcBudgetMs;
        AE2EnhancedConfig.crafting.nativeCalcBudgetMs = 1000;
        try {
            CraftingJob job = env.runLp(mult(b, 100));
            assertThat(PlanView.of(job).simulation()).as("LP 正常路径不受预算影响").isFalse();
        } finally {
            AE2EnhancedConfig.crafting.nativeCalcBudgetMs = old;
        }
    }
}
