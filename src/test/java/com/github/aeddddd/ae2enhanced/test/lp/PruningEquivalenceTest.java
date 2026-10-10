package com.github.aeddddd.ae2enhanced.test.lp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.MECraftingInventory;

import com.github.aeddddd.ae2enhanced.diag.plansnapshot.PlanSnapshot;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.FlowReconciler;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.PruneSettings;
import com.github.aeddddd.ae2enhanced.specialcrafting.PruneSettings.Mode;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.StableOrder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpModelBuilder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;
import com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.MiniWeb;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.ReusePatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 查询域剪枝等价性测试(任务 2:P1 需求门控 / P2 平凡单元闭式 / P3 单元划分 memo / P4 候选可达性)。
 *
 * <p>核心断言(方案 §验证):同一夹具/快照下 {@code prune=off} 与 {@code prune=full} 的</p>
 * <ul>
 * <li>赤字键集逐键相同(数量在管线自身噪声内:1e-6 相对,远低于 1e-3 赤字噪声地板);</li>
 * <li>FlowReconciler 后 missing 逐键相同(整数,精确);</li>
 * <li>整数化执行记录(样板签名+变体 → 次数)相同、总执行数相同、逐键净流量相同;</li>
 * <li>方向:lpUnits / iterations / degradedUnits 不增(剪枝只减 LP 工作量);</li>
 * <li>单样板单元 count 在闭式与 LP 之间逐位相等(P2 正确性关键断言);</li>
 * <li>cut 确定性:同夹具多次/注册序扰动下 digest 逐位相同(W4 连跑 3 次迭代数相同)。</li>
 * </ul>
 */
public class PruningEquivalenceTest {

    /** 赤字值对拍容差(管线自身噪声:实数传播 + 闭式/LP 的 1e-7 相对松弛泄漏). */
    private static final double DEFICIT_REL = 1e-6;
    private static final double DEFICIT_ABS = 1e-6;

    @BeforeAll
    public static void boot() {
        com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap.boot();
    }

    // ===== 对拍口径 =====

    /** 一次求解的扁平口径指纹(求解 + 对账重演,与生产同构). */
    private static final class Probe {
        final LpPlanOutcome outcome;
        /** 整数化执行次数:样板签名 + 变体键 → 次数(TreeMap:digest 稳定). */
        final Map<String, Long> execCounts;
        final long totalCrafts;
        /** 逐键净流量(canon 键 → 产出 − 消耗,整数化口径). */
        final Map<IAEItemStack, Long> netFlow;
        final Map<IAEItemStack, Long> missing;

        Probe(LpPlanOutcome outcome, Map<String, Long> execCounts, long totalCrafts,
                Map<IAEItemStack, Long> netFlow, Map<IAEItemStack, Long> missing) {
            this.outcome = outcome;
            this.execCounts = execCounts;
            this.totalCrafts = totalCrafts;
            this.netFlow = netFlow;
            this.missing = missing;
        }
    }

