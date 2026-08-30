package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;

/**
 * 蛛网分量 LP 求解编排（方案 L §4.3 字典序两阶段）.
 * <ol>
 * <li><b>阶段①</b>:min Σ d_k（缺料最小化 = LP 意义精确最优）;</li>
 * <li><b>阶段②</b>:追加禁行 Σ d_k ≤ obj①+噪声容差（松弛化等式）,以
 * c_p = 1 + {@value #EPSILON_RANK}·rank(p) 最小化总执行次数——剔除冗余放大,
 * ε 体现样板登记序/优先级（D2）.</li>
 * </ol>
 * 注意与单纯形内部 Phase 1/2（人工变量求可行基）不同层。
 * <p>模型按构造恒可行（赤字变量吸收任意短缺）,INFEASIBLE 视为数值异常;
 * 任何非 OPTIMAL 结果由调用方按 D4 对蛛网分量整体"库存直通"降级.</p>
 */
public final class SccLpSolve {

    /** 阶段② 样板优先级权重(D2:仅 ε 体现,不改变主目标量级). */
    private static final double EPSILON_RANK = 1e-4;
    /** 结果提取零阈(低于此值的执行数/赤字视为 0,物化层 M5 再整数化). */
    private static final double EXTRACT_TOL = 1e-6;

    private SccLpSolve() {
    }

    /** 单条执行记录(变体变量独立成行,M5 物化时按 pattern+variantInputs 还原输入). */
    public static final class Execution {
        public final ICraftingPatternDetails pattern;
        /** 变体输入覆盖(空 = 编码输入). */
        public final Map<IAEItemStack, Long> variantInputs;
        /** LP 执行次数(实数,整数化在 M5 对账层). */
        public final double count;

        /** 供冷凝驱动器截断路径按可行水位重建执行记录. */
        public Execution(ICraftingPatternDetails pattern, Map<IAEItemStack, Long> variantInputs, double count) {
            this.pattern = pattern;
            this.variantInputs = variantInputs;
            this.count = count;
        }
    }

    /** 分量求解结果. */
    public static final class SccSolution {
        /** 求解状态(OPTIMAL 或失败状态;失败时下列各表为空). */
        public final LpResult.Status status;
        /** 失败原因(诊断;OPTIMAL 为 null). */
        @Nullable
        public final String reason;
        /** 执行记录(x > 阈值的结构变量). */
        public final List<Execution> executions;
        /** 赤字(canon 键 → 缺料量;阶段①最优意义下的最小缺料). */
        public final Map<IAEItemStack, Double> deficits;
        /** 环外输入折算(canon 键 → 总量;冷凝序向后传播给上游分量). */
        public final Map<IAEItemStack, Double> externalDemands;
        /** 阶段①最优赤字和(诊断/校验). */
        public final double deficitObjective;
        /** 两阶段总迭代数(诊断埋点). */
        public final int iterations;

        private SccSolution(LpResult.Status status, String reason, List<Execution> executions,
                Map<IAEItemStack, Double> deficits, Map<IAEItemStack, Double> externalDemands,
                double deficitObjective, int iterations) {
            this.status = status;
            this.reason = reason;
            this.executions = executions;
            this.deficits = deficits;
            this.externalDemands = externalDemands;
            this.deficitObjective = deficitObjective;
            this.iterations = iterations;
        }

        static SccSolution failure(LpResult result, int iterations) {
            return new SccSolution(result.status, result.reason, Collections.emptyList(),
                    Collections.emptyMap(), Collections.emptyMap(), Double.NaN, iterations);
        }
    }

