package com.github.aeddddd.ae2enhanced.test.lp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.SimulationEnv;

/**
 * 冷凝分层驱动器（M3）集成测试.
 * <p>覆盖:跨单元需求传播/输出共享合并/发射台/迷你蛛网/共享原料扇入.
 * 断言 LP 语义解（含"无种子自举"松弛,M4 校验前）.</p>
 */
public class CondensationPlannerTest {

    private static final double EPS = 1e-4;

    /** 跨单元链:X←A(A 为原料).需求 X=10,库存 A=4 → pX=10,赤字 A=6. */
    @Test
    public void crossUnitChainPropagation() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack x = block(Blocks.STONE);
        IAEItemStack a = block(Blocks.COBBLESTONE);
        ICraftingPatternDetails px = env.addPattern(new ProcessingPatternBuilder(x)
                .addPreciseInput(1, a).build());
        LpPlanOutcome out = solve(env, x, 10, stockOf(a, 4));
        assertTrue(out.allOptimal);
        assertEquals(1, out.deficits.size(), "唯一赤字键 A: " + out.deficits);
        assertEquals(6.0, out.deficits.get(canon(a)), EPS);
        assertEquals(10.0, execOf(out, px), EPS);
    }

    /**
     * 输出共享合并:催化样板 1A→1X+1B 的输出 X/B 分属不同 SCC,
     * 并查集合并后 {X,A,B} 单单元一次 LP 求解:p1=10, p2=9,无赤字.
     */
    @Test
    public void outputSharingMergedUnit() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack b = block(Blocks.DIRT);
        ICraftingPatternDetails p1 = env.addPattern(new ProcessingPatternBuilder(x, b)
                .addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env.addPattern(new ProcessingPatternBuilder(a)
                .addPreciseInput(1, b).build());
        LpPlanOutcome out = solve(env, x, 10, stockOf(a, 1));
        assertTrue(out.allOptimal);
        assertTrue(out.deficits.isEmpty(), "合并单元应满足全部需求: " + out.deficits);
        assertEquals(10.0, execOf(out, p1), EPS);
        assertEquals(9.0, execOf(out, p2), EPS);
    }

    /** 发射台键:需求 100 全由发射台供给,无执行、无赤字. */
    @Test
    public void emitterKeyCovered() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack e = block(Blocks.STONE);
        env.addEmitable(e);
        LpPlanOutcome out = solve(env, e, 100, stockOf());
        assertTrue(out.allOptimal);
        assertTrue(out.deficits.isEmpty(), "发射台键不应赤字: " + out.deficits);
        assertTrue(out.executions.isEmpty(), "发射台键不应有执行: " + out.executions);
    }

    /**
     * 迷你蛛网(50 键环 + 3 自增环)+ M4 种子校验:LP 首轮选无种子自举的 dup@45
     * (LP 语义最优 600),校验拦截 → 禁约束重解走有种子的 dup@25(种子 K25=1):
     * dup25=99 + 环流 25→49→0 各 100,总执行 2599,赤字空.
     */
    @Test
    public void miniWebEndToEnd() {
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
        env.addPattern(new ProcessingPatternBuilder(mult(keys[5], 2)).addPreciseInput(1, keys[5]).build());
        ICraftingPatternDetails dup25 = env.addPattern(new ProcessingPatternBuilder(mult(keys[25], 2))
                .addPreciseInput(1, keys[25]).build());
        ICraftingPatternDetails dup45 = env.addPattern(new ProcessingPatternBuilder(mult(keys[45], 2))
                .addPreciseInput(1, keys[45]).build());
        LpPlanOutcome out = solve(env, keys[0], 100, stockOf(keys[25], 1));
        assertTrue(out.allOptimal);
        assertTrue(out.deficits.isEmpty(), "种子重解后应满足全部需求: " + out.deficits);
        assertEquals(99.0, execOf(out, dup25), EPS);
        assertEquals(0.0, execOf(out, dup45), EPS);
        for (int i = 25; i <= 49; i++) {
            assertEquals(100.0, execOf(out, ring[i]), EPS, "交付环流 p" + i);
        }
        assertEquals(2599.0, totalExec(out), EPS);
    }

    /** M4:无种子自增环(1A→2A,零库存)——LP 自举解被拦截,4 轮内降级为如实赤字. */
    @Test
    public void noSeedDupDegradesToDeficit() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        ICraftingPatternDetails dup = env.addPattern(new ProcessingPatternBuilder(mult(a, 2))
                .addPreciseInput(1, a).build());
        LpPlanOutcome out = solve(env, a, 10, stockOf());
        assertEquals(1, out.deficits.size(), "无种子应如实缺料: " + out.deficits);
        assertEquals(10.0, out.deficits.get(canon(a)), EPS);
        assertEquals(0.0, execOf(out, dup), EPS);
    }

    /** M4:有种子自增环(stock 1)——校验通过,全额生产 dup=10(根库存不抵交付),无赤字. */
    @Test
    public void seededDupAccepted() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        ICraftingPatternDetails dup = env.addPattern(new ProcessingPatternBuilder(mult(a, 2))
                .addPreciseInput(1, a).build());
        LpPlanOutcome out = solve(env, a, 10, stockOf(a, 1));
        assertTrue(out.allOptimal);
        assertTrue(out.deficits.isEmpty(), "有种子应满足: " + out.deficits);
        assertEquals(10.0, execOf(out, dup), EPS);
    }

    /** M4:双键增殖环(1A→2B, 1B→1A)零库存不可启动 → 赤字;种子 B=1 可启动 → 无赤字. */
    @Test
    public void twoKeyRingSeedGate() {
        // 零库存:不可启动 → 赤字 A=8
        SimulationEnv env1 = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        env1.addPattern(new ProcessingPatternBuilder(mult(b, 2)).addPreciseInput(1, a).build());
        env1.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, b).build());
        LpPlanOutcome out1 = solve(env1, a, 8, stockOf());
        assertEquals(8.0, out1.deficits.get(canon(a)), EPS, "零库存应赤字: " + out1.deficits);

        // 种子 B=1:1B→1A→2B→2A→... 指数启动 → 无赤字
        // (守恒:x1=7 耗 7A 产 14B;x2=15 耗 15B=1 种子+14 产 15A;净交付 A=15−7=8)
        SimulationEnv env2 = new SimulationEnv();
        ICraftingPatternDetails p1 = env2.addPattern(new ProcessingPatternBuilder(mult(b, 2))
                .addPreciseInput(1, a).build());
        ICraftingPatternDetails p2 = env2.addPattern(new ProcessingPatternBuilder(a)
                .addPreciseInput(1, b).build());
        LpPlanOutcome out2 = solve(env2, a, 8, stockOf(b, 1));
        assertTrue(out2.deficits.isEmpty(), "种子 B=1 应可启动: " + out2.deficits);
        assertEquals(7.0, execOf(out2, p1), EPS);
        assertEquals(15.0, execOf(out2, p2), EPS);
    }

    /**
     * 共享原料扇入:根 Z 消耗 X 与 Y,X/Y 各自消耗同一原料 R.
     * 需求 Z=5 → R 总需求 10,库存 4 → 赤字 R=6(库存只计一次).
     */
    @Test
    public void sharedRawMaterialFanIn() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack z = block(Blocks.STONE);
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack y = block(Blocks.DIRT);
        IAEItemStack r = block(Blocks.SAND);
        env.addPattern(new ProcessingPatternBuilder(x).addPreciseInput(1, r).build());
        env.addPattern(new ProcessingPatternBuilder(y).addPreciseInput(1, r).build());
        env.addPattern(new ProcessingPatternBuilder(z)
                .addPreciseInput(1, x).addPreciseInput(1, y).build());
        LpPlanOutcome out = solve(env, z, 5, stockOf(r, 4));
        assertTrue(out.allOptimal);
        assertEquals(1, out.deficits.size(), "唯一赤字键 R: " + out.deficits);
        assertEquals(6.0, out.deficits.get(canon(r)), EPS);
    }

    /** 全额生产(原生 ignore(output) 语义):请求物自身库存不抵交付,
     * 需求 ≤ 库存也照常生产,零赤字. */
    @Test
    public void stockCoversDemand() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack x = block(Blocks.STONE);
        IAEItemStack a = block(Blocks.COBBLESTONE);
        ICraftingPatternDetails p = env.addPattern(
                new ProcessingPatternBuilder(x).addPreciseInput(1, a).build());
        LpPlanOutcome out = solve(env, x, 10, stockOf(x, 64, a, 64));
        assertTrue(out.allOptimal);
        assertTrue(out.deficits.isEmpty());
        assertEquals(10.0, execOf(out, p), EPS, "库存不抵交付,全额生产: " + out.executions);
    }

    /**
     * 理序素复现(多来源 + 成环,根在环内):L⇄C 1:1000 互转,L 另有第二来源 X→L,
     * 板 P 与 L 互压(同在 SCC).下单 L×100000,库存 C=900/L=414:
     * 应选 融化 C→L×100(种子 C 库存充足),无赤字.
     */
    @Test
    public void multiSourceCycleRootIntermediate() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack l = block(Blocks.STONE);       // 理序素
        IAEItemStack c = block(Blocks.COBBLESTONE); // 水晶
        IAEItemStack p = block(Blocks.DIRT);        // 板
        IAEItemStack x = block(Blocks.SAND);        // L 的第二来源原料
        ICraftingPatternDetails melt = env.addPattern(new ProcessingPatternBuilder(mult(l, 1000))
                .addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, mult(l, 1000)).build());
        env.addPattern(new ProcessingPatternBuilder(p).addPreciseInput(1, l).build());
        env.addPattern(new ProcessingPatternBuilder(l).addPreciseInput(1, p).build());
        env.addPattern(new ProcessingPatternBuilder(mult(l, 500)).addPreciseInput(1, x).build());
        LpPlanOutcome out = solve(env, l, 100000, stockOf(c, 900, l, 414));
        assertTrue(out.deficits.isEmpty(), "融化种子充足应满足: " + out.deficits);
        assertEquals(100.0, execOf(out, melt), EPS, "应选融化×100: " + out.executions);
    }

    /**
     * 理序素复现(根为板):同上网路,下单 P×1,库存 C=900/L=414:
     * 应选 压制 L→P×1(L 库存直供),无赤字、零额外生产.
     */
    @Test
    public void multiSourceCycleRootPlate() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack l = block(Blocks.STONE);
        IAEItemStack c = block(Blocks.COBBLESTONE);
        IAEItemStack p = block(Blocks.DIRT);
        IAEItemStack x = block(Blocks.SAND);
        env.addPattern(new ProcessingPatternBuilder(mult(l, 1000)).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, mult(l, 1000)).build());
        ICraftingPatternDetails press = env.addPattern(new ProcessingPatternBuilder(p)
                .addPreciseInput(1, l).build());
        env.addPattern(new ProcessingPatternBuilder(l).addPreciseInput(1, p).build());
        env.addPattern(new ProcessingPatternBuilder(mult(l, 500)).addPreciseInput(1, x).build());
        LpPlanOutcome out = solve(env, p, 1, stockOf(c, 900, l, 414));
        assertTrue(out.deficits.isEmpty(), "压板应满足: " + out.deficits);
        assertEquals(1.0, execOf(out, press), EPS, "应选压制×1: " + out.executions);
    }

    /**
     * 理序素复现加强版(互转球):5 键两两 1:1 互转构成大 SCC,其一与 L 1:1 互转,
     * L⇄C 1:1000,P⇄L 互压.下单 P×1,库存 C=900:大 SCC + 多来源下仍应选 压制×1.
     */
    @Test
    public void multiSourceCycleTransmutationBall() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack l = block(Blocks.STONE);
        IAEItemStack c = block(Blocks.COBBLESTONE);
        IAEItemStack p = block(Blocks.DIRT);
        Block[] palette = { Blocks.SAND, Blocks.GRAVEL, Blocks.LOG, Blocks.GLASS, Blocks.CLAY };
        IAEItemStack[] ball = new IAEItemStack[palette.length];
        for (int i = 0; i < ball.length; i++) {
            ball[i] = block(palette[i]);
        }
        for (int i = 0; i < ball.length; i++) {
            for (int j = 0; j < ball.length; j++) {
                if (i != j) {
                    env.addPattern(new ProcessingPatternBuilder(ball[j]).addPreciseInput(1, ball[i]).build());
                }
            }
        }
        env.addPattern(new ProcessingPatternBuilder(ball[0]).addPreciseInput(1, l).build());
        env.addPattern(new ProcessingPatternBuilder(l).addPreciseInput(1, ball[0]).build());
        env.addPattern(new ProcessingPatternBuilder(mult(l, 1000)).addPreciseInput(1, c).build());
        env.addPattern(new ProcessingPatternBuilder(c).addPreciseInput(1, mult(l, 1000)).build());
        ICraftingPatternDetails press = env.addPattern(new ProcessingPatternBuilder(p)
                .addPreciseInput(1, l).build());
        env.addPattern(new ProcessingPatternBuilder(l).addPreciseInput(1, p).build());
        LpPlanOutcome out = solve(env, p, 1, stockOf(c, 900));
        assertTrue(out.deficits.isEmpty(), "互转球中压板应满足: " + out.deficits);
        assertEquals(1.0, execOf(out, press), EPS, "应选压制×1: " + out.executions);
    }

    // ===== 工具 =====

    private static LpPlanOutcome solve(SimulationEnv env, IAEItemStack what, long target,
            Map<IAEItemStack, Long> stock) {
        return CondensationPlanner.solve(env.craftingGrid(), NetworkPatternIndex.of(env.craftingGrid()),
                what, target, stock);
    }

    private static double execOf(LpPlanOutcome out, ICraftingPatternDetails pattern) {
        double total = 0;
        for (SccLpSolve.Execution e : out.executions) {
            if (e.pattern == pattern) {
                total += e.count;
            }
        }
        return total;
    }

    private static double totalExec(LpPlanOutcome out) {
        double total = 0;
        for (SccLpSolve.Execution e : out.executions) {
            total += e.count;
        }
        return total;
    }

    private static IAEItemStack canon(IAEItemStack stack) {
        return RecursiveCraftingHelper.canon(stack);
    }

    private static Map<IAEItemStack, Long> stockOf(Object... kv) {
        Map<IAEItemStack, Long> stock = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            stock.put(canon((IAEItemStack) kv[i]), ((Number) kv[i + 1]).longValue());
        }
        return stock;
    }

    private static IAEItemStack block(Block block) {
        return AEItemStack.fromItemStack(new ItemStack(block));
    }

    private static IAEItemStack mult(IAEItemStack template, long multiplier) {
        IAEItemStack copy = template.copy();
        copy.setStackSize(template.getStackSize() * multiplier);
        return copy;
    }
}
