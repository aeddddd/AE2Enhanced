package com.github.aeddddd.ae2enhanced.test.lp;

import static com.github.aeddddd.ae2enhanced.test.lp.CacheDigestSupport.fixture;
import static com.github.aeddddd.ae2enhanced.test.lp.CacheDigestSupport.fixtureNames;
import static com.github.aeddddd.ae2enhanced.test.lp.CacheDigestSupport.probe;
import static com.github.aeddddd.ae2enhanced.test.lp.CacheDigestSupport.sha256;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.MECraftingInventory;

import com.github.aeddddd.ae2enhanced.specialcrafting.FlowReconciler;
import com.github.aeddddd.ae2enhanced.specialcrafting.LpPlanCache;
import com.github.aeddddd.ae2enhanced.specialcrafting.LpPlanCache.Stats;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.PruneSettings;
import com.github.aeddddd.ae2enhanced.specialcrafting.PruneSettings.Mode;

/**
 * 计划结果缓存等价性测试(任务 4:方案 §验证 V1–V10).
 *
 * <p>不变式(全部用例的共同判据):</p>
 * <ul>
 * <li><b>命中不改内容</b>:命中与否的 digest(单元/迭代/降级 + 整数化执行记录 + 缺料)
 *     必须逐位相同,且缓存开启与关闭({@code planCache=off} 对拍)也必须逐位相同;</li>
 * <li><b>指纹精确</b>:库存/发射位/请求量/剪枝档/求解器后端任一变化都必须 miss;</li>
 * <li><b>覆盖集完备</b>:稀疏库存的读穿兜底计数恒 0(C2 覆盖集 = 求解器实际读取的键集).</li>
 * </ul>
 *
 * <p>digest 口径跨 JVM 启动逐位可复现(任务 1 的 StableOrder 链),故变更前采集的
 * SHA-256 基线(V8)可作真实的"off 与基线逐位一致"锚点。</p>
 */
public class CacheEquivalenceTest {

    /** 缓存档位(条目上限取大,避免 LRU 干扰等价性用例). */
    private static final LpPlanCache.Settings FULL =
            LpPlanCache.Settings.of(LpPlanCache.Mode.FULL, 32, 32, true);
    private static final LpPlanCache.Settings CLOSURE =
            LpPlanCache.Settings.of(LpPlanCache.Mode.CLOSURE, 32, 32, true);
    private static final LpPlanCache.Settings OFF =
            LpPlanCache.Settings.of(LpPlanCache.Mode.OFF, 32, 32, true);

    private static final PruneSettings PRUNE_FULL = new PruneSettings(Mode.FULL, false, false);
    private static final PruneSettings PRUNE_OFF = PruneSettings.OFF;

    /**
     * 变更前基线 digest 的 SHA-256(2026-10-11 变更前代码采集;夹具顺序 = {@code fixtures()}).
     * <p>采集条件:{@code ae2e.prune=full}、{@code ae2e.lpSolver=auto}、{@code ae2e.hotStart=verify}、
     * 无缓存(变更前不存在缓存);digest 口径见 {@link CacheDigestSupport#outcomeDigest}.</p>
     */
    private static final String[] BASELINE_SHA = {
            "31237af7915b7ac4e4f5dc9491fa39ff72d5d0d69423cb693e7711757eed4449", // 链-赤字
            "ebbdffe3e225cbaf460cb3c99ad7205c12b49e1ef109e1b9d64f5b06d7a0872b", // 种子自增环
            "9b7bf8c723e06eed3582124562b3cd3ca6d42ddaf7ef0d973d333f5aa609cc39", // 无种子自增环-降级
            "5c931c9c2a99f1d69e177dc4ee7a23f1b6a666989b8032d46e6f2e7ba4841295", // 容器返还
            "3d5f74e7ab2f7b9e0a8acf7fcfe83d456d68a3dab0b716f4522ec564d263aa04", // 替代候选异单元
            "2eb57a6f3de4a3d532f06821d101c73f3fa3d1988c3669adc446e555c15fa369", // 迷你蛛网
            "558ce9243bd36cba4439583d5eb533c602ff273aa03c604dc448d2126fd7af43", // 弦环网-大SCC
            "c9f1feeb049cecb5d09b0aa1555a5ea8eb2a936c32dce7b792e81ffbb469b337", // 库存全覆盖
            "af4fec043391989abddf933c89bfa19126eae2d03f58eb7e9d4025b6a1ae012c", // 千键弦环网
    };

