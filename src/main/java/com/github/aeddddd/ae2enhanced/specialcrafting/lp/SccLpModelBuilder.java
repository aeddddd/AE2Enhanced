package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.FlowReconciler;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;

/**
 * SCC 分量 → LP 模型构建器（方案 L §4）.
 * <p>模型（逐 SCC 键 k 的流量守恒，赤字变量 d_k 吸收缺料、盈余变量 s_k 吸收剩余）:</p>
 * <pre>stock_k + Σ_p out_{p,k}·x_p + d_k ≥ demand_k + Σ_p in_{p,k}·x_p
 * ⇔ Σ_p (out−in)·x_p + d_k − s_k = demand_k − stock_k,  d_k,s_k ≥ 0</pre>
 * <ul>
 * <li><b>变量</b>:x_p = 样板执行次数（实数），d_k = 键赤字，s_k = 键盈余（零成本,
 * 表达"剩余留存网络"——等式化必需,否则剩余库存被错判不可行）;</li>
 * <li><b>矿词替代</b>:替代槽位展开为变体变量（D1:每槽候选封顶
 * {@value #MAX_VARIANTS_PER_SLOT}，超限仅保留编码输入；组合乘积封顶
 * {@value #MAX_VARIANTS_PER_PATTERN}，超限同样退化）;</li>
 * <li><b>上界</b>:U_p = ⌈min({@link #DMAX_CAP}, D_max·K)⌉。D_max = D_total·ρ_max^K
 * （K=分量键数,ρ_max = 分量内最大"输入/输出"损耗比）——逐键毛流量 ≤ 需求沿最长
 * 损耗链的逐跳放大,p 的执行数 ≤ 其输入键毛流量 ≤ D_max,K 为裕量（自增环执行数
 * 等于净增量本身,不得以 outMin 折算）。界值不进入约束矩阵,不影响条件数;</li>
 * <li><b>发射台键</b>（canEmitFor）按 D_max 计库存（零成本有界供给,与原生语义一致）;</li>
 * <li><b>环外输入</b>（输入键 ∉ SCC）不参与约束,按变量记录系数,求解后折算为
 * 对上游分量的需求（冷凝序向后传播,见 {@link SccLpSolve.SccSolution#externalDemands}）——
 * 但纯原料键（无生产者、非发射台）在两类场景内化为守恒行（库存计价、赤字入
 * 阶段①目标）:① 替代参与的键（否则阶段①对变体选择"失明"）;② 单元独占的
 * 原料键（否则阶段③对多分支选择"失明",稀缺容量无法封顶）。跨单元共享的
 * 原料键仍折算传播,在叶子单元统一结算,避免跨单元重复计库存;</li>
 * <li><b>多变体耦合</b>:同样板全部变体共享执行数上界(Σ x_variants ≤ U_p,松弛变量化等式).</li>
 * </ul>
 * 目标系数由 {@link SccLpSolve} 按字典序两阶段设置,本类产出的模型默认阶段①
 * （赤字和最小化）。
 */
public final class SccLpModelBuilder {

    /** D1:矿词替代槽位候选键上限(含编码输入;超限仅保留编码输入). */
    public static final int MAX_VARIANTS_PER_SLOT = 4;
    /** 同样板变体组合乘积上限(防御:多替代槽笛卡尔积爆炸时退化为仅编码输入). */
    public static final int MAX_VARIANTS_PER_PATTERN = 64;
    /** 需求闭包上界封顶(双精度安全,远低于 {@link LpModel#INF}). */
    public static final double DMAX_CAP = 1e18;

    /** 结构变量元数据(列 → 样板/变体/环外系数). */
    public static final class VarInfo {
        /** 所属样板(身份引用). */
        public final ICraftingPatternDetails pattern;
        /** 变体输入覆盖(canon 键 → 相对编码输入的净调整量;空 = 编码输入基准变量). */
        public final Map<IAEItemStack, Long> variantInputs;
        /** 执行数上界 U_p. */
        public final double upper;
        /** 环外输入系数(canon 键 → 单次执行消耗;仅非 SCC 键). */
        public final Map<IAEItemStack, Double> externalInputs;
        /** 阶段② ε 排序用登记秩(收集序 = 键序 × 优先级序). */
        public final int rank;
        /** 多变体耦合行号(无耦合 = -1). */
        public final int couplingRow;

