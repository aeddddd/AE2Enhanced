package com.github.aeddddd.ae2enhanced.test.lp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.item.Item;

import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.diag.plansnapshot.PlanSnapshot;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner;
import com.github.aeddddd.ae2enhanced.specialcrafting.LpPlanCache;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.PruneSettings;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 随机基准测试:从真实网络快照(全样板+全库存)随机抽取可合成物品×任意数量
 * 发起完整计划,在不同 LP 后端(系统属性 ae2e.lpSolver: dual|legacy)下对比
 * 完成率/缺料一致性/耗时——用统计面代替单点试错.
 * <p>抽样:固定锚点(known-hard 根请求)+ 最大 SCC 成员物品 + SCC 外随机物品;
 * 数量梯度 {1, 64, 1024, 65536, 1e6}.同一随机种子下两后端跑完全相同的请求集.</p>
 * <p>用法:{code ./gradlew test --tests PlannerBenchmarkTest -Dae2e.planreplay=<文件>
 * -Dae2e.benchRequests=<数>};诊断性输出,不硬断言(随机请求可能真实缺料).
 * 剪枝档位用 {@code -Dae2e.prune=off|demand|full}(默认 full),单元画像用
 * {@code -Dae2e.unitProfile=true}(P0 埋点:巨单元 vs 平凡单元的耗时/迭代占比)。</p>
 */
public class PlannerBenchmarkTest {

    @Test
    public void benchmark() throws Exception {
        String path = System.getProperty("ae2e.planreplay");
        File file = path != null ? new File(path) : latestSnapshot();
        org.junit.jupiter.api.Assumptions.assumeTrue(file != null && file.isFile(),
                "无计划快照(用 -Dae2e.planreplay=<文件> 或放 research/plansnapshot/),跳过");
        PlanSnapshot snap = PlanSnapshot.load(file);
        com.github.aeddddd.ae2enhanced.diag.DiagSwitch.setOverride(
                com.github.aeddddd.ae2enhanced.diag.DiagSwitch.SPECIAL_CRAFTING, false); // 基准期不转储
        // 关键:全部 StackRef 的 dummy Item 必须预建并<b>全局共享同一实例</b>——
        // AEItemStack 相等按 Item 身份,各 env 若各建实例,根请求会恒判
        // "无生产者"(实测:首请求全部误判缺料=根,迭代 0;此后碰巧共享实例
        // 的请求才进入完整计划——harness 伪影,非求解器差异)。
        // 注意:toAe 须在任一 SimulationEnv 存在之后调用(AE API 注册表初始化
        // 前置,否则 ItemStack 静态初始化失败——实测 ExceptionInInitializerError)
        Map<String, Item> items = new HashMap<>();
        SnapshotPlanSupport.envOf(snap, items); // 首次调用顺带完成 AE 初始化 + 全量 Item 预建
        SnapshotPlanSupport.toAe(snap.root, items);

        // 请求集构建:锚点 + 大 SCC 物品 + 外部随机物品
        int nReq = Integer.getInteger("ae2e.benchRequests", 20);
        List<PlanSnapshot.StackRef> requests = buildRequests(snap, nReq);

        // 后端对比(默认仅 dual;legacy 为对照仅按需开启:
        // -Dae2e.benchBackends=dual,legacy——legacy 在巨单元上 190s/请求,勿常跑)
        String[] backends = System.getProperty("ae2e.benchBackends", "dual").split(",");
        Map<String, List<String>> results = new HashMap<>();
        for (String backend : backends) {
            System.setProperty("ae2e.lpSolver", backend);
            List<String> rows = new ArrayList<>();
            System.out.println("[BENCH] ===== 后端 " + backend + " =====");
            for (PlanSnapshot.StackRef req : requests) {
                SnapshotPlanSupport.toAe(req, items); // 请求物品同样预建(同实例)
                SimulationEnv env = SnapshotPlanSupport.envOf(snap, items);
                IAEItemStack root = SnapshotPlanSupport.toAe(req, items);
                long target = Math.max(1, req.count);
                long t0 = System.nanoTime();
                String row;
                try {
                    CondensationPlanner.LpPlanOutcome out = CondensationPlanner.solve(env.craftingGrid(),
                            NetworkPatternIndex.of(env.craftingGrid()), root, target,
                            SnapshotPlanSupport.stockOf(env));
                    long wall = (System.nanoTime() - t0) / 1_000_000;
                    row = String.format("%s×%d | 单元%d LP%d 降级%d | 缺料%d种 | 迭代%d | %dms",
                            req.id, target, out.units, out.lpUnits, out.degradedUnits,
                            out.deficits.size(), out.iterations, wall);
                    row += " | 缺料键:" + out.deficits.keySet();
                } catch (Throwable t) {
                    long wall = (System.nanoTime() - t0) / 1_000_000;
                    row = String.format("%s×%d | 异常 %s | %dms", req.id, target, t, wall);
                }
                rows.add(row);
                System.out.println("[BENCH] " + row);
            }
            results.put(backend, rows);
        }

        // 对比汇总(仅当多后端运行时)
        if (results.size() > 1) {
            System.out.println("[BENCH] ===== 对比汇总 =====");
            String other = results.keySet().stream().filter(k -> !"dual".equals(k)).findFirst().orElse(null);
            for (int i = 0; i < requests.size(); i++) {
                PlanSnapshot.StackRef req = requests.get(i);
                String a = results.get("dual").get(i);
                String b = results.get(other).get(i);
                boolean sameMissing = missingKeysOf(a).equals(missingKeysOf(b));
                System.out.printf("[BENCH] #%d %s×%d 缺料一致=%s%n  dual:   %s%n  %s: %s%n",
                        i, req.id, Math.max(1, req.count), sameMissing, a, other, b);
            }
        }
    }