    @BeforeAll
    public static void boot() {
        com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap.boot();
        // 求解器后端固定在默认档(基线采集同档);本类用例会临时切档(V6)后恢复
        System.setProperty("ae2e.lpSolver", "auto");
    }

    // ===== V1:同请求连跑两次 =====

    @Test
    public void v1SecondRunHitsAndDigestIdentical() {
        CacheDigestSupport.Case c = fixture("迷你蛛网");
        NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
        index.planCache().resetStats();

        CacheDigestSupport.Probe first = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        Stats afterFirst = index.planCache().stats();
        CacheDigestSupport.Probe second = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        Stats afterSecond = index.planCache().stats();

        assertEquals(first.digest, second.digest, "二轮 digest 与首轮不一致(命中改变了计划内容)");
        assertEquals(1L, afterFirst.planMisses, "首轮应 miss 一次");
        assertEquals(0L, afterFirst.planHits, "首轮不应命中");
        assertEquals(1L, afterSecond.planHits, "二轮应命中一次");
        assertEquals(afterFirst.planMisses, afterSecond.planMisses, "二轮不应再算");
        assertSame(second.outcome, first.outcome, "命中必须返回同一次计算的产物(不可变共享)");
        assertEquals(0L, afterSecond.coverageFallbacks,
                "稀疏库存覆盖集不全(读穿兜底 " + afterSecond.coverageFallbacks + " 次)");
        System.out.printf("[CACHE-EQ] V1 %s 冷=%dms 热=%dms hits=%d misses=%d coverage=%d%n",
                c.name, first.nanos / 1_000_000, second.nanos / 1_000_000, afterSecond.planHits,
                afterSecond.planMisses, index.planCache().stats().closureEntries);
    }

    // ===== V2:重建索引后 digest 相同 =====

    @Test
    public void v2RebuiltIndexSameDigest() {
        CacheDigestSupport.Case c = fixture("迷你蛛网");
        NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
        CacheDigestSupport.Probe cold = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        CacheDigestSupport.Probe warm = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        assertEquals(cold.digest, warm.digest, "同索引热跑 digest 漂移");

        // 重建索引(新 env ⇒ 新 NetworkPatternIndex ⇒ 缓存整体失效,不得跨索引复用)
        CacheDigestSupport.Case rebuilt = fixture("迷你蛛网");
        NetworkPatternIndex rebuiltIndex = NetworkPatternIndex.of(rebuilt.env.craftingGrid());
        assertNotSameIndexCache(index, rebuiltIndex);
        CacheDigestSupport.Probe rebuiltCold = probe(rebuilt.env, rebuilt.root, rebuilt.target, PRUNE_FULL,
                FULL);
        assertEquals(cold.digest, rebuiltCold.digest, "索引重建后的冷算 digest 与首轮不一致");
        Stats rebuiltStats = rebuiltIndex.planCache().stats();
        assertEquals(1L, rebuiltStats.planMisses, "新索引不应继承旧索引的缓存条目");
        assertEquals(0L, rebuiltStats.planHits, "新索引首跑不应命中");
    }

    private static void assertNotSameIndexCache(NetworkPatternIndex a, NetworkPatternIndex b) {
        assertTrue(a != b, "夹具未重建索引(缓存生命周期无法验证)");
        assertTrue(a.planCache() != b.planCache(), "索引重建未更换缓存实例");
    }

