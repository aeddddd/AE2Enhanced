package com.github.aeddddd.ae2enhanced.test.support;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

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
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;

/**
 * LP 直调测试的公共助手(原散落在 CondensationPlannerTest/SccLpSolveTest/
 * LpPlanMaterializationTest/ExtremeInterlinkedCycleTest/MassiveKindCycleStressTest
 * 五处的逐字复制统一收纳).
 */
public final class LpTestSupport {

    /** 相对容差(与 X/W 组压测口径一致). */
    private static final double REL = 1e-6;

    private LpTestSupport() {
    }

    /** 经冷凝分层驱动器直接求解(不经 LpCraftingJob 编排). */
    public static LpPlanOutcome solve(SimulationEnv env, IAEItemStack what, long target,
            Map<IAEItemStack, Long> stock) {
        return CondensationPlanner.solve(env.craftingGrid(), NetworkPatternIndex.of(env.craftingGrid()),
                what, target, stock);
    }

    /** 指定样板的总执行次数(LP outcome 浮点口径). */
    public static double execOf(LpPlanOutcome out, ICraftingPatternDetails pattern) {
        double total = 0;
        for (SccLpSolve.Execution e : out.executions) {
            if (e.pattern == pattern) {
                total += e.count;
            }
        }
        return total;
    }

    /** 全部样板的总执行次数. */
    public static double totalExec(LpPlanOutcome out) {
        double total = 0;
        for (SccLpSolve.Execution e : out.executions) {
            total += e.count;
        }
        return total;
    }

    /** 键规范化(与主源码 canon 同口径). */
    public static IAEItemStack canon(IAEItemStack stack) {
        return RecursiveCraftingHelper.canon(stack);
    }

    /** 键值对构造库存映射(canon 后键控). */
    public static Map<IAEItemStack, Long> stockOf(Object... kv) {
        Map<IAEItemStack, Long> stock = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            stock.put(canon((IAEItemStack) kv[i]), ((Number) kv[i + 1]).longValue());
        }
        return stock;
    }

    /** 大数值闭式解断言:相对 1e-6,地板 64(≫ 1e17 量级 double ULP=16,≪量级). */
    public static void assertClose(double expected, double actual, String label) {
        double tol = Math.max(64, Math.abs(expected) * REL);
        Assertions.assertEquals(expected, actual, tol,
                label + " 期望 " + (long) expected + " 实际 " + (long) actual);
    }

    /**
     * 迷你蛛网夹具(50 键环 + dup@5/25/45,三处分层测试共用构造):
     * SccLpSolveTest(LP 裸语义)/CondensationPlannerTest(M4 重解)/
     * LpPlanMaterializationTest(物化整数).
     */
    public static MiniWeb buildMiniWeb(SimulationEnv env) {
        MiniWeb web = new MiniWeb();
        web.keys = new IAEItemStack[50];
        Block[] palette = { Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.PLANKS, Blocks.SAND,
                Blocks.GRAVEL, Blocks.LOG, Blocks.GLASS, Blocks.CLAY, Blocks.BRICK_BLOCK };
        for (int i = 0; i < web.keys.length; i++) {
            web.keys[i] = AEItemStack.fromItemStack(new ItemStack(palette[i % palette.length], 1,
                    i / palette.length));
        }
        web.ring = new ICraftingPatternDetails[50];
        for (int i = 0; i < 50; i++) {
            web.ring[i] = env.addPattern(new ProcessingPatternBuilder(web.keys[(i + 1) % 50])
                    .addPreciseInput(1, web.keys[i]).build());
        }
        web.dup5 = env.addPattern(new ProcessingPatternBuilder(mult(web.keys[5], 2))
                .addPreciseInput(1, web.keys[5]).build());
        web.dup25 = env.addPattern(new ProcessingPatternBuilder(mult(web.keys[25], 2))
                .addPreciseInput(1, web.keys[25]).build());
        web.dup45 = env.addPattern(new ProcessingPatternBuilder(mult(web.keys[45], 2))
                .addPreciseInput(1, web.keys[45]).build());
        return web;
    }

    /** 倍数复制(multiplicative 语义,与 {@link SimulationEnv#mult} 一致). */
    public static IAEItemStack mult(IAEItemStack template, long multiplier) {
        return SimulationEnv.mult(template, multiplier);
    }

    /** 迷你蛛网夹具的句柄(键数组/环样板/三个 dup 样板). */
    public static final class MiniWeb {
        public IAEItemStack[] keys;
        public ICraftingPatternDetails[] ring;
        public ICraftingPatternDetails dup5;
        public ICraftingPatternDetails dup25;
        public ICraftingPatternDetails dup45;

        private MiniWeb() {
        }
    }
}