    private static String missingKeysOf(String row) {
        int p = row.indexOf("缺料键:");
        return p < 0 ? "" : row.substring(p);
    }

    /** 请求集:根锚点 + 大 SCC 成员 + SCC 外随机物品(固定种子 42,与后端对比集完全一致). */
    private static List<PlanSnapshot.StackRef> buildRequests(PlanSnapshot snap, int nReq) {
        List<PlanSnapshot.StackRef> requests = new ArrayList<>();
        requests.add(snap.root);
        List<PlanSnapshot.StackRef> pool = new ArrayList<>();
        for (PlanSnapshot.PatternEntry e : snap.patterns) {
            pool.addAll(e.outputs);
        }
        List<String> bigScc = biggestSccKeys(snap);
        List<PlanSnapshot.StackRef> inScc = new ArrayList<>();
        List<PlanSnapshot.StackRef> outScc = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (PlanSnapshot.StackRef ref : pool) {
            String k = ref.id + ":" + ref.meta + ":" + ref.nbt;
            if (seen.add(k)) {
                (bigScc.contains(k) ? inScc : outScc).add(ref);
            }
        }
        Random rng = new Random(42);
        for (int i = 0; i < nReq; i++) {
            PlanSnapshot.StackRef base;
            if (i % 2 == 0 && !inScc.isEmpty()) {
                base = inScc.get(rng.nextInt(inScc.size()));
            } else {
                base = outScc.get(rng.nextInt(outScc.size()));
            }
            PlanSnapshot.StackRef req = new PlanSnapshot.StackRef();
            req.id = base.id;
            req.meta = base.meta;
            req.nbt = base.nbt;
            req.count = new long[] { 1, 64, 1024, 65536, 1_000_000 }[i % 5];
            requests.add(req);
        }
        return requests;
    }

    // ===== 任务 4:计划缓存 off vs full(命中率 + 墙钟) =====