        VarInfo(ICraftingPatternDetails pattern, Map<IAEItemStack, Long> variantInputs, double upper,
                Map<IAEItemStack, Double> externalInputs, int rank, int couplingRow) {
            this.pattern = pattern;
            this.variantInputs = variantInputs;
            this.upper = upper;
            this.externalInputs = externalInputs;
            this.rank = rank;
            this.couplingRow = couplingRow;
        }

        /** 是否矿词替代变体(非编码输入). */
        public boolean isVariant() {
            return !this.variantInputs.isEmpty();
        }
    }

    /** 构建产物:模型 + 列/行映射(求解结果回填用) + 可扩展列数据(阶段②加禁行需重建 CSC). */
    public static final class Built {
        public final LpModel lp;
        /** 结构列 j → 元数据(长度 = 结构列数;列号与列顺序一致). */
        public final List<VarInfo> vars;
        /** 行 i → canon 键(前 keys.size() 行为键守恒,其后为耦合行). */
        public final List<IAEItemStack> keys;
        /** 行 i → 赤字列号(赤字列恒为最末 keys.size() 列). */
        public final int[] deficitCol;
        /** 行 i → 盈余列号(零成本,剩余库存等式化). */
        public final int[] surplusCol;
        /** 需求闭包上界 D_max(诊断埋点). */
        public final double dMax;
        /** 内化原料行数(本次构建实际内化的纯原料键;不可行两趟回退判定用). */
        public final int rawRows;
        /** CSC 列数据(含耦合松弛/盈余/赤字列;阶段②追加禁行时按此行号空间扩展重建). */
        final List<Map<Integer, Double>> columns;

        Built(LpModel lp, List<VarInfo> vars, List<IAEItemStack> keys, int[] deficitCol, int[] surplusCol,
                double dMax, int rawRows, List<Map<Integer, Double>> columns) {
            this.lp = lp;
            this.vars = vars;
            this.keys = keys;
            this.deficitCol = deficitCol;
            this.surplusCol = surplusCol;
            this.dMax = dMax;
            this.rawRows = rawRows;
            this.columns = columns;
        }

        /** 结构列数(变体变量计入;耦合松弛/赤字列不在其内). */
        public int structuralCols() {
            return this.vars.size();
        }
    }

    private SccLpModelBuilder() {
    }

    /**
     * 构建 SCC 分量的 LP 模型.
     *
     * @param cc 合成网格(getCraftingFor/canEmitFor)
     * @param index 网络样板索引(副产物倒排)
     * @param keys 求解单元键集(canon;冷凝驱动器按"输出共享合并后的 SCC 组"给出)
     * @param stock 网络库存(canon 键 → 数量;缺省 0;发射台键内部按 D_max 覆盖)
     * @param demands 单元内需求(canon 键 → 数量,实数;含根请求与下游单元折算,缺省 0)
     * @param upperCaps 样板执行数额外上界(M4 禁约束:种子不足环方向上界压至库存可行水位);
     *                  键为样板身份引用,空表 = 无禁约束
     * @param committed 已在下游单元承诺的跨单元样板及其执行数(冷凝序先解下游):
     *                  不再作为本单元变量,其单元内产出折算为库存调整(输入消费已由
     *                  下游单元的环外折算传播为 demand,不得重复计)
     * @param internalizeRaw 是否内化纯原料键(库存计价);不可行两趟回退的第二趟
     *                  传 false——缺料场景原生退化为"分支序贪心幻影生产"显示语义,
     *                  去掉内化行让阶段③秩贪心复刻该口径
     */
    public static Built build(ICraftingGrid cc, NetworkPatternIndex index, List<IAEItemStack> keys,
            Map<IAEItemStack, Long> stock, Map<IAEItemStack, Double> demands,
            Map<ICraftingPatternDetails, Double> upperCaps,
            Map<ICraftingPatternDetails, Double> committed, boolean internalizeRaw) {
        Map<IAEItemStack, Integer> rowOf = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            rowOf.put(keys.get(i), i);
        }

        // 收集分量内样板:主输出命中(getCraftingFor) + 副产物命中(byproduct 倒排),身份去重;
        // 已承诺(下游分量已求解)的跨分量样板不再成为本分量变量
        Set<ICraftingPatternDetails> patternSet = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ICraftingPatternDetails> patterns = new ArrayList<>();
        for (IAEItemStack key : keys) {
            for (ICraftingPatternDetails p : index.patternsFor(key)) {
                if (!committed.containsKey(p) && patternSet.add(p)) {
                    patterns.add(p);
                }
            }
            for (ICraftingPatternDetails p : index.byproductMap().getOrDefault(key, Collections.emptyList())) {
                if (!committed.containsKey(p) && patternSet.add(p)) {
                    patterns.add(p);
                }
            }
        }

