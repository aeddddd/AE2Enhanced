package com.github.aeddddd.ae2enhanced.test.modpack;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.diag.metrics.Timer;
import com.github.aeddddd.ae2enhanced.specialcrafting.CycleAnalyzer;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;

/**
 * 真实订单热点定位(AE2E_BENCH=1 门控):tardis_polyp×100 在真实配方拓扑上超时,
 * 用周期性线程栈采样 + 阶段计时器快照定位耗时阶段.
 */
public class ModpackHotspotTest {

    private static ModpackFixture.Loaded loaded;

    @BeforeAll
    public static void loadSnapshot() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("AE2E_BENCH")), "热点定位默认跳过(AE2E_BENCH=1 开启)");
        Assumptions.assumeTrue(ModpackFixture.available(), "无整合包快照,跳过");
        loaded = ModpackFixture.loadLatest();
        // 打开特殊配方诊断日志:[DAG] 回落原因会打进测试输出
        com.github.aeddddd.ae2enhanced.diag.DiagSwitch.setOverride(
                com.github.aeddddd.ae2enhanced.diag.DiagSwitch.SPECIAL_CRAFTING, true);
    }

    @Test
    public void testTardisPolypHotspot() throws Exception {
        IAEItemStack what = loaded.item("contenttweaker:tardis_polyp").copy();
        what.setStackSize(100);
        CraftingJob job = loaded.env.newDagJob(what);

        Thread jobThread = new Thread(job, "ae2e-hotspot-job");
        jobThread.setDaemon(true);
        jobThread.start();
        job.simulateFor(Integer.MAX_VALUE);

        // 每 2s 采样一次工作线程栈(最多 25 次 = 50 秒);深栈帧定位病态求解的真实落点
        for (int i = 0; i < 25 && jobThread.isAlive(); i++) {
            Thread.sleep(2000);
            if (!jobThread.isAlive()) {
                break;
            }
            StackTraceElement[] stack = jobThread.getStackTrace();
            StringBuilder sb = new StringBuilder("[HOTSPOT] t=").append((i + 1) * 2).append('s');
            for (int f = 0; f < Math.min(25, stack.length); f++) {
                sb.append("\n  at ").append(stack[f]);
            }
            System.out.println(sb);
        }
        jobThread.interrupt();
        jobThread.join(5000);

        // 阶段计时器快照 + 回落原因计数
        for (Timer timer : MetricsRegistry.timers()) {
            if (timer.name().startsWith("plan.")) {
                System.out.println("[HOTSPOT] " + timer.name() + " -> " + timer.snapshot());
            }
        }
        for (com.github.aeddddd.ae2enhanced.diag.metrics.Counter counter : MetricsRegistry.counters()) {
            if (counter.name().startsWith("plan.")) {
                System.out.println("[HOTSPOT] " + counter.name() + " = " + counter.get());
            }
        }
        System.out.println("[HOTSPOT] job 存活=" + jobThread.isAlive() + " simulation=" + job.isSimulation());
    }

    /** 探针 2:BFS 重建键图邻接,找出 coal↝singularity 的真实路径(SCC 声称同环,枚举却为 0). */
    @Test
    public void probeSccPath() {
        IAEItemStack singularity = RecursiveCraftingHelper.canon(loaded.item("extendedcrafting:singularity"));
        IAEItemStack coal = RecursiveCraftingHelper.canon(loaded.item("minecraft:coal"));
        // 复刻 NetworkPatternIndex.build 的邻接:每个输出键 → 全部输入键
        com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess access =
                (com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess) loaded.env.craftingGrid();
        Map<IAEItemStack, Set<IAEItemStack>> adj = new java.util.HashMap<>();
        Set<ICraftingPatternDetails> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (IAEItemStack craftable : access.ae2enhanced$craftableKeys()) {
            for (ICraftingPatternDetails pattern : loaded.env.craftingGrid()
                    .getCraftingFor(craftable, null, -1, loaded.env.world())) {
                if (!seen.add(pattern)) {
                    continue;
                }
                for (IAEItemStack output : pattern.getCondensedOutputs()) {
                    if (output == null) {
                        continue;
                    }
                    Set<IAEItemStack> targets = adj.computeIfAbsent(
                            RecursiveCraftingHelper.canon(output), k -> new java.util.HashSet<>());
                    for (IAEItemStack input : pattern.getCondensedInputs()) {
                        if (input != null && input.getStackSize() > 0) {
                            targets.add(RecursiveCraftingHelper.canon(input));
                        }
                    }
                }
            }
        }
        // BFS coal ↝ singularity
        Map<IAEItemStack, IAEItemStack> prev = new java.util.HashMap<>();
        java.util.ArrayDeque<IAEItemStack> queue = new java.util.ArrayDeque<>();
        queue.add(coal);
        prev.put(coal, null);
        while (!queue.isEmpty()) {
            IAEItemStack cur = queue.poll();
            if (cur.equals(singularity)) {
                break;
            }
            for (IAEItemStack next : adj.getOrDefault(cur, java.util.Collections.emptySet())) {
                if (!prev.containsKey(next)) {
                    prev.put(next, cur);
                    queue.add(next);
                }
            }
        }
        if (!prev.containsKey(singularity)) {
            System.out.println("[PROBE2] coal↝singularity 不可达!邻接键数=" + adj.size());
            return;
        }
        List<IAEItemStack> path = new java.util.ArrayList<>();
        for (IAEItemStack cur = singularity; cur != null; cur = prev.get(cur)) {
            path.add(0, cur);
        }
        System.out.println("[PROBE2] 路径(len " + path.size() + "):");
        for (IAEItemStack p : path) {
            System.out.println("[PROBE2]   " + p);
        }
    }


    /** 探针:pyrotheum(thermalfoundation:material@1028)的环结构——tardis 链上的新边界. */
    @Test
    public void probePyrotheumCycle() {
        probeKey("thermalfoundation:material:1028");
    }

    /** 探针:extendedcrafting:singularity:0 的环结构与并集分析. */
    @Test
    public void probeSingularityCycle() {
        probeKey("extendedcrafting:singularity:0");
    }

    /** 探针:deepmoblearning:living_matter_extraterrestrial:0(21s 慢边界求解)的环结构与并集分析. */
    @Test
    public void probeLivingMatterCycle() {
        probeKey("deepmoblearning:living_matter_extraterrestrial:0");
    }

    /** 探针:minecraft:end_stone:0 的产出者(判定环盲降级后是否会缺料). */
    @Test
    public void probeEndStone() {
        probeKey("minecraft:end_stone:0");
    }

    /** 探针:minecraft:bedrock:0 的 dup 样板(solveDup 内部 35s 爆炸点). */
    @Test
    public void probeBedrock() {
        probeKey("minecraft:bedrock:0");
    }

    /** 探针:deepmoblearning:living_matter_hellish:0 的产出者(原生逐单位循环爆炸点). */
    @Test
    public void probeHellish() {
        probeKey("deepmoblearning:living_matter_hellish:0");
    }

    private void probeKey(String idWithMeta) {
        IAEItemStack key = loaded.byKey.get(idWithMeta);
        if (key == null) {
            System.out.println("[PROBE] 快照无 " + idWithMeta);
            return;
        }
        key = RecursiveCraftingHelper.canon(key);
        System.out.println("[PROBE] 键 = " + key + "@" + key.getItemDamage());
        // SCC 归属直查:isCycleStep 声称同 SCC,而环枚举为 0——验证到底谁在说真话
        com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex index =
                com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex.of(loaded.env.craftingGrid());
        IAEItemStack coal = loaded.item("minecraft:coal");
        System.out.println("[PROBE] sccId(singularity) = " + (index == null ? null : index.sccIdOf(key))
                + ", sccId(coal) = " + (index == null || coal == null ? null
                        : index.sccIdOf(RecursiveCraftingHelper.canon(coal))));
        if (coal != null) {
            IAEItemStack coalKey = RecursiveCraftingHelper.canon(coal);
            java.util.List<ICraftingPatternDetails> coalProducers = new java.util.ArrayList<>();
            coalProducers.addAll(loaded.env.craftingGrid().getCraftingFor(coalKey, null, -1,
                    loaded.env.world()));
            if (index != null) {
                List<ICraftingPatternDetails> byproduct = index.byproductMap().get(coalKey);
                if (byproduct != null) {
                    coalProducers.addAll(byproduct);
                }
            }
            System.out.println("[PROBE] 煤的生产者数: " + coalProducers.size());
            int shown = 0;
            for (ICraftingPatternDetails pattern : coalProducers) {
                if (++shown > 40) {
                    break;
                }
                StringBuilder in = new StringBuilder();
                for (IAEItemStack input : pattern.getCondensedInputs()) {
                    if (input != null) {
                        in.append(input).append('×').append(input.getStackSize()).append(' ');
                    }
                }
                StringBuilder out = new StringBuilder();
                for (IAEItemStack output : pattern.getCondensedOutputs()) {
                    if (output != null) {
                        out.append(output).append('×').append(output.getStackSize()).append(' ');
                    }
                }
                System.out.println("[PROBE]   煤生产者: [" + in + "] -> [" + out + "]");
            }
        }
        for (ICraftingPatternDetails pattern : loaded.env.craftingGrid()
                .getCraftingFor(key, null, -1, loaded.env.world())) {
            StringBuilder in = new StringBuilder();
            for (IAEItemStack input : pattern.getCondensedInputs()) {
                if (input != null) {
                    in.append(input).append('×').append(input.getStackSize()).append(' ');
                }
            }
            StringBuilder out = new StringBuilder();
            for (IAEItemStack output : pattern.getCondensedOutputs()) {
                if (output != null) {
                    out.append(output).append('×').append(output.getStackSize()).append(' ');
                }
            }
            System.out.println("[PROBE] 样板: [" + in + "] -> [" + out + "] 成环="
                    + CycleAnalyzer.isCycleStep(loaded.env.craftingGrid(), loaded.env.world(), pattern,
                            new CycleAnalyzer.ProducerIndex(loaded.env.craftingGrid(), loaded.env.world()))
                    + " 短环=" + new CycleAnalyzer.ProducerIndex(loaded.env.craftingGrid(),
                            loaded.env.world()).participatesInShortCycle(pattern));
        }
        List<List<CycleAnalyzer.CycleStep>> cycles = CycleAnalyzer
                .findCyclesThrough(loaded.env.craftingGrid(), key, loaded.env.world());
        System.out.println("[PROBE] 过键环数: " + cycles.size());
        java.util.Map<CycleAnalyzer.RateClass, Integer> byClass = new java.util.EnumMap<>(
                CycleAnalyzer.RateClass.class);
        int analyzeNull = 0;
        int smallest = Integer.MAX_VALUE;
        CycleAnalyzer.Analysis smallestAnalysis = null;
        for (List<CycleAnalyzer.CycleStep> cycle : cycles) {
            CycleAnalyzer.Analysis analysis = CycleAnalyzer.analyze(cycle);
            if (analysis == null) {
                analyzeNull++;
                continue;
            }
            byClass.merge(analysis.rateClass(), 1, Integer::sum);
            if (cycle.size() < smallest) {
                smallest = cycle.size();
                smallestAnalysis = analysis;
            }
        }
        System.out.println("[PROBE] 环分析分布: " + byClass + " null=" + analyzeNull
                + " 最小环 size=" + smallest + " -> " + (smallestAnalysis == null ? "-"
                        : smallestAnalysis.rateClass() + " netGain=" + smallestAnalysis.netGain()));
        // 短环逐个打印(≤12 键):判定"值得求解"的直接依据
        for (List<CycleAnalyzer.CycleStep> cycle : cycles) {
            if (cycle.size() > 12) {
                continue;
            }
            CycleAnalyzer.Analysis analysis = CycleAnalyzer.analyze(cycle);
            StringBuilder sb = new StringBuilder("[PROBE]   短环(size " + cycle.size() + ") "
                    + (analysis == null ? "分析null" : analysis.rateClass() + " netGain=" + analysis.netGain())
                    + ": ");
            for (CycleAnalyzer.CycleStep step : cycle) {
                sb.append(step.fromKey()).append('@').append(step.fromKey().getItemDamage())
                        .append("→");
            }
            sb.append("(闭合)");
            System.out.println(sb);
        }
        CycleAnalyzer.Analysis union = CycleAnalyzer.analyzeUnion(cycles);
        System.out.println("[PROBE] 并集: " + (union == null ? "null"
                : union.rateClass() + " keys=" + union.keys().size() + " netGain=" + union.netGain()));
    }
}
