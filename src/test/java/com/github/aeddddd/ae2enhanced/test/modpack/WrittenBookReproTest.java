package com.github.aeddddd.ae2enhanced.test.modpack;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;

import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.metrics.Counter;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.test.support.PlanView;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;

/** 临时复现:latest(14).log 中 written_book×1 订单(569 键肉丸单元自举重解超时截断).
 * 快照未采集玩家编码的 written_book 处理样板,按日志中的真实输入手工注入:
 * in=[105 tardis_stem, 28 tardis_branch, 76 tardis_casing, 20 tardis_polyp] out=[1 written_book]. */
public class WrittenBookReproTest {

    private static ModpackFixture.Loaded loaded;

    @BeforeAll
    public static void loadSnapshot() throws Exception {
        Assumptions.assumeTrue(ModpackFixture.available(), "无快照,跳过");
        loaded = ModpackFixture.loadLatest();
    }

    @Test
    public void reproWrittenBook() {
        IAEItemStack book = ref("minecraft:written_book");
        IAEItemStack stem = ref("contenttweaker:tardis_stem");
        IAEItemStack branch = ref("contenttweaker:tardis_branch");
        IAEItemStack casing = ref("contenttweaker:tardis_casing");
        IAEItemStack polyp = ref("contenttweaker:tardis_polyp");
        Assumptions.assumeTrue(book != null && stem != null && branch != null && casing != null
                && polyp != null, "tardis 材料不全,跳过");
        loaded.env.addPattern(new ProcessingPatternBuilder(book)
                .addPreciseInput(105, stem)
                .addPreciseInput(28, branch)
                .addPreciseInput(76, casing)
                .addPreciseInput(20, polyp)
                .build());
        IAEItemStack what = book.copy();
        what.setStackSize(1);
        long degradedBefore = metric("plan.lp.degradedUnit");
        long t0 = System.nanoTime();
        CraftingJob job = loaded.env.runJobTimed(loaded.env.newLpJob(what), 120, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("[REPRO] written_book×1: %d ms job=%s degraded=%d%n", ms,
                job == null ? "null(超时)" : "ok", metric("plan.lp.degradedUnit") - degradedBefore);
        if (job != null) {
            PlanView view = PlanView.of(job);
            System.out.printf("[REPRO] simulation=%s missing=%d 种%n", view.simulation(),
                    view.missingItems().size());
            int shown = 0;
            for (java.util.Map.Entry<IAEItemStack, Long> e : view.missingItems().entrySet()) {
                if (shown++ < 40) {
                    System.out.printf("[REPRO]   missing %s = %d%n", e.getKey(), e.getValue());
                }
            }
        }
    }

    private static IAEItemStack ref(String id) {
        StackRef r = new StackRef();
        r.id = id;
        r.meta = 0;
        r.count = 1;
        return loaded.toAe(r);
    }

    private static long metric(String name) {
        for (Counter c : MetricsRegistry.counters()) {
            if (c.name().equals(name)) {
                return c.get();
            }
        }
        return 0;
    }
}