        // D_max = D_total·ρ_max^K(损耗链逐跳放大的闭式充分上界),封顶 DMAX_CAP
        double dTotal = 0;
        for (IAEItemStack key : keys) {
            dTotal += Math.max(0, demands.getOrDefault(key, 0.0));
        }
        double rhoMax = 1;
        for (ICraftingPatternDetails p : patterns) {
            double inScc = 0;
            double outScc = 0;
            for (IAEItemStack in : p.getCondensedInputs()) {
                if (in != null && in.getStackSize() > 0 && rowOf.containsKey(RecursiveCraftingHelper.canon(in))) {
                    inScc += in.getStackSize();
                }
            }
            for (IAEItemStack out : p.getCondensedOutputs()) {
                if (out != null && rowOf.containsKey(RecursiveCraftingHelper.canon(out))) {
                    outScc += out.getStackSize();
                }
            }
            if (outScc > 0) {
                rhoMax = Math.max(rhoMax, inScc / outScc);
            }
        }
        double dMax = Math.max(1, dTotal) * Math.pow(rhoMax, Math.max(1, keys.size()));
        if (!Double.isFinite(dMax) || dMax > DMAX_CAP) {
            dMax = DMAX_CAP;
        }

        // 变量展开(含矿词变体)与列系数
        List<VarInfo> vars = new ArrayList<>();
        List<Map<Integer, Double>> columns = new ArrayList<>();
        List<double[]> bounds = new ArrayList<>(); // [lower, upper](与 columns 对齐)
        List<int[]> coupling = new ArrayList<>(); // 多变体耦合:变体列号组

        // 预扫描:逐样板展开变体(缓存),并收集内化的纯原料键(无生产者、非发射台):
        // ① 替代参与的键(变体 override 涉及)——否则阶段①对变体选择"失明"
        //   (基准变量吃稀缺编码物与变体变量吃充足候选同为零成本,候选库存形同虚设);
        // ② 单元独占的原料键(全部消费者均属本单元)——否则阶段③对多分支选择"失明"
        //   (分支 1 吃稀缺原料与分支 2 吃充足原料同为零成本,稀缺容量无法封顶).
        // 跨单元共享的原料键仍折算传播,在叶子单元统一结算,避免跨单元重复计库存.
        Map<ICraftingPatternDetails, List<Map<IAEItemStack, Long>>> patternVariants = new IdentityHashMap<>();
        Set<IAEItemStack> internalRaw = new java.util.LinkedHashSet<>();
        for (ICraftingPatternDetails pattern : patterns) {
            List<Map<IAEItemStack, Long>> variants = expandVariants(pattern);
            patternVariants.put(pattern, variants);
            if (!internalizeRaw) {
                continue;
            }
            for (Map<IAEItemStack, Long> override : variants) {
                for (IAEItemStack k : override.keySet()) {
                    if (!rowOf.containsKey(k) && !index.canEmit(k) && !hasProducer(index, k)) {
                        internalRaw.add(k);
                    }
                }
                for (IAEItemStack k : condensedInputs(pattern, override).keySet()) {
                    if (!rowOf.containsKey(k) && !index.canEmit(k) && !hasProducer(index, k)
                            && consumersAllInUnit(index, k, patternSet)) {
                        internalRaw.add(k);
                    }
                }
            }
        }
        // 多分支判定:同单元内与其他样板共享输出键(原生逐分支批量分配语义——
        // 自返还输入按库存批量容量封顶,区别于单样板催化链的逐次种子复用)
        Map<IAEItemStack, Integer> outputCounts = new HashMap<>();
        for (ICraftingPatternDetails pattern : patterns) {
            for (IAEItemStack out : pattern.getCondensedOutputs()) {
                if (out != null) {
                    outputCounts.merge(RecursiveCraftingHelper.canon(out), 1, Integer::sum);
                }
            }
        }
        // 行号空间扩展:单元键(0..K-1) + 内化原料行(K..K+R-1),demand 恒 0
        List<IAEItemStack> allKeys = new ArrayList<>(keys);
        for (IAEItemStack raw : internalRaw) {
            rowOf.put(raw, allKeys.size());
            allKeys.add(raw);
        }

