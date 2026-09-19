package com.github.aeddddd.ae2enhanced.test.lp;

import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.execOf;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.solve;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.stockOf;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.block;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpResult;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpModelBuilder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.SccSolution;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 退化行预处理(重复守恒行差分)的直测.
 * <p>生产实证(569 键肉丸单元,lpm 20260912-123222-1):模型含多组完全相同结构行
 * (可互键被同一样板以相同数量消耗/产出),其盈余/赤字单位列同时离基时结构子矩阵
 * 秩亏 → 基奇异 NUMERIC_FAILURE,四条轨迹全灭.预处理把多余行替换为与首行的差行
 * (行操作,可行域不变)后同模型离线回放 OPTIMAL.</p>
 */
public class DuplicateRowPresolveTest {

    /**
     * 完全相同结构行:p1(1A→1B+1C)与 p4(1B+1C→1D)使行 B/C 的结构签名逐列相同
     * (B:+1@p1 −1@p4;C:+1@p1 −1@p4).请求 D×4、库存 A=4.
     * 断言:预处理触发(差行结构清空)且解正确(OPTIMAL、零赤字、p1=4、p4=4).
     */
    @Test
    public void identicalStructureRowsPresolved() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        IAEItemStack c = block(Blocks.DIRT);
        IAEItemStack d = block(Blocks.SAND);
        ICraftingPatternDetails p1 = env.addPattern(new ProcessingPatternBuilder(b, c)
                .addPreciseInput(1, a).build());
        ICraftingPatternDetails p4 = env.addPattern(new ProcessingPatternBuilder(d)
                .addPreciseInput(1, b).addPreciseInput(1, c).build());

        // 构建器直测:行 B/C 结构签名相同,差分后行 C 的结构内容应被清空
        NetworkPatternIndex index = NetworkPatternIndex.of(env.craftingGrid());
        IAEItemStack cb = RecursiveCraftingHelper.canon(b);
        IAEItemStack cc = RecursiveCraftingHelper.canon(c);
        List<IAEItemStack> keys = java.util.Arrays.asList(
                RecursiveCraftingHelper.canon(a), cb, cc, RecursiveCraftingHelper.canon(d));
        Map<IAEItemStack, Double> demands = new java.util.HashMap<>();
        demands.put(RecursiveCraftingHelper.canon(d), 4.0);
        SccLpModelBuilder.Built built = SccLpModelBuilder.build(env.craftingGrid(), index, keys,
                stockOf(a, 4), demands, Collections.emptyMap(), Collections.emptyMap(), true);
        int rowC = built.keys.indexOf(cc);
        assertTrue(rowC >= 0, "键 C 应在行号空间");
        for (int j = 0; j < built.vars.size(); j++) {
            for (int p = built.lp.a.colPtr[j]; p < built.lp.a.colPtr[j + 1]; p++) {
                assertTrue(built.lp.a.rowIdx[p] != rowC,
                        "差分后重复行 C 在结构列 " + j + " 仍有系数 " + built.lp.a.values[p]);
            }
        }

        // 全链路:解正确
        LpPlanOutcome out = solve(env, d, 4, stockOf(a, 4));
        System.out.printf("[DUPROW] allOptimal=%s degraded=%d deficits=%s p1=%.0f p4=%.0f%n",
                out.allOptimal, out.degradedUnits, out.deficits, execOf(out, p1), execOf(out, p4));
        assertTrue(out.allOptimal, "应全程最优: " + out.degradedReasons);
        assertTrue(out.deficits.isEmpty(), "可行场景不应赤字: " + out.deficits);
        assertEquals(4.0, execOf(out, p1), 1e-4, "p1 执行数");
        assertEquals(4.0, execOf(out, p4), 1e-4, "p4 执行数");

        // 同单元直接求解交叉验证(双保险:SccLpSolve 口径)
        SccSolution sol = SccLpSolve.solve(env.craftingGrid(), index, keys, stockOf(a, 4), demands,
                Collections.emptyMap(), Collections.emptyMap());
        assertEquals(LpResult.Status.OPTIMAL, sol.status);
        assertTrue(sol.deficits.isEmpty(), "SccLpSolve 口径不应赤字: " + sol.deficits);
    }
}