    // ===== V3:稀疏 vs 全量库存等价 =====

    @Test
    public void v3SparseStockEqualsFullStock() {
        int checked = 0;
        for (String name : fixtureNames()) {
            CacheDigestSupport.Case offCase = fixture(name);
            CacheDigestSupport.Probe full = probe(offCase.env, offCase.root, offCase.target, PRUNE_FULL, OFF);
            assertEquals(0L, NetworkPatternIndex.of(offCase.env.craftingGrid()).planCache().stats()
                    .coverageFallbacks, name + " off 档不应发生读穿");

            CacheDigestSupport.Case sparseCase = fixture(name);
            NetworkPatternIndex sparseIndex = NetworkPatternIndex.of(sparseCase.env.craftingGrid());
            CacheDigestSupport.Probe sparse = probe(sparseCase.env, sparseCase.root, sparseCase.target,
                    PRUNE_FULL, CLOSURE);
            assertEquals(full.digest, sparse.digest, name + " 稀疏库存与全量库存 digest 不一致");
            assertEquals(0L, sparseIndex.planCache().stats().coverageFallbacks,
                    name + " 出现覆盖集外读穿(需补齐闭包覆盖集)");
            checked++;
        }
        assertEquals(fixtureNames().size(), checked, "夹具覆盖不足");
    }

    // ===== V4:缺料缓存 → 补货 → 必须 miss 且等于冷算 =====

    @Test
    public void v4RestockInvalidatesDeficitHit() {
        CacheDigestSupport.Case c = fixture("链-赤字");
        NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
        index.planCache().resetStats();

        CacheDigestSupport.Probe cold = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        assertTrue(cold.outcome.deficits.size() > 0, "夹具应有缺料(否则 V4 无意义)");
        CacheDigestSupport.Probe warm = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        assertEquals(cold.digest, warm.digest, "缺料结果缓存后热跑 digest 漂移");
        Stats before = index.planCache().stats();
        assertEquals(1L, before.planHits);

        // 补货 10× SAND(根链的原料):同一索引、同一闭包,但覆盖键库存变了 ⇒ 指纹失配
        c.env.addStoredItem(CacheDigestSupport.mult(CacheDigestSupport.block(Blocks.SAND), 10));
        CacheDigestSupport.Probe restocked = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        Stats after = index.planCache().stats();
        assertEquals(before.planMisses + 1, after.planMisses, "补货后必须 miss(指纹未参与失效)");
        assertEquals(before.planHits, after.planHits, "补货后不应命中");
        assertEquals(before.closureHits + 1, after.closureHits, "补货不应使闭包缓存失效(闭包与库存无关)");
        assertNotEquals(warm.digest, restocked.digest, "补货未改变计划(digest 相同)?");
        assertTrue(restocked.outcome.deficits.isEmpty(), "补货后不应再缺料");

        // 等于冷算:同等商品/库存的全新环境冷跑
        CacheDigestSupport.Case fresh = fixture("链-赤字");
        fresh.env.addStoredItem(CacheDigestSupport.mult(CacheDigestSupport.block(Blocks.SAND), 10));
        CacheDigestSupport.Probe reference = probe(fresh.env, fresh.root, fresh.target, PRUNE_FULL, FULL);
        assertEquals(reference.digest, restocked.digest, "补货后的结果与冷算不一致");
    }

    // ===== V5:发射位变化必须 miss =====

