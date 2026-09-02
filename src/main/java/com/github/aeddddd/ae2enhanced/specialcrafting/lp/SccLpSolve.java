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

import com.github.aeddddd.ae2enhanced.specialcrafting.FlowReconciler;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;

/**
 * 蛛网分量 LP 求解编排（方案 L §4.3 字典序三阶段）.
 * <ol>
 * <li><b>阶段①</b>:min Σ d_k（缺料最小化 = LP 意义精确最优）;</li>
 * <li><b>阶段②</b>:禁行 Σ d_k ≤ obj①+噪声容差,min Σ d_需求行（交付最大化——
 * 否则阶段③会把赤字"搬迁"到需求行换零执行:内化原料行使需求行赤字合法化,
 * 整单不交（d_root=需求、零样板执行）与尽力合成同为阶段①最优);</li>
 * <li><b>阶段③</b>:再禁行 Σ d_需求行 ≤ obj②+容差,以
 * c_p = 1 + {@value #EPSILON_RANK}·rank(p) 最小化总执行次数——剔除冗余放大,
 * ε 体现样板登记序/优先级（D2）,非基准变体再加 {@value #EPSILON_VARIANT}
 * （同等可行编码输入优先）.</li>
 * </ol>
 * 缺料两趟回退:阶段① obj>0(交付不可达)且模型含内化原料行时,去内化重解——
 * 原生缺料场景是"分支序贪心幻影生产"显示语义,内化行的库存高效分配反而
 * 偏离原生口径;可行(obj①≈0)时内化行的容量计价与原生逐分支容量分配一致.
 * 求解后执行<b>变体回移</b>:基准变量的替代槽编码键为环外可合成键时,其需求
 * 在上游单元展开为合成执行——逐单元 LP 看不到该成本,阶段③同执行数并列时
 * ε 偏向基准变量,违反"候选库存优先于合成编码物"的原生替代语义;故把候选行
 * 盈余允许的执行数从基准变量回移给变体变量.
 * 注意与单纯形内部 Phase 1/2（人工变量求可行基）不同层。
 * <p>模型按构造恒可行（赤字变量吸收任意短缺）,INFEASIBLE 视为数值异常;
 * 任何非 OPTIMAL 结果由调用方按 D4 对蛛网分量整体"库存直通"降级.</p>
 */
public final class SccLpSolve {

    /** 阶段② 样板优先级权重(D2:仅 ε 体现,不改变主目标量级). */
    private static final double EPSILON_RANK = 1e-4;
    /** 阶段② 非基准变体的微小附加成本(≪ 优先级 ε):同等可行时编码输入优先,
     * 候选仅在编码物不足/更省执行数时启用(原生"先编码物、后候选"语义). */
    private static final double EPSILON_VARIANT = 1e-6;
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
        /** 自返还种子需求(canon 键 → 一次性种子量;催化剂型容器净消耗为零,
         * 但首次点火需要 per-craft 输入量的实物种子——按活跃样板扁平记账,
         * 与环外折算同路传播给上游分量). */
        public final Map<IAEItemStack, Double> seedDemands;
        /** 阶段①最优赤字和(诊断/校验). */
        public final double deficitObjective;
        /** 两阶段总迭代数(诊断埋点). */
        public final int iterations;

        private SccSolution(LpResult.Status status, String reason, List<Execution> executions,
                Map<IAEItemStack, Double> deficits, Map<IAEItemStack, Double> externalDemands,
                Map<IAEItemStack, Double> seedDemands, double deficitObjective, int iterations) {
            this.status = status;
            this.reason = reason;
            this.executions = executions;
            this.deficits = deficits;
            this.externalDemands = externalDemands;
            this.seedDemands = seedDemands;
            this.deficitObjective = deficitObjective;
            this.iterations = iterations;
        }

