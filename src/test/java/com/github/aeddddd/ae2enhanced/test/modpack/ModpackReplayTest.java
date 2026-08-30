package com.github.aeddddd.ae2enhanced.test.modpack;

import static com.github.aeddddd.ae2enhanced.test.specialcrafting.PlanAssert.assertThatPlan;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;

import com.github.aeddddd.ae2enhanced.diag.metrics.Counter;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.PlanView;

/**
 * 整合包真实配方回放与普查（工作流规划文档阶段 3）.
 * <p>依赖 {@code research/harvest/harvest-*.json} 快照（/ae2e harvest 在真实
 * 整合包内生成,拷入该目录;不入 Git）;快照缺失时整类跳过.</p>
 * <ul>
 * <li>崩溃单回放:历史触发看门狗的订单在真实配方拓扑上必须<b>不回落原生、
 * 不超预算、守恒可提交</b>(终端原材料库存放大,见 ModpackFixture);</li>
 * <li>全网普查({@code AE2E_CENSUS=1} 环境变量门控,默认跳过):对每个可合成键
 * 请求 1 个,产出路径分布/耗时分布/失败清单报告。</li>
 * </ul>
 */
public class ModpackReplayTest {

    /** 单订单墙钟上限:此前崩溃单经回落原生烧满 30s 预算;60s 已含充足余量. */
    private static final long ORDER_TIMEOUT_MS = 60_000L;

    private static ModpackFixture.Loaded loaded;

    @BeforeAll
    public static void loadSnapshot() throws Exception {
        Assumptions.assumeTrue(ModpackFixture.available(),
                "无整合包快照(research/harvest/ 缺 harvest-*.json),跳过");
        loaded = ModpackFixture.loadLatest();
        System.out.printf("[MODPACK] 快照 %s: 合成台样板 %d, 熔炉 %d, 机器 %d, 跳过 %d, 终端库存键 %d%n",
                ModpackFixture.latestSnapshot().getName(), loaded.patternsCrafting,
                loaded.patternsFurnace, loaded.patternsMachine, loaded.skipped, loaded.terminalStockKeys);
    }

    /** 历史崩溃单一:1000× cosmic_balance(30s 原生预算中断样本之一). */
    @Test
    public void testCosmicBalance() {
        replay("avaritiaitem:cosmic_balance", 1000);
    }

    /** 历史崩溃单二:100× tardis_polyp(30s 原生预算中断样本之二). */
    @Test
    public void testTardisPolyp() {
        replay("contenttweaker:tardis_polyp", 100);
    }

    /** tardis 家族最难单:100× tardis_branch(曾暴露模拟趟缺全盲兜底的回落缺陷). */
    @Test
    public void testTardisBranch() {
        replay("contenttweaker:tardis_branch", 100);
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
        CraftingJob job = loaded.env.runJobTimed(loaded.env.newDagJob(what),
                ORDER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(job).as("%s×%d 计算超时(%dms)", id, count, ORDER_TIMEOUT_MS).isNotNull();
        PlanView view = PlanView.of(job);
        System.out.printf("[MODPACK] 回放 %s×%d: %d ms, simulation=%s, missing=%d 种%n",
                id, count, elapsedMs, view.simulation(), view.missingItems().size());
        for (java.util.Map.Entry<IAEItemStack, Long> e : view.missingItems().entrySet()) {
            System.out.printf("[MODPACK]   missing %s = %d%n", e.getKey(), e.getValue());
        }
        assertThat(fallbackCount()).as("%s×%d 发生 DAG→原生回落", id, count).isEqualTo(fallbacksBefore);
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

    /**
     * 全网普查:每个可合成键请求 1 个,统计路径/耗时/失败分布.
     * 报告写出到 research/harvest/census-时间戳.txt(不入 Git).
     */
    @Test
    public void testNetworkCensus() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("AE2E_CENSUS")),
                "普查默认跳过(AE2E_CENSUS=1 开启)");
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
            long t0 = System.nanoTime();
            CraftingJob job = null;
            try {
                job = loaded.env.runJobTimed(loaded.env.newDagJob(what), 10, TimeUnit.SECONDS);
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
    }

    /** 全局 plan.fallback.* 计数总和(DAG→原生回落事件数). */
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
