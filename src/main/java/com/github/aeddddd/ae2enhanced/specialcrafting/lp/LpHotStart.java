package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * 对偶单纯形热启动链状态(P-A)。
 *
 * <p>一条链 = 同一个"基所在模型"上的一系列求解(自举轮之间只收紧执行数上界;
 * 同轮阶段之间还叠加禁行右端变化)。数学依据: 收紧上界与改动右端都<b>不改变</b>
 * 约化成本(rc 只依赖 cost 与 B), 因此旧解保持<b>对偶可行</b>, B 的 LU/eta/缩放
 * 全部可复用, 越界由对偶单纯形主循环修。可复用的前提是后续模型的系数矩阵与旧
 * 模型<b>逐位相同</b>(指纹校验; 不满足即冷启动)。</p>
 *
 * <p>本状态持有 {@link Basis} 实例的所有权: 被 {@code DualSimplex.solveHot} 消费后
 * 即失效, 调用方必须用返回的 {@code next} 替换槽位(见 {@link #Chain})。
 * 对外只暴露 {@link Chain}(编排层按整单持有一条链并向下传参)。</p>
 */
public final class LpHotStart {

    /** 热启动档位. */
    public enum Mode {
        /** 完全不走热启动(等价性对拍基线). */
        OFF,
        /** 走热启动: 起步分类 + pivot 预算护栏(超预算回退冷启动); 默认. */
        VERIFY,
        /** 走热启动: 起步分类保留, 取消 pivot 预算护栏(不做超限回退). */
        ON
    }

    final int rows;
    final int cols;
    /** 未缩放系数矩阵的结构指纹(与 {@link DualSimplex} 的 {@code fingerprint} 同口径). */
    final long aHash;
    /** 产生本状态时的 Ruiz 缩放方案(逐轮因子, 可逐位重放). */
    final DualSimplex.ScalingPlan plan;
    /** 基列(位置语义: 第 p 位是基变量的列号). */
    final int[] basisCol;
    /** 非基变量的界状态(0=基, 1=下界, 2=上界). */
    final byte[] state;
    /** 变量取值(基变量与 {basisCol} 对齐, 非基变量钉在界上). */
    final double[] x;
    /** 因子化基(所有权已转移, 不得被两个状态共享). */
    final Basis basis;
    /** 产生本状态的求解迭代数(热启动 pivot 预算的参照). */
    final int iterations;

    LpHotStart(int rows, int cols, long aHash, DualSimplex.ScalingPlan plan, int[] basisCol,
            byte[] state, double[] x, Basis basis, int iterations) {
        this.rows = rows;
        this.cols = cols;
        this.aHash = aHash;
        this.plan = plan;
        this.basisCol = basisCol;
        this.state = state;
        this.x = x;
        this.basis = basis;
        this.iterations = iterations;
    }

    /**
     * 链槽位集合: 按 (rows, cols) 分槽, 每条链内互不干扰(阶段①②③的维度天然不同;
     * 去内化重建、ExactSimplex 跨阈值都会落到不同槽或空槽 ⇒ 自然冷启动)。
     * 槽内再经 {@link #aHash} 指纹校验, 矩阵不同即拒用。
     */
    public static final class Chain {
        /** 槽位上限(防御: 单单元最多几个阶段模型; 超限整体丢弃, 退化为冷启动). */
        private static final int MAX_SLOTS = 8;

        private final Mode mode;
        private final Map<Long, LpHotStart> slots = new LinkedHashMap<>();
        /** 成功热启动(分类通过、真正复用旧基)的次数; 诊断与测试用。 */
        private int hotReuses;

        private Chain(Mode mode) {
            this.mode = mode;
        }

        /** 入口一次性解析系统属性 {@code ae2e.hotStart = off|verify|on}(默认 verify)。 */
        public static Chain fromSystemProperties() {
            return new Chain(parseMode(System.getProperty("ae2e.hotStart", "verify")));
        }

        /** 关闭热启动的链(冷启动基线的显式入口)。 */
        public static Chain disabled() {
            return new Chain(Mode.OFF);
        }

        /** 指定档位的链(测试/对拍用)。 */
        public static Chain of(Mode mode) {
            return new Chain(mode);
        }

        static Mode parseMode(String raw) {
            switch (raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)) {
                case "off":
                    return Mode.OFF;
                case "on":
                    return Mode.ON;
                default:
                    return Mode.VERIFY;
            }
        }

        /** 热启动是否启用(off 档下始终走冷启动)。 */
        public boolean enabled() {
            return this.mode != Mode.OFF;
        }

        /** 当前档位(诊断用)。 */
        public Mode mode() {
            return this.mode;
        }

        /** pivot 预算护栏是否生效(仅 verify 档; 0 = 不限)。 */
        int pivotBudget(int priorIterations) {
            if (this.mode != Mode.VERIFY) {
                return 0;
            }
            return Math.max(256, 4 * priorIterations);
        }

        /** 取出该维度的旧状态(不占用所有权; 指纹不符由调用方拒用)。 */
        @Nullable
        LpHotStart peek(int rows, int cols) {
            return this.slots.get(key(rows, cols));
        }

        /** 登记一次"真正走了热启动"的求解(分类通过)。 */
        public void noteHotReuse() {
            this.hotReuses++;
        }

        /** 本题/本单累计热启动命中次数。 */
        public int hotReuses() {
            return this.hotReuses;
        }

        /** 用新状态替换槽位(旧状态的所有权已随本次求解转移, 不得再用)。 */
        void put(LpHotStart next) {
            if (next == null) {
                return;
            }
            if (this.slots.size() >= MAX_SLOTS && !this.slots.containsKey(key(next.rows, next.cols))) {
                this.slots.clear();
            }
            this.slots.put(key(next.rows, next.cols), next);
        }

        private static long key(int rows, int cols) {
            return ((long) rows << 32) | (cols & 0xFFFFFFFFL);
        }
    }
}
