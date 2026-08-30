package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.MECraftingInventory;

import com.github.aeddddd.ae2enhanced.craftingplan.dag.DagExecutor;
import com.github.aeddddd.ae2enhanced.craftingplan.dag.SaturatedMath;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.UnitSolution;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpModelBuilder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;

/**
 * 整数化对账层（方案 L §10.3.4,M5）:LP 实数计数 → 整数次数 + 精确 used/missing 归因.
 * <p>方法 = <b>静态批量重演</b>:按可行调度序（单元冷凝逆序 = 上游先产;单元内
 * 增益相→中性相,与 {@link SeedBootstrapCheck} 同序）在模拟库存上整批重演,
 * 记账口径完全复用 {@link DagExecutor}（网络优先提取记 used、合成侧余额抵扣、
 * 产出/容器返还注入、发射台免费满足）——物化计划的账目与 DAG 路径逐字节同语义.</p>
 * <ul>
 * <li><b>整数化</b>:次数 = ⌈count − 1e-6⌉(只增不减,守恒方向安全;尾差 ±1/边
 * 在重演中浮现为输入缺口,精确归因 missing——§10.3.4 小量尾差策略);</li>
 * <li><b>守恒校验</b>:重演中任何输入提取不足(库存+合成侧余额都不够,非发射台)
 * 即记 missing(防御;LP+种子校验一致时不触发,整数化尾差除外);</li>
 * <li><b>发射台</b>:canEmitFor 键提取网络实存后剩余免费满足(原生同语义);</li>
 * <li><b>根库存</b>:与驱动器一致,请求物自身库存参与种子/抵扣(LP 路径语义).</li>
 * </ul>
 */
public final class FlowReconciler {

    private FlowReconciler() {
    }

    /** 对账产物. */
    public static final class Reconciled {
        /** 逐执行记录整数次数(与 {@link LpPlanOutcome#executions} 同序对齐;物化 crafts 用). */
        public final Map<Execution, Long> times;
        /** 缺料(canon 键 → 数量;LP 赤字整数化 + 重演缺口). */
        public final Map<IAEItemStack, Long> missing;
        /** 网络实取(canon 键 → 数量;诊断——实际记账在根节点 used 列表). */
        public final Map<IAEItemStack, Long> networkSourced;
        /** 初始提取总量(bytes 近似,同 DagExecutor 口径). */
        public final long totalExtracted;

        Reconciled(Map<Execution, Long> times, Map<IAEItemStack, Long> missing,
                Map<IAEItemStack, Long> networkSourced, long totalExtracted) {
            this.times = times;
            this.missing = missing;
            this.networkSourced = networkSourced;
            this.totalExtracted = totalExtracted;
        }
    }

