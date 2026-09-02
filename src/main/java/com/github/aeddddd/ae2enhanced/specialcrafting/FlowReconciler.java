package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.MECraftingInventory;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.UnitSolution;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpModelBuilder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;

/**
 * 整数化对账层（方案 L §10.3.4,M5）:LP 实数计数 → 整数次数 + 精确 used/missing 归因.
 * <p>方法 = <b>静态批量重演</b>:按可行调度序（单元冷凝逆序 = 上游先产;单元内
 * 增益相→中性相,与 {@link SeedBootstrapCheck} 同序）在模拟库存上整批重演,
 * 记账口径与原 DAG 路径逐字节同语义（网络优先提取记 used、合成侧余额抵扣、
 * 产出/容器返还注入、发射台免费满足;助手方法见本类末尾）.</p>
 * <ul>
 * <li><b>整数化</b>:次数 = ⌈count − 1e-6⌉(只增不减,守恒方向安全;尾差 ±1/边
 * 在重演中浮现为输入缺口,精确归因 missing——§10.3.4 小量尾差策略);</li>
 * <li><b>守恒校验</b>:重演中任何输入提取不足(库存+合成侧余额都不够,非发射台)
 * 即记 missing(防御;LP+种子校验一致时不触发,整数化尾差除外);</li>
 * <li><b>发射台</b>:canEmitFor 键提取网络实存后剩余免费满足(原生同语义);</li>
 * <li><b>根库存</b>:请求物自身库存不抵交付(全额生产,原生 CraftingJob.ignore(output)
 * 同语义),但可作为点火种子被提取(记 used,执行层点火必需),期末由循环盈余
 * 随 CPU 剩余返还网络(种子保留).</li>
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
        /** 初始提取总量(bytes 近似). */
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
    public static Reconciled reconcile(ICraftingGrid cc, NetworkPatternIndex index, IAEItemStack what,
            long target, LpPlanOutcome outcome, MECraftingInventory inv, CraftingTreeNode rootNode,
            IActionSource src) {
        // 1) 整数化(只增)
        Map<Execution, Long> times = new IdentityHashMap<>();
        for (Execution exec : outcome.executions) {
            long t = toLong(exec.count);
            if (t > 0) {
                times.put(exec, t);
            }
        }

        // 1.5) 整数化守恒修复:独立 ⌈⌉ 对分数速率环会破坏配比(如 2C→3A 的 1.5 轮
        // 上取整后中间键 3≠4 失衡)——逐单元检查内部键净平衡(库存+Σ(out−in)·t),
        // 负平衡时按产出系数最小整数回补该键的生产者,级联至收敛.只增不减,
        // 交付能力不受损;不修则重演/执行层在失衡键上断料死锁
        repairIntegralBalance(cc, index, outcome, times, inv);

        // 守恒修复回补激活的零计数执行记录补入扁平列表(物化/成环判定按
        // outcome.executions 遍历;身份判等,已在表中的活跃执行不重复加入)
        for (UnitSolution unit : outcome.unitSolutions) {
            for (Execution exec : unit.executions) {
                if (times.getOrDefault(exec, 0L) > 0 && !outcome.executions.contains(exec)) {
                    outcome.executions.add(exec);
                }
            }
        }

        // 2) 重演:单元冷凝逆序(上游先产),多趟不动点——跨单元容器返还
        // (下游单元样板的返还物是上游单元样板的输入,如空桶自举链)在本单元
        // 重演时尚未回记,单趟会误判"无法启动";整轮重扫直至无进展为止.
        // 仍无法启动的剩余次数由上游赤字承担差额,不重复记账
        Map<IAEItemStack, Long> synthetic = new LinkedHashMap<>();
        Map<IAEItemStack, Long> fundedByCredit = new LinkedHashMap<>();
        Map<IAEItemStack, Long> networkSourced = new LinkedHashMap<>();
        Map<IAEItemStack, Long> missing = new LinkedHashMap<>();
        Set<IAEItemStack> containerKeys = new LinkedHashSet<>();
        long totalExtracted = 0;
        IAEItemStack rootKey = RecursiveCraftingHelper.canon(what);

        List<UnitSolution> units = outcome.unitSolutions;
        List<List<ReplayVar>> unitVars = new ArrayList<>(units.size());
        for (UnitSolution unit : units) {
            List<ReplayVar> vars = new ArrayList<>();
            Set<IAEItemStack> keySet = new LinkedHashSet<>(unit.keys);
            for (Execution exec : unit.executions) {
                Long t = times.get(exec);
                if (t == null || t <= 0) {
                    continue;
                }
                vars.add(new ReplayVar(exec, t, keySet));
            }
            unitVars.add(vars);
        }
        boolean anyProgress = true;
        for (int pass = 0; anyProgress && pass < 64; pass++) {
            anyProgress = false;
            for (int u = units.size() - 1; u >= 0; u--) {
                ReplayPass passResult = replayUnit(cc, index, unitVars.get(u), inv, synthetic, fundedByCredit,
                        networkSourced, missing, containerKeys, rootNode, src);
                anyProgress |= passResult.progressed;
                totalExtracted = SaturatedMath.add(totalExtracted, passResult.extracted);
            }
        }

        // 3) 根键交付结算:全额生产口径——交付只吃合成盈余(库存不抵交付),
        // 缺口由 LP 赤字(步骤 5)表达;发射台根全额免费满足,不提取、不记 used
        if (!index.canEmit(rootKey)) {
            long funded = Math.min(target, synthetic.getOrDefault(rootKey, 0L));
            if (funded > 0) {
                synthetic.merge(rootKey, -funded, Long::sum);
            }
        }

        // 4) 种子高水位修正:零网络来源的自举容器补 missing=1
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

    /** 单元一趟重演的产物:网络实取总量 + 是否有任何批次得以前进(多趟不动点终止判定). */
    private static final class ReplayPass {
        final long extracted;
        final boolean progressed;

        ReplayPass(long extracted, boolean progressed) {
            this.extracted = extracted;
            this.progressed = progressed;
        }
    }

    /**
     * 单元重演一趟:增益相(增益源反复至耗尽)→ 中性相(一趟),交替至本趟无法前进.
     * 剩余未完成的次数保留在 {@link ReplayVar#remaining} 中(跨单元返还可能在下趟
     * 回记后使其可启动);LP+种子校验已保证可行,多趟不动点必收敛;提取不足记
     * missing(防御/整数化尾差).
     */
    private static ReplayPass replayUnit(ICraftingGrid cc, NetworkPatternIndex index, List<ReplayVar> vars,
            MECraftingInventory inv,
            Map<IAEItemStack, Long> synthetic, Map<IAEItemStack, Long> fundedByCredit,
            Map<IAEItemStack, Long> networkSourced, Map<IAEItemStack, Long> missing,
            Set<IAEItemStack> containerKeys, CraftingTreeNode rootNode, IActionSource src) {
        long extractedTotal = 0;
        boolean progressedAny = false;
        // 与 SeedBootstrapCheck 一致的轮数防御
        int rounds = 0;
        boolean reserveForGain = true; // 卡死防御:保留过激致整趟零进展时退化为无保留贪心
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
                    long cap = capacity(v, inv, synthetic, index);
                    if (cap <= 0) {
                        continue;
                    }
                    gainProgress = progressed = progressedAny = true;
                    extractedTotal = SaturatedMath.add(extractedTotal, applyBatch(v, cap, index, inv, synthetic,
                            fundedByCredit, networkSourced, containerKeys, rootNode, src));
                }
            }
            // 中性相:竞争键(多个可启动变量的共同输入)按各变量剩余需求比例
            // 快照分配(与 SeedBootstrapCheck 中性相同口径)——贪心全量访问会让
            // 先访问变量吃光共享种子、饿死增益源上游(θ 环 crush/charge 争同一
            // 原料时 sand 种子未实取,执行层断料死锁);另对每个仍可启动的增益
            // 变量保留"一次批量"所需(增益相已先跑,未点火即等待中性产物)——
            // 否则中性变量会吃光共享种子饿死尚未点火的增益源(X4:中性 p1 吃光
            // 32 石,增益 p2 无法启动;分母计入全部剩余需求的比例式会几何衰减,
            // 同样到不了点火水位)
            boolean neutralProgress = true;
            int neutralPasses = 0;
            while (neutralProgress && neutralPasses++ < 4096) {
                neutralProgress = false;
                // 快照:竞争键总需求(仅中性变量)+ 增益变量一次批量保留
                Map<IAEItemStack, Long> contested = new LinkedHashMap<>();
                Map<IAEItemStack, Long> reserved = new LinkedHashMap<>();
                for (ReplayVar v : vars) {
                    if (v.remaining <= 0) {
                        continue;
                    }
                    for (Map.Entry<IAEItemStack, Long> in : v.inputs.entrySet()) {
                        if (index.canEmit(in.getKey())) {
                            continue;
                        }
                        if (v.netGain > 0) {
                            if (reserveForGain) {
                                reserved.merge(in.getKey(), in.getValue(), SaturatedMath::add);
                            }
                        } else {
                            contested.merge(in.getKey(), SaturatedMath.multiply(in.getValue(), v.remaining),
                                    SaturatedMath::add);
                        }
                    }
                }
                // 快照式分配:先按快照算出全部变量的本趟可行量,再统一入账
                // (中性总需求 ≤ 保留后可用量时退化为全量贪心;否则按需求比例分配)
                long[] caps = new long[vars.size()];
                for (int i = 0; i < vars.size(); i++) {
                    ReplayVar v = vars.get(i);
                    if (v.netGain > 0 || v.remaining <= 0) {
                        continue;
                    }
                    long cap = v.remaining;
                    for (Map.Entry<IAEItemStack, Long> in : v.inputs.entrySet()) {
                        if (index.canEmit(in.getKey())) {
                            continue;
                        }
                        long available = Math.max(0L, invAmount(inv, in.getKey())
                                - reserved.getOrDefault(in.getKey(), 0L));
                        long totalDemand = contested.getOrDefault(in.getKey(), 0L);
                        long share = totalDemand > available && totalDemand > 0
                                ? (long) (available * ((double) in.getValue() * v.remaining / totalDemand))
                                : available;
                        cap = Math.min(cap, share / in.getValue());
                    }
                    caps[i] = cap;
                }
                for (int i = 0; i < vars.size(); i++) {
                    ReplayVar v = vars.get(i);
                    if (v.netGain > 0 || caps[i] <= 0) {
                        continue;
                    }
                    neutralProgress = progressed = progressedAny = true;
                    extractedTotal = SaturatedMath.add(extractedTotal, applyBatch(v, caps[i], index, inv, synthetic,
                            fundedByCredit, networkSourced, containerKeys, rootNode, src));
                }
            }
            if (!progressed) {
                if (reserveForGain) {
                    // 保留过激致整趟零进展(如增益源等待跨单元注入):退化为
                    // 无保留贪心重试本趟(总比卡死强)
                    reserveForGain = false;
                    continue;
                }
                // 本趟无法前进:剩余生产留待下趟(跨单元返还回记后可能可启动);
                // 终态仍无法启动的差额由上游赤字承担,不重复记账
                break;
            }
        }
        return new ReplayPass(extractedTotal, progressedAny);
    }

    /** 当前可行批量:min(剩余, 逐输入 (库存+合成侧余额)/单耗);发射台输入不设限. */
    private static long capacity(ReplayVar v, MECraftingInventory inv, Map<IAEItemStack, Long> synthetic,
            NetworkPatternIndex index) {
        long cap = v.remaining;
        for (Map.Entry<IAEItemStack, Long> in : v.inputs.entrySet()) {
            if (index.canEmit(in.getKey())) {
                continue;
            }
            long available = invAmount(inv, in.getKey());
            cap = Math.min(cap, available / in.getValue());
        }
        return cap;
    }

    /**
     * 整批执行 cap 次:逐输入提取(网络优先记 used,合成侧余额抵扣;发射台免费)
     * → 产出注入 + 容器返还回记.
     *
     * @return 本批网络实取量
     */
    private static long applyBatch(ReplayVar v, long cap, NetworkPatternIndex index, MECraftingInventory inv,
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
            // 根键输入按普通键提取(记 used):点火种子必须包含在计划的初始提取中,
            // 执行层(游戏与 ExecutionHarness 同构)任务中途不可访问网络,种子不入
            // used 则 CPU 无法点火死锁;种子期末由循环盈余随 CPU 剩余返还网络
            if (index.canEmit(in.getKey())) {
                // 发射台:网络实存先取,剩余免费满足(原生同语义,不记缺料)
                long realAvailable = Math.max(0L, invAmount(inv, in.getKey())
                        - synthetic.getOrDefault(in.getKey(), 0L));
                if (realAvailable > 0) {
                    extractCredited(in.getKey(), Math.min(need, realAvailable), inv, synthetic,
                            fundedByCredit, networkSourced, rootNode, src, true);
                }
                continue;
            }
            ExtractOutcome outcome = extractCredited(in.getKey(), need, inv, synthetic,
                    fundedByCredit, networkSourced, rootNode, src, false);
            fromNetwork = SaturatedMath.add(fromNetwork, outcome.fromNetwork);
            // 提取缺口不记 missing:上游赤字/降级已承担差额(整数化尾差随赤字口径收敛)
        }
        // 容器返还(自返还 cap-1 保种子,跨样板全额——与原 DAG 路径同口径)
        creditReturns(v.exec.pattern, cap, inv, synthetic, containerKeys, src);
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

    /**
     * 整数化守恒修复(逐单元):内部键净平衡 = 库存 + Σ(out−in)·t 不得为负.
     * 负平衡时回补该键净产出为正的执行记录(⌈缺口/净产出⌉ 次,只增不减),
     * 回补可能消耗其他键造成新负平衡,级联修补(防御上限 64 轮;净增环
     * 保证整数可行解存在,经验上数轮内收敛).
     */
    private static void repairIntegralBalance(ICraftingGrid cc, NetworkPatternIndex index,
            LpPlanOutcome outcome, Map<Execution, Long> times, MECraftingInventory inv) {
        // 外层不动点:内部键修复只保证单元内配比;回补抬升的次数会多耗跨单元外部键
        // (如 H13 的 p0 由 1 抬到 2,dirt 消耗翻倍超过 LP 折算的 1)——外部键负平衡时
        // 回补其生产者所在单元,可能破坏该单元内部平衡,故内外交替至收敛(防御 64 轮)
        for (int outer = 0; outer < 64; outer++) {
            repairInternalBalance(outcome, times, inv);
            if (!repairExternalBalance(cc, index, outcome, times, inv)) {
                break;
            }
        }
    }

    /**
     * 跨单元外部键守恒修复:全图口径净平衡 = 库存 + Σ(产出·t) − Σ(消耗·t) 不得为负.
     * 负平衡时回补该键的生产者执行记录(⌈缺口/单次产出⌉ 次,只增不减);无生产者的
     * 纯原料键跳过(真实缺料,由 LP 赤字口径承担).
     *
     * @return 是否有任何回补(有则需重跑内部键修复)
     */
    private static boolean repairExternalBalance(ICraftingGrid cc, NetworkPatternIndex index,
            LpPlanOutcome outcome,
            Map<Execution, Long> times, MECraftingInventory inv) {
        // 全图生产者倒排:canon 键 → 生产执行记录(含零计数——LP 判定无需生产的
        // 键可能因整数化回补抬升下游消耗而需要点火,如 H13 辅材子合成)
        Map<IAEItemStack, List<Execution>> producerExecs = new HashMap<>();
        for (UnitSolution unit : outcome.unitSolutions) {
            for (Execution exec : unit.executions) {
                for (IAEItemStack o : exec.pattern.getCondensedOutputs()) {
                    if (o == null) {
                        continue;
                    }
                    IAEItemStack k = RecursiveCraftingHelper.canon(o);
                    List<Execution> list = producerExecs.computeIfAbsent(k, key -> new ArrayList<>());
                    if (!list.contains(exec)) {
                        list.add(exec);
                    }
                }
            }
        }
        boolean bumpedAny = false;
        for (int round = 0; round < 64; round++) {
            // 净平衡用 double 累计(同内部修复;SaturatedMath 不适用于负数域)
            Map<IAEItemStack, Double> balance = new HashMap<>();
            Map<IAEItemStack, Double> gross = new HashMap<>();
            for (Map.Entry<Execution, Long> entry : times.entrySet()) {
                Execution exec = entry.getKey();
                long t = entry.getValue();
                if (t <= 0) {
                    continue;
                }
                for (IAEItemStack o : exec.pattern.getCondensedOutputs()) {
                    if (o != null) {
                        IAEItemStack k = RecursiveCraftingHelper.canon(o);
                        double flow = (double) o.getStackSize() * t;
                        balance.merge(k, flow, Double::sum);
                        gross.merge(k, flow, Double::sum);
                    }
                }
                for (Map.Entry<IAEItemStack, Long> in : SccLpModelBuilder
                        .condensedInputs(exec.pattern, exec.variantInputs).entrySet()) {
                    double flow = (double) in.getValue() * t;
                    balance.merge(in.getKey(), -flow, Double::sum);
                    gross.merge(in.getKey(), flow, Double::sum);
                }
            }
            // 库存+全图产出−全图消耗为负且有生产者即回补;天文数字流量下 double
            // 噪声(ULP 随量级增长,1e18 时 ≈128)会伪装成微量负平衡——按相对容差
            // 过滤(该量级下真实尾差本就无法表示,由 LP 赤字口径承担)
            IAEItemStack worst = null;
            double worstBalance = 0;
            for (Map.Entry<IAEItemStack, Double> entry : balance.entrySet()) {
                if (index.canEmit(entry.getKey())) {
                    continue; // 发射台键余额免费满足,无需回补
                }
                double net = entry.getValue() + invAmount(inv, entry.getKey());
                double tol = Math.max(1e-6, 1e-9 * gross.getOrDefault(entry.getKey(), 0.0));
                if (net < -tol && net < worstBalance && producerExecs.containsKey(entry.getKey())) {
                    worst = entry.getKey();
                    worstBalance = net;
                }
            }
            if (worst == null) {
                return bumpedAny;
            }
            // 回补单次产出最大的生产者(最少回补次数)
            Execution best = null;
            long bestOut = 0;
            for (Execution exec : producerExecs.get(worst)) {
                long out = 0;
                for (IAEItemStack o : exec.pattern.getCondensedOutputs()) {
                    if (o != null && RecursiveCraftingHelper.canon(o).equals(worst)) {
                        out += o.getStackSize();
                    }
                }
                if (out > bestOut) {
                    bestOut = out;
                    best = exec;
                }
            }
            if (best == null) {
                return bumpedAny; // 理论防御(containsKey 已保证有生产者)
            }
            long bump = Math.max(1L, (long) Math.ceil(-worstBalance / bestOut - 1e-9));
            times.merge(best, bump, Long::sum);
            bumpedAny = true;
        }
        return bumpedAny;
    }

    /** 逐单元内部键守恒修复(原 repairIntegralBalance 主体). */
    private static void repairInternalBalance(LpPlanOutcome outcome, Map<Execution, Long> times,
            MECraftingInventory inv) {
        for (UnitSolution unit : outcome.unitSolutions) {
            Set<IAEItemStack> keySet = new LinkedHashSet<>(unit.keys);
            List<Execution> execs = new ArrayList<>();
            List<Map<IAEItemStack, Long>> netCoefs = new ArrayList<>();
            for (Execution exec : unit.executions) {
                Long t = times.get(exec);
                if (t == null || t <= 0) {
                    continue;
                }
                Map<IAEItemStack, Long> coef = new LinkedHashMap<>();
                for (IAEItemStack o : exec.pattern.getCondensedOutputs()) {
                    if (o != null) {
                        IAEItemStack k = RecursiveCraftingHelper.canon(o);
                        if (keySet.contains(k)) {
                            coef.merge(k, o.getStackSize(), Long::sum);
                        }
                    }
                }
                for (Map.Entry<IAEItemStack, Long> in : SccLpModelBuilder
                        .condensedInputs(exec.pattern, exec.variantInputs).entrySet()) {
                    if (keySet.contains(in.getKey())) {
                        coef.merge(in.getKey(), -in.getValue(), Long::sum);
                    }
                }
                execs.add(exec);
                netCoefs.add(coef);
            }
            for (int round = 0; round < 64; round++) {
                // 净平衡用 double 累计(SaturatedMath 是正数域工具,负系数会被钳零)
                Map<IAEItemStack, Double> balance = new LinkedHashMap<>();
                for (IAEItemStack k : keySet) {
                    balance.put(k, (double) invAmount(inv, k));
                }
                for (int e = 0; e < execs.size(); e++) {
                    long t = times.getOrDefault(execs.get(e), 0L);
                    if (t <= 0) {
                        continue;
                    }
                    for (Map.Entry<IAEItemStack, Long> c : netCoefs.get(e).entrySet()) {
                        balance.merge(c.getKey(), (double) c.getValue() * t, Double::sum);
                    }
                }
                IAEItemStack worst = null;
                double worstBalance = 0;
                for (Map.Entry<IAEItemStack, Double> entry : balance.entrySet()) {
                    if (entry.getValue() < worstBalance) {
                        worst = entry.getKey();
                        worstBalance = entry.getValue();
                    }
                }
                if (worst == null) {
                    break; // 全部非负:本单元收敛
                }
                boolean bumped = false;
                for (int e = 0; e < execs.size(); e++) {
                    Long coef = netCoefs.get(e).get(worst);
                    if (coef != null && coef > 0) {
                        long bump = Math.max(1L, (long) Math.ceil(-worstBalance / coef - 1e-9));
                        times.merge(execs.get(e), bump, Long::sum);
                        bumped = true;
                        break;
                    }
                }
                if (!bumped) {
                    break; // 无净产出为正的执行记录(理论防御;上游赤字已承担)
                }
            }
        }
    }

    // ===== 记账助手(M7 起内化于本类) =====

    /** 单次提取的结果:总提取量 + 其中来自网络实取的量. */
    private static final class ExtractOutcome {
        final long extracted;
        final long fromNetwork;

        ExtractOutcome(long extracted, long fromNetwork) {
            this.extracted = extracted;
            this.fromNetwork = fromNetwork;
        }
    }

    /**
     * 提取记账(网络优先):物理实取记 used;实取超过网络真实可用的部分由合成侧
     * 余额抵扣(credited/funded).
     * {@code networkOnly} 为 true 时(发射台键)只取网络真实库存,不吃合成侧余额——
     * 对齐原生 notRecursive"纯库存"语义.
     */
    private static ExtractOutcome extractCredited(IAEItemStack key, long amount, MECraftingInventory inv,
            Map<IAEItemStack, Long> synthetic, Map<IAEItemStack, Long> fundedByCredit,
            Map<IAEItemStack, Long> networkSourced, CraftingTreeNode rootNode, IActionSource src,
            boolean networkOnly) {
        long credited = synthetic.getOrDefault(key, 0L);
        long realAvailable = Math.max(0L, invAmount(inv, key) - credited);
        long extracted = extract(inv, key, networkOnly ? Math.min(amount, realAvailable) : amount, src);
        long fromNetwork = 0;
        if (extracted > 0) {
            fromNetwork = Math.min(extracted, realAvailable);
            long funded = extracted - fromNetwork;
            if (funded > 0) {
                synthetic.merge(key, -funded, Long::sum);
                fundedByCredit.merge(key, funded, Long::sum);
            }
            if (fromNetwork > 0) {
                networkSourced.merge(key, fromNetwork, Long::sum);
                IAEItemStack usedStack = key.copy();
                usedStack.setStackSize(fromNetwork);
                Ae2CraftingReflect.getNodeUsed(rootNode).add(usedStack);
            }
        }
        return new ExtractOutcome(extracted, fromNetwork);
    }

    /**
     * 每次合成返还表(canon 键 → 单次返还量):可合成样板以配方 getRemainingItems 为准
     * (覆盖 CrT reuse 等不消耗实现),其余回退 Item 容器 API.
     * 计划(LP 守恒)/校验(种子自举)/对账(重演回记)三层共用同一口径.
     */
    public static Map<IAEItemStack, Long> returnsPerCraft(ICraftingPatternDetails pattern) {
        Map<IAEItemStack, Long> remainingTable = RecipeRemainingResolver.remainingPerCraft(pattern);
        if (remainingTable != null) {
            Map<IAEItemStack, Long> out = new LinkedHashMap<>();
            for (Map.Entry<IAEItemStack, Long> entry : remainingTable.entrySet()) {
                if (entry.getValue() > 0) {
                    out.put(entry.getKey(), entry.getValue());
                }
            }
            return out;
        }
        Map<IAEItemStack, Long> out = new LinkedHashMap<>();
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null) {
                continue;
            }
            Item item = input.getItem();
            ItemStack def = input.getDefinition();
            if (item == null || !item.hasContainerItem(def)) {
                continue;
            }
            ItemStack containerStack = item.getContainerItem(def);
            if (containerStack.isEmpty()) {
                continue;
            }
            IAEItemStack containerAe = AEItemStack.fromItemStack(containerStack);
            if (containerAe == null) {
                continue;
            }
            out.merge(RecursiveCraftingHelper.canon(containerAe), input.getStackSize(), Long::sum);
        }
        return out;
    }

    /**
     * 返还物回记:消耗 N 份输入回记返还——自返还(返还物本身是本样板的输入,
     * 催化剂型)按 times-1 计(最后一份无法自供,保住种子提取);跨样板复用的
     * 返还物按全额 times 计.
     */
    private static void creditReturns(ICraftingPatternDetails pattern, long times,
            MECraftingInventory inv, Map<IAEItemStack, Long> synthetic, Set<IAEItemStack> containerKeys,
            IActionSource src) {
        for (Map.Entry<IAEItemStack, Long> entry : returnsPerCraft(pattern).entrySet()) {
            boolean selfReturn = containsInput(pattern, entry.getKey());
            long creditTimes = selfReturn ? times - 1 : times;
            if (creditTimes <= 0) {
                continue;
            }
            long credit = SaturatedMath.multiply(entry.getValue(), creditTimes);
            IAEItemStack creditStack = entry.getKey().copy();
            creditStack.setStackSize(credit);
            inv.injectItems(creditStack, Actionable.MODULATE, src);
            IAEItemStack containerKey = RecursiveCraftingHelper.canon(creditStack);
            synthetic.merge(containerKey, credit, Long::sum);
            containerKeys.add(containerKey);
        }
    }

    /** 容器键是否同时是本样板的输入（自返还/催化剂型判定）. */
    public static boolean containsInput(ICraftingPatternDetails pattern, IAEItemStack containerKey) {
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input != null && containerKey.equals(input)) {
                return true;
            }
        }
        return false;
    }

    /** 模拟库存中某 key 的当前总量（含网络余量与已注入的合成侧余额）. */
    private static long invAmount(MECraftingInventory inv, IAEItemStack key) {
        IAEItemStack entry = inv.getItemList().findPrecise(key);
        return entry == null ? 0L : entry.getStackSize();
    }

    /** 从模拟库存提取（精确匹配）,返回实际提取量. */
    private static long extract(MECraftingInventory inv, IAEItemStack key, long amount, IActionSource src) {
        IAEItemStack request = key.copy();
        request.setStackSize(amount);
        IAEItemStack result = inv.extractItems(request, Actionable.MODULATE, src);
        return result == null ? 0 : result.getStackSize();
    }
}