        static SccSolution failure(LpResult result, int iterations) {
            return new SccSolution(result.status, result.reason, Collections.emptyList(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Double.NaN,
                    iterations);
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
                committed, true);

        // 阶段①:赤字和最小化
        LpResult phase1 = RevisedSimplex.solve(built.lp);
        if (phase1.status != LpResult.Status.OPTIMAL) {
            return SccSolution.failure(phase1, phase1.iterations);
        }
        double obj1 = phase1.objective;

        // 不可行两趟回退:原料库存计价(内化行)只在交付可达时等价原生的
        // 容量分配;缺料( obj①>0 )时原生退化为"分支序贪心幻影生产"显示语义
        // (首分支包揽全额、缺料记在其原料层)——去掉内化行重解,让阶段③
        // 秩贪心复刻该口径,而不是把赤字摊到各原料行换"库存高效"分配
        if (obj1 > 1e-6 && built.rawRows > 0) {
            built = SccLpModelBuilder.build(cc, index, keys, stock, demands, upperCaps, committed, false);
            phase1 = RevisedSimplex.solve(built.lp);
            if (phase1.status != LpResult.Status.OPTIMAL) {
                return SccSolution.failure(phase1, phase1.iterations);
            }
            obj1 = phase1.objective;
        }

        int keyRows = built.keys.size();
        int oldRows = built.lp.a.rows;
        // 需求行(本单元有实际交付义务的键:根请求 + 下游传播需求)
        List<Integer> demandRows = new ArrayList<>();
        for (int i = 0; i < keyRows; i++) {
            if (demands.getOrDefault(built.keys.get(i), 0.0) > 0) {
                demandRows.add(i);
            }
        }

        // 禁行容差 = 阶段目标值的数值噪声上界;后续阶段会以"假赤字"合法利用该松弛
        // (赤字零成本换更少执行数),故提取阈值必须高于此泄漏上界(见下方 extractTol)
        double slack1 = obj1 * 1e-9 + 1e-7;

        // 阶段②:禁行 Σd ≤ obj1+s1(松弛列),目标 = 需求行赤字和最小化(交付最大化)
        double obj2 = 0;
        int iterations = phase1.iterations;
        int rows2 = oldRows + 1;
        if (!demandRows.isEmpty()) {
            List<Map<Integer, Double>> columns2 = new ArrayList<>(built.columns.size() + 1);
            for (Map<Integer, Double> col : built.columns) {
                columns2.add(new TreeMap<>(col));
            }
            for (int i = 0; i < keyRows; i++) {
                columns2.get(built.deficitCol[i]).put(oldRows, 1.0);
            }
            Map<Integer, Double> slackCol2 = new TreeMap<>();
            slackCol2.put(oldRows, 1.0);
            columns2.add(slackCol2);
            int n2 = columns2.size();
            double[] b2 = java.util.Arrays.copyOf(built.lp.b, rows2);
            b2[oldRows] = obj1 + slack1;
            double[] cost2 = new double[n2];
            for (int row : demandRows) {
                cost2[built.deficitCol[row]] = 1;
            }
            double[] lower2 = java.util.Arrays.copyOf(built.lp.lower, n2);
            double[] upper2 = java.util.Arrays.copyOf(built.lp.upper, n2);
            upper2[n2 - 1] = LpModel.INF;
            LpResult phase2 = RevisedSimplex.solve(
                    new LpModel(SparseMatrix.fromColumns(rows2, columns2), b2, cost2, lower2, upper2));
            iterations += phase2.iterations;
            if (phase2.status != LpResult.Status.OPTIMAL) {
                return SccSolution.failure(phase2, iterations);
            }
            obj2 = phase2.objective;
        }
        double slack2 = obj2 * 1e-9 + 1e-7;

        // 阶段③:禁行 Σd ≤ obj1+s1、Σd_需求 ≤ obj2+s2(各配松弛列),
        // 目标 = 总执行次数 + ε·优先级(+ ε·非基准变体)
        List<Map<Integer, Double>> columns = new ArrayList<>(built.columns.size() + 2);
        for (Map<Integer, Double> col : built.columns) {
            columns.add(new TreeMap<>(col));
        }
        for (int i = 0; i < keyRows; i++) {
            columns.get(built.deficitCol[i]).put(oldRows, 1.0);
        }
        for (int row : demandRows) {
            columns.get(built.deficitCol[row]).merge(oldRows + 1, 1.0, Double::sum);
        }
        Map<Integer, Double> slackColA = new TreeMap<>();
        slackColA.put(oldRows, 1.0);
        columns.add(slackColA);
        Map<Integer, Double> slackColB = new TreeMap<>();
        slackColB.put(oldRows + 1, 1.0);
        columns.add(slackColB);

        int n = columns.size();
        double[] b = java.util.Arrays.copyOf(built.lp.b, oldRows + 2);
        b[oldRows] = obj1 + slack1;
        b[oldRows + 1] = obj2 + slack2;
        double[] cost = new double[n];
        for (int j = 0; j < built.vars.size(); j++) {
            cost[j] = 1 + EPSILON_RANK * built.vars.get(j).rank
                    + (built.vars.get(j).isVariant() ? EPSILON_VARIANT : 0);
        }
        double[] lower = java.util.Arrays.copyOf(built.lp.lower, n);
        double[] upper = java.util.Arrays.copyOf(built.lp.upper, n);
        upper[n - 2] = LpModel.INF;
        upper[n - 1] = LpModel.INF;
        LpModel phase3Model = new LpModel(SparseMatrix.fromColumns(oldRows + 2, columns), b, cost, lower, upper);

        LpResult phase3 = RevisedSimplex.solve(phase3Model);
        iterations += phase3.iterations;
        if (phase3.status != LpResult.Status.OPTIMAL) {
            return SccSolution.failure(phase3, iterations);
        }

        // 变体回移:候选行盈余内,把"替代槽编码键环外(可合成)"的基准变量执行数
        // 回移给变体变量(候选库存优先于上游合成编码物;逐单元 LP 看不到上游合成成本)
        double[] counts = new double[built.vars.size()];
        for (int j = 0; j < built.vars.size(); j++) {
            counts[j] = phase3.x[j];
        }
        shiftToCandidateVariants(cc, index, built, phase3.x, counts);

        // 结果回填:执行记录 / 赤字 / 环外折算
        List<Execution> executions = new ArrayList<>();
        Map<IAEItemStack, Double> externalDemands = new LinkedHashMap<>();
        for (int j = 0; j < built.vars.size(); j++) {
            double count = counts[j];
            SccLpModelBuilder.VarInfo info = built.vars.get(j);
            // 零计数执行记录同样保留:对账层整数化守恒修复可能因跨单元外部键
            // 失衡回补本单元生产者(如 H13 辅材子合成),需要执行记录身份在场;
            // 消费方(reconcile/物化/种子需求)均按 count>0 过滤,语义不变
            executions.add(new Execution(info.pattern, info.variantInputs, count));
            if (count <= EXTRACT_TOL) {
                continue;
            }
            for (Map.Entry<IAEItemStack, Double> ext : info.externalInputs.entrySet()) {
                externalDemands.merge(ext.getKey(), ext.getValue() * count, Double::sum);
            }
        }
        Map<IAEItemStack, Double> deficits = new LinkedHashMap<>();
        double deficitTol = Math.max(EXTRACT_TOL, 4 * (slack1 + slack2));
        for (int i = 0; i < keyRows; i++) {
            double deficit = phase3.x[built.deficitCol[i]];
            if (deficit > deficitTol) {
                deficits.put(built.keys.get(i), deficit);
            }
        }
        Map<IAEItemStack, Double> seedDemands = seedDemandsOf(executions,
                new java.util.HashSet<>(built.keys));
        return new SccSolution(LpResult.Status.OPTIMAL, null, executions, deficits, externalDemands,
                seedDemands, obj1, iterations);
    }