    /**
     * 求解单个 SCC 分量(字典序两阶段).
     *
     * @param upperCaps 样板执行数额外上界(M4 禁约束;空表 = 首轮无约束求解)
     * @param committed 已在下游分量承诺的跨分量样板及执行数(见
     *                  {@link SccLpModelBuilder#build} 同名参数)
     */
    public static SccSolution solve(ICraftingGrid cc, NetworkPatternIndex index, List<IAEItemStack> keys,
            Map<IAEItemStack, Long> stock, Map<IAEItemStack, Double> demands,
            Map<ICraftingPatternDetails, Double> upperCaps,
            Map<ICraftingPatternDetails, Double> committed) {
        SccLpModelBuilder.Built built = SccLpModelBuilder.build(cc, index, keys, stock, demands, upperCaps,
                committed);

        // 阶段①:赤字和最小化
        LpResult phase1 = RevisedSimplex.solve(built.lp);
        if (phase1.status != LpResult.Status.OPTIMAL) {
            return SccSolution.failure(phase1, phase1.iterations);
        }
        double obj1 = phase1.objective;

        // 阶段②:禁行 Σ d_k ≤ obj1(容差放宽) + 松弛列,目标 = 总执行次数 + ε·优先级
        int keyRows = built.keys.size();
        int oldRows = built.lp.a.rows;
        List<Map<Integer, Double>> columns = new ArrayList<>(built.columns.size() + 1);
        for (Map<Integer, Double> col : built.columns) {
            columns.add(new TreeMap<>(col));
        }
        for (int i = 0; i < keyRows; i++) {
            columns.get(built.deficitCol[i]).put(oldRows, 1.0);
        }
        Map<Integer, Double> forbidSlackCol = new TreeMap<>();
        forbidSlackCol.put(oldRows, 1.0);
        columns.add(forbidSlackCol);

        int n = columns.size();
        double[] b = java.util.Arrays.copyOf(built.lp.b, oldRows + 1);
        // 禁行容差 = 阶段①目标值的数值噪声上界;阶段②会以"假赤字"合法利用该松弛
        // (赤字零成本换更少执行数),故提取阈值必须高于此泄漏上界(见下方 extractTol)
        double forbidSlack = obj1 * 1e-9 + 1e-7;
        b[oldRows] = obj1 + forbidSlack;
        double[] cost = new double[n];
        for (int j = 0; j < built.vars.size(); j++) {
            cost[j] = 1 + EPSILON_RANK * built.vars.get(j).rank;
        }
        double[] lower = java.util.Arrays.copyOf(built.lp.lower, n);
        double[] upper = java.util.Arrays.copyOf(built.lp.upper, n);
        upper[n - 1] = LpModel.INF;
        LpModel phase2Model = new LpModel(SparseMatrix.fromColumns(oldRows + 1, columns), b, cost, lower, upper);

        LpResult phase2 = RevisedSimplex.solve(phase2Model);
        if (phase2.status != LpResult.Status.OPTIMAL) {
            return SccSolution.failure(phase2, phase1.iterations + phase2.iterations);
        }

        // 结果回填:执行记录 / 赤字 / 环外折算
        List<Execution> executions = new ArrayList<>();
        Map<IAEItemStack, Double> externalDemands = new LinkedHashMap<>();
        for (int j = 0; j < built.vars.size(); j++) {
            double count = phase2.x[j];
            if (count <= EXTRACT_TOL) {
                continue;
            }
            SccLpModelBuilder.VarInfo info = built.vars.get(j);
            executions.add(new Execution(info.pattern, info.variantInputs, count));
            for (Map.Entry<IAEItemStack, Double> ext : info.externalInputs.entrySet()) {
                externalDemands.merge(ext.getKey(), ext.getValue() * count, Double::sum);
            }
        }
        Map<IAEItemStack, Double> deficits = new LinkedHashMap<>();
        double deficitTol = Math.max(EXTRACT_TOL, 4 * forbidSlack);
        for (int i = 0; i < keyRows; i++) {
            double deficit = phase2.x[built.deficitCol[i]];
            if (deficit > deficitTol) {
                deficits.put(built.keys.get(i), deficit);
            }
        }
        return new SccSolution(LpResult.Status.OPTIMAL, null, executions, deficits, externalDemands,
                obj1, phase1.iterations + phase2.iterations);
    }
}
