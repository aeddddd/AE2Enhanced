package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.NetworkCraftingSimulationState;

/**
 * AE2 合成计算内部成员的反射桥.
 * <p><b>为什么不用 mixin accessor</b>:{@link SpecialCraftingCalculation} 必须同时运行在
 * 游戏内（mixin 环境）与纯 JUnit 单元测试（无 mixin 环境,accessor 接口的强制转换会
 * ClassCastException 并导致时间片死锁）.反射在两种环境下行为一致,因此特殊配方包
 * 统一经本桥访问 AE2 包私有/私有成员.项目其余仅在 mixin 环境执行的代码仍使用 accessor.</p>
 * <p>所有成员名在初始化时一次性解析并校验,AE2 升级导致签名变化时在首次调用即
 * 抛出明确异常（路由层捕获后回落原生行为）.</p>
 */
public final class Ae2CraftingReflect {

    private static final Field NETWORK_INV;
    private static final Field SIMULATE;
    private static final Field LEVEL;
    private static final Method COMPUTE_PLAN;
    private static final Method FINISH;
    private static final Method HANDLE_PAUSING;
    private static final Method ADD_MISSING;
    private static final Method TREE_PROCESS_REQUEST;

    static {
        try {
            NETWORK_INV = CraftingCalculation.class.getDeclaredField("networkInv");
            NETWORK_INV.setAccessible(true);
            SIMULATE = CraftingCalculation.class.getDeclaredField("simulate");
            SIMULATE.setAccessible(true);
            LEVEL = CraftingCalculation.class.getDeclaredField("level");
            LEVEL.setAccessible(true);
            COMPUTE_PLAN = CraftingCalculation.class.getDeclaredMethod("computePlan");
            COMPUTE_PLAN.setAccessible(true);
            FINISH = CraftingCalculation.class.getDeclaredMethod("finish");
            FINISH.setAccessible(true);
            HANDLE_PAUSING = CraftingCalculation.class.getDeclaredMethod("handlePausing");
            HANDLE_PAUSING.setAccessible(true);
            ADD_MISSING = CraftingCalculation.class.getDeclaredMethod("addMissing",
                    appeng.api.stacks.AEKey.class, long.class);
            ADD_MISSING.setAccessible(true);
            TREE_PROCESS_REQUEST = CraftingTreeProcess.class.getDeclaredMethod("request",
                    CraftingSimulationState.class, long.class);
            TREE_PROCESS_REQUEST.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Ae2CraftingReflect() {
    }

    public static NetworkCraftingSimulationState getNetworkInv(CraftingCalculation calc) {
        try {
            return (NetworkCraftingSimulationState) NETWORK_INV.get(calc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingCalculation.networkInv 失败", e);
        }
    }

    /** 计算所在 level(模板 isValid 过滤等需要真实 level 的调用方使用). */
    public static net.minecraft.world.level.Level getLevel(CraftingCalculation calc) {
        try {
            return (net.minecraft.world.level.Level) LEVEL.get(calc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingCalculation.level 失败", e);
        }
    }

    /**
     * 置位 simulation 标志.原生由失败重试(runCraftAttempt(true, ...))置位;
     * 自有求解器缺料不抛异常,记录缺料后须显式置位,
     * 否则产出"有缺料却标记可提交"的不一致计划.
     */
    public static void setSimulate(CraftingCalculation calc, boolean value) {
        try {
            SIMULATE.setBoolean(calc, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingCalculation.simulate 失败", e);
        }
    }

    public static ICraftingPlan computePlan(CraftingCalculation calc) throws InterruptedException {
        try {
            return (ICraftingPlan) COMPUTE_PLAN.invoke(calc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingCalculation.computePlan 失败", e);
        }
    }

    public static void finish(CraftingCalculation calc) {
        try {
            FINISH.invoke(calc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingCalculation.finish 失败", e);
        }
    }

    public static void handlePausing(CraftingCalculation calc) throws InterruptedException {
        try {
            HANDLE_PAUSING.invoke(calc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingCalculation.handlePausing 失败", e);
        }
    }

    public static void addMissing(CraftingCalculation calc, appeng.api.stacks.AEKey what, long amount) {
        try {
            ADD_MISSING.invoke(calc, what, amount);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingCalculation.addMissing 失败", e);
        }
    }

    static void treeProcessRequest(CraftingTreeProcess pro, CraftingSimulationState inv, long times)
            throws CraftBranchFailure, InterruptedException {
        try {
            TREE_PROCESS_REQUEST.invoke(pro, inv, times);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 还原原生受检异常,保持调用方语义
            if (e.getCause() instanceof CraftBranchFailure failure) {
                throw failure;
            }
            if (e.getCause() instanceof InterruptedException interrupted) {
                throw interrupted;
            }
            throw new IllegalStateException("CraftingTreeProcess.request 执行异常", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingTreeProcess.request 失败", e);
        }
    }
}