    /**
     * 自返还种子需求汇总(活跃样板去重):返还键同为输入(催化剂型)且非行键时,
     * 首次点火需 per-craft 输入量的实物种子——守恒净系数为零,不在行约束中表达,
     * 在此扁平记账(每活跃样板一份),由冷凝驱动器传播给上游分量;
     * 单元行键的自返还种子由库存/种子自举校验链兜底,不在此传播.
     */
    public static Map<IAEItemStack, Double> seedDemandsOf(List<Execution> executions,
            java.util.Set<IAEItemStack> rowKeys) {
        Map<IAEItemStack, Double> seed = new LinkedHashMap<>();
        java.util.Set<ICraftingPatternDetails> seen = Collections
                .newSetFromMap(new java.util.IdentityHashMap<>());
        for (Execution exec : executions) {
            if (exec.count <= EXTRACT_TOL || !seen.add(exec.pattern)) {
                continue;
            }
            for (Map.Entry<IAEItemStack, Long> ret : FlowReconciler.returnsPerCraft(exec.pattern).entrySet()) {
                if (rowKeys.contains(ret.getKey()) || !FlowReconciler.containsInput(exec.pattern, ret.getKey())) {
                    continue;
                }
                for (IAEItemStack input : exec.pattern.getCondensedInputs()) {
                    if (input != null && ret.getKey().equals(RecursiveCraftingHelper.canon(input))) {
                        seed.merge(ret.getKey(), (double) input.getStackSize(), Double::sum);
                        break;
                    }
                }
            }
        }
        return seed;
    }

