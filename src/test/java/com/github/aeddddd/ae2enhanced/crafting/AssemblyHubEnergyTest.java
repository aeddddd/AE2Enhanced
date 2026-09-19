package com.github.aeddddd.ae2enhanced.crafting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssemblyHubEnergy} 测试。
 *
 * <p>覆盖：能量优化模块的每级减半与满级归零、单 tick 额定上限（2.1G）截断、
 * 欠电时的按比例降载、以及 0 份/0 单价/0 额度的边界。</p>
 */
public class AssemblyHubEnergyTest {

    private static final long BASE = 16384L;

    // ------------------------------------------------------------------
    // 单价：每级减半，满级归零
    // ------------------------------------------------------------------

    /** 未安装模块时为配置基础单价。 */
    @Test
    public void testBaseCostWithoutModules() {
        assertThat(AssemblyHubEnergy.perCraftCost(BASE, 0)).isEqualTo(BASE);
    }

    /** 每级减半：1~4 张分别为 1/2、1/4、1/8、1/16。 */
    @Test
    public void testCostHalvesPerModule() {
        assertThat(AssemblyHubEnergy.perCraftCost(BASE, 1)).isEqualTo(BASE / 2);
        assertThat(AssemblyHubEnergy.perCraftCost(BASE, 2)).isEqualTo(BASE / 4);
        assertThat(AssemblyHubEnergy.perCraftCost(BASE, 3)).isEqualTo(BASE / 8);
        assertThat(AssemblyHubEnergy.perCraftCost(BASE, 4)).isEqualTo(BASE / 16);
    }

    /** 满级（5 张）完全不耗能，超过满级同样为 0。 */
    @Test
    public void testFullModulesAreFree() {
        assertThat(AssemblyHubEnergy.perCraftCost(BASE, AssemblyHubEnergy.FREE_MODULE_COUNT)).isZero();
        assertThat(AssemblyHubEnergy.perCraftCost(BASE, 99)).isZero();
    }

    /** 减半不产生 0 单价（除满级外），下限 1 FE；基础单价为 0 时恒为 0。 */
    @Test
    public void testCostFloorAndZeroBase() {
        assertThat(AssemblyHubEnergy.perCraftCost(1L, 1)).isEqualTo(1L);
        assertThat(AssemblyHubEnergy.perCraftCost(3L, 1)).isEqualTo(1L);
        assertThat(AssemblyHubEnergy.perCraftCost(0L, 0)).isZero();
        assertThat(AssemblyHubEnergy.perCraftCost(-5L, 0)).isZero();
    }

    // ------------------------------------------------------------------
    // 计费需求：单 tick 额定上限
    // ------------------------------------------------------------------

    /** 未触及上限时需求 = 份数 × 单价。 */
    @Test
    public void testDemandIsOpsTimesPrice() {
        assertThat(AssemblyHubEnergy.demand(64L, BASE, AssemblyHubEnergy.RATED_CAP_PER_TICK))
                .isEqualTo(64L * BASE);
    }

    /** 超过本 tick 剩余额度时按额度截断（额定最大功耗语义）。 */
    @Test
    public void testDemandCappedByTickRoom() {
        long room = 1000L;
        assertThat(AssemblyHubEnergy.demand(64L, BASE, room)).isEqualTo(room);
    }

    /** 免费（单价 0）、无份数、额度耗尽时均不计费。 */
    @Test
    public void testDemandZeroCases() {
        assertThat(AssemblyHubEnergy.demand(10L, 0L, 1000L)).isZero();
        assertThat(AssemblyHubEnergy.demand(0L, BASE, 1000L)).isZero();
        assertThat(AssemblyHubEnergy.demand(10L, BASE, 0L)).isZero();
        assertThat(AssemblyHubEnergy.demand(10L, BASE, -1L)).isZero();
    }

    /** 乘法溢出时饱和到 Long.MAX_VALUE，再由额度截断。 */
    @Test
    public void testDemandSaturatesOnOverflow() {
        assertThat(AssemblyHubEnergy.demand(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
                .isEqualTo(Long.MAX_VALUE);
        assertThat(AssemblyHubEnergy.demand(Long.MAX_VALUE, Long.MAX_VALUE, 1000L)).isEqualTo(1000L);
    }

    // ------------------------------------------------------------------
    // 欠电降载
    // ------------------------------------------------------------------

    /** 足额支付（含额度截断）时全量放行。 */
    @Test
    public void testGrantedOpsWhenFullyPaid() {
        long demand = AssemblyHubEnergy.demand(64L, BASE, AssemblyHubEnergy.RATED_CAP_PER_TICK);
        assertThat(AssemblyHubEnergy.grantedOps(64L, demand, demand)).isEqualTo(64L);
        // 需求被上限截断时，付满截断额即视为放行（超出上限部分不计费）
        assertThat(AssemblyHubEnergy.grantedOps(64L, 1000L, 1000L)).isEqualTo(64L);
    }

    /** 部分支付按比例降载。 */
    @Test
    public void testGrantedOpsScalesWithPayment() {
        long demand = 64L * BASE;
        assertThat(AssemblyHubEnergy.grantedOps(64L, demand, demand / 2)).isEqualTo(32L);
        assertThat(AssemblyHubEnergy.grantedOps(64L, demand, 1L)).isZero();
    }

    /** 无电时不放行任何份数；免费（需求 0）时全量放行。 */
    @Test
    public void testGrantedOpsEdgeCases() {
        assertThat(AssemblyHubEnergy.grantedOps(64L, 100L, 0L)).isZero();
        assertThat(AssemblyHubEnergy.grantedOps(64L, 0L, 0L)).isEqualTo(64L);
        assertThat(AssemblyHubEnergy.grantedOps(0L, 100L, 100L)).isZero();
    }

    /** 降载结果恒不超过请求份数。 */
    @Test
    public void testGrantedOpsNeverExceedsRequest() {
        assertThat(AssemblyHubEnergy.grantedOps(10L, 1000L, 999L)).isEqualTo(9L);
        assertThat(AssemblyHubEnergy.grantedOps(Long.MAX_VALUE, 1000L, 999L)).isLessThanOrEqualTo(Long.MAX_VALUE);
    }
}