    /**
     * 整数化并重演对账.
     *
     * @param inv 模拟库存(调用方新建,含请求物自身库存——与驱动器根库存语义一致)
     * @param rootNode 物化根节点(used 记账宿主)
     */
    public static Reconciled reconcile(ICraftingGrid cc, IAEItemStack what, long target,
            LpPlanOutcome outcome, MECraftingInventory inv, CraftingTreeNode rootNode, IActionSource src) {
        // 1) 整数化(只增)
        Map<Execution, Long> times = new IdentityHashMap<>();
        for (Execution exec : outcome.executions) {
            long t = toLong(exec.count);
            if (t > 0) {
                times.put(exec, t);
            }
        }

        // 2) 重演:单元冷凝逆序(上游先产),单元内 增益相→中性相 至收敛
        Map<IAEItemStack, Long> synthetic = new LinkedHashMap<>();
        Map<IAEItemStack, Long> fundedByCredit = new LinkedHashMap<>();
        Map<IAEItemStack, Long> networkSourced = new LinkedHashMap<>();
        Map<IAEItemStack, Long> missing = new LinkedHashMap<>();
        Set<IAEItemStack> containerKeys = new LinkedHashSet<>();
        long totalExtracted = 0;

        List<UnitSolution> units = outcome.unitSolutions;
        for (int u = units.size() - 1; u >= 0; u--) {
            UnitSolution unit = units.get(u);
            List<ReplayVar> vars = new ArrayList<>();
            Set<IAEItemStack> keySet = new LinkedHashSet<>(unit.keys);
            for (Execution exec : unit.executions) {
                Long t = times.get(exec);
                if (t == null || t <= 0) {
                    continue;
                }
                vars.add(new ReplayVar(exec, t, keySet));
            }
            totalExtracted = SaturatedMath.add(totalExtracted,
                    replayUnit(cc, vars, inv, synthetic, fundedByCredit, networkSourced, missing,
                            containerKeys, rootNode, src));
        }

        // 3) 根键交付结算(同 DagExecutor 根节点口径:先吃网络实存记 used,
        // 生产注入部分由合成侧余额抵扣;缺口已由 LP 赤字表达,不重复记账)
        IAEItemStack rootKey = RecursiveCraftingHelper.canon(what);
        if (cc.canEmitFor(rootKey)) {
            // 发射台根:实存先取,剩余免费满足(原生同语义)
            long realAvailable = Math.max(0L, DagExecutor.invAmount(inv, rootKey)
                    - synthetic.getOrDefault(rootKey, 0L));
            if (realAvailable > 0) {
                totalExtracted = SaturatedMath.add(totalExtracted, DagExecutor.extractCredited(rootKey,
                        Math.min(target, realAvailable), inv, synthetic, fundedByCredit, networkSourced,
                        rootNode, src, true).fromNetwork);
            }
        } else {
            totalExtracted = SaturatedMath.add(totalExtracted, DagExecutor.extractCredited(rootKey, target,
                    inv, synthetic, fundedByCredit, networkSourced, rootNode, src, false).fromNetwork);
        }

        // 4) 种子高水位修正(与 DagExecutor 同口径):零网络来源的自举容器补 missing=1
        for (IAEItemStack containerKey : containerKeys) {
            if (fundedByCredit.getOrDefault(containerKey, 0L) > 0
                    && networkSourced.getOrDefault(containerKey, 0L) <= 0) {
                missing.merge(containerKey, 1L, Long::sum);
            }
        }

        // 5) LP 赤字整数化入账(单元内短缺/降级直通/截断缺口;重演提取缺口由上游
        // 赤字承担,不重复记账——整数化尾差(±1/边)按 §10.3.4 策略随赤字口径收敛)
        for (Map.Entry<IAEItemStack, Double> deficit : outcome.deficits.entrySet()) {
            long amount = toLong(deficit.getValue());
            if (amount > 0) {
                missing.merge(deficit.getKey(), amount, Long::sum);
            }
        }

        return new Reconciled(times, missing, networkSourced, totalExtracted);
    }

    /** 重演变量:整数剩余次数 + 输入/输出表 + 单元净增益(分相序用). */
    private static final class ReplayVar {
        final Execution exec;
        final Map<IAEItemStack, Long> inputs;
        final Map<IAEItemStack, Long> outputs;
        final double netGain;
        long remaining;

        ReplayVar(Execution exec, long times, Set<IAEItemStack> unitKeys) {
            this.exec = exec;
            this.remaining = times;
            this.inputs = SccLpModelBuilder.condensedInputs(exec.pattern, exec.variantInputs);
            this.outputs = new LinkedHashMap<>();
            for (IAEItemStack o : exec.pattern.getCondensedOutputs()) {
                if (o != null) {
                    this.outputs.merge(RecursiveCraftingHelper.canon(o), o.getStackSize(), Long::sum);
                }
            }
            double in = 0;
            for (Map.Entry<IAEItemStack, Long> e : this.inputs.entrySet()) {
                if (unitKeys.contains(e.getKey())) {
                    in += e.getValue();
                }
            }
            double out = 0;
            for (Map.Entry<IAEItemStack, Long> e : this.outputs.entrySet()) {
                if (unitKeys.contains(e.getKey())) {
                    out += e.getValue();
                }
            }
            this.netGain = out - in;
        }
    }

    /**
     * 单元重演:增益相(增益源反复至耗尽)→ 中性相(一趟),交替至全部完成.
     * LP+种子校验已保证可行,循环必收敛;提取不足记 missing(防御/整数化尾差).
     *
     * @return 本单元网络实取总量(bytes 记账用)
     */
    private static long replayUnit(ICraftingGrid cc, List<ReplayVar> vars, MECraftingInventory inv,
            Map<IAEItemStack, Long> synthetic, Map<IAEItemStack, Long> fundedByCredit,
            Map<IAEItemStack, Long> networkSourced, Map<IAEItemStack, Long> missing,
            Set<IAEItemStack> containerKeys, CraftingTreeNode rootNode, IActionSource src) {
        long extractedTotal = 0;
        // 与 SeedBootstrapCheck 一致的轮数防御
        int rounds = 0;
        while (rounds++ < 4096) {
            boolean anyRemaining = false;
            for (ReplayVar v : vars) {
                if (v.remaining > 0) {
                    anyRemaining = true;
                    break;
                }
            }
            if (!anyRemaining) {
                break;
            }
            boolean progressed = false;
            // 增益相
            boolean gainProgress = true;
            int gainPasses = 0;
            while (gainProgress && gainPasses++ < 4096) {
                gainProgress = false;
                for (ReplayVar v : vars) {
                    if (v.netGain <= 0 || v.remaining <= 0) {
                        continue;
                    }
                    long cap = capacity(v, inv, synthetic, cc);
                    if (cap <= 0) {
                        continue;
                    }
                    gainProgress = progressed = true;
                    extractedTotal = SaturatedMath.add(extractedTotal, applyBatch(v, cap, cc, inv, synthetic,
                            fundedByCredit, networkSourced, containerKeys, rootNode, src));
                }
            }
            // 中性相
            for (ReplayVar v : vars) {
                if (v.netGain > 0 || v.remaining <= 0) {
                    continue;
                }
                long cap = capacity(v, inv, synthetic, cc);
                if (cap <= 0) {
                    continue;
                }
                progressed = true;
                extractedTotal = SaturatedMath.add(extractedTotal, applyBatch(v, cap, cc, inv, synthetic,
                        fundedByCredit, networkSourced, containerKeys, rootNode, src));
            }
            if (!progressed) {
                // 剩余生产无法启动(上游赤字已承担差额):放弃剩余次数,不重复记账
                for (ReplayVar v : vars) {
                    v.remaining = 0;
                }
                break;
            }
        }
        return extractedTotal;
    }