    private static Probe run(SimulationEnv env, IAEItemStack what, long target,
            Map<IAEItemStack, Long> stock, PruneSettings settings) {
        ICraftingGrid cc = env.craftingGrid();
        NetworkPatternIndex index = NetworkPatternIndex.of(cc);
        LpPlanOutcome out = CondensationPlanner.solve(cc, index, what, target, stock, settings);
        // 对账重演:库存快照复制一份(与 DeterministicOrderTest/生产同构)
        IItemList<IAEItemStack> snapshot = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList();
        for (IAEItemStack stack : env.networkStorage()) {
            snapshot.add(stack.copy());
        }
        MECraftingInventory inv = new MECraftingInventory(new MECraftingInventory(snapshot), true, false, true);
        CraftingTreeNode root = new CraftingTreeNode(cc, env.newLpJob(what), what.copy(), null, -1, 0);
        IActionSource src = mock(IActionSource.class);
        FlowReconciler.Reconciled rec = FlowReconciler.reconcile(cc, index, what, target, out, inv, root, src);

        Map<String, Long> execCounts = new TreeMap<>();
        Map<IAEItemStack, Long> netFlow = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<Execution, Long> entry : rec.times.entrySet()) {
            Execution exec = entry.getKey();
            long t = entry.getValue();
            if (t <= 0) {
                continue;
            }
            execCounts.merge(execSignature(exec), t, Long::sum);
            total += t;
            for (IAEItemStack o : exec.pattern.getCondensedOutputs()) {
                if (o != null && o.getStackSize() > 0) {
                    netFlow.merge(RecursiveCraftingHelper.canon(o), o.getStackSize() * t, Long::sum);
                }
            }
            for (Map.Entry<IAEItemStack, Long> in : SccLpModelBuilder
                    .condensedInputs(exec.pattern, exec.variantInputs).entrySet()) {
                netFlow.merge(in.getKey(), -in.getValue() * t, Long::sum);
            }
        }
        return new Probe(out, execCounts, total, netFlow, rec.missing);
    }

    /** 执行记录签名(样板内容签名 + 变体覆盖;跨 JVM/集合序稳定). */
    private static String execSignature(Execution exec) {
        StringBuilder sb = new StringBuilder(StableOrder.signatureOf(exec.pattern));
        sb.append("@v{");
        boolean first = true;
        for (Map.Entry<IAEItemStack, Long> e : StableOrder.sortedEntries(exec.variantInputs)) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getValue()).append('x').append(StableOrder.keyOf(e.getKey()));
        }
        return sb.append('}').toString();
    }

    /** off vs full 的等价性硬断言 + 方向断言. */
    private static void assertEquivalent(String label, Probe off, Probe full) {
        assertEquivalent(label, off, full, true);
    }

    /**
     * off vs full 的等价性断言 + 方向断言.
     *
     * @param strictExecs 是否要求整数化执行记录逐位相同。小/中型夹具必须为 true(全链路
     *                    逐位可复现); 真实快照(巨单元)为 false——巨单元的 LP 现在真的求解
     *                    (改造前恒 NUMERIC_FAILURE 降级, 两档"相等"是降级的副产品),
     *                    而退化面上的停滞微扰是<b>轨迹敏感</b>的: OFF 与 FULL 的单元
     *                    轨迹不同 ⇒ 微扰触发点不同 ⇒ 落点可能落在同一最优面内的不同顶点,
     *                    冗余执行记录随之不同。此时仍硬断言"交付面逐位相同"+"剪枝不下达
     *                    更多工作量"。
     */
    private static void assertEquivalent(String label, Probe off, Probe full, boolean strictExecs) {
        // 1) 赤字逐键相同(键集精确一致;数量在管线噪声内)
        assertEquals(keyList(off.outcome.deficits.keySet()), keyList(full.outcome.deficits.keySet()),
                label + " 赤字键集不一致");
        for (IAEItemStack key : off.outcome.deficits.keySet()) {
            double a = off.outcome.deficits.get(key);
            double b = full.outcome.deficits.get(key);
            assertEquals(a, b, Math.max(DEFICIT_ABS, DEFICIT_REL * Math.abs(a)),
                    label + " 赤字值不一致 key=" + StableOrder.keyOf(key));
        }
        // 2) 整数化缺料逐键相同(精确)
        assertEquals(keyList(off.missing.keySet()), keyList(full.missing.keySet()),
                label + " missing 键集不一致");
        for (IAEItemStack key : off.missing.keySet()) {
            assertEquals(off.missing.get(key), full.missing.get(key),
                    label + " missing 数量不一致 key=" + StableOrder.keyOf(key));
        }
        // 3) 整数化执行记录 / 总执行数 / 逐键净流量
        if (strictExecs) {
            assertEquals(off.execCounts, full.execCounts, label + " 整数化执行记录不一致");
            assertEquals(off.totalCrafts, full.totalCrafts, label + " 总执行数不一致");
            assertEquals(off.netFlow, full.netFlow, label + " 逐键净流量不一致");
        } else {
            // 松弛口径: 剪枝只会把冗余执行剪掉, 不得凭空新增工作量
            assertTrue(full.totalCrafts <= off.totalCrafts,
                    label + " 剪枝后总执行数上升: " + off.totalCrafts + " → " + full.totalCrafts);
            for (Map.Entry<String, Long> e : full.execCounts.entrySet()) {
                long base = off.execCounts.getOrDefault(e.getKey(), 0L);
                assertTrue(e.getValue() <= base,
                        label + " 剪枝后某样板执行数上升: " + base + " → " + e.getValue());
            }
        }
        // 4) 方向:剪枝只减少 LP 工作量,不得回退
        assertTrue(full.outcome.lpUnits <= off.outcome.lpUnits,
                label + " lpUnits 回退: " + off.outcome.lpUnits + " → " + full.outcome.lpUnits);
        assertTrue(full.outcome.iterations <= off.outcome.iterations,
                label + " 迭代数回退: " + off.outcome.iterations + " → " + full.outcome.iterations);
        assertTrue(full.outcome.degradedUnits <= off.outcome.degradedUnits,
                label + " 降级单元数回退: " + off.outcome.degradedUnits + " → "
                        + full.outcome.degradedUnits);
    }

    private static List<String> keyList(java.util.Collection<IAEItemStack> keys) {
        List<String> out = new ArrayList<>();
        for (IAEItemStack key : StableOrder.sortedKeys(keys)) {
            out.add(StableOrder.keyOf(key));
        }
        return out;
    }

    // ===== 夹具 =====

    /** 夹具句柄. */
    private static final class Case {
        final String name;
        final SimulationEnv env;
        final IAEItemStack root;
        final long target;
        final Map<IAEItemStack, Long> stock;

        Case(String name, SimulationEnv env, IAEItemStack root, long target,
                Map<IAEItemStack, Long> stock) {
            this.name = name;
            this.env = env;
            this.root = root;
            this.target = target;
            this.stock = stock;
        }
    }

    private static Map<IAEItemStack, Long> stockOf(SimulationEnv env) {
        Map<IAEItemStack, Long> stock = new LinkedHashMap<>();
        for (IAEItemStack stack : env.networkStorage()) {
            if (stack.getStackSize() > 0) {
                stock.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(), Long::sum);
            }
        }
        return stock;
    }

    private static IAEItemStack block(net.minecraft.block.Block b) {
        return appeng.util.item.AEItemStack.fromItemStack(new net.minecraft.item.ItemStack(b));
    }

    private static IAEItemStack mult(IAEItemStack template, long multiplier) {
        return SimulationEnv.mult(template, multiplier);
    }

    /** 夹具集:单样板链 / 种子自举 / 无种子降级 / 大 SCC 弦环 / 多来源环 / 替代候选 / 容器返还. */
    private static List<Supplier<Case>> fixtures() {
        List<Supplier<Case>> cases = new ArrayList<>();

        // 1) 跨单元链(原料赤字):R → A → B,请求 10,零库存
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack r = block(Blocks.SAND);
            IAEItemStack a = block(Blocks.STONE);
            IAEItemStack b = block(Blocks.COBBLESTONE);
            env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, r).build());
            env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
            return new Case("链-赤字", env, b, 10, stockOf(env));
        });

        // 2) 链 + 部分库存(单样板单元进入闭式,原料单元仍有赤字)
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack r = block(Blocks.SAND);
            IAEItemStack a = block(Blocks.STONE);
            IAEItemStack b = block(Blocks.COBBLESTONE);
            env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, r).build());
            env.addPattern(new ProcessingPatternBuilder(mult(b, 2)).addPreciseInput(1, a).build());
            env.addStoredItem(mult(r, 3));
            return new Case("链2:1-部分库存", env, b, 10, stockOf(env));
        });

        // 3) 有种子自增环(种子自举重解路径)
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack a = block(Blocks.STONE);
            env.addPattern(new ProcessingPatternBuilder(mult(a, 2)).addPreciseInput(1, a).build());
            env.addStoredItem(a.copy());
            return new Case("种子自增环", env, a, 10, stockOf(env));
        });

        // 4) 无种子自增环(LP 自举解被拦截 → 降级/截断路径)
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack a = block(Blocks.STONE);
            env.addPattern(new ProcessingPatternBuilder(mult(a, 2)).addPreciseInput(1, a).build());
            return new Case("无种子自增环-降级", env, a, 10, stockOf(env));
        });

        // 5) 迷你蛛网(50 键环 + 3 dup,种子 K25=1)
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            MiniWeb web = com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.buildMiniWeb(env);
            env.addStoredItem(web.keys[25].copy());
            return new Case("迷你蛛网", env, web.keys[0], 200, stockOf(env));
        });

        // 6) 48 键弦环网(单大 SCC)+ 种子,需求 1e9
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            int n = 48;
            for (int i = 0; i < n; i++) {
                env.addPattern(new ProcessingPatternBuilder(
                        com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key((i + 1) % n))
                        .addPreciseInput(1,
                                com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(i))
                        .build());
                for (int off : new int[] { 5, 17 }) {
                    env.addPattern(new ProcessingPatternBuilder(
                            com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key((i + off) % n))
                            .addPreciseInput(1,
                                    com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(i))
                            .build());
                }
            }
            env.addPattern(new ProcessingPatternBuilder(mult(
                    com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(24), 2))
                    .addPreciseInput(1,
                            com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(24))
                    .build());
            env.addStoredItem(com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(24).copy());
            return new Case("弦环网-大SCC", env,
                    com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(0), 1_000_000_000L,
                    stockOf(env));
        });

        // 7) 多来源环(L⇄C 1:1000 + P⇄L 互压),库存充足
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack l = block(Blocks.STONE);
            IAEItemStack c = block(Blocks.COBBLESTONE);
            IAEItemStack p = block(Blocks.DIRT);
            IAEItemStack x = block(Blocks.SAND);
            env.addPattern(new ProcessingPatternBuilder(mult(l, 1000)).addPreciseInput(1, c).build());
            env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, mult(l, 1000)).build());
            env.addPattern(new ProcessingPatternBuilder(p).addPreciseInput(1, l).build());
            env.addPattern(new ProcessingPatternBuilder(l).addPreciseInput(1, p).build());
            env.addPattern(new ProcessingPatternBuilder(mult(l, 500)).addPreciseInput(1, x).build());
            env.addStoredItem(mult(c, 900));
            env.addStoredItem(mult(l, 414));
            return new Case("多来源环", env, l, 100000, stockOf(env));
        });

        // 8) 替代候选在另一单元(修复前候选需求被静默丢弃)
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack a = block(Blocks.STONE);
            IAEItemStack alt = block(Blocks.DIRT);
            IAEItemStack y = block(Blocks.SAND);
            IAEItemStack b = block(Blocks.COBBLESTONE);
            env.addPattern(new ProcessingPatternBuilder(alt).addPreciseInput(1, y).build());
            env.addPattern(new ReusePatternBuilder(b)
                    .addPreciseInput(1, a)
                    .nativeStyleSubstituteList()
                    .substituteAlternatives(a, alt)
                    .canSubstitute(true)
                    .build());
            env.addStoredItem(mult(y, 100));
            return new Case("替代候选异单元", env, b, 5, stockOf(env));
        });

        // 9) 容器返还(自返还催化剂:1催化剂 + 1原料 → 1产物,催化剂循环)
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack catalyst = block(Blocks.STONE);
            IAEItemStack raw = block(Blocks.SAND);
            IAEItemStack out = block(Blocks.COBBLESTONE);
            env.addPattern(new ReusePatternBuilder(out)
                    .addPreciseInput(1, catalyst)
                    .addPreciseInput(1, raw)
                    .reused(catalyst)
                    .canSubstitute(false)
                    .build());
            env.addStoredItem(raw.copy());
            env.addStoredItem(catalyst.copy());
            return new Case("容器返还", env, out, 8, stockOf(env));
        });

        // 10) 库存全覆盖(需求门控命中:lpUnits 应下降,无执行)
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack r = block(Blocks.SAND);
            IAEItemStack a = block(Blocks.STONE);
            IAEItemStack b = block(Blocks.COBBLESTONE);
            env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, r).build());
            env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
            env.addStoredItem(mult(a, 100));
            env.addStoredItem(mult(r, 100));
            return new Case("库存全覆盖-门控", env, b, 5, stockOf(env));
        });

        // 11) 微额需求(1e-7 与 1e-6 之间):边界单元走闭式且 reps=0(x≡0),
        //     不得因无需求行而空指针(闭式适用条件外的最短路径回归)
        cases.add(() -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack z = block(Blocks.GRAVEL);
            IAEItemStack y = block(Blocks.SAND);
            IAEItemStack a = block(Blocks.STONE);
            IAEItemStack b = block(Blocks.COBBLESTONE);
            env.addPattern(new ProcessingPatternBuilder(y).addPreciseInput(1, z).build());
            env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, y).build());
            env.addPattern(new ProcessingPatternBuilder(mult(b, 1_000_000))
                    .addPreciseInput(1, a).build());
            return new Case("微额需求-闭式零解", env, b, 1, stockOf(env));
        });

        return cases;
    }

    // ===== 1) 夹具等价性 =====

    @Test
    public void fixtureEquivalence() {
        int checked = 0;
        int gatedReduced = 0;
        int iterReduced = 0;
        for (Supplier<Case> supplier : fixtures()) {
            Case c = supplier.get();
            Probe off = run(c.env, c.root, c.target, c.stock, PruneSettings.OFF);
            Probe full = run(c.env, c.root, c.target, c.stock,
                    new PruneSettings(Mode.FULL, false, false));
            assertEquivalent(c.name, off, full);
            if (full.outcome.lpUnits < off.outcome.lpUnits) {
                gatedReduced++;
            }
            if (full.outcome.iterations < off.outcome.iterations) {
                iterReduced++;
            }
            System.out.printf("[PRUNE-EQ] %s: 单元 %d→%d lpUnits %d→%d 迭代 %d→%d 降级 %d→%d "
                            + "总执行 %d→%d 缺料 %d→%d%n",
                    c.name, off.outcome.units, full.outcome.units, off.outcome.lpUnits,
                    full.outcome.lpUnits, off.outcome.iterations, full.outcome.iterations,
                    off.outcome.degradedUnits, full.outcome.degradedUnits, off.totalCrafts,
                    full.totalCrafts, off.missing.size(), full.missing.size());
            checked++;
        }
        assertTrue(checked >= 11, "夹具覆盖不足: " + checked);
        assertTrue(gatedReduced >= 1, "需求门控未在任何夹具上生效");
        assertTrue(iterReduced >= 3, "平凡单元闭式未在夹具上生效(迭代下降仅 " + iterReduced + " 个)");
    }

    /** demand 档位(仅 P1)也必须与 off 等价. */
    @Test
    public void demandGateOnlyEquivalence() {
        for (Supplier<Case> supplier : fixtures()) {
            Case c = supplier.get();
            Probe off = run(c.env, c.root, c.target, c.stock, PruneSettings.OFF);
            Probe demand = run(c.env, c.root, c.target, c.stock,
                    new PruneSettings(Mode.DEMAND, false, false));
            assertEquivalent(c.name + "(demand)", off, demand);
        }
    }

    // ===== 2) 单样板单元闭式 vs LP 逐位相等 =====

    /**
     * P2 关键断言:单样板单元(闭式适用)的 count 与 LP 角点逐位相等——
     * 含阶段③禁行松弛泄漏(1e-7/最紧行净系数)的同口径复现。
     *
     * <p>对拍后端固定为精确有理数(LP 后端选择在 solve 入口读系统属性):
     * 浮点对偶单纯形路径受 Ruiz 行列均衡的末位舍入影响,同一模型在 auto/dual 下
     * 可差 1–2 ULP(实测 2A→1B 请求 10:精确 4.99999995 / 浮点 4.9999999499999985),
     * 这是求解器自身口径而非闭式口径;闭式与<b>精确路径的角点</b>逐位一致,
     * 与浮点路径的整数化口径(⌈x−1e-6⌉)见下方第二段断言。</p>
     */
    @Test
    public void closedFormCountBitIdenticalToLp() {
        String previousSolver = System.getProperty("ae2e.lpSolver");
        System.setProperty("ae2e.lpSolver", "exact");
        try {
            // 三种列净系数(1/2/3)+ 编码物由另一单元供给(⇒ 无内化原料行,闭式适用)
            for (long perCraft : new long[] { 1, 2, 3 }) {
                SimulationEnv env = new SimulationEnv();
                IAEItemStack r = block(Blocks.SAND);
                IAEItemStack a = block(Blocks.STONE);
                IAEItemStack b = block(Blocks.COBBLESTONE);
                env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, r).build());
                ICraftingPatternDetails ab = env.addPattern(
                        new ProcessingPatternBuilder(mult(b, perCraft)).addPreciseInput(1, a).build());
                Map<IAEItemStack, Long> stock = stockOf(env);
                Probe off = run(env, b, 10, stock, PruneSettings.OFF);
                Probe full = run(env, b, 10, stock, new PruneSettings(Mode.FULL, false, false));
                double offCount = execOf(off.outcome, ab);
                double fullCount = execOf(full.outcome, ab);
                assertEquals(Double.doubleToLongBits(offCount), Double.doubleToLongBits(fullCount),
                        "单样板单元 count 非逐位相等(系数 " + perCraft + "): LP=" + offCount
                                + "(" + Long.toHexString(Double.doubleToLongBits(offCount)) + ") 闭式="
                                + fullCount + "(" + Long.toHexString(Double.doubleToLongBits(fullCount))
                                + ")");
                assertTrue(offCount > 0, "夹具应产生正执行数");
            }
        } finally {
            if (previousSolver == null) {
                System.clearProperty("ae2e.lpSolver");
            } else {
                System.setProperty("ae2e.lpSolver", previousSolver);
            }
        }
    }

    /**
     * 默认后端(auto)下的交付面等价:闭式与 LP 的 count 整数化口径(⌈x−1e-6⌉)逐位相同
     * (浮点路径末位噪声不改变整数化)。
     */
    @Test
    public void closedFormIntegerizationIdenticalUnderAutoBackend() {
        for (long perCraft : new long[] { 1, 2, 3, 5 }) {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack r = block(Blocks.SAND);
            IAEItemStack a = block(Blocks.STONE);
            IAEItemStack b = block(Blocks.COBBLESTONE);
            env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, r).build());
            ICraftingPatternDetails ab = env.addPattern(
                    new ProcessingPatternBuilder(mult(b, perCraft)).addPreciseInput(1, a).build());
            Map<IAEItemStack, Long> stock = stockOf(env);
            Probe off = run(env, b, 10, stock, PruneSettings.OFF);
            Probe full = run(env, b, 10, stock, new PruneSettings(Mode.FULL, false, false));
            long offTimes = integerize(execOf(off.outcome, ab));
            long fullTimes = integerize(execOf(full.outcome, ab));
            assertEquals(offTimes, fullTimes,
                    "整数化执行次数不一致(系数 " + perCraft + "): 精确=" + execOf(off.outcome, ab)
                            + " 闭式=" + execOf(full.outcome, ab));
            assertTrue(offTimes > 0, "夹具应产生正执行数");
        }
    }

    /** 整数化口径(与 FlowReconciler.toLong 一致:⌈count − 1e-6⌉). */
    private static long integerize(double count) {
        if (count <= 1e-6) {
            return 0;
        }
        return (long) Math.ceil(count - 1e-6);
    }

    /** 单样板单元 count 汇总(浮点口径). */
    private static double execOf(LpPlanOutcome out, ICraftingPatternDetails pattern) {
        double total = 0;
        for (Execution e : out.executions) {
            if (e.pattern == pattern) {
                total += e.count;
            }
        }
        return total;
    }

    // ===== 3) pruneTrace 审计 =====

    /**
     * 审计:闭式命中数 + 走 LP 数 = 门控后活跃单元数(= lpUnits);
     * 快速路径 + 门控跳过 + lpUnits = 可达单元数。
     */
    @Test
    public void pruneTraceAudit() throws Exception {
        Case c = fixtures().get(1).get();
        String[] lines = runCapturingTrace(c);
        int units = -1;
        int fastPath = -1;
        int gated = -1;
        int closed = -1;
        int lpOnly = -1;
        int lpUnits = -1;
        for (String line : lines) {
            if (!line.contains("[PRUNE] 审计:")) {
                continue;
            }
            System.out.println("[PRUNE-EQ] " + line.trim());
            units = intField(line, "单元=");
            fastPath = intField(line, "快速路径=");
            gated = intField(line, "门控跳过=");
            closed = intField(line, "闭式=");
            lpOnly = intField(line, "走LP=");
            lpUnits = intField(line, "LP单元=");
        }
        assertTrue(units > 0, "审计行缺失: " + String.join("\n", lines));
        assertEquals(units, fastPath + gated + lpUnits, "可达单元数分解不闭合");
        assertEquals(lpUnits, closed + lpOnly, "闭式命中 + 走 LP ≠ 门控后活跃单元数");
        assertTrue(closed > 0, "夹具应命中闭式");
    }

    private static int intField(String line, String prefix) {
        int at = line.indexOf(prefix);
        assertTrue(at >= 0, "缺字段 " + prefix + ": " + line);
        int i = at + prefix.length();
        int j = i;
        while (j < line.length() && (Character.isDigit(line.charAt(j)))) {
            j++;
        }
        return Integer.parseInt(line.substring(i, j));
    }

    /** 以 trace 档位求解并捕获 stdout(审计行解析用). */
    private static String[] runCapturingTrace(Case c) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream old = System.out;
        try {
            System.setOut(new PrintStream(buf, true, "UTF-8"));
            CondensationPlanner.solve(c.env.craftingGrid(), NetworkPatternIndex.of(c.env.craftingGrid()),
                    c.root, c.target, c.stock, new PruneSettings(Mode.FULL, true, false));
        } finally {
            System.setOut(old);
        }
        return buf.toString("UTF-8").split("\\R");
    }

    // ===== 4) 真实快照等价性 =====

    /**
     * 真实快照(5319 样板)上的 off vs full 等价性:根请求 + 若干确定性采样请求。
     * 这是 P1/P2 在真实数据(巨单元 + 上千平凡单元)上的主验收。
     */
    @Test
    public void snapshotEquivalence() throws Exception {
        File file = latestSnapshot();
        Assumptions.assumeTrue(file != null && file.isFile(),
                "无计划快照(放 research/plansnapshot/ 或用 -Dae2e.planreplay=),跳过");
        PlanSnapshot snap = PlanSnapshot.load(file);
        com.github.aeddddd.ae2enhanced.diag.DiagSwitch.setOverride(
                com.github.aeddddd.ae2enhanced.diag.DiagSwitch.SPECIAL_CRAFTING, false);
        Map<String, Item> items = new HashMap<>();
        SnapshotPlanSupport.envOf(snap, items); // AE 初始化 + 全量 Item 预建(全局共享实例)
        SnapshotPlanSupport.toAe(snap.root, items);

        List<PlanSnapshot.StackRef> requests = new ArrayList<>();
        requests.add(snap.root);
        List<PlanSnapshot.StackRef> pool = new ArrayList<>();
        for (PlanSnapshot.PatternEntry e : snap.patterns) {
            pool.addAll(e.outputs);
        }
        java.util.Random rng = new java.util.Random(20261010L);
        long[] counts = { 1, 64, 4096, 262144 };
        for (int i = 0; i < 8 && !pool.isEmpty(); i++) {
            PlanSnapshot.StackRef base = pool.get(rng.nextInt(pool.size()));
            PlanSnapshot.StackRef req = new PlanSnapshot.StackRef();
            req.id = base.id;
            req.meta = base.meta;
            req.nbt = base.nbt;
            req.count = counts[i % counts.length];
            requests.add(req);
        }

        long offIter = 0;
        long fullIter = 0;
        int offLp = 0;
        int fullLp = 0;
        long offWall = 0;
        long fullWall = 0;
        for (PlanSnapshot.StackRef req : requests) {
            IAEItemStack root = SnapshotPlanSupport.toAe(req, items);
            long target = Math.max(1, req.count);
            SimulationEnv env = SnapshotPlanSupport.envOf(snap, items);
            Map<IAEItemStack, Long> stock = SnapshotPlanSupport.stockOf(env);
            Probe off = run(env, root, target, stock, PruneSettings.OFF);
            Probe full = run(env, root, target, stock, new PruneSettings(Mode.FULL, false, false));
            assertEquivalent("快照 " + req.id + "×" + target, off, full, false);
            offIter += off.outcome.iterations;
            fullIter += full.outcome.iterations;
            offLp += off.outcome.lpUnits;
            fullLp += full.outcome.lpUnits;
            offWall += off.outcome.wallMs;
            fullWall += full.outcome.wallMs;
        }
        System.out.printf("[PRUNE-EQ] 快照合计: lpUnits %d→%d 迭代 %d→%d 求解墙钟 %dms→%dms 请求 %d%n",
                offLp, fullLp, offIter, fullIter, offWall, fullWall, requests.size());
        assertTrue(fullLp < offLp, "快照上 lpUnits 未下降");
        assertTrue(fullIter < offIter, "快照上迭代数未下降");
    }

    private static File latestSnapshot() {
        File dir = new File("research/plansnapshot");
        File[] files = dir.isDirectory()
                ? dir.listFiles((d, name) -> name.startsWith("plansnapshot-"))
                : null;
        if (files == null || files.length == 0) {
            return null;
        }
        java.util.Arrays.sort(files);
        return files[files.length - 1];
    }

    // ===== 5) 确定性(W4 连跑 3 次 + 注册序扰动) =====

    /** W4 夹具(千键弦环网):full 档连跑 3 次迭代数与 digest 逐位相同,且与 off 档同值
     * (巨单元不被剪枝,剪枝不得扰动其 LP 轨迹)。 */
    @Test
    public void w4ThreeRunsIdentical() {
        Probe off = null;
        for (int run = 0; run < 4; run++) {
            Mode mode = run == 0 ? Mode.OFF : Mode.FULL;
            SimulationEnv env = new SimulationEnv();
            buildW4(env);
            Map<IAEItemStack, Long> stock = new LinkedHashMap<>();
            stock.put(RecursiveCraftingHelper.canon(
                    com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(500)), 1L);
            Probe probe = run(env, com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(0),
                    1_000_000_000_000L, stock, new PruneSettings(mode, false, false));
            System.out.printf("[PRUNE-EQ] W4 %s(第 %d 次): units=%d lpUnits=%d iterations=%d 降级=%d%n",
                    mode, run == 0 ? 1 : run, probe.outcome.units, probe.outcome.lpUnits,
                    probe.outcome.iterations, probe.outcome.degradedUnits);
            if (off == null) {
                off = probe;
                continue;
            }
            assertEquals(off.outcome.iterations, probe.outcome.iterations,
                    "W4 " + mode + " 第 " + run + " 次迭代数漂移");
            assertEquals(digestOf(off), digestOf(probe), "W4 " + mode + " 第 " + run + " 次 digest 漂移");
        }
    }

    /** 注册序扰动下 digest 不变(跨 JVM 稳定的前提:列/行/样板序均走 StableOrder). */
    @Test
    public void registrationOrderPerturbationKeepsDigest() {
        for (Mode mode : new Mode[] { Mode.OFF, Mode.FULL }) {
            String base = miniWebDigest(false, mode);
            String reversed = miniWebDigest(true, mode);
            assertEquals(base, reversed, mode + " 注册序扰动改变了 digest");
        }
    }

    private static String miniWebDigest(boolean reverse, Mode mode) {
        SimulationEnv src = new SimulationEnv();
        MiniWeb web = com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.buildMiniWeb(src);
        SimulationEnv env = new SimulationEnv();
        if (reverse) {
            for (int i = web.ring.length - 1; i >= 0; i--) {
                env.addPattern(web.ring[i]);
            }
            env.addPattern(web.dup45);
            env.addPattern(web.dup25);
            env.addPattern(web.dup5);
        } else {
            for (ICraftingPatternDetails p : web.ring) {
                env.addPattern(p);
            }
            env.addPattern(web.dup5);
            env.addPattern(web.dup25);
            env.addPattern(web.dup45);
        }
        env.addStoredItem(web.keys[25].copy());
        Probe probe = run(env, web.keys[0], 200, stockOf(env), new PruneSettings(mode, false, false));
        return digestOf(probe);
    }

    /** W4 夹具:1000 键主环 + 一组弦 + 自增源(与 MassiveKindCycleStressTest 同构). */
    private static void buildW4(SimulationEnv env) {
        int n = 1000;
        for (int i = 0; i < n; i++) {
            env.addPattern(new ProcessingPatternBuilder(
                    com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key((i + 1) % n))
                    .addPreciseInput(1, com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(i))
                    .build());
            env.addPattern(new ProcessingPatternBuilder(
                    com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key((i + 133) % n))
                    .addPreciseInput(1, com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(i))
                    .build());
        }
        env.addPattern(new ProcessingPatternBuilder(mult(
                com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(500), 2))
                .addPreciseInput(1, com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key(500))
                .build());
    }

    /** 计划指纹(集合序全部走 StableOrder ⇒ 跨 JVM 可复现). */
    private static String digestOf(Probe probe) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("mode=").append(probe.outcome.allOptimal ? "OPTIMAL" : "DEGRADED")
                .append(" units=").append(probe.outcome.units)
                .append(" lpUnits=").append(probe.outcome.lpUnits)
                .append(" iters=").append(probe.outcome.iterations)
                .append(" degraded=").append(probe.outcome.degradedUnits)
                .append(" crafts=").append(probe.totalCrafts)
                .append(" execs=").append(probe.execCounts)
                .append(" missing=").append(keyList(probe.missing.keySet()))
                .append(" deficits=").append(keyList(probe.outcome.deficits.keySet()));
        return sb.toString();
    }
}