    /**
     * 计划缓存 off vs full 对比:同一网格(单一 env/index,生产形态)上把整套请求连跑两轮
     * ——首轮冷(C1 全 miss)、次轮热(应全 hit),并逐请求打印墙钟/迭代。
     *
     * <p>off 档 = 旧全量库存 + 无缓存(对拍基线,口径与变更前一致);full 档 = C3 闭包 +
     * C2 稀疏库存 + C1 计划结果。两档的冷算统计(单元/LP 单元/迭代/降级/缺料种类)必须
     * 逐请求相同——这是"命中不改计划内容"在真实快照上的对拍。</p>
     *
     * <p>用法:{@code AE2E_BENCH=1 ./gradlew test --tests PlannerBenchmarkTest}
     * (可用 {@code -Dae2e.planCacheMax=64} 放宽 C1 条目上限;默认 8 时 21 个请求会互相
     * 逐出,命中率受 LRU 影响——报告里两种都值得看)。</p>
     */
    @Test
    public void cacheOffVsFull() throws Exception {
        String path = System.getProperty("ae2e.planreplay");
        File file = path != null ? new File(path) : latestSnapshot();
        org.junit.jupiter.api.Assumptions.assumeTrue(file != null && file.isFile(),
                "无计划快照(用 -Dae2e.planreplay=<文件> 或放 research/plansnapshot/),跳过");
        PlanSnapshot snap = PlanSnapshot.load(file);
        com.github.aeddddd.ae2enhanced.diag.DiagSwitch.setOverride(
                com.github.aeddddd.ae2enhanced.diag.DiagSwitch.SPECIAL_CRAFTING, false);
        Map<String, Item> items = new HashMap<>();
        SnapshotPlanSupport.envOf(snap, items);
        SnapshotPlanSupport.toAe(snap.root, items);
        List<PlanSnapshot.StackRef> requests = buildRequests(snap,
                Integer.getInteger("ae2e.benchRequests", 20));

        // 单一网格:一个 env/index 服务全部请求(生产形态)
        SimulationEnv env = SnapshotPlanSupport.envOf(snap, items);
        IAEItemStack[] roots = new IAEItemStack[requests.size()];
        String[] labels = new String[requests.size()];
        long[] targets = new long[requests.size()];
        for (int i = 0; i < requests.size(); i++) {
            PlanSnapshot.StackRef req = requests.get(i);
            roots[i] = SnapshotPlanSupport.toAe(req, items);
            labels[i] = req.id + "×" + Math.max(1, req.count);
            targets[i] = Math.max(1, req.count);
        }

        LpPlanCache.Settings cacheSettings = LpPlanCache.Settings.fromSystemProperties();
        System.out.println("[CACHE-BENCH] 快照 " + file.getName() + " 请求 " + requests.size()
                + " 库存键 " + CacheDigestSupport.fullStock(env).size() + " 档位 " + cacheSettings);
        Round offCold = runCacheRound("off-冷", env, roots, labels, targets, cacheSettings,
                LpPlanCache.Mode.OFF);
        Round offWarm = runCacheRound("off-热", env, roots, labels, targets, cacheSettings,
                LpPlanCache.Mode.OFF);
        Round fullCold = runCacheRound("full-冷", env, roots, labels, targets, cacheSettings,
                LpPlanCache.Mode.FULL);
        Round fullWarm = runCacheRound("full-热", env, roots, labels, targets, cacheSettings,
                LpPlanCache.Mode.FULL);

        // 等价性对拍:off 冷 vs full 冷 的逐请求统计必须逐位相同
        org.junit.jupiter.api.Assertions.assertArrayEquals(offCold.iterations, fullCold.iterations,
                "off 与 full 的逐请求迭代数不一致(缓存改变了计划)");
        org.junit.jupiter.api.Assertions.assertArrayEquals(offCold.lpUnits, fullCold.lpUnits,
                "off 与 full 的逐请求 LP 单元数不一致");
        org.junit.jupiter.api.Assertions.assertArrayEquals(offCold.units, fullCold.units,
                "off 与 full 的逐请求求解单元数不一致(闭包缓存改变了单元发现)");
        org.junit.jupiter.api.Assertions.assertArrayEquals(offCold.deficitKinds, fullCold.deficitKinds,
                "off 与 full 的逐请求缺料种类数不一致");
        org.junit.jupiter.api.Assertions.assertArrayEquals(offCold.labels, fullCold.labels,
                "逐请求标签不一致");

        LpPlanCache.Stats stats = NetworkPatternIndex.of(env.craftingGrid()).planCache().stats();
        org.junit.jupiter.api.Assertions.assertEquals(0L, stats.coverageFallbacks,
                "真实快照上出现稀疏库存覆盖集外读穿(覆盖集不完备)——次数 " + stats.coverageFallbacks);
        long lookups = stats.planHits + stats.planMisses;
        System.out.printf("[CACHE-BENCH] 汇总: off 冷 %dms/热 %dms | full 冷 %dms/热 %dms"
                        + " | full 命中率 %d/%d=%.1f%% closure %d/%d fallback=%d%n",
                offCold.wallMs, offWarm.wallMs, fullCold.wallMs, fullWarm.wallMs,
                stats.planHits, lookups, lookups == 0 ? 0.0 : 100.0 * stats.planHits / lookups,
                stats.closureHits, stats.closureHits + stats.closureMisses, stats.coverageFallbacks);
    }