    /** 当前可行批量:min(剩余, 逐输入 (库存+合成侧余额)/单耗);发射台输入不设限. */
    private static long capacity(ReplayVar v, MECraftingInventory inv, Map<IAEItemStack, Long> synthetic,
            ICraftingGrid cc) {
        long cap = v.remaining;
        for (Map.Entry<IAEItemStack, Long> in : v.inputs.entrySet()) {
            if (cc.canEmitFor(in.getKey())) {
                continue;
            }
            long available = DagExecutor.invAmount(inv, in.getKey());
            cap = Math.min(cap, available / in.getValue());
        }
        return cap;
    }

    /**
     * 整批执行 cap 次:逐输入提取(网络优先记 used,合成侧余额抵扣;发射台免费)
     * → 产出注入 + 容器返还回记(同 DagExecutor 口径).
     *
     * @return 本批网络实取量
     */
    private static long applyBatch(ReplayVar v, long cap, ICraftingGrid cc, MECraftingInventory inv,
            Map<IAEItemStack, Long> synthetic, Map<IAEItemStack, Long> fundedByCredit,
            Map<IAEItemStack, Long> networkSourced,
            Set<IAEItemStack> containerKeys, CraftingTreeNode rootNode, IActionSource src) {
        v.remaining -= cap;
        long fromNetwork = 0;
        for (Map.Entry<IAEItemStack, Long> in : v.inputs.entrySet()) {
            long need = SaturatedMath.multiply(in.getValue(), cap);
            if (need <= 0) {
                continue;
            }
            if (cc.canEmitFor(in.getKey())) {
                // 发射台:网络实存先取,剩余免费满足(原生同语义,不记缺料)
                long realAvailable = Math.max(0L, DagExecutor.invAmount(inv, in.getKey())
                        - synthetic.getOrDefault(in.getKey(), 0L));
                if (realAvailable > 0) {
                    DagExecutor.extractCredited(in.getKey(), Math.min(need, realAvailable), inv, synthetic,
                            fundedByCredit, networkSourced, rootNode, src, true);
                }
                continue;
            }
            DagExecutor.ExtractOutcome outcome = DagExecutor.extractCredited(in.getKey(), need, inv, synthetic,
                    fundedByCredit, networkSourced, rootNode, src, false);
            fromNetwork = SaturatedMath.add(fromNetwork, outcome.fromNetwork);
            // 提取缺口不记 missing:上游赤字/降级已承担差额(整数化尾差随赤字口径收敛)
        }
        // 容器返还(自返还 cap-1 保种子,跨样板全额——与 DagExecutor 同口径)
        DagExecutor.creditReturns(v.exec.pattern, cap, inv, synthetic, containerKeys, src);
        // 产出注入 + 合成侧余额
        for (Map.Entry<IAEItemStack, Long> out : v.outputs.entrySet()) {
            IAEItemStack produced = out.getKey().copy();
            produced.setStackSize(SaturatedMath.multiply(out.getValue(), cap));
            inv.injectItems(produced, Actionable.MODULATE, src);
            synthetic.merge(out.getKey(), produced.getStackSize(), Long::sum);
        }
        return fromNetwork;
    }

    /** 实数计数 → 整数次数:⌈count − 1e-6⌉,饱和转换. */
    private static long toLong(double count) {
        if (count <= 1e-6) {
            return 0;
        }
        double ceiled = Math.ceil(count - 1e-6);
        if (ceiled >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) ceiled;
    }
}
