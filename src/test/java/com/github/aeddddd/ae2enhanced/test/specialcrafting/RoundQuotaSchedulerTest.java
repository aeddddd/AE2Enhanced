package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.RoundQuotaScheduler;

/**
 * 超轮配额调度器纯函数测试(1.12.2 移植版):闭包推导、GCD 轮次恢复、配额过滤.
 */
public class RoundQuotaSchedulerTest {

    /** θ 结构样板集:A→B、A→C、B+C→4A + 无关外部 pattern(dirt→stick). */
    private static final class ThetaPatterns {
        final ICraftingPatternDetails crush;
        final ICraftingPatternDetails charge;
        final ICraftingPatternDetails back;
        final ICraftingPatternDetails external;
        final IAEItemStack stone;

        ThetaPatterns() {
            this.stone = SimulationEnv.block(Blocks.STONE);
            IAEItemStack cobble = SimulationEnv.block(Blocks.COBBLESTONE);
            IAEItemStack sand = SimulationEnv.block(Blocks.SAND);
            IAEItemStack dirt = SimulationEnv.block(Blocks.DIRT);
            IAEItemStack stick = SimulationEnv.item(Items.STICK);
            this.crush = new ProcessingPatternBuilder(cobble).addPreciseInput(1, this.stone).build();
            this.charge = new ProcessingPatternBuilder(sand).addPreciseInput(1, this.stone).build();
            this.back = new ProcessingPatternBuilder(SimulationEnv.mult(this.stone, 4))
                    .addPreciseInput(1, cobble)
                    .addPreciseInput(1, sand)
                    .build();
            this.external = new ProcessingPatternBuilder(stick).addPreciseInput(1, dirt).build();
        }
    }

    /** T1:闭包推导——外部 pattern 豁免,GCD 恢复超轮比. */
    @Test
    public void testDeriveQuotaThetaClosureAndGcd() {
        ThetaPatterns p = new ThetaPatterns();
        Map<ICraftingPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(p.crush, 4L);
        totals.put(p.charge, 4L);
        totals.put(p.back, 4L);
        totals.put(p.external, 7L); // 外部子合成:不触及闭包键

        RoundQuotaScheduler.Quota quota = RoundQuotaScheduler.deriveQuota(totals, p.stone);
        assertThat(quota).isNotNull();
        assertThat(quota.perRound).containsOnlyKeys(p.crush, p.charge, p.back);
        assertThat(quota.perRound.values()).containsExactlyInAnyOrder(1L, 1L, 1L);
    }

    /** T2:非自消耗 job(普通链)→ 不调度. */
    @Test
    public void testDeriveQuotaRejectsNormalPlan() {
        IAEItemStack stone = SimulationEnv.block(Blocks.STONE);
        IAEItemStack cobble = SimulationEnv.block(Blocks.COBBLESTONE);
        IAEItemStack dirt = SimulationEnv.block(Blocks.DIRT);
        ICraftingPatternDetails p0 = new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build();
        ICraftingPatternDetails p1 = new ProcessingPatternBuilder(dirt).addPreciseInput(1, cobble).build();

        Map<ICraftingPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(p0, 1L);
        totals.put(p1, 1L);
        RoundQuotaScheduler.Quota quota = RoundQuotaScheduler.deriveQuota(totals, dirt);
        assertThat(quota).isNull();
    }

    /** T3:大尺度 GCD 恢复:totals {512,8,512} → 超轮比 {64,1,64}. */
    @Test
    public void testGcdRecoversRounds() {
        ThetaPatterns p = new ThetaPatterns();
        Map<ICraftingPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(p.crush, 512L);
        totals.put(p.back, 8L);
        totals.put(p.charge, 512L);

        RoundQuotaScheduler.Quota quota = RoundQuotaScheduler.deriveQuota(totals, p.stone);
        assertThat(quota).isNotNull();
        assertThat(quota.perRound.get(p.crush)).isEqualTo(64L);
        assertThat(quota.perRound.get(p.back)).isEqualTo(1L);
        assertThat(quota.perRound.get(p.charge)).isEqualTo(64L);
    }

    /** T4:配额推进——先行推满的 pattern 被闸,其余放行;最慢进度前进后配额放宽. */
    @Test
    public void testFilterPushableRoundBarrier() {
        ThetaPatterns p = new ThetaPatterns();
        Map<ICraftingPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(p.crush, 4L);
        totals.put(p.charge, 4L);
        totals.put(p.back, 4L);
        RoundQuotaScheduler.Quota quota = RoundQuotaScheduler.deriveQuota(totals, p.stone);
        assertThat(quota).isNotNull();

        // 初始:全部剩余 4 → round 0 → 全部允许
        Map<ICraftingPatternDetails, Long> remaining = new LinkedHashMap<>(totals);
        assertThat(RoundQuotaScheduler.filterPushable(quota, totals, remaining))
                .containsExactlyInAnyOrder(p.crush, p.charge, p.back);

        // crush 推满(剩余 0)→ round 仍 0(back/charge 未动)→ crush 超配额被闸
        remaining.put(p.crush, 0L);
        assertThat(RoundQuotaScheduler.filterPushable(quota, totals, remaining))
                .containsExactlyInAnyOrder(p.charge, p.back);

        // back 也推 1 轮(剩余 3)→ round 仍 0(charge 未动)→ back 配额用尽
        remaining.put(p.back, 3L);
        assertThat(RoundQuotaScheduler.filterPushable(quota, totals, remaining))
                .containsExactly(p.charge);

        // charge 推 1 轮 → 最慢进度 round=1 → 配额放宽到 2 轮
        remaining.put(p.charge, 3L);
        assertThat(RoundQuotaScheduler.filterPushable(quota, totals, remaining))
                .containsExactlyInAnyOrder(p.charge, p.back);
    }

    /** T5:闭包外 pattern 不受配额限制. */
    @Test
    public void testExternalPatternNeverThrottled() {
        ThetaPatterns p = new ThetaPatterns();
        Map<ICraftingPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(p.crush, 4L);
        totals.put(p.charge, 4L);
        totals.put(p.back, 4L);
        totals.put(p.external, 7L);
        RoundQuotaScheduler.Quota quota = RoundQuotaScheduler.deriveQuota(totals, p.stone);
        assertThat(quota).isNotNull();

        Map<ICraftingPatternDetails, Long> remaining = new LinkedHashMap<>(totals);
        remaining.put(p.crush, 0L); // 先行推满,触发闸门
        List<ICraftingPatternDetails> allowed = RoundQuotaScheduler.filterPushable(quota, totals, remaining);
        assertThat(allowed).contains(p.external);
        assertThat(allowed).doesNotContain(p.crush);
    }
}