    /**
     * 变体回移(候选库存优先于上游合成编码物).
     * <p>逐组(同样板的基准变量 + 变体变量)扫描:变体的负调整键(替代槽编码物)
     * 存在环外键(不在行号空间 = 可合成/传播键)时,把基准变量执行数在候选行
     * 盈余允许范围内回移给该变体;编码键已内化为行的场景由阶段③ ε 定价,
     * 不在此重复移动.回移保持耦合行 Σ变体 不变(仅在同组内转移).</p>
     */
    private static void shiftToCandidateVariants(appeng.api.networking.crafting.ICraftingGrid cc,
            NetworkPatternIndex index, SccLpModelBuilder.Built built, double[] x, double[] counts) {
        Map<IAEItemStack, Integer> rowOf = new LinkedHashMap<>();
        for (int i = 0; i < built.keys.size(); i++) {
            rowOf.put(built.keys.get(i), i);
        }
        // 候选行剩余盈余(初始 = 阶段③解的盈余变量值)
        Map<Integer, Double> spare = new LinkedHashMap<>();
        int j = 0;
        while (j < built.vars.size()) {
            // 同组变量:连续且同一样板(构建器按样板连续展开)
            ICraftingPatternDetails pattern = built.vars.get(j).pattern;
            int groupEnd = j + 1;
            while (groupEnd < built.vars.size() && built.vars.get(groupEnd).pattern == pattern) {
                groupEnd++;
            }
            int base = j; // 基准变量恒为组首(expandVariants 首元 = 编码输入)
            if (counts[base] > EXTRACT_TOL && groupEnd - j > 1) {
                for (int v = j + 1; v < groupEnd && counts[base] > EXTRACT_TOL; v++) {
                    Map<IAEItemStack, Long> override = built.vars.get(v).variantInputs;
                    boolean externalEncoded = false;
                    for (Map.Entry<IAEItemStack, Long> e : override.entrySet()) {
                        // 环外且非发射台(发射台编码物免费,回移只会浪费候选库存)
                        if (e.getValue() < 0 && !rowOf.containsKey(e.getKey())
                                && !index.canEmit(e.getKey())) {
                            externalEncoded = true;
                            break;
                        }
                    }
                    if (!externalEncoded) {
                        continue;
                    }
                    double cap = counts[base];
                    for (Map.Entry<IAEItemStack, Long> e : override.entrySet()) {
                        if (e.getValue() <= 0) {
                            continue;
                        }
                        Integer row = rowOf.get(e.getKey());
                        if (row == null) {
                            cap = 0;
                            break;
                        }
                        double avail = spare.computeIfAbsent(row,
                                r -> Math.max(0, x[built.surplusCol[r]]));
                        cap = Math.min(cap, avail / e.getValue());
                    }
                    if (cap <= EXTRACT_TOL) {
                        continue;
                    }
                    counts[base] -= cap;
                    counts[v] += cap;
                    for (Map.Entry<IAEItemStack, Long> e : override.entrySet()) {
                        if (e.getValue() > 0) {
                            Integer row = rowOf.get(e.getKey());
                            spare.merge(row, -cap * e.getValue(), Double::sum);
                        }
                    }
                }
            }
            j = groupEnd;
        }
    }
}
