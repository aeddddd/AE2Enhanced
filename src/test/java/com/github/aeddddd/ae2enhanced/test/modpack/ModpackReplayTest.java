package com.github.aeddddd.ae2enhanced.test.modpack;

import static com.github.aeddddd.ae2enhanced.test.support.PlanAssert.assertThatPlan;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;

import com.github.aeddddd.ae2enhanced.diag.metrics.Counter;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.test.support.PlanView;

/**
 * 整合包真实配方回放与普查(LP 计划器默认路径).
 * <p>可重复入口由 manifest 显式指定 fixture；旧诊断模式仍可读
 * {@code research/harvest/harvest-*.json}，但不会被默认 test 或 modpackReplayTest 执行。</p>
 */
@Tag("replay")
public class ModpackReplayTest {

    /** 单订单墙钟上限:此前崩溃单经回落原生烧满 30s 预算;60s 已含充足余量. */
    private static final long ORDER_TIMEOUT_MS = 60_000L;

    private static ModpackFixture.Loaded loaded;
    private static ModpackFixtureManifest.Loaded manifestFixture;

    @BeforeAll
    public static void loadSnapshot() throws Exception {
        if (System.getProperty(ModpackFixtureManifest.FIXTURE_PROPERTY) != null) {
            manifestFixture = ModpackFixtureManifest.loadFromProperty();
            loaded = manifestFixture.fixture;
            System.out.printf("[MODPACK] fixture %s (%s %s), 快照 %s%n",
                    manifestFixture.manifest.directory, manifestFixture.manifest.source,
                    manifestFixture.manifest.sourceVersion, manifestFixture.manifest.snapshotFile.getName());
        } else {
            Assumptions.assumeTrue(ModpackFixture.available(),
                    "无整合包快照(research/harvest/ 缺 harvest-*.json),跳过");
            loaded = ModpackFixture.loadLatest();
        }
        File snapshot = manifestFixture == null ? ModpackFixture.latestSnapshot() : manifestFixture.manifest.snapshotFile;
        System.out.printf("[MODPACK] 快照 %s: 合成台样板 %d, 熔炉 %d, 机器 %d, 跳过 %d, 终端库存键 %d%n",
                snapshot.getName(), loaded.patternsCrafting, loaded.patternsFurnace,
                loaded.patternsMachine, loaded.skipped, loaded.terminalStockKeys);
    }