    /** 一轮请求执行的观测结果(逐请求单元/迭代/LP 单元/缺料种类 + 总墙钟). */
    private static final class Round {
        long[] iterations;
        int[] lpUnits;
        int[] units;
        int[] deficitKinds;
        String[] labels;
        long wallMs;
    }

    private static Round runCacheRound(String roundLabel, SimulationEnv env, IAEItemStack[] roots,
            String[] labels, long[] targets, LpPlanCache.Settings cacheSettings,
            LpPlanCache.Mode mode) {
        Round round = new Round();
        round.iterations = new long[roots.length];
        round.lpUnits = new int[roots.length];
        round.units = new int[roots.length];
        round.deficitKinds = new int[roots.length];
        round.labels = labels;
        LpPlanCache.Settings roundSettings = LpPlanCache.Settings.of(mode, cacheSettings.planMax,
                cacheSettings.closureMax, false);
        NetworkPatternIndex index = NetworkPatternIndex.of(env.craftingGrid());
        int degradedTotal = 0;
        for (int i = 0; i < roots.length; i++) {
            // 每请求新建库存来源(生产:每个 CraftingJob 读一次网络库存)
            CacheDigestSupport.EnvStockSource source = new CacheDigestSupport.EnvStockSource(env);
            long t0 = System.nanoTime();
            CondensationPlanner.LpPlanOutcome out = CondensationPlanner.solve(env.craftingGrid(), index,
                    roots[i], targets[i], source, new PruneSettings(PruneSettings.Mode.FULL, false, false),
                    com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpHotStart.Chain
                            .fromSystemProperties(),
                    roundSettings);
            long wall = (System.nanoTime() - t0) / 1_000_000;
            round.iterations[i] = out.iterations;
            round.lpUnits[i] = out.lpUnits;
            round.units[i] = out.units;
            round.deficitKinds[i] = out.deficits.size();
            round.wallMs += wall;
            degradedTotal += out.degradedUnits;
            System.out.printf("[CACHE-BENCH] %s #%d %s | 单元%d LP%d 迭代%d 降级%d 缺料%d种 | %dms%n",
                    roundLabel, i, labels[i], out.units, out.lpUnits, out.iterations, out.degradedUnits,
                    out.deficits.size(), wall);
        }
        long iterations = 0;
        int lpUnits = 0;
        int deficits = 0;
        for (int i = 0; i < roots.length; i++) {
            iterations += round.iterations[i];
            lpUnits += round.lpUnits[i];
            deficits += round.deficitKinds[i];
        }
        LpPlanCache.Stats stats = index.planCache().stats();
        System.out.printf("[CACHE-BENCH] %s 合计: 请求%d 迭代%d LP单元%d 缺料%d 降级%d 墙钟%dms"
                        + " | plan hit=%d miss=%d closure hit=%d miss=%d fallback=%d%n",
                roundLabel, roots.length, iterations, lpUnits, deficits, degradedTotal, round.wallMs,
                stats.planHits, stats.planMisses, stats.closureHits, stats.closureMisses,
                stats.coverageFallbacks);
        return round;
    }