        // 执行数上界:U_p = ⌈min(DMAX_CAP, D_max·K)⌉(见类注释)
        double baseUpper = Math.min(DMAX_CAP, dMax * Math.max(1, keys.size()));
        for (int rank = 0; rank < patterns.size(); rank++) {
            ICraftingPatternDetails pattern = patterns.get(rank);
            boolean hasSccOutput = false;
            for (IAEItemStack out : pattern.getCondensedOutputs()) {
                if (out != null && rowOf.containsKey(RecursiveCraftingHelper.canon(out))) {
                    hasSccOutput = true;
                    break;
                }
            }
            if (!hasSccOutput) {
                continue; // 无 SCC 产出(防御;按收集方式不会发生)
            }
            double upper = Math.min(Math.ceil(baseUpper),
                    upperCaps.getOrDefault(pattern, Double.MAX_VALUE));
            List<Map<IAEItemStack, Long>> variants = patternVariants.get(pattern);
            int[] varCols = new int[variants.size()];
            for (int v = 0; v < variants.size(); v++) {
                Map<IAEItemStack, Long> variantOverride = variants.get(v);
                Map<Integer, Double> col = new TreeMap<>();
                Map<IAEItemStack, Double> external = new LinkedHashMap<>();
                // 输入:SCC 键入守恒行(负),环外键记折算系数
                for (Map.Entry<IAEItemStack, Long> in : condensedInputs(pattern, variantOverride).entrySet()) {
                    Integer row = rowOf.get(in.getKey());
                    if (row != null) {
                        col.merge(row, -(double) in.getValue(), Double::sum);
                    } else {
                        external.merge(in.getKey(), (double) in.getValue(), Double::sum);
                    }
                }
                // 输出:仅 SCC 键入守恒行(正)
                for (IAEItemStack out : pattern.getCondensedOutputs()) {
                    if (out == null) {
                        continue;
                    }
                    Integer row = rowOf.get(RecursiveCraftingHelper.canon(out));
                    if (row != null) {
                        col.merge(row, (double) out.getStackSize(), Double::sum);
                    }
                }
                // 容器/配方返还(三层同口径,见 FlowReconciler.returnsPerCraft):
                // 行键 +perCraft 入守恒行;环外键记负系数(返还供给折算,可抵消
                // 其他单元对同键的需求传播).自返还(返还键同为输入)净系数归零——
                // 其一次性种子需求由 SccLpSolve 按活跃变量扁平记账(seedDemands).
                // 例外:多分支样板的自返还内化原料键不回记(保留 −perCraft 毛输入
                // 系数作批量容量行)——原生逐分支批量分配按库存容量封顶后溢出到
                // 次优分支,净零系数会让阶段③对该容量"失明"(区别于单样板
                // 催化链的逐次种子复用语义,后者保持净零)
                for (Map.Entry<IAEItemStack, Long> ret : FlowReconciler.returnsPerCraft(pattern).entrySet()) {
                    if (internalRaw.contains(ret.getKey())
                            && FlowReconciler.containsInput(pattern, ret.getKey())
                            && isMultiBranch(pattern, outputCounts)) {
                        continue;
                    }
                    Integer row = rowOf.get(ret.getKey());
                    if (row != null) {
                        col.merge(row, (double) ret.getValue(), Double::sum);
                    } else {
                        external.merge(ret.getKey(), -(double) ret.getValue(), Double::sum);
                    }
                }
                // 净系数为 0 的行移出(自平衡样板,如 1A→1A)
                col.values().removeIf(val -> val == 0.0);
                external.values().removeIf(val -> val == 0.0);
                varCols[v] = columns.size();
                vars.add(new VarInfo(pattern, variantOverride, upper, external, rank, -1));
                columns.add(col);
                bounds.add(new double[] { 0, upper });
            }
            if (variants.size() > 1) {
                coupling.add(varCols);
            }
        }