    @Test
    public void v5EmitBitChangeInvalidates() {
        CacheDigestSupport.Case c = fixture("链-赤字");
        NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
        index.planCache().resetStats();
        CacheDigestSupport.Probe cold = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        Stats before = index.planCache().stats();

        // SAND 是覆盖集内的原料键:标记发射台 ⇒ 免费供给 ⇒ b 与快速路径都变(库存未变)
        c.env.addEmitable(CacheDigestSupport.block(Blocks.SAND));
        CacheDigestSupport.Probe emitted = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        Stats after = index.planCache().stats();
        assertEquals(before.planMisses + 1, after.planMisses, "发射位变化必须 miss");
        assertEquals(before.planHits, after.planHits, "发射位变化不得命中");
        assertNotEquals(cold.digest, emitted.digest, "发射位变化未改变计划?");

        CacheDigestSupport.Case fresh = fixture("链-赤字");
        fresh.env.addEmitable(CacheDigestSupport.block(Blocks.SAND));
        CacheDigestSupport.Probe reference = probe(fresh.env, fresh.root, fresh.target, PRUNE_FULL, FULL);
        assertEquals(reference.digest, emitted.digest, "发射位变化后的结果与冷算不一致");
        assertEquals(0L, after.coverageFallbacks, "发射位用例出现读穿兜底");
    }

    // ===== V6:属性切换不得跨模式命中 =====

    @Test
    public void v6ModeSwitchNeverHitsAcrossModes() {
        // (a) 剪枝档:pruneMode 进 C1 键
        CacheDigestSupport.Case c = fixture("链-赤字");
        NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
        index.planCache().resetStats();
        probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        Stats before = index.planCache().stats();
        CacheDigestSupport.Probe pruned = probe(c.env, c.root, c.target, PRUNE_OFF, FULL);
        Stats after = index.planCache().stats();
        assertEquals(before.planMisses + 1, after.planMisses, "剪枝档切换必须 miss");
        assertEquals(before.planHits, after.planHits, "剪枝档切换不得命中");
        CacheDigestSupport.Case fresh = fixture("链-赤字");
        assertEquals(probe(fresh.env, fresh.root, fresh.target, PRUNE_OFF, FULL).digest, pruned.digest,
                "剪枝档切换后结果与冷算不一致");

        // (b) 求解器后端:solverMode 进 C1 键
        String previous = System.getProperty("ae2e.lpSolver");
        try {
            System.setProperty("ae2e.lpSolver", "auto");
            CacheDigestSupport.Case solverCase = fixture("链-赤字");
            NetworkPatternIndex solverIndex = NetworkPatternIndex.of(solverCase.env.craftingGrid());
            solverIndex.planCache().resetStats();
            probe(solverCase.env, solverCase.root, solverCase.target, PRUNE_FULL, FULL);
            Stats beforeSolver = solverIndex.planCache().stats();
            System.setProperty("ae2e.lpSolver", "exact");
            CacheDigestSupport.Probe exact = probe(solverCase.env, solverCase.root, solverCase.target,
                    PRUNE_FULL, FULL);
            Stats afterSolver = solverIndex.planCache().stats();
            assertEquals(beforeSolver.planMisses + 1, afterSolver.planMisses, "求解器后端切换必须 miss");
            assertEquals(beforeSolver.planHits, afterSolver.planHits, "求解器后端切换不得命中");
            CacheDigestSupport.Case freshSolver = fixture("链-赤字");
            assertEquals(probe(freshSolver.env, freshSolver.root, freshSolver.target, PRUNE_FULL, FULL).digest,
                    exact.digest, "切换后端后的结果与该后端冷算不一致(缓存串了模式)");
        } finally {
            System.setProperty("ae2e.lpSolver", previous == null ? "auto" : previous);
        }
    }

    // ===== V7:8 线程并发同请求 =====

