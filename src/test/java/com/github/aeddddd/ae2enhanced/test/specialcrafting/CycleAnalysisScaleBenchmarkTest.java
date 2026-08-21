package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CycleAnalyzer;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialRecipeDetector;

/**
 * 大 SCC / 长环网络的环分析规模基准（默认跳过,设环境变量 {@code AE2E_BENCH=1} 启用）.
 * <p>背景:生产 Issue——非常复杂下单触发服务器看门狗.detector 在 beginCraftingJob
 * （服务器线程）同步执行,memo 未命中时 findCyclesThrough + 逐环 analyze + analyzeUnion;
 * solveSystem 的零空间求解为每环 n 个 (n-1) 阶 Bareiss 行列式(O(n⁴) 大整数),
 * 长环/密集 SCC 下单次分析即可达秒~分钟级.</p>
 * <p>本基准构造:① 中性单环（长度 N 递增）测量 analyze 单项耗时曲线;
 * ② 密集 SCC(每键 2 条成环样板,候选环打满 64 上限）测量 detector 全链路耗时.</p>
 */
public class CycleAnalysisScaleBenchmarkTest {

    private static final boolean ENABLED = System.getenv("AE2E_BENCH") != null;

    private static IAEItemStack key(int id) {
        return AEItemStack.fromItemStack(new ItemStack(Items.STICK, 1, id));
    }

    /** 中性单环 N 键:key i → key (i+1)%N(1→1,净率中性,detector 必然走完全部分析). */
    private static SimulationEnv ringEnv(int n) {
        SimulationEnv env = new SimulationEnv();
        for (int i = 0; i < n; i++) {
            env.addPattern(new ProcessingPatternBuilder(key((i + 1) % n)).addPreciseInput(1, key(i))
                    .build());
        }
        return env;
    }

    /**
     * 密集 SCC N 键:key i 有两条成环样板(来自 i-1 与 i-2),候选环数量打满
     * MAX_CYCLES=64 上限;环长 ~N.净率中性,无短路.
     */
    private static SimulationEnv denseSccEnv(int n) {
        SimulationEnv env = new SimulationEnv();
        for (int i = 0; i < n; i++) {
            env.addPattern(new ProcessingPatternBuilder(key(i)).addPreciseInput(1, key((i + 1) % n))
                    .build());
            env.addPattern(new ProcessingPatternBuilder(key(i)).addPreciseInput(1, key((i + 2) % n))
                    .build());
        }
        return env;
    }

    private static void timeAnalyze(SimulationEnv env, IAEItemStack root, String label) {
        List<List<CycleAnalyzer.CycleStep>> cycles = CycleAnalyzer.findCyclesThrough(
                env.craftingGrid(), root, null);
        System.out.printf("[BENCH] %s: 枚举环数=%d 最长环=%d%n", label, cycles.size(),
                cycles.isEmpty() ? 0 : cycles.get(0).size());
        long t0 = System.nanoTime();
        int productive = 0;
        for (List<CycleAnalyzer.CycleStep> cycle : cycles) {
            CycleAnalyzer.Analysis a = CycleAnalyzer.analyze(cycle);
            if (a != null && a.rateClass() == CycleAnalyzer.RateClass.PRODUCTIVE) {
                productive++;
            }
        }
        long perCycles = System.nanoTime() - t0;
        t0 = System.nanoTime();
        CycleAnalyzer.Analysis union = CycleAnalyzer.analyzeUnion(cycles);
        long unionNanos = System.nanoTime() - t0;
        System.out.printf(
                "[BENCH] %s: 逐环分析=%,.1f ms(增殖 %d) 并集分析=%,.1f ms(键数 %s)%n", label,
                perCycles / 1e6, productive, unionNanos / 1e6,
                union == null ? "n/a" : String.valueOf(union.keys().size()));
    }

    @Test
    public void neutralRingAnalyzeScaling() {
        Assumptions.assumeTrue(ENABLED, "set AE2E_BENCH=1 to enable");
        for (int n : new int[] { 16, 32, 64, 128, 256 }) {
            SimulationEnv env = ringEnv(n);
            long t0 = System.nanoTime();
            List<List<CycleAnalyzer.CycleStep>> cycles = CycleAnalyzer.findCyclesThrough(
                    env.craftingGrid(), key(0), null);
            long enumNanos = System.nanoTime() - t0;
            System.out.printf("[BENCH] 单环 N=%d: 枚举=%,.1f ms 环数=%d%n", n, enumNanos / 1e6,
                    cycles.size());
            timeAnalyze(env, key(0), "单环 N=" + n);
        }
    }

    @Test
    public void denseSccDetectorCost() {
        Assumptions.assumeTrue(ENABLED, "set AE2E_BENCH=1 to enable");
        for (int n : new int[] { 16, 32, 64, 128, 256 }) {
            SimulationEnv env = denseSccEnv(n);
            // detector 全链路(服务器线程等价):memo 在模拟网格上每次重建,必走完整 detect
            long t0 = System.nanoTime();
            boolean verdict = SpecialRecipeDetector.mayInvolveSpecialRecipes(env.craftingGrid(),
                    key(0), null);
            long detectNanos = System.nanoTime() - t0;
            System.out.printf("[BENCH] 密集 SCC N=%d: detector 全链路=%,.1f ms (verdict=%s)%n", n,
                    detectNanos / 1e6, verdict);
        }
    }
}