        // 行布局:0..K+R-1 键守恒(单元键 + 内化原料行);其后为多变体耦合(Σ变体 + slack = U_p)
        int keyRows = allKeys.size();
        double[] b = new double[keyRows + coupling.size()];
        for (int i = 0; i < keyRows; i++) {
            IAEItemStack key = allKeys.get(i);
            double demand = demands.getOrDefault(key, 0.0);
            double stockK = index.canEmit(key) ? dMax : stock.getOrDefault(key, 0L);
            b[i] = demand - stockK;
        }
        // 已承诺跨分量样板:其 SCC 内产出已实物存在,折算为库存(b = demand − stock 故减去);
        // 输入消费不计(已由下游分量的环外折算传播为 demand)
        for (Map.Entry<ICraftingPatternDetails, Double> com : committed.entrySet()) {
            for (IAEItemStack out : com.getKey().getCondensedOutputs()) {
                if (out == null) {
                    continue;
                }
                Integer row = rowOf.get(RecursiveCraftingHelper.canon(out));
                if (row != null) {
                    b[row] -= out.getStackSize() * com.getValue();
                }
            }
        }

        // 多变体耦合行:共享执行数上界(§4.1 上界共享),松弛列紧随结构列
        for (int ci = 0; ci < coupling.size(); ci++) {
            int[] varCols = coupling.get(ci);
            int row = keyRows + ci;
            b[row] = vars.get(varCols[0]).upper;
            for (int col : varCols) {
                columns.get(col).put(row, 1.0);
                VarInfo info = vars.get(col);
                vars.set(col, new VarInfo(info.pattern, info.variantInputs, info.upper, info.externalInputs,
                        info.rank, row));
            }
            Map<Integer, Double> slackCol = new TreeMap<>();
            slackCol.put(row, 1.0);
            columns.add(slackCol);
            bounds.add(new double[] { 0, LpModel.INF });
        }

        // 盈余列(每键一列,行系数 −1,两阶段零成本):剩余库存留存网络的等式化
        int[] surplusCol = new int[keyRows];
        for (int i = 0; i < keyRows; i++) {
            surplusCol[i] = columns.size();
            Map<Integer, Double> sCol = new TreeMap<>();
            sCol.put(i, -1.0);
            columns.add(sCol);
            bounds.add(new double[] { 0, LpModel.INF });
        }

        // 赤字列(每键一列,行系数 +1,阶段①目标 1),恒为最末 keyRows 列
        int[] deficitCol = new int[keyRows];
        for (int i = 0; i < keyRows; i++) {
            deficitCol[i] = columns.size();
            Map<Integer, Double> dCol = new TreeMap<>();
            dCol.put(i, 1.0);
            columns.add(dCol);
            bounds.add(new double[] { 0, LpModel.INF });
        }

        int n = columns.size();
        double[] lower = new double[n];
        double[] upperArr = new double[n];
        double[] cost = new double[n];
        for (int j = 0; j < n; j++) {
            lower[j] = bounds.get(j)[0];
            upperArr[j] = bounds.get(j)[1];
        }
        for (int i = 0; i < keyRows; i++) {
            cost[deficitCol[i]] = 1; // 阶段①:赤字和最小化
        }