    private static void replay(ModpackFixtureManifest.Request request) {
        String id = request.item;
        long count = request.count;
        IAEItemStack what = loaded.item(id);
        assertThat(what).as("%s 必须存在于 fixture", id).isNotNull();
        assertThat(hasProducer(what)).as("%s 必须有产出样板", id).isTrue();
        what = what.copy();
        what.setStackSize(count);
        long fallbacksBefore = fallbackCount();
        long degradedBefore = lpDegradedCount();

        long t0 = System.nanoTime();
        CraftingJob job = loaded.env.runJobTimed(loaded.env.newLpJob(what),
                request.timeoutMs, TimeUnit.MILLISECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        boolean timedOut = job == null;

        assertThat(timedOut).as("%s×%d timeout", id, count).isEqualTo(request.timeout);
        assertThat(fallbackCount() - fallbacksBefore).as("%s×%d fallback", id, count)
                .isEqualTo(request.fallback);
        assertThat(lpDegradedCount() - degradedBefore).as("%s×%d degraded", id, count)
                .isEqualTo(request.degraded);
        if (timedOut) {
            System.out.printf("[MODPACK] 回放 %s×%d: %d ms, timeout=true%n", id, count, elapsedMs);
            return;
        }

        PlanView view = PlanView.of(job);
        System.out.printf("[MODPACK] 回放 %s×%d: %d ms, simulation=%s, missing=%d 种, "
                + "fallback=%d, degraded=%d, timeout=false%n", id, count, elapsedMs,
                view.simulation(), view.missingItems().size(), request.fallback, request.degraded);
        assertThat(view.simulation()).as("%s×%d simulation", id, count).isEqualTo(request.simulation);
        assertThat(view.missingItems().size()).as("%s×%d missing", id, count).isEqualTo(request.missing);
        if (!view.simulation()) {
            assertThatPlan(view).succeeded();
        }
    }

    /**
     * 全网普查:每个可合成键请求 1 个,统计路径/耗时/失败分布(M6 验收:
     * 回落率与 LP 单元降级率必须为 0).报告写出到 research/harvest/census-时间戳.txt.
     */
    private static void census() throws Exception {
        List<IAEItemStack> keys = new ArrayList<>(
                ((com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess) loaded.env.craftingGrid())
                        .ae2enhanced$craftableKeys());
        int ok = 0, missing = 0, timeout = 0, fallback = 0, error = 0;
        List<String> failures = new ArrayList<>();
        long worstMs = 0;
        String worstKey = null;
        long censusStart = System.nanoTime();
        for (IAEItemStack key : keys) {
            IAEItemStack what = key.copy();
            what.setStackSize(1);
            long fallbacksBefore = fallbackCount();
            long degradedBefore = lpDegradedCount();
            long t0 = System.nanoTime();
            CraftingJob job = null;
            try {
                job = loaded.env.runJobTimed(loaded.env.newLpJob(what), 10, TimeUnit.SECONDS);
            } catch (Throwable t) {
                error++;
                failures.add("ERROR " + key + " : " + t);
                continue;
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (ms > worstMs) {
                worstMs = ms;
                worstKey = String.valueOf(key);
            }
            if (job == null) {
                timeout++;
                failures.add("TIMEOUT " + key);
                continue;
            }
            if (fallbackCount() > fallbacksBefore) {
                fallback++;
                failures.add("FALLBACK " + key);
                continue;
            }
            if (lpDegradedCount() > degradedBefore) {
                error++;
                failures.add("LP-DEGRADED " + key);
                continue;
            }
            if (PlanView.of(job).simulation()) {
                missing++;
                failures.add("MISSING " + key + " : " + PlanView.of(job).missingItems().size() + " 种");
            } else {
                ok++;
            }
        }
        long totalMs = (System.nanoTime() - censusStart) / 1_000_000;
        String summary = String.format(
                "普查完成: 共 %d 键, 可合成 %d, 缺料 %d, 回落 %d, 超时 %d, 异常 %d; 总耗时 %d ms, 最慢 %d ms (%s)",
                keys.size(), ok, missing, fallback, timeout, error, totalMs, worstMs, worstKey);
        System.out.println("[MODPACK] " + summary);
        File report = new File(ModpackFixture.SNAPSHOT_DIR,
                "census-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".txt");
        try (PrintWriter w = new PrintWriter(new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(report), StandardCharsets.UTF_8))) {
            w.println(summary);
            for (String failure : failures) {
                w.println(failure);
            }
        }
        assertThat(fallback).as("全网普查不得发生 LP→原生回落: %s", failures).isZero();
        assertThat(error).as("全网普查不得异常或 LP 单元降级: %s", failures).isZero();
        assertThat(timeout).as("全网普查不得超时: %s", failures).isZero();
        assertThat(ok + missing).as("全网普查每个键都必须产出计划").isEqualTo(keys.size());
    }

    @TestFactory
    @Tag("fixture")
    public List<DynamicTest> replayManifestRequests() {
        if (manifestFixture == null) {
            return Collections.emptyList();
        }
        List<DynamicTest> tests = new ArrayList<>();
        int index = 0;
        for (ModpackFixtureManifest.Request request : manifestFixture.manifest.requests) {
            String name = String.format("%02d %s×%d", ++index, request.item, request.count);
            tests.add(DynamicTest.dynamicTest(name, () -> replay(request)));
        }
        return tests;
    }

    /** 历史崩溃单一:1000× cosmic_balance(30s 原生预算中断样本之一). */
    @Test
    @Tag("legacy")
    public void testCosmicBalance() {
        replay("avaritiaitem:cosmic_balance", 1000);
    }

    /** 历史崩溃单二:100× tardis_polyp(30s 原生预算中断样本之二). */
    @Test
    @Tag("legacy")
    public void testTardisPolyp() {
        replay("contenttweaker:tardis_polyp", 100);
    }

    private static void replay(String id, long count) {
        IAEItemStack what = loaded.item(id);
        Assumptions.assumeTrue(what != null, id + " 不存在于快照,跳过");
        Assumptions.assumeTrue(hasProducer(what),
                id + " 在快照中无任何产出样板(玩家编码样板/代码注册配方不在采集面),跳过回放");
        what = what.copy();
        what.setStackSize(count);
        long fallbacksBefore = fallbackCount();

        long t0 = System.nanoTime();
        CraftingJob job = loaded.env.runJobTimed(loaded.env.newLpJob(what),
                ORDER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(job).as("%s×%d 计算超时(%dms)", id, count, ORDER_TIMEOUT_MS).isNotNull();
        PlanView view = PlanView.of(job);
        System.out.printf("[MODPACK] 回放 %s×%d: %d ms, simulation=%s, missing=%d 种%n",
                id, count, elapsedMs, view.simulation(), view.missingItems().size());
        for (java.util.Map.Entry<IAEItemStack, Long> e : view.missingItems().entrySet()) {
            System.out.printf("[MODPACK]   missing %s = %d%n", e.getKey(), e.getValue());
        }
        assertThat(fallbackCount()).as("%s×%d 发生 LP→原生回落", id, count).isEqualTo(fallbacksBefore);
        // 验收口径:计算有界 + 不回落原生是硬指标;夹具里非终端键 0 库存,
        // 被环盲降级的增产环如实报缺料(模拟计划)是正确结果——真实网络备料
        // 充足时预检放行(库存覆盖缺口),求解器照常出可提交计划
        if (view.simulation()) {
            System.out.printf("[MODPACK] %s×%d 以缺料模拟计划完成(如实上报缺失)%n", id, count);
            assertThat(view.missingItems()).as("%s×%d 模拟计划应有缺失条目", id, count).isNotEmpty();
        } else {
            assertThatPlan(view).succeeded(); // 断言失败详情见上方 [MODPACK] 打印
        }
    }

    /** 快照图中该键是否有产出样板(主索引或副产物). */
    private static boolean hasProducer(IAEItemStack what) {
        if (!loaded.env.craftingGrid().getCraftingFor(what, null, -1, loaded.env.world()).isEmpty()) {
            return true;
        }
        com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex index =
                com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex.of(loaded.env.craftingGrid());
        return index != null && index.byproductMap().containsKey(
                com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper.canon(what));
    }

    /** tardis 家族最难单:100× tardis_branch(曾暴露模拟趟缺全盲兜底的回落缺陷). */
    @Test
    @Tag("legacy")
    public void testTardisBranch() {
        replay("contenttweaker:tardis_branch", 100);
    }

    /**
     * 全网普查:每个可合成键请求 1 个,统计路径/耗时/失败分布.
     * 报告写出到 research/harvest/census-时间戳.txt(不入 Git).
     */
    @Test
    @Tag("census")
    public void testNetworkCensus() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("AE2E_CENSUS")),
                "普查默认跳过(AE2E_CENSUS=1 开启)");
        census();
    }

    /** 全局 plan.lp.degradedUnit 计数(LP 单元降级事件数). */
    private static long lpDegradedCount() {
        for (Counter counter : MetricsRegistry.counters()) {
            if (counter.name().equals("plan.lp.degradedUnit")) {
                return counter.get();
            }
        }
        return 0;
    }

    /** 全局 plan.fallback.* 计数总和(LP→原生回落事件数). */
    private static long fallbackCount() {
        long total = 0;
        for (Counter counter : MetricsRegistry.counters()) {
            if (counter.name().startsWith("plan.fallback.")) {
                total += counter.get();
            }
        }
        return total;
    }
}
