package com.github.aeddddd.ae2enhanced.crafting;

/**
 * 装配枢纽能耗模型（纯逻辑，可单测）.
 *
 * <h3>计费口径</h3>
 * <ul>
 *   <li><b>单价</b>：每完成一份合成（一次批量结算的最小单位）计费 {@code energyPerCraft} FE；
 *       能量优化模块每级减半，满级（{@link #FREE_MODULE_COUNT} 张）归零 = 完全不耗能.</li>
 *   <li><b>额定上限</b>：单 tick 计费额不超过 {@link #RATED_CAP_PER_TICK}（2.1G FE = int 上限，
 *       与奇点腔室的能量上限约定一致）。超过上限的合成不再额外计费——满级并行卡不会
 *       无限吃电（机器的额定最大功耗语义）.</li>
 *   <li><b>欠电降载</b>：网络实付不足需求时，按实付比例折算可结算份数
 *       （{@link #grantedOps}），低于 1 份即本 tick 放弃该样板；不出现「付了电却一份不做」.</li>
 * </ul>
 *
 * <p>单位为 FE：支付路径与奇点腔室一致（ME 网络能源通道存量优先，不足走 AE 能源网按 1 AE = 2 FE）。</p>
 */
public final class AssemblyHubEnergy {

    /** 满级（不耗能）所需能量优化模块数量. */
    public static final int FREE_MODULE_COUNT = 5;

    /** 单 tick 额定功耗上限：2.1G FE（int 上限）——对齐奇点腔室的能量上限约定. */
    public static final long RATED_CAP_PER_TICK = Integer.MAX_VALUE;

    private AssemblyHubEnergy() {
    }

    /**
     * 单份合成能耗：每级减半（至少 1 FE），满级返回 0（不耗能）.
     *
     * @param baseCost 基础单价（配置项）
     * @param modules  已安装的能量优化模块数量
     */
    public static long perCraftCost(long baseCost, int modules) {
        if (baseCost <= 0L) {
            return 0L;
        }
        if (modules >= FREE_MODULE_COUNT) {
            return 0L;
        }
        long cost = baseCost;
        for (int i = 0; i < modules && cost > 1L; i++) {
            cost = Math.max(cost / 2L, 1L);
        }
        return cost;
    }

    /**
     * 本次计费需求：min(份数 × 单价, 本 tick 剩余额度).
     *
     * @return 0 表示不计费（单价为 0 / 无份数 / 本 tick 已达额定上限）
     */
    public static long demand(long ops, long perCraft, long tickRoom) {
        if (ops <= 0L || perCraft <= 0L || tickRoom <= 0L) {
            return 0L;
        }
        return Math.min(saturatedMul(ops, perCraft), tickRoom);
    }

    /**
     * 按实付比例折算可结算份数（欠电降载）.
     *
     * @param paid 网络实际可支付/已支付的 FE
     */
    public static long grantedOps(long ops, long demand, long paid) {
        if (ops <= 0L) {
            return 0L;
        }
        if (demand <= 0L || paid >= demand) {
            return ops;
        }
        if (paid <= 0L) {
            return 0L;
        }
        // 大数下用 double 近似比例即可：这是降载启发式,结果夹在 [0, ops]
        long granted = (long) ((double) ops * (double) paid / (double) demand);
        if (granted <= 0L) {
            return 0L;
        }
        return Math.min(granted, ops);
    }

    private static long saturatedMul(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }
}