        LpModel lp = new LpModel(SparseMatrix.fromColumns(b.length, columns), b, cost, lower, upperArr);
        return new Built(lp, vars, allKeys, deficitCol, surplusCol, dMax, internalRaw.size(), columns);
    }

    /** 键是否有生产者样板(主输出命中或副产物倒排命中)——内化原料行判定用. */
    private static boolean hasProducer(NetworkPatternIndex index, IAEItemStack key) {
        return !index.patternsFor(key).isEmpty()
                || index.byproductMap().containsKey(key);
    }

    /** 键的全部消费者样板是否都属于本单元(单元独占原料判定;已承诺的跨单元
     * 样板不在 unitPatterns 中,其消费会正确阻止内化,避免跨单元重复计库存). */
    private static boolean consumersAllInUnit(NetworkPatternIndex index, IAEItemStack key,
            Set<ICraftingPatternDetails> unitPatterns) {
        for (ICraftingPatternDetails consumer : index.consumersOf(key)) {
            if (!unitPatterns.contains(consumer)) {
                return false;
            }
        }
        return true;
    }

    /** 样板是否与同单元其他样板共享输出键(多分支;原生逐分支批量分配语义适用). */
    private static boolean isMultiBranch(ICraftingPatternDetails pattern,
            Map<IAEItemStack, Integer> outputCounts) {
        for (IAEItemStack out : pattern.getCondensedOutputs()) {
            if (out != null && outputCounts.getOrDefault(RecursiveCraftingHelper.canon(out), 0) >= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * 凝聚输入(canon 键 → 单次消耗),变体变量以 override 覆盖替代槽的编码输入份额.
     * 包内共享:{@link SeedBootstrapCheck} 调度模拟与冷凝驱动器截断路径按同一口径还原变体输入.
     */
    public static Map<IAEItemStack, Long> condensedInputs(ICraftingPatternDetails pattern,
            Map<IAEItemStack, Long> variantOverride) {
        Map<IAEItemStack, Long> merged = new LinkedHashMap<>();
        if (variantOverride.isEmpty()) {
            for (IAEItemStack in : pattern.getCondensedInputs()) {
                if (in != null && in.getStackSize() > 0) {
                    merged.merge(RecursiveCraftingHelper.canon(in), in.getStackSize(), Long::sum);
                }
            }
            return merged;
        }
        // 变体:逐槽重建(编码槽原样计入),override 为相对净调整(替代槽:编码键减、候选键加)
        for (IAEItemStack in : pattern.getInputs()) {
            if (in != null && in.getStackSize() > 0) {
                merged.merge(RecursiveCraftingHelper.canon(in), in.getStackSize(), Long::sum);
            }
        }
        for (Map.Entry<IAEItemStack, Long> ov : variantOverride.entrySet()) {
            merged.merge(ov.getKey(), ov.getValue(), Long::sum);
        }
        merged.values().removeIf(v -> v <= 0);
        return merged;
    }

    /**
     * 展开矿词替代变体(D1).
     * <p>返回变体输入覆盖表列表;首元素恒为空表(编码输入基准变量).
     * 无替代槽/替代关闭/超限退化时列表长度为 1.</p>
     */
    private static List<Map<IAEItemStack, Long>> expandVariants(ICraftingPatternDetails pattern) {
        List<Map<IAEItemStack, Long>> variants = new ArrayList<>();
        variants.add(Collections.emptyMap());
        if (!pattern.isCraftable() || !pattern.canSubstitute()) {
            return variants;
        }
        IAEItemStack[] slots = pattern.getInputs();
        // 可替代槽:候选 canon 键(编码键恒为首元)2..MAX_VARIANTS_PER_SLOT 个;超限槽整体退化
        List<Integer> expandableSlots = new ArrayList<>();
        List<List<IAEItemStack>> candidates = new ArrayList<>();
        for (int s = 0; s < slots.length; s++) {
            IAEItemStack in = slots[s];
            if (in == null || in.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack encoded = RecursiveCraftingHelper.canon(in);
            List<IAEItemStack> canonCandidates = new ArrayList<>();
            canonCandidates.add(encoded);
            Set<IAEItemStack> seen = new HashSet<>();
            seen.add(encoded);
            List<IAEItemStack> subs = pattern.getSubstituteInputs(s);
            if (subs != null) {
                for (IAEItemStack sub : subs) {
                    if (sub == null) {
                        continue;
                    }
                    IAEItemStack canon = RecursiveCraftingHelper.canon(sub);
                    if (seen.add(canon)) {
                        canonCandidates.add(canon);
                    }
                }
            }
            if (canonCandidates.size() >= 2 && canonCandidates.size() <= MAX_VARIANTS_PER_SLOT) {
                expandableSlots.add(s);
                candidates.add(canonCandidates);
            }
        }
        if (expandableSlots.isEmpty()) {
            return variants;
        }
        // 笛卡尔积展开(组合数封顶 MAX_VARIANTS_PER_PATTERN,超限退化为仅编码输入)
        int combos = 1;
        for (List<IAEItemStack> c : candidates) {
            combos *= c.size();
            if (combos > MAX_VARIANTS_PER_PATTERN) {
                variants.clear();
                variants.add(Collections.emptyMap());
                return variants;
            }
        }
        for (int c = 1; c < combos; c++) {
            int rem = c;
            Map<IAEItemStack, Long> override = new LinkedHashMap<>();
            for (int e = expandableSlots.size() - 1; e >= 0; e--) {
                int pick = rem % candidates.get(e).size();
                rem /= candidates.get(e).size();
                IAEItemStack encoded = candidates.get(e).get(0);
                IAEItemStack chosen = candidates.get(e).get(pick);
                if (!chosen.equals(encoded)) {
                    int slot = expandableSlots.get(e);
                    override.merge(encoded, -slots[slot].getStackSize(), Long::sum);
                    override.merge(chosen, slots[slot].getStackSize(), Long::sum);
                }
            }
            if (!override.isEmpty()) {
                variants.add(override);
            }
        }
        return variants;
    }
}
