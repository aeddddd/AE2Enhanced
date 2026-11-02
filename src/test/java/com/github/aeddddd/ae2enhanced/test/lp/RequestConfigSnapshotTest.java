package com.github.aeddddd.ae2enhanced.test.lp;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.LpPlanCache;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.PruneSettings;
import com.github.aeddddd.ae2enhanced.specialcrafting.RequestConfigSnapshot;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpHotStart;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.RevisedSimplex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** M2b：LP 请求配置快照的捕获、整单复用与并发缓存隔离。 */
public class RequestConfigSnapshotTest {

    private static final String[] PROPERTIES = {
            "ae2e.prune", "ae2e.pruneTrace", "ae2e.unitProfile",
            "ae2e.planCache", "ae2e.planCacheMax", "ae2e.planCacheClosureMax",
            "ae2e.planCacheBytesMB", "ae2e.planCacheStats", "ae2e.hotStart", "ae2e.lpSolver",
            "ae2e.bootstrapIterBudget", "ae2e.bootstrapFuseNs", "ae2e.lpDebug",
            "ae2e.perturbRestarts", "ae2e.repairBudget", "ae2e.restoreBudget", "ae2e.basisDump"
    };

    @BeforeAll
    public static void boot() {
        com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap.boot();
    }

    private static Callable<LpPlanOutcome> solveConcurrent(CacheDigestSupport.Case c,
            RequestConfigSnapshot snapshot, CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return solve(c, snapshot);
        };
    }

    private static LpPlanOutcome solve(CacheDigestSupport.Case c, RequestConfigSnapshot snapshot) {
        return com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.solve(c.env.craftingGrid(),
                NetworkPatternIndex.of(c.env.craftingGrid()), c.root, c.target,
                new CacheDigestSupport.EnvStockSource(c.env), snapshot);
    }

    private static RequestConfigSnapshot config(PruneSettings.Mode prune, LpHotStart.Mode hotStart,
            int bootstrapIterBudget) {
        return new RequestConfigSnapshot(new PruneSettings(prune, false, false),
                LpPlanCache.Settings.of(LpPlanCache.Mode.FULL, 32, 32, false), hotStart, "auto", true,
                30_000, bootstrapIterBudget, 60_000_000_000L, false,
                new RevisedSimplex.Settings(6, 8, null, false));
    }

    @Test
    public void captureFreezesServerAndSystemConfiguration() {
        try (PropertyGuard ignored = new PropertyGuard()) {
            System.setProperty("ae2e.prune", "demand");
            System.setProperty("ae2e.pruneTrace", "true");
            System.setProperty("ae2e.unitProfile", "true");
            System.setProperty("ae2e.planCache", "closure");
            System.setProperty("ae2e.planCacheMax", "11");
            System.setProperty("ae2e.planCacheClosureMax", "12");
            System.setProperty("ae2e.planCacheBytesMB", "3");
            System.setProperty("ae2e.planCacheStats", "true");
            System.setProperty("ae2e.hotStart", "on");
            System.setProperty("ae2e.lpSolver", "dual");
            System.setProperty("ae2e.bootstrapIterBudget", "12345");
            System.setProperty("ae2e.bootstrapFuseNs", "6789");
            System.setProperty("ae2e.lpDebug", "true");
            System.setProperty("ae2e.perturbRestarts", "7");
            System.setProperty("ae2e.repairBudget", "9");
            System.setProperty("ae2e.restoreBudget", "10");
            System.setProperty("ae2e.basisDump", "true");
            AE2EnhancedConfig.crafting.specialCrafting = false;
            AE2EnhancedConfig.crafting.nativeCalcBudgetMs = 4321;

            RequestConfigSnapshot snapshot = RequestConfigSnapshot.capture();

            System.setProperty("ae2e.prune", "off");
            System.setProperty("ae2e.pruneTrace", "false");
            System.setProperty("ae2e.unitProfile", "false");
            System.setProperty("ae2e.planCache", "off");
            System.setProperty("ae2e.hotStart", "off");
            System.setProperty("ae2e.lpSolver", "exact");
            System.setProperty("ae2e.bootstrapIterBudget", "1");
            System.setProperty("ae2e.bootstrapFuseNs", "1");
            System.setProperty("ae2e.lpDebug", "false");
            System.setProperty("ae2e.perturbRestarts", "1");
            System.setProperty("ae2e.repairBudget", "1");
            System.setProperty("ae2e.restoreBudget", "1");
            System.setProperty("ae2e.basisDump", "false");
            AE2EnhancedConfig.crafting.specialCrafting = true;
            AE2EnhancedConfig.crafting.nativeCalcBudgetMs = 9999;

            assertEquals(PruneSettings.Mode.DEMAND, snapshot.prune.mode);
            assertTrue(snapshot.prune.trace);
            assertTrue(snapshot.prune.unitProfile);
            assertEquals(LpPlanCache.Mode.CLOSURE, snapshot.cache.mode);
            assertEquals(11, snapshot.cache.planMax);
            assertEquals(12, snapshot.cache.closureMax);
            assertEquals(3L * 1024L * 1024L, snapshot.cache.maxBytes);
            assertTrue(snapshot.cache.stats);
            assertEquals(LpHotStart.Mode.ON, snapshot.hotStart);
            assertEquals(LpHotStart.Mode.ON, snapshot.newHotStartChain().mode());
            assertNotSame(snapshot.newHotStartChain(), snapshot.newHotStartChain());
            assertEquals("dual", snapshot.solverMode);
            assertFalse(snapshot.specialCrafting);
            assertEquals(4321, snapshot.nativeCalcBudgetMs);
            assertEquals(12345, snapshot.bootstrapIterBudget);
            assertEquals(6789L, snapshot.bootstrapFuseNs);
            assertTrue(snapshot.lpDebug);
            assertEquals(7, snapshot.legacy.perturbRestarts);
            assertEquals(9, snapshot.legacy.repairBudget);
            assertEquals(Integer.valueOf(10), snapshot.legacy.restoreBudget);
            assertTrue(snapshot.legacy.basisDump);
        }
    }

    @Test
    public void capturedRequestDoesNotReadLaterGlobalChanges() {
        try (PropertyGuard ignored = new PropertyGuard()) {
            CacheDigestSupport.Case c = CacheDigestSupport.fixture("链-赤字");
            NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
            RequestConfigSnapshot snapshot = config(PruneSettings.Mode.FULL, LpHotStart.Mode.OFF, 200_000);
            LpPlanOutcome first = solve(c, snapshot);

            System.setProperty("ae2e.prune", "off");
            System.setProperty("ae2e.planCache", "off");
            System.setProperty("ae2e.hotStart", "on");
            System.setProperty("ae2e.lpSolver", "exact");
            System.setProperty("ae2e.bootstrapIterBudget", "1");
            System.setProperty("ae2e.bootstrapFuseNs", "1");
            System.setProperty("ae2e.lpDebug", "true");
            AE2EnhancedConfig.crafting.specialCrafting = !snapshot.specialCrafting;
            AE2EnhancedConfig.crafting.nativeCalcBudgetMs = snapshot.nativeCalcBudgetMs + 1;

            LpPlanOutcome second = solve(c, snapshot);
            assertSame(first, second, "同一快照必须命中原请求配置对应的缓存产物");
            assertEquals(1L, index.planCache().stats().planHits);
        }
    }

    @Test
    public void concurrentRequestsUseIndependentSnapshotsAndCacheEntries() throws Exception {
        CacheDigestSupport.Case c = CacheDigestSupport.fixture("链-赤字");
        NetworkPatternIndex index = NetworkPatternIndex.of(c.env.craftingGrid());
        RequestConfigSnapshot a = config(PruneSettings.Mode.FULL, LpHotStart.Mode.OFF, 200_000);
        RequestConfigSnapshot b = config(PruneSettings.Mode.OFF, LpHotStart.Mode.ON, 12345);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<LpPlanOutcome> futureA = executor.submit(solveConcurrent(c, a, barrier));
            Future<LpPlanOutcome> futureB = executor.submit(solveConcurrent(c, b, barrier));
            LpPlanOutcome outA = futureA.get(30, TimeUnit.SECONDS);
            LpPlanOutcome outB = futureB.get(30, TimeUnit.SECONDS);

            assertNotSame(outA, outB, "不同快照不得共享同一个 C1 条目");
            assertEquals(2L, index.planCache().stats().planMisses);
            assertEquals(0L, index.planCache().stats().planHits);
            assertEquals(2, index.planCache().stats().planEntries);

            assertSame(outA, solve(c, a), "请求 A 必须命中自己的快照条目");
            assertSame(outB, solve(c, b), "请求 B 必须命中自己的快照条目");
            assertEquals(2L, index.planCache().stats().planHits);
            assertEquals(2, index.planCache().stats().planEntries);
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class PropertyGuard implements AutoCloseable {
        private final Map<String, String> properties = new LinkedHashMap<>();
        private final boolean specialCrafting = AE2EnhancedConfig.crafting.specialCrafting;
        private final int nativeCalcBudgetMs = AE2EnhancedConfig.crafting.nativeCalcBudgetMs;

        PropertyGuard() {
            for (String key : PROPERTIES) {
                this.properties.put(key, System.getProperty(key));
            }
        }

        @Override
        public void close() {
            for (Map.Entry<String, String> entry : this.properties.entrySet()) {
                if (entry.getValue() == null) {
                    System.clearProperty(entry.getKey());
                } else {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }
            AE2EnhancedConfig.crafting.specialCrafting = this.specialCrafting;
            AE2EnhancedConfig.crafting.nativeCalcBudgetMs = this.nativeCalcBudgetMs;
        }
    }
}
