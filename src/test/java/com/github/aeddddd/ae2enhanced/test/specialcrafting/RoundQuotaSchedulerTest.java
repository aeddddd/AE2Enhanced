package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.RoundQuotaScheduler;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * 超轮配额调度器纯函数测试:闭包推导、GCD 轮次恢复、配额过滤.
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class RoundQuotaSchedulerTest {

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }

    private record ThetaPatterns(IPatternDetails crush, IPatternDetails charge, IPatternDetails back,
            IPatternDetails external, GenericStack stone) {
    }

    /** θ 结构:A→B、A→C、B+C→4A + 无关外部 pattern(dirt→stick). */
    private ThetaPatterns theta() {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var dirt = item(Items.DIRT);
        var stick = item(Items.STICK);
        var crush = new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build();
        var charge = new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build();
        var back = new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build();
        var external = new ProcessingPatternBuilder(stick).addPreciseInput(1, dirt).build();
        return new ThetaPatterns(crush, charge, back, external, stone);
    }

    /** T1:闭包推导——外部 pattern 豁免,GCD 恢复超轮比. */
    @Test
    public void testDeriveQuotaThetaClosureAndGcd() {
        var p = theta();
        Map<IPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(p.crush(), 4L);
        totals.put(p.charge(), 4L);
        totals.put(p.back(), 4L);
        totals.put(p.external(), 7L); // 外部子合成:不触及闭包键

        var quota = RoundQuotaScheduler.deriveQuota(totals, p.stone().what());
        assertThat(quota).isNotNull();
        assertThat(quota.perRound()).containsOnlyKeys(p.crush(), p.charge(), p.back());
        assertThat(quota.perRound().values()).containsExactlyInAnyOrder(1L, 1L, 1L);
    }

    /** T2:非自消耗 job(普通链)→ 不调度. */
    @Test
    public void testDeriveQuotaRejectsNormalPlan() {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var dirt = item(Items.DIRT);
        var p0 = new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build();
        var p1 = new ProcessingPatternBuilder(dirt).addPreciseInput(1, cobble).build();

        var quota = RoundQuotaScheduler.deriveQuota(Map.of(p0, 1L, p1, 1L), dirt.what());
        assertThat(quota).isNull();
    }

    /** T6(1.1.0):深层环——最终产出不在闭包内(DAG 边界计划),仍应限推. */
    @Test
    public void testDeriveQuotaDeepCycleWithoutFinalOutput() {
        var p = theta();
        var gravel = item(Items.GRAVEL);
        var pRoot = new ProcessingPatternBuilder(gravel).addPreciseInput(1, p.stone()).build();
        Map<IPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(pRoot, 8L);
        totals.put(p.crush(), 4L);
        totals.put(p.charge(), 4L);
        totals.put(p.back(), 4L);

        // finalOutput = gravel(根),不在环闭包内——1.1.0 前返回 null,深层环失去限推
        var quota = RoundQuotaScheduler.deriveQuota(totals, gravel.what());
        assertThat(quota).isNotNull();
        // 根 pattern 消耗环键(多消费者之一)→ 纳入闭包限推是安全的;GCD=4 → t={2,1,1,1}
        assertThat(quota.perRound()).containsOnlyKeys(pRoot, p.crush(), p.charge(), p.back());
        assertThat(quota.perRound().get(pRoot)).isEqualTo(2L);
        assertThat(quota.perRound().get(p.back())).isEqualTo(1L);
    }

    /** T7(1.1.0):线性副产物复用(产出也被消耗但不成环)→ 不调度,防误伤死锁. */
    @Test
    public void testDeriveQuotaRejectsLinearByproductReuse() {
        var a = item(Items.STONE);
        var b = item(Items.COBBLESTONE);
        var x = item(Items.SAND);
        var c = item(Items.DIRT);
        // 1A → 2B + 1X(副产物 X);1X → 1C:X 既产出又消耗,但不成环
        var p0 = new ProcessingPatternBuilder(mult(b, 2), x).addPreciseInput(1, a).build();
        var p1 = new ProcessingPatternBuilder(c).addPreciseInput(1, x).build();

        var quota = RoundQuotaScheduler.deriveQuota(Map.of(p0, 4L, p1, 4L), c.what());
        assertThat(quota).isNull();
    }

    /** T3:赛特斯尺度 GCD 恢复:totals {512,8,512} → 超轮比 {64,1,64}. */
    @Test
    public void testGcdRecoversRounds() {
        var p = theta();
        Map<IPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(p.crush(), 512L);
        totals.put(p.back(), 8L);
        totals.put(p.charge(), 512L);

        var quota = RoundQuotaScheduler.deriveQuota(totals, p.stone().what());
        assertThat(quota).isNotNull();
        assertThat(quota.perRound().get(p.crush())).isEqualTo(64L);
        assertThat(quota.perRound().get(p.back())).isEqualTo(1L);
        assertThat(quota.perRound().get(p.charge())).isEqualTo(64L);
    }

    /** T4:配额推进——先行推满的 pattern 被闸,其余放行;最慢进度前进后配额放宽. */
    @Test
    public void testFilterPushableRoundBarrier() {
        var p = theta();
        Map<IPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(p.crush(), 4L);
        totals.put(p.charge(), 4L);
        totals.put(p.back(), 4L);
        var quota = RoundQuotaScheduler.deriveQuota(totals, p.stone().what());
        assertThat(quota).isNotNull();

        // 初始:全部剩余 4 → round 0 → 全部允许
        var remaining = new LinkedHashMap<>(totals);
        var allowed = RoundQuotaScheduler.filterPushable(quota, totals, remaining);
        assertThat(allowed).containsExactlyInAnyOrder(p.crush(), p.charge(), p.back());

        // crush 推满(剩余 0)→ round 仍 0(back/charge 未动)→ crush 超配额被闸
        remaining.put(p.crush(), 0L);
        allowed = RoundQuotaScheduler.filterPushable(quota, totals, remaining);
        assertThat(allowed).containsExactlyInAnyOrder(p.charge(), p.back());

        // back 也推 1 轮(剩余 3)→ round 仍 0(charge 未动)→ back 配额用尽
        remaining.put(p.back(), 3L);
        allowed = RoundQuotaScheduler.filterPushable(quota, totals, remaining);
        assertThat(allowed).containsExactly(p.charge());

        // charge 推 1 轮 → 最慢进度 round=1 → 配额放宽到 2 轮
        remaining.put(p.charge(), 3L);
        allowed = RoundQuotaScheduler.filterPushable(quota, totals, remaining);
        assertThat(allowed).containsExactlyInAnyOrder(p.charge(), p.back());
    }

    /** T5:闭包外 pattern 不受配额限制. */
    @Test
    public void testExternalPatternNeverThrottled() {
        var p = theta();
        Map<IPatternDetails, Long> totals = new LinkedHashMap<>();
        totals.put(p.crush(), 4L);
        totals.put(p.charge(), 4L);
        totals.put(p.back(), 4L);
        totals.put(p.external(), 7L);
        var quota = RoundQuotaScheduler.deriveQuota(totals, p.stone().what());
        assertThat(quota).isNotNull();

        var remaining = new LinkedHashMap<>(totals);
        remaining.put(p.crush(), 0L); // 先行推满,触发闸门
        var allowed = RoundQuotaScheduler.filterPushable(quota, totals, remaining);
        assertThat(allowed).contains(p.external());
        assertThat(allowed).doesNotContain(p.crush());
    }
}