    /** 快照键图(id:meta:nbt)上的最大 SCC 成员键列表(Tarjan,迭代). */
    private static List<String> biggestSccKeys(PlanSnapshot snap) {
        Map<String, java.util.Set<String>> adj = new HashMap<>();
        for (PlanSnapshot.PatternEntry e : snap.patterns) {
            for (PlanSnapshot.StackRef o : e.outputs) {
                String from = o.id + ":" + o.meta + ":" + o.nbt;
                for (PlanSnapshot.StackRef in : e.inputs) {
                    adj.computeIfAbsent(from, k -> new LinkedHashSet<>())
                            .add(in.id + ":" + in.meta + ":" + in.nbt);
                }
            }
        }
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        java.util.Set<String> onStk = new LinkedHashSet<>();
        java.util.Deque<String> stk = new java.util.ArrayDeque<>();
        int[] counter = { 0 };
        List<List<String>> sccs = new ArrayList<>();
        for (String v : adj.keySet()) {
            if (!index.containsKey(v)) {
                tarjan(v, adj, index, low, onStk, stk, counter, sccs);
            }
        }
        List<String> best = Collections.emptyList();
        for (List<String> scc : sccs) {
            if (scc.size() > best.size()) {
                best = scc;
            }
        }
        System.out.println("[BENCH] 最大 SCC 键数: " + best.size() + " (共 " + sccs.size() + " 个)");
        return best;
    }

    private static void tarjan(String start, Map<String, java.util.Set<String>> adj,
            Map<String, Integer> index, Map<String, Integer> low, java.util.Set<String> onStk,
            java.util.Deque<String> stk, int[] counter, List<List<String>> sccs) {
        java.util.Deque<Object[]> work = new java.util.ArrayDeque<>();
        index.put(start, counter[0]);
        low.put(start, counter[0]);
        counter[0]++;
        stk.push(start);
        onStk.add(start);
        work.push(new Object[] { start, adj.getOrDefault(start, Collections.emptySet()).iterator() });
        while (!work.isEmpty()) {
            Object[] top = work.peek();
            String node = (String) top[0];
            @SuppressWarnings("unchecked")
            java.util.Iterator<String> it = (java.util.Iterator<String>) top[1];
            boolean advanced = false;
            while (it.hasNext()) {
                String w = it.next();
                if (!index.containsKey(w)) {
                    index.put(w, counter[0]);
                    low.put(w, counter[0]);
                    counter[0]++;
                    stk.push(w);
                    onStk.add(w);
                    work.push(new Object[] { w, adj.getOrDefault(w, Collections.emptySet()).iterator() });
                    advanced = true;
                    break;
                } else if (onStk.contains(w)) {
                    low.put(node, Math.min(low.get(node), index.get(w)));
                }
            }
            if (advanced) {
                continue;
            }
            work.pop();
            if (!work.isEmpty()) {
                String parent = (String) work.peek()[0];
                low.put(parent, Math.min(low.get(parent), low.get(node)));
            }
            if (low.get(node).equals(index.get(node))) {
                List<String> comp = new ArrayList<>();
                String w;
                do {
                    w = stk.pop();
                    onStk.remove(w);
                    comp.add(w);
                } while (!w.equals(node));
                sccs.add(comp);
            }
        }
    }

    private static File latestSnapshot() {
        File dir = new File("research/plansnapshot");
        File[] files = dir.isDirectory() ? dir.listFiles((d, name) -> name.startsWith("plansnapshot-")) : null;
        if (files == null || files.length == 0) {
            return null;
        }
        java.util.Arrays.sort(files);
        return files[files.length - 1];
    }
}