    @Test
    public void v7ConcurrentSameRequestIdenticalDigest() throws Exception {
        final CacheDigestSupport.Case c = fixture("迷你蛛网");
        final NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
        index.planCache().resetStats();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> probe(c.env, c.root, c.target, PRUNE_FULL, FULL).digest);
            }
            List<Future<String>> futures = pool.invokeAll(tasks, 300, TimeUnit.SECONDS);
            String expected = null;
            for (Future<String> future : futures) {
                String digest = future.get(); // 异常/超时在此抛出
                if (expected == null) {
                    expected = digest;
                } else {
                    assertEquals(expected, digest, "并发求解 digest 不一致");
                }
            }
            Stats stats = index.planCache().stats();
            assertEquals(0L, stats.coverageFallbacks, "并发用例出现读穿兜底");
            assertTrue(stats.planHits + stats.planMisses >= threads, "并发请求未走计划缓存路径");
            // 并发批后同请求必须命中(并发写入的条目可用且等价)
            CacheDigestSupport.Probe after = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
            assertEquals(expected, after.digest, "并发写入后命中 digest 漂移");
            assertTrue(index.planCache().stats().planHits >= 1, "并发批后同请求应命中");
            System.out.printf("[CACHE-EQ] V7 并发 %d 线程 digest 全同; plan hits=%d misses=%d closure hits=%d misses=%d%n",
                    threads, stats.planHits, stats.planMisses, stats.closureHits, stats.closureMisses);
        } finally {
            pool.shutdownNow();
        }
    }

    // ===== V8:off 与基线逐位一致 =====

    @Test
    public void v8OffMatchesRecordedBaseline() {
        List<String> names = fixtureNames();
        assertEquals(BASELINE_SHA.length, names.size(), "夹具数与基线锚点数不一致");
        for (int i = 0; i < names.size(); i++) {
            CacheDigestSupport.Case c = fixture(names.get(i));
            CacheDigestSupport.Probe off = probe(c.env, c.root, c.target, PRUNE_FULL, OFF);
            assertEquals(BASELINE_SHA[i], sha256(off.digest),
                    "off 档 digest 与变更前基线不一致:\n  " + off.digest);
            Stats stats = NetworkPatternIndex.of(c.env.craftingGrid()).planCache().stats();
            assertEquals(0L, stats.planHits + stats.planMisses, "off 档不应使用计划缓存");
            assertEquals(0L, stats.closureHits + stats.closureMisses, "off 档不应使用闭包缓存");
            System.out.printf("[CACHE-EQ] V8 %s off 档与基线一致(%dms)%n", names.get(i),
                    off.nanos / 1_000_000);
        }
    }

    // ===== V9:冷/热 digest 全等(跨 JVM 口径由 digest 本身保证) =====

    @Test
    public void v9ColdAndHotDigestsIdentical() {
        for (String name : new String[] { "链-赤字", "容器返还", "迷你蛛网" }) {
            CacheDigestSupport.Case c = fixture(name);
            NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
            CacheDigestSupport.Probe cold = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
            CacheDigestSupport.Probe hot1 = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
            CacheDigestSupport.Probe hot2 = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
            assertEquals(cold.digest, hot1.digest, name + " 冷/热 digest 不一致");
            assertEquals(cold.digest, hot2.digest, name + " 冷/热 digest 不一致(第三跑)");
            Stats stats = index.planCache().stats();
            assertEquals(2L, stats.planHits, name + " 二/三轮应各命中一次");
            assertEquals(1L, stats.planMisses, name + " 仅首轮应 miss");
        }
    }

    // ===== R5:无正计数执行时 inv==null 与传真实库存对账等价 =====

    /**
     * R5 证明:无正计数执行(计划全零,如库存全覆盖)时 {@code FlowReconciler} 不读取
     * 传入的模拟库存——故 {@code inv == null} 的快路径与传真实库存副本逐位等价
     * (守恒修复在全零计划上是恒等操作,被显式跳过).
     */
    @Test
    public void r5NullInventoryEquivalentWithoutExecutions() {
        // 无种子自增环:LP 判不可交付 ⇒ 扁平执行记录为空(R5 前提)
        CacheDigestSupport.Case c = fixture("无种子自增环-降级");
        ICraftingGrid cc = c.env.craftingGrid();
        NetworkPatternIndex index = NetworkPatternIndex.of(cc);
        CacheDigestSupport.Probe probe = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        assertTrue(probe.outcome.executions.isEmpty(), "夹具应无正计数执行(R5 前提)");

        // 传 null 库存(生产 R5 快路径)
        CraftingTreeNode rootNull = new CraftingTreeNode(cc, c.env.newLpJob(c.root), c.root.copy(), null, -1,
                0);
        FlowReconciler.Reconciled nullInv = FlowReconciler.reconcile(cc, index, c.root, c.target,
                probe.outcome, null, rootNull, mock(IActionSource.class));
        // 传真实库存副本
        MECraftingInventory inv = new MECraftingInventory(
                new MECraftingInventory(CacheDigestSupport.storageList(c.env)), true, false, true);
        CraftingTreeNode rootInv = new CraftingTreeNode(cc, c.env.newLpJob(c.root), c.root.copy(), null, -1,
                0);
        FlowReconciler.Reconciled withInv = FlowReconciler.reconcile(cc, index, c.root, c.target,
                probe.outcome, inv, rootInv, mock(IActionSource.class));

        assertEquals(CacheDigestSupport.outcomeDigest(probe.outcome, withInv.times, withInv.missing),
                CacheDigestSupport.outcomeDigest(probe.outcome, nullInv.times, nullInv.missing),
                "R5 inv==null 快路径的对账结果与传真实库存不一致");
        assertEquals(withInv.totalExtracted, nullInv.totalExtracted, "R5 快路径提取总量不一致");
        assertEquals(withInv.executions.size(), nullInv.executions.size(), "R5 快路径活跃执行数不一致");
    }

    // ===== V10:二轮耗时显著下降(同一闭包内命中) =====

    @Test
    public void v10SecondRunMuchFaster() {
        CacheDigestSupport.Case c = fixture("千键弦环网");
        NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
        index.planCache().resetStats();
        CacheDigestSupport.Probe cold = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        CacheDigestSupport.Probe warm = probe(c.env, c.root, c.target, PRUNE_FULL, FULL);
        assertEquals(cold.digest, warm.digest, "千键弦环网冷/热 digest 不一致");
        assertEquals(1L, index.planCache().stats().planHits, "二轮应命中计划缓存");
        System.out.printf("[CACHE-EQ] V10 千键弦环网 冷=%dms 热=%dms 迭代=%d coverage=%d%n",
                cold.nanos / 1_000_000, warm.nanos / 1_000_000, cold.outcome.iterations,
                index.planCache().stats().closureEntries);
        assertTrue(cold.nanos > TimeUnit.MILLISECONDS.toNanos(5), "冷却算太快,耗时对比无意义: "
                + cold.nanos / 1_000_000 + "ms");
        assertTrue(warm.nanos * 4 < cold.nanos,
                "二轮耗时未显著下降: 冷=" + cold.nanos / 1_000_000 + "ms 热=" + warm.nanos / 1_000_000 + "ms");

        // C1 关闭(仅 C3+C2)时:闭包命中但不省 LP ⇒ 计划内容相同而工作量不降
        CacheDigestSupport.Case closureOnly = fixture("千键弦环网");
        NetworkPatternIndex closureIndex = NetworkPatternIndex.of(closureOnly.env.craftingGrid());
        closureIndex.planCache().resetStats();
        CacheDigestSupport.Probe c0 = probe(closureOnly.env, closureOnly.root, closureOnly.target, PRUNE_FULL,
                CLOSURE);
        CacheDigestSupport.Probe c1 = probe(closureOnly.env, closureOnly.root, closureOnly.target, PRUNE_FULL,
                CLOSURE);
        assertEquals(c0.digest, c1.digest, "closure 档 digest 漂移");
        assertEquals(c0.outcome.iterations, c1.outcome.iterations, "closure 档(C1 关闭)不应省 LP 迭代");
        assertEquals(1L, closureIndex.planCache().stats().closureHits, "closure 档二轮应命中闭包缓存");
        assertEquals(0L, closureIndex.planCache().stats().planHits, "closure 档不应命中计划缓存");
    }
}
