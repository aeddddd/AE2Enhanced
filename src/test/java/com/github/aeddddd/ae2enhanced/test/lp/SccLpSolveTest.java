package com.github.aeddddd.ae2enhanced.test.lp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpResult;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.SccSolution;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.SimulationEnv;

/**
 * SCC → LP 模型构建器/求解编排合成夹具（方案 L §6.2）.
 * <p>6 类已知最优解的合成 SCC + 矿词变体展开(D1)用例;断言赤字键集与
 * 手工推导一致、阶段②计数无冗余放大.</p>
 * <p><b>注意</b>:LP 为实数松弛,自增环在零种子下允许"无种子自举"
 * （守恒方程不感知启动可行性）——种子校验是 M4 职责,本层夹具按 LP 语义断言.</p>
 */
public class SccLpSolveTest {

    private static final double EPS = 1e-4;

    /** ① 单键 dup(1A→2A):种子 1 + 9 次净增 = 10,赤字 0. */
    @Test
    public void singleKeyDup() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        ICraftingPatternDetails dup = env.addPattern(new ProcessingPatternBuilder(mult(a, 2))
                .addPreciseInput(1, a).build());
        Map<IAEItemStack, Long> stock = stockOf(a, 1);
        SccSolution sol = solve(env, a, stock, demandsOf(a, 10));
        assertEquals(LpResult.Status.OPTIMAL, sol.status);
        assertTrue(sol.deficits.isEmpty(), "dup 应满足全部需求: " + sol.deficits);
        assertEquals(9.0, execOf(sol, dup), EPS);
        assertEquals(9.0, totalExec(sol), EPS);
    }

    /** ② 双键增殖环(1A→2B, 1B→1A,环增益 2):x1=7, x2=14,赤字 0. */
    @Test
    public void twoKeyProductiveCycle() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        ICraftingPatternDetails p1 = env.addPattern(new ProcessingPatternBuilder(mult(b, 2))
                .addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(a)
                .addPreciseInput(1, b).build());
        SccSolution sol = solve(env, a, stockOf(a, 1), demandsOf(a, 8));
        assertEquals(LpResult.Status.OPTIMAL, sol.status);
        assertTrue(sol.deficits.isEmpty(), "增殖环应满足全部需求: " + sol.deficits);
        assertEquals(7.0, execOf(sol, p1), EPS);
        assertEquals(14.0, execOf(sol, p2), EPS);
    }

    /**
     * ③ θ 形共享并集:环 A↔B 与 B↔C 共享枢纽 B.
     * 需求 C=5,种子 A=5:最省路径 A→B→C(x1=5, x3=5),冗余环向 x2=x4=0.
     */
    @Test
    public void thetaSharedUnion() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        IAEItemStack c = block(Blocks.DIRT);
        ICraftingPatternDetails p1 = env.addPattern(new ProcessingPatternBuilder(b)
                .addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(a)
                .addPreciseInput(1, b).build());
        ICraftingPatternDetails p3 = env.addPattern(new ProcessingPatternBuilder(c)
                .addPreciseInput(1, b).build());
        ICraftingPatternDetails p4 = env.addPattern(new ProcessingPatternBuilder(b)
                .addPreciseInput(1, c).build());
        SccSolution sol = solve(env, c, stockOf(a, 5), demandsOf(c, 5));
        assertEquals(LpResult.Status.OPTIMAL, sol.status);
        assertTrue(sol.deficits.isEmpty(), "θ 形并集应满足全部需求: " + sol.deficits);
        assertEquals(5.0, execOf(sol, p1), EPS);
        assertEquals(5.0, execOf(sol, p3), EPS);
        assertEquals(0.0, execOf(sol, p2), EPS);
        assertEquals(0.0, execOf(sol, p4), EPS);
    }

    /**
     * ④ 催化环(1A→1X+1B, 1B→1A),跨分量样板承诺语义:
     * 下游分量(单键 {X})先解 p1=10,环外折算需求 A=10;
     * 上游环分量 {A,B} 以承诺 p1=10(产出 B 折算库存)再解:x2=9(末次 p1 后无需再生 A,
     * B 盈余 1),赤字 0.
     */
    @Test
    public void catalyticCycle() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack b = block(Blocks.DIRT);
        ICraftingPatternDetails p1 = env.addPattern(new ProcessingPatternBuilder(x, b)
                .addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(a)
                .addPreciseInput(1, b).build());

        // 下游:需求键 X 所在分量
        SccSolution downstream = solve(env, x, stockOf(), demandsOf(x, 10));
        assertEquals(LpResult.Status.OPTIMAL, downstream.status);
        assertEquals(10.0, execOf(downstream, p1), EPS);
        assertEquals(10.0, downstream.externalDemands.get(RecursiveCraftingHelper.canon(a)), EPS);

        // 上游:{A,B} 环分量,承诺 p1=10(其 B 产出折算库存,A 消费已折算为 demand)
        Map<ICraftingPatternDetails, Double> committed = new HashMap<>();
        committed.put(p1, 10.0);
        SccSolution sol = solve(env, a, stockOf(a, 1), demandsOf(a, 10), committed);
        assertEquals(LpResult.Status.OPTIMAL, sol.status);
        assertTrue(sol.deficits.isEmpty(), "催化环应满足全部需求: " + sol.deficits);
        assertEquals(9.0, execOf(sol, p2), EPS);
        assertEquals(0.0, execOf(sol, p1), EPS);
    }

    /** ⑤ 耗散环(2A→1A,不可解):环不运行,赤字 = 需求 − 库存 = 5. */
    @Test
    public void dissipativeCycleDeficit() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        ICraftingPatternDetails lossy = env.addPattern(new ProcessingPatternBuilder(a)
                .addPreciseInput(2, a).build());
        SccSolution sol = solve(env, a, stockOf(a, 5), demandsOf(a, 10));
        assertEquals(LpResult.Status.OPTIMAL, sol.status);
        assertEquals(1, sol.deficits.size(), "耗散环唯一赤字键: " + sol.deficits);
        assertEquals(5.0, sol.deficits.get(RecursiveCraftingHelper.canon(a)), EPS);
        assertEquals(0.0, execOf(sol, lossy), EPS);
    }

    /**
     * ⑥ 迷你蛛网:50 键有向环(1K_i→1K_{i+1}) + 3 个自增环竞争(dup@5/25/45).
     * 需求 K0=100,种子 K25=1.
     * <p>LP 语义最优:dup@45 无种子自举 x=100(守恒方程允许),经 45→49→0 五跳交付,
     * 总执行 600;种子校验(M4)将修正为物理可执行解,本层按 LP 语义断言.</p>
     */
    @Test
    public void miniWebCompetingGainLoops() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack[] keys = new IAEItemStack[50];
        Block[] palette = { Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.PLANKS, Blocks.SAND,
                Blocks.GRAVEL, Blocks.LOG, Blocks.GLASS, Blocks.CLAY, Blocks.BRICK_BLOCK };
        for (int i = 0; i < keys.length; i++) {
            keys[i] = AEItemStack.fromItemStack(new ItemStack(palette[i % palette.length], 1, i / palette.length));
        }
        ICraftingPatternDetails[] ring = new ICraftingPatternDetails[50];
        for (int i = 0; i < 50; i++) {
            ring[i] = env.addPattern(new ProcessingPatternBuilder(keys[(i + 1) % 50])
                    .addPreciseInput(1, keys[i]).build());
        }
        ICraftingPatternDetails dup5 = env.addPattern(new ProcessingPatternBuilder(mult(keys[5], 2))
                .addPreciseInput(1, keys[5]).build());
        ICraftingPatternDetails dup25 = env.addPattern(new ProcessingPatternBuilder(mult(keys[25], 2))
                .addPreciseInput(1, keys[25]).build());
        ICraftingPatternDetails dup45 = env.addPattern(new ProcessingPatternBuilder(mult(keys[45], 2))
                .addPreciseInput(1, keys[45]).build());

        SccSolution sol = solve(env, keys[0], stockOf(keys[25], 1), demandsOf(keys[0], 100));
        assertEquals(LpResult.Status.OPTIMAL, sol.status);
        assertTrue(sol.deficits.isEmpty(), "蛛网净增环应满足全部需求: " + sol.deficits);
        // LP 最优:dup@45 自举 100,环流仅 45→49→0 五跳各 100
        assertEquals(100.0, execOf(sol, dup45), EPS);
        assertEquals(0.0, execOf(sol, dup25), EPS);
        assertEquals(0.0, execOf(sol, dup5), EPS);
        for (int i = 45; i <= 49; i++) {
            assertEquals(100.0, execOf(sol, ring[i]), EPS, "交付环流 p" + i);
        }
        for (int i = 25; i <= 44; i++) {
            assertEquals(0.0, execOf(sol, ring[i]), EPS, "冗余环流 p" + i);
        }
        assertEquals(600.0, totalExec(sol), EPS);
    }

    /**
     * ⑦ 矿词替代变体展开(D1):1A→2A 可替代(候选 {A, A2}).
     * 种子 A2=5 在环外(无样板产出),变体路径 x_var=5(消耗 5×A2 → 10 A),赤字 0,
     * 环外折算 A2=5 传播给上游.
     */
    @Test
    public void oreDictVariantExpansion() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack a2 = block(Blocks.COBBLESTONE);
        ICraftingPatternDetails subst = env.addPattern(
                substitutePattern(a, new IAEItemStack[] { mult(a, 2) }, a2));
        SccSolution sol = solve(env, a, stockOf(), demandsOf(a, 10));
        assertEquals(LpResult.Status.OPTIMAL, sol.status);
        assertTrue(sol.deficits.isEmpty(), "变体路径应满足全部需求: " + sol.deficits);
        // 阶段②:变体 5 次(环外 A2 不计入本分量约束)优于编码自举 10 次
        assertEquals(5.0, variantExecOf(sol, subst, a2), EPS);
        assertEquals(5.0, sol.externalDemands.get(RecursiveCraftingHelper.canon(a2)), EPS);
    }

    // ===== 工具 =====

    private static SccSolution solve(SimulationEnv env, IAEItemStack sccKey,
            Map<IAEItemStack, Long> stock, Map<IAEItemStack, Double> demands) {
        return solve(env, sccKey, stock, demands, java.util.Collections.emptyMap());
    }

    private static SccSolution solve(SimulationEnv env, IAEItemStack sccKey,
            Map<IAEItemStack, Long> stock, Map<IAEItemStack, Double> demands,
            Map<ICraftingPatternDetails, Double> committed) {
        NetworkPatternIndex index = NetworkPatternIndex.of(env.craftingGrid());
        Integer sccId = index.sccIdOf(RecursiveCraftingHelper.canon(sccKey));
        assertTrue(sccId != null, "键不在键图中: " + sccKey);
        return SccLpSolve.solve(env.craftingGrid(), index, index.keysOfScc(sccId), stock, demands,
                java.util.Collections.emptyMap(), committed);
    }

    private static double execOf(SccSolution sol, ICraftingPatternDetails pattern) {
        double total = 0;
        for (SccLpSolve.Execution e : sol.executions) {
            if (e.pattern == pattern) {
                total += e.count;
            }
        }
        return total;
    }

    private static double variantExecOf(SccSolution sol, ICraftingPatternDetails pattern,
            IAEItemStack variantKey) {
        double total = 0;
        for (SccLpSolve.Execution e : sol.executions) {
            if (e.pattern == pattern && e.variantInputs.containsKey(RecursiveCraftingHelper.canon(variantKey))) {
                total += e.count;
            }
        }
        return total;
    }

    private static double totalExec(SccSolution sol) {
        double total = 0;
        for (SccLpSolve.Execution e : sol.executions) {
            total += e.count;
        }
        return total;
    }

    private static Map<IAEItemStack, Long> stockOf(Object... kv) {
        Map<IAEItemStack, Long> stock = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            stock.put(RecursiveCraftingHelper.canon((IAEItemStack) kv[i]), ((Number) kv[i + 1]).longValue());
        }
        return stock;
    }

    private static Map<IAEItemStack, Double> demandsOf(Object... kv) {
        Map<IAEItemStack, Double> demands = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            demands.put(RecursiveCraftingHelper.canon((IAEItemStack) kv[i]), ((Number) kv[i + 1]).doubleValue());
        }
        return demands;
    }

    private static IAEItemStack block(Block block) {
        return AEItemStack.fromItemStack(new ItemStack(block));
    }

    private static IAEItemStack mult(IAEItemStack template, long multiplier) {
        IAEItemStack copy = template.copy();
        copy.setStackSize(template.getStackSize() * multiplier);
        return copy;
    }

    /**
     * 可替代工作台样板(⑦ 变体展开用):isCraftable/canSubstitute = true,
     * 槽 0 候选 = [encoded, alternatives...].
     */
    private static ICraftingPatternDetails substitutePattern(IAEItemStack encodedInput,
            IAEItemStack[] outputs, IAEItemStack... alternatives) {
        IAEItemStack[] slotInputs = new IAEItemStack[] { encodedInput };
        java.util.List<IAEItemStack> subs = new java.util.ArrayList<>();
        subs.add(encodedInput);
        subs.addAll(java.util.Arrays.asList(alternatives));
        return new ICraftingPatternDetails() {
            @Override
            public ItemStack getPattern() {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean isValidItemForSlot(int slot, ItemStack stack, net.minecraft.world.World world) {
                return false;
            }

            @Override
            public boolean isCraftable() {
                return true;
            }

            @Override
            public IAEItemStack[] getInputs() {
                return slotInputs.clone();
            }

            @Override
            public IAEItemStack[] getCondensedInputs() {
                return slotInputs.clone();
            }

            @Override
            public IAEItemStack[] getCondensedOutputs() {
                return outputs.clone();
            }

            @Override
            public IAEItemStack[] getOutputs() {
                return outputs.clone();
            }

            @Override
            public boolean canSubstitute() {
                return true;
            }

            @Override
            public java.util.List<IAEItemStack> getSubstituteInputs(int slot) {
                return slot == 0 ? new java.util.ArrayList<>(subs) : java.util.Collections.emptyList();
            }

            @Override
            public ItemStack getOutput(net.minecraft.inventory.InventoryCrafting ic, net.minecraft.world.World w) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public void setPriority(int p) {
            }
        };
    }
}
